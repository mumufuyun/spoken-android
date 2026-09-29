/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.view.KeyEvent
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import androidx.core.view.descendants
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.KeyState
import org.fcitx.fcitx5.android.core.KeyStates
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.keyboard.KeyDef.Appearance.Border
import org.fcitx.fcitx5.android.input.keyboard.KeyDef.Appearance.Variant
import org.fcitx.fcitx5.android.input.voice.SpokenVoiceState
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.above
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.leftOfParent
import splitties.views.dsl.constraintlayout.leftToRightOf
import splitties.views.dsl.constraintlayout.rightOfParent
import splitties.views.dsl.constraintlayout.rightToLeftOf
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add
import splitties.views.imageResource
import splitties.views.padding

private class TouchT9DigitKey(
    letters: String,
    keyCode: Int
) : KeyDef(
    Appearance.Text(
        displayText = letters,
        textSize = 25f,
        percentWidth = 0.333f,
        border = Border.On,
        margin = false
    ),
    setOf(Behavior.Press(KeyAction.VirtualT9KeyAction(keyCode)))
)

private class TouchT9ReadingKey(slot: Int) : KeyDef(
    Appearance.Text(
        displayText = "",
        textSize = 17f,
        textStyle = Typeface.BOLD,
        percentWidth = 1f,
        variant = Variant.Alternative,
        border = Border.On,
        margin = false
    ),
    setOf(Behavior.Press(KeyAction.T9ReadingSlotAction(slot)))
)

private class TouchT9ModeKey : KeyDef(
    Appearance.Text(
        displayText = "中/英",
        textSize = 14f,
        textStyle = Typeface.BOLD,
        percentWidth = 0.15f,
        variant = Variant.Normal,
        border = Border.On,
        margin = false,
        viewId = R.id.button_lang
    ),
    setOf(Behavior.Press(KeyAction.LangSwitchAction))
)

private class TouchT9VoiceKey : KeyDef(
    Appearance.Image(
        src = R.drawable.ic_spoken_waveform_36,
        variant = Variant.Accent,
        border = Border.On,
        margin = false
    ),
    setOf(Behavior.Press(KeyAction.SpokenVoiceAction)),
    emptyArray()
)

private class TouchQwertyKey(
    val character: String,
    percentWidth: Float
) : KeyDef(
    Appearance.Text(
        displayText = character,
        textSize = 22f,
        percentWidth = percentWidth,
        border = Border.On,
        margin = false
    ),
    setOf(Behavior.Press(KeyAction.FcitxKeyAction(character)))
)

private class TouchCapsKey : KeyDef(
    Appearance.Image(
        src = R.drawable.ic_capslock_none,
        percentWidth = 0.15f,
        variant = Variant.Alternative,
        border = Border.On,
        margin = false,
        viewId = R.id.button_caps
    ),
    setOf(
        Behavior.Press(KeyAction.CapsAction(false)),
        Behavior.LongPress(KeyAction.CapsAction(true)),
        Behavior.DoubleTap(KeyAction.CapsAction(true))
    )
)

private enum class TouchPanel {
    TEXT,
    NUMBER,
    SYMBOL_PRIMARY,
    SYMBOL_SECONDARY
}

private class TouchPanelKey(
    label: String,
    panel: TouchPanel,
    percentWidth: Float,
    variant: Variant = Variant.Alternative
) : KeyDef(
    Appearance.Text(
        displayText = label,
        textSize = 15f,
        textStyle = Typeface.BOLD,
        percentWidth = percentWidth,
        variant = variant,
        border = Border.On,
        margin = false
    ),
    setOf(Behavior.Press(KeyAction.TouchPanelAction(panel.name)))
)

private class TouchCommitKey(
    label: String,
    percentWidth: Float,
    variant: Variant = Variant.Normal,
    textSize: Float = 22f
) : KeyDef(
    Appearance.Text(
        displayText = label,
        textSize = textSize,
        percentWidth = percentWidth,
        variant = variant,
        border = Border.On,
        margin = false
    ),
    setOf(Behavior.Press(KeyAction.CommitAction(label)))
)

/**
 * Touch-first T9 layout built on a stable four-row grid.
 *
 * Punctuation and digits occupy the first three rows. Bottom controls occupy row four. Delete
 * aligns with digit row one, the voice target spans digit rows two and three, and Return aligns
 * with the bottom controls. The waveform key stays presentation-only until the in-process Spoken
 * voice pipeline replaces the legacy external-IME handoff.
 */
@SuppressLint("ViewConstructor")
class T9Keyboard(
    context: Context,
    theme: Theme
) : BaseKeyboard(context, theme, emptyList(), keyPreviewEnabled = false) {

    private enum class CapsState { NONE, ONCE, LOCK }

    companion object {
        const val Name = "T9"
        private const val TOUCH_KEY_GAP_DP = 2
        private const val TOUCH_BOTTOM_KEY_VERTICAL_INSET_DP = 2
        private const val TOUCH_BOTTOM_ROW_HEIGHT_DP = 48
    }

    private fun createTouchKeyView(def: KeyDef): KeyView = createKeyView(def).apply {
        // Keep the whole cell tappable while reducing the visible gap between adjacent keycaps.
        padding = dp(TOUCH_KEY_GAP_DP)
    }

    private fun createCompactBottomKeyView(def: KeyDef): KeyView = createKeyView(def).apply {
        setPadding(
            dp(TOUCH_KEY_GAP_DP),
            dp(TOUCH_BOTTOM_KEY_VERTICAL_INSET_DP),
            dp(TOUCH_KEY_GAP_DP),
            dp(TOUCH_BOTTOM_KEY_VERTICAL_INSET_DP)
        )
    }

    private fun horizontalRow(
        definitions: List<KeyDef>,
        compactBottomKeycaps: Boolean = false
    ): ConstraintLayout = constraintLayout {
        val views = definitions.map { definition ->
            if (compactBottomKeycaps && definition !is SpaceKey) {
                createCompactBottomKeyView(definition)
            } else {
                createTouchKeyView(definition)
            }
        }
        views.forEachIndexed { index, view ->
            add(view, lParams {
                centerVertically()
                if (index == 0) {
                    leftOfParent()
                    horizontalChainStyle = ConstraintLayout.LayoutParams.CHAIN_PACKED
                } else {
                    leftToRightOf(views[index - 1])
                }
                if (index == views.lastIndex) {
                    rightOfParent()
                } else {
                    rightToLeftOf(views[index + 1])
                }
                matchConstraintPercentWidth = definitions[index].appearance.percentWidth
            })
        }
    }

    private fun verticalColumn(definitions: List<KeyDef>): ConstraintLayout =
        verticalViewColumn(definitions.map(::createTouchKeyView))

    private fun verticalViewColumn(views: List<KeyView>): ConstraintLayout = constraintLayout {
        views.forEachIndexed { index, view ->
            add(view, lParams {
                centerHorizontally()
                if (index == 0) {
                    topOfParent()
                    verticalChainStyle = ConstraintLayout.LayoutParams.CHAIN_PACKED
                } else {
                    below(views[index - 1])
                }
                if (index == views.lastIndex) {
                    bottomOfParent()
                } else {
                    above(views[index + 1])
                }
                matchConstraintPercentHeight = 1f / views.size
            })
        }
    }

    private val punctuationRail = verticalColumn(
        listOf(
            SymbolKey(",", variant = Variant.Alternative, border = Border.On, margin = false),
            SymbolKey(".", variant = Variant.Alternative, border = Border.On, margin = false),
            SymbolKey("?", variant = Variant.Alternative, border = Border.On, margin = false),
            SymbolKey("!", variant = Variant.Alternative, border = Border.On, margin = false)
        )
    )

    private val pinyinChoiceViews = (0 until 4).map { slot ->
        createTouchKeyView(TouchT9ReadingKey(slot)) as TextKeyView
    }
    private val pinyinRail = verticalViewColumn(pinyinChoiceViews).apply {
        visibility = View.GONE
    }

    private val leftRailSurface = constraintLayout {
        add(punctuationRail, lParams {
            leftOfParent()
            rightOfParent()
            topOfParent()
            bottomOfParent()
        })
        add(pinyinRail, lParams {
            leftOfParent()
            rightOfParent()
            topOfParent()
            bottomOfParent()
        })
    }

    private val digitRows = listOf(
        horizontalRow(
            listOf(
                TouchT9DigitKey("@#", KeyEvent.KEYCODE_1),
                TouchT9DigitKey("ABC", KeyEvent.KEYCODE_2),
                TouchT9DigitKey("DEF", KeyEvent.KEYCODE_3)
            )
        ),
        horizontalRow(
            listOf(
                TouchT9DigitKey("GHI", KeyEvent.KEYCODE_4),
                TouchT9DigitKey("JKL", KeyEvent.KEYCODE_5),
                TouchT9DigitKey("MNO", KeyEvent.KEYCODE_6)
            )
        ),
        horizontalRow(
            listOf(
                TouchT9DigitKey("PQRS", KeyEvent.KEYCODE_7),
                TouchT9DigitKey("TUV", KeyEvent.KEYCODE_8),
                TouchT9DigitKey("WXYZ", KeyEvent.KEYCODE_9)
            )
        )
    )

    private val digitSurface = constraintLayout {
        digitRows.forEachIndexed { index, row ->
            add(row, lParams {
                centerHorizontally()
                if (index == 0) {
                    topOfParent()
                    verticalChainStyle = ConstraintLayout.LayoutParams.CHAIN_PACKED
                } else {
                    below(digitRows[index - 1])
                }
                if (index == digitRows.lastIndex) {
                    bottomOfParent()
                } else {
                    above(digitRows[index + 1])
                }
                matchConstraintPercentHeight = 1f / digitRows.size
            })
        }
    }

    private val qwertyLetterViews = mutableListOf<TextKeyView>()

    private fun qwertyRow(characters: String, keyWidth: Float = 1f / characters.length) =
        horizontalRow(
            characters.map { character ->
                TouchQwertyKey(character.toString(), keyWidth)
            }
        ).also { row ->
            row.children.filterIsInstance<TextKeyView>().forEach(qwertyLetterViews::add)
        }

    private val qwertyRows = listOf(
        qwertyRow("qwertyuiop"),
        qwertyRow("asdfghjkl"),
        horizontalRow(
            listOf(TouchCapsKey()) +
                "zxcvbnm".map { TouchQwertyKey(it.toString(), 0.85f / 7f) }
        ).also { row ->
            row.children.filterIsInstance<TextKeyView>().forEach(qwertyLetterViews::add)
        }
    )

    private val qwertySurface = constraintLayout {
        qwertyRows.forEachIndexed { index, row ->
            add(row, lParams {
                centerHorizontally()
                if (index == 0) {
                    topOfParent()
                    verticalChainStyle = ConstraintLayout.LayoutParams.CHAIN_PACKED
                } else {
                    below(qwertyRows[index - 1])
                }
                if (index == qwertyRows.lastIndex) {
                    bottomOfParent()
                } else {
                    above(qwertyRows[index + 1])
                }
                matchConstraintPercentHeight = 1f / qwertyRows.size
            })
        }
    }

    private val numberOperatorRail = verticalColumn(
        listOf(
            TouchCommitKey("+", 1f, Variant.Alternative),
            TouchCommitKey("-", 1f, Variant.Alternative),
            TouchCommitKey("×", 1f, Variant.Alternative),
            TouchCommitKey("÷", 1f, Variant.Alternative)
        )
    )

    private val numberRows = listOf(
        horizontalRow((1..3).map { TouchCommitKey(it.toString(), 1f / 3f, textSize = 27f) }),
        horizontalRow((4..6).map { TouchCommitKey(it.toString(), 1f / 3f, textSize = 27f) }),
        horizontalRow((7..9).map { TouchCommitKey(it.toString(), 1f / 3f, textSize = 27f) })
    )

    private val numberDigitSurface = constraintLayout {
        numberRows.forEachIndexed { index, row ->
            add(row, lParams {
                centerHorizontally()
                if (index == 0) {
                    topOfParent()
                    verticalChainStyle = ConstraintLayout.LayoutParams.CHAIN_PACKED
                } else {
                    below(numberRows[index - 1])
                }
                if (index == numberRows.lastIndex) bottomOfParent() else above(numberRows[index + 1])
                matchConstraintPercentHeight = 1f / numberRows.size
            })
        }
    }

    private val numberSurface = constraintLayout {
        add(numberOperatorRail, lParams {
            leftOfParent()
            topOfParent()
            bottomOfParent()
            matchConstraintPercentWidth = 0.18f
        })
        add(numberDigitSurface, lParams {
            leftToRightOf(numberOperatorRail)
            rightOfParent()
            topOfParent()
            bottomOfParent()
        })
    }

    private fun symbolSurface(rows: List<List<String>>) = constraintLayout {
        val rowViews = rows.map { symbols ->
            horizontalRow(symbols.map { TouchCommitKey(it, 1f / symbols.size, textSize = 20f) })
        }
        rowViews.forEachIndexed { index, row ->
            add(row, lParams {
                centerHorizontally()
                if (index == 0) {
                    topOfParent()
                    verticalChainStyle = ConstraintLayout.LayoutParams.CHAIN_PACKED
                } else {
                    below(rowViews[index - 1])
                }
                if (index == rowViews.lastIndex) bottomOfParent() else above(rowViews[index + 1])
                matchConstraintPercentHeight = 1f / rowViews.size
            })
        }
    }

    private val primarySymbolSurface = symbolSurface(
        listOf(
            listOf("，", "。", "？", "！", "：", "；", "、"),
            listOf("“", "”", "‘", "’", "（", "）", "…"),
            listOf("@", "#", "%", "&", "+", "-", "=")
        )
    )

    private val secondarySymbolSurface = symbolSurface(
        listOf(
            listOf("[", "]", "{", "}", "<", ">", "/"),
            listOf("\\", "|", "_", "~", "^", "*", "·"),
            listOf("￥", "$", "€", "£", "℃", "°", "✓")
        )
    )

    private val bottomRow = horizontalRow(
        listOf(
            TouchPanelKey("符号", TouchPanel.SYMBOL_PRIMARY, 0.18f),
            TouchPanelKey("123", TouchPanel.NUMBER, 0.15f, Variant.Normal),
            SpaceKey(0.52f, Border.On, margin = false, displayText = "spoken"),
            TouchT9ModeKey()
        ),
        compactBottomKeycaps = true
    )

    private val numberBottomRow = horizontalRow(
        listOf(
            TouchPanelKey("符号", TouchPanel.SYMBOL_PRIMARY, 0.18f),
            TouchPanelKey("ABC", TouchPanel.TEXT, 0.15f),
            TouchCommitKey("0", 0.52f, textSize = 25f),
            TouchCommitKey(".", 0.15f, Variant.Alternative)
        ),
        compactBottomKeycaps = true
    )

    private val primarySymbolBottomRow = horizontalRow(
        listOf(
            TouchPanelKey("123", TouchPanel.NUMBER, 0.18f),
            TouchPanelKey("ABC", TouchPanel.TEXT, 0.15f),
            TouchPanelKey("1/2", TouchPanel.SYMBOL_SECONDARY, 0.15f, Variant.Normal),
            SpaceKey(0.52f, Border.On, margin = false, displayText = "spoken")
        ),
        compactBottomKeycaps = true
    )

    private val secondarySymbolBottomRow = horizontalRow(
        listOf(
            TouchPanelKey("123", TouchPanel.NUMBER, 0.18f),
            TouchPanelKey("ABC", TouchPanel.TEXT, 0.15f),
            TouchPanelKey("2/2", TouchPanel.SYMBOL_PRIMARY, 0.15f, Variant.Normal),
            SpaceKey(0.52f, Border.On, margin = false, displayText = "spoken")
        ),
        compactBottomKeycaps = true
    )

    private val backspace = createTouchKeyView(BackspaceKey(border = Border.On, margin = false))
    private val voice = createTouchKeyView(TouchT9VoiceKey()).apply { id = R.id.button_voice }
    private val returnKey = createTouchKeyView(ReturnKey(border = Border.On, margin = false))

    private val rightActionSurface = constraintLayout {
        add(backspace, lParams {
            leftOfParent()
            rightOfParent()
            topOfParent()
            matchConstraintPercentWidth = 1f
            matchConstraintPercentHeight = 1f / 3f
        })
        add(voice, lParams {
            leftOfParent()
            rightOfParent()
            below(backspace)
            bottomOfParent()
            matchConstraintPercentWidth = 1f
        })
    }

    init {
        add(leftRailSurface, lParams {
            leftOfParent()
            topOfParent()
            above(bottomRow)
            matchConstraintPercentWidth = 0.15f
        })
        add(digitSurface, lParams {
            leftToRightOf(leftRailSurface)
            rightToLeftOf(rightActionSurface)
            topOfParent()
            above(bottomRow)
        })
        add(qwertySurface, lParams {
            leftOfParent()
            rightToLeftOf(rightActionSurface)
            topOfParent()
            above(bottomRow)
        })
        add(numberSurface, lParams {
            leftOfParent()
            rightToLeftOf(rightActionSurface)
            topOfParent()
            above(numberBottomRow)
        })
        add(primarySymbolSurface, lParams {
            leftOfParent()
            rightToLeftOf(rightActionSurface)
            topOfParent()
            above(primarySymbolBottomRow)
        })
        add(secondarySymbolSurface, lParams {
            leftOfParent()
            rightToLeftOf(rightActionSurface)
            topOfParent()
            above(secondarySymbolBottomRow)
        })
        add(rightActionSurface, lParams {
            rightOfParent()
            topOfParent()
            above(returnKey)
            matchConstraintPercentWidth = 0.16f
        })
        add(bottomRow, lParams {
            leftOfParent()
            rightToLeftOf(returnKey)
            bottomOfParent()
            height = dp(TOUCH_BOTTOM_ROW_HEIGHT_DP)
        })
        listOf(numberBottomRow, primarySymbolBottomRow, secondarySymbolBottomRow).forEach { row ->
            add(row, lParams {
                leftOfParent()
                rightToLeftOf(returnKey)
                bottomOfParent()
                height = dp(TOUCH_BOTTOM_ROW_HEIGHT_DP)
            })
        }
        add(returnKey, lParams {
            rightOfParent()
            bottomOfParent()
            matchConstraintPercentWidth = 0.16f
            height = dp(TOUCH_BOTTOM_ROW_HEIGHT_DP)
        })
        descendants.filterIsInstance<TextKeyView>()
            .filter { it.def.viewId == R.id.button_space }
            .forEach { space ->
                space.mainText.apply {
                    alpha = 0.58f
                    letterSpacing = 0.08f
                    setTypeface(typeface, Typeface.BOLD_ITALIC)
                }
            }
        showPanel(TouchPanel.TEXT)
    }

    private val mode: TextKeyView by lazy { findViewById(R.id.button_lang) }
    private val returnView: ImageKeyView by lazy { findViewById(R.id.button_return) }
    private val capsView: ImageKeyView by lazy { findViewById(R.id.button_caps) }
    private var capsState = CapsState.NONE
    private var currentModeLabel = "中"
    private var currentPanel = TouchPanel.TEXT

    override fun onAction(action: KeyAction, source: KeyActionListener.Source) {
        var transformed = action
        when (action) {
            is KeyAction.TouchPanelAction -> {
                showPanel(TouchPanel.valueOf(action.panel))
                return
            }
            is KeyAction.CapsAction -> {
                capsState = if (action.lock) {
                    if (capsState == CapsState.LOCK) CapsState.NONE else CapsState.LOCK
                } else {
                    if (capsState == CapsState.NONE) CapsState.ONCE else CapsState.NONE
                }
                updateQwertyCaps()
                return
            }
            is KeyAction.FcitxKeyAction -> if (
                currentPanel == TouchPanel.TEXT &&
                currentModeLabel == "英" &&
                action.act.length == 1 &&
                action.act[0].isLetter()
            ) {
                transformed = when (capsState) {
                    CapsState.NONE -> action.copy(act = action.act.lowercase())
                    CapsState.ONCE -> action.copy(
                        act = action.act.uppercase(),
                        states = KeyStates(KeyState.Virtual, KeyState.Shift)
                    ).also {
                        capsState = CapsState.NONE
                        updateQwertyCaps()
                    }
                    CapsState.LOCK -> action.copy(
                        act = action.act.uppercase(),
                        states = KeyStates(KeyState.Virtual, KeyState.CapsLock)
                    )
                }
            }
            else -> Unit
        }
        super.onAction(transformed, source)
    }

    private fun updateQwertyCaps() {
        val uppercase = capsState != CapsState.NONE
        qwertyLetterViews.forEach { view ->
            val character = (view.def as KeyDef.Appearance.Text).displayText
            view.mainText.text = if (uppercase) character.uppercase() else character.lowercase()
        }
        capsView.img.imageResource = when (capsState) {
            CapsState.NONE -> R.drawable.ic_capslock_none
            CapsState.ONCE -> R.drawable.ic_capslock_once
            CapsState.LOCK -> R.drawable.ic_capslock_lock
        }
    }

    private fun showPanel(panel: TouchPanel) {
        currentPanel = panel
        val showEnglish = panel == TouchPanel.TEXT && currentModeLabel == "英"
        leftRailSurface.visibility = if (panel == TouchPanel.TEXT && !showEnglish) View.VISIBLE else View.GONE
        digitSurface.visibility = if (panel == TouchPanel.TEXT && !showEnglish) View.VISIBLE else View.GONE
        qwertySurface.visibility = if (showEnglish) View.VISIBLE else View.GONE
        numberSurface.visibility = if (panel == TouchPanel.NUMBER) View.VISIBLE else View.GONE
        primarySymbolSurface.visibility = if (panel == TouchPanel.SYMBOL_PRIMARY) View.VISIBLE else View.GONE
        secondarySymbolSurface.visibility = if (panel == TouchPanel.SYMBOL_SECONDARY) View.VISIBLE else View.GONE
        bottomRow.visibility = if (panel == TouchPanel.TEXT) View.VISIBLE else View.GONE
        numberBottomRow.visibility = if (panel == TouchPanel.NUMBER) View.VISIBLE else View.GONE
        primarySymbolBottomRow.visibility = if (panel == TouchPanel.SYMBOL_PRIMARY) View.VISIBLE else View.GONE
        secondarySymbolBottomRow.visibility = if (panel == TouchPanel.SYMBOL_SECONDARY) View.VISIBLE else View.GONE
    }

    override fun onReturnDrawableUpdate(returnDrawable: Int) {
        returnView.img.imageResource = returnDrawable
    }

    fun updateT9ModeLabel(modeLabel: String) {
        currentModeLabel = modeLabel
        mode.mainText.text = modeLabel
        showPanel(TouchPanel.TEXT)
    }

    fun updateT9ReadingOptions(options: List<String>) {
        val showPinyin = options.isNotEmpty()
        punctuationRail.visibility = if (showPinyin) View.GONE else View.VISIBLE
        pinyinRail.visibility = if (showPinyin) View.VISIBLE else View.GONE
        pinyinChoiceViews.forEachIndexed { index, view ->
            val reading = options.getOrNull(index)
            view.mainText.text = reading.orEmpty()
            view.visibility = if (reading == null) View.INVISIBLE else View.VISIBLE
        }
    }

    fun updateSpokenVoiceState(state: SpokenVoiceState) {
        val active = state is SpokenVoiceState.Starting ||
            state is SpokenVoiceState.Recording ||
            state is SpokenVoiceState.Finishing ||
            state is SpokenVoiceState.AiProcessing
        voice.animate().cancel()
        voice.alpha = if (state is SpokenVoiceState.Finishing ||
            state is SpokenVoiceState.AiProcessing
        ) 0.58f else 1f
        voice.scaleX = 1f
        voice.scaleY = 1f
        if (state is SpokenVoiceState.Starting || state is SpokenVoiceState.Recording) {
            voice.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .alpha(0.72f)
                .setDuration(520L)
                .withEndAction {
                    if (voice.isAttachedToWindow && active) {
                        voice.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(1f)
                            .setDuration(520L)
                            .start()
                    }
                }
                .start()
        }
    }
}
