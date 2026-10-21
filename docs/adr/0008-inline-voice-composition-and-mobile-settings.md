# Inline voice composition and mobile settings

## Decision

Recognized speech appears at the host editor's caret as underlined composing text.
The same region stays visible during AI processing. One tap on Finish ends capture;
AI automatically commits the final text into that region without another confirmation
button. The input method must never replace the entire host field or append a second
copy of the recording. The fixed keyboard dock keeps a horizontal scene strip during
idle and active voice work. Cancel replaces language/settings while active. The voice target stays in
place and becomes Use original during AI processing.

`VoiceComposition` owns editor snapshots and replacement decisions separately from
Rime composition. `AndroidVoiceEditor` adapts the captured InputConnection and predicts
IME selection updates. Every asynchronous callback retains the existing session and
editor generation checks. The coordinator publishes both partial and final recognition
before delivery, so AI edits the exact final ASR span.

## Ownership boundaries

- Surrounding text and selection are read locally to verify ownership, bounded to
  32,768 UTF-16 code units. They are not logged, persisted, or sent to providers.
- Each preview/final commit compares the current editor with the expected local
  snapshot. Changes to content or selection stop automatic replacement.
- Explicit Cancel removes only the owned composing span and restores any original
  selected text and selection direction. If ownership is lost, it preserves the
  user's current text instead of deleting by stale offsets.
- Manual keyboard input, unexpected cursor changes, composition removal, field
  changes, keyboard hiding, and service destruction invalidate delayed callbacks.
  Manual editing/hiding keeps the visible original; a newly bound editor is never
  touched through cancellation of the old session.
- Editors without usable text extraction or rejecting composing text use a compact
  dock preview and a single final commit. If an editor drops a composition after
  accepting it, rewriting stops and the visible text is retained. Host support must
  be tested; a successful API return alone is not universal compatibility evidence.
- AI failures keep the existing exact-original fallback and separate notice.
  Recovery remains an explicit insertion of the last original, not undo.

## Keyboard and settings

Portrait height defaults to 28 percent; landscape defaults to 35 percent. The 20–40
percent preference is the actual rendered key-area height. Presets are 23, 28, and
31 percent, plus the fixed 50 dp dock. The bottom action row has a 48 dp hit target.
Previously capped values migrate once to a comfortable portrait default, while
voice credentials remain unchanged.

The dock reuses its existing height for a horizontally scrollable scene strip and
fixed language/settings/hide actions. Scrolling does not select; tapping a scene does.
Hide remains at the top-right: accidental dismissal is addressed through touch intent,
not relocation. Its lower 10 dp edge ignores touches near Backspace. Down, all sampled
moves, and Up must stay in the safe region without exceeding system touch slop; leaving
it cancels the whole gesture. A 300 ms cooldown after key-area touches protects rapid
input/deletion, including concurrent fingers. The guard consumes rejected gestures and
has no delayed dismissal, so an ignored press cannot fire after the cooldown expires.
A deliberate tap still hides directly; semantic/accessibility clicks bypass these
physical-touch restrictions. Dock height, other controls, and deletion hit areas stay
unchanged.
Pinyin candidates retain precedence. Output language remains a separate sheet, and
output language stays fixed during a recording. Scenes remain tappable during connection,
recording, ASR finalization, and AI processing. Recognition/audio continue unchanged;
the last selected scene processes the full transcript. During AI work a new selection
cancels the old request and starts from the original ASR text, never a prior AI output.
Each request owns an identity, so cancelled results cannot deliver or count usage.
DIRECT/original immediately commits exact original text; DIRECT with another language
still translates. After delivery a scene tap affects only the next recording.

The voice key carries state labels. Editors lacking composing support retain a 22 dp
single-line preview under a 28 dp scene strip within the existing dock height. Hide
keeps its rightmost position and touch guard. Long-press voice retains cancel and
original-recovery actions.

Personal ordering balances fewer taps with stable positions: manually ordered pins
come first; other modes rank by successful committed recordings from the last 30 UTC
calendar days, requiring at least three uses. Equal counts keep canonical order.
Only scene IDs, day buckets, and bounded counts are stored locally. Failed, incomplete,
skipped, cancelled, recovery, or rejected/stale deliveries do not count; private
editors disable learning. Automatic ordering can be disabled. The order is frozen
until the keyboard is hidden and reopened, including across view recreation, and
sorting never changes the selected scene. Usage counts only the final delivered scene;
manual scene changes are independent of frozen connection/language settings.

Settings use neutral app bars, Flat Pop cards, and four primary categories. Voice
connection fields are separate from everyday scene/language controls. The keyboard
page includes effective height controls and a real local editor for preview. Stored
configuration badges do not claim network/provider validation.

## Verification

Focused JVM checks cover repeated partial replacement, final replacement, cancellation
with a reversed selection, UTF-16 offsets, manual edits, cursor movement, lifecycle
abandonment, and unsupported-editor fallback. Kotlin/resource compilation and APK
installation are separate from live provider and host-app acceptance.

User acceptance should include:

1. During a recording switch from Original to Work, then finish. During AI processing
   switch to Formal and confirm only that result is inserted. Repeat with Original,
   which should immediately preserve exact ASR text without duplication.
2. Set each height preset and type in the settings playground; key and dock positions
   should remain stable when starting/stopping voice.
3. Start with existing text, place the caret in the middle, and dictate a correction.
   Partials should be underlined; Finish should replace only that span and remove
   the underline once AI completes, without duplicate text.
4. Replace selected text by voice, then Cancel; the original selection should return.
5. While AI is running, type or move the caret, then switch fields/apps. A delayed
   result must not overwrite the edit or enter another field.
6. Repeat in notes, messaging, and browser editors. Report host app/version, action,
   observed text, and whether the underline or dock preview was shown on failure.
