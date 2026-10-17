# Mobile Voice Adaptation from Spoken Desktop

## Context

The desktop reference is the published Spoken 2.4.9 / 249.2 release and clean
`spoken-mac@6cd06a86e00276f1db0ba71a585e6830608fdf2e` source on `main`.
The seven default tasks match its `PromptComposer` and frozen configuration-v8
fixture. Settings, keyboard layout, and visual presentation are outside the
mode-parity change; existing scene and output-language identifiers stay stable.

Desktop input recovery, scene editing, completion markers, and final-body handling
are useful product references. Mobile is an input method: its small keyboard,
editor lifecycle, and frequent app switching require different delivery boundaries.
The app implements those behaviors in existing Android components. Prompt text
is transferred from the user's companion `mumufuyun/spoken-mac` project at the
user's request; no third-party algorithm, schema, or evaluation dataset is imported.

## Decisions

| Desktop capability | Mobile decision | Reason |
| --- | --- | --- |
| Fluent transcription and scene editing | Keep SMOOTH beside DIRECT; use all seven full desktop-v8 tasks | Screen size does not justify shortening model instructions. Existing direct-input choices must stay free of AI calls and lexical rewriting. |
| Prompt composition | Preserve exact task text, wrapper order, trailing newlines, output-language suffix, and final-output contract | Content sharing omits the common wrapper; AI instruction puts its target-task preamble first. Recomposition can change behavior despite identical scene names. |
| Model parameters | Match host and model before applying the desktop built-in policy; Qwen 3.8 Flash enables thinking only for meeting notes of at least 800 Unicode code points | Do not send vendor flags to arbitrary compatible gateways or enable reasoning for everyday edits. Android counts code points; desktop Swift counts grapheme clusters, so compound emoji can differ at the threshold. |
| Long meeting responses | Buffer streaming final-content deltas and require both `stop` and `[DONE]` | The editor must never receive reasoning, truncated output, or a partial stream. A malformed, oversized, or interrupted stream falls back to the original. |
| Long-task deadline | Preserve the existing user-selected 3–120 second whole-call timeout | A keyboard must not adopt the desktop's potentially 30-minute wait. Long thinking can time out and return the original; cancellation remains available. |
| Scene and output-language dimensions | Keep independent choices in the long-press sheet | Avoid adding fixed keys or a settings page to every recording. Existing settings take effect at the next recording. |
| Recognition finalization | Preserve partial text with an incomplete marker and skip AI | A network timeout must not turn unfinished speech into an apparently complete rewritten message. |
| AI final-body protection | Read final text only; reject explicit truncation, tool calls, empty responses, and known reasoning/protocol markers | Results go directly into an editor. Failure preserves the exact original and shows a separate notice. |
| Last-input recovery | Offer explicit insertion of the original in the current editor for two minutes | Retain one in-memory entry up to 20,000 characters; clear on field change, keyboard hide, or service destruction. Do not add a cross-app history or clipboard fallback. |
| Recovery during recording | Cancel current recording or AI before inserting the retained original | Discard late callbacks by session/request and editor identity. An old popup cannot insert a newer entry. |
| Background/profile guidance | Defer to a separate opt-in settings change | Terms and style could help, but sending personal context needs a clear scope, toggle, and deletion flow. |
| Six custom modes and multiple saved model connections | Defer; retain the existing compatible endpoint setting | Potentially useful, but creation, editing, credentials, and migration need a dedicated compact settings design. |
| Per-application scene suggestions or selection rewriting | Defer | A phone app can contain chat, search, and password fields; app identity alone does not determine intent. |
| Global hotkeys, floating windows, focus restoration, simulated paste | Do not port | Use the keyboard and current InputConnection. Do not request an overlay or Accessibility service for voice input. |
| macOS local recognition and updater | Do not port | Apple frameworks and Sparkle are platform-specific. Android keeps its existing ASR and update paths. |
| Meeting recording | Retain editing of a spoken recap, not a background meeting recorder | Long background capture and speaker diarization are outside an IME's current scope. |

## Boundaries

- DIRECT plus original language preserves the recognized string. Pure translation
  still requires AI. Existing enum identifiers and selected modes remain unchanged.
- The seven default prompts without personal context match the desktop exactly.
  `app/src/test/resources/voice/desktop-2.4.9-prompts.sha256` records independent
  fingerprints for all 28 scene/language combinations, derived from desktop
  `SpokenTests/Offline/Fixtures/prompt-v8/frozen-prompts.json`. Its source hash is
  included in the fingerprint file. Before updating prompts, compare that fixture
  with the desktop production composer, including its special wrapper order.
  Personal context, custom modes, editable presets, and multi-connection settings
  remain outside this UI-preserving scope. Desktop evaluation scores do not
  transfer; prompt and wire assertions do not prove live model quality.
- Known non-Qwen models use the desktop's default-off thinking preference when
  supported, preserve always-on reasoning for MiniMax M2, and leave unknown
  provider thinking/temperature fields unset. Token limits scale with input
  length. Provider/model support is pinned to the desktop reference above.
- Output checks are deliberately conservative. Literal protocol markers in valid
  code may fall back to the original; unmarked reasoning or semantic changes may
  evade the checks. Do not claim complete reasoning detection.
- AI settings accept an HTTPS base URL or full `/chat/completions` URL. The
  desktop localhost HTTP exception is not adopted: the Android app does not
  enable cleartext traffic. Requests reject embedded credentials, query strings,
  and fragments, and use the configured timeout as a whole-call deadline.
- The recovery action inserts at the current cursor or selection in the same
  editor; it is not undo or an automatic replacement of previously inserted text.
  Only user activation inserts it. Cancelled/empty recordings do not replace the
  retained original; an oversized new original clears it rather than truncating.
- Recovery does not resend text, save audio, or access the system clipboard.
  Inline voice editing now uses a bounded local editor snapshot for ownership
  checks; surrounding text is never sent to providers (see ADR 0008). No new permissions, dependencies, or cloud calls are added
  beyond a user-selected AI scene. No background sync is introduced.
- Full APK compilation and device acceptance remain separate from focused JVM
  checks. Use the [device workflow](../android-studio-final-test-workflow.md) for
  actual microphone, keyboard, network, and host-app behavior.
