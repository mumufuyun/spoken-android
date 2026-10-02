# Mobile Voice Adaptation from Spoken Desktop

## Context

The desktop reference is the local 2.4.5 / 245.2 release metadata and integrated
source `spoken-mac@2fb99eb0e786e8a8355ad057e16e4895539a4c5f` on
`codex/client-updates`. The remote published prerelease inspected was 2.4.2
(`origin/main@87df94f`); GitHub's stable `latest` still selected 2.2.2. These are
different release channels, not evidence that all local changes are published.
The installed desktop app reported 2.4.5 / 245.1; the reference metadata and source
also include the later 245.2 finalization changes.
Uncommitted changes in the desktop main checkout were not modified or treated as
the release baseline.

Desktop input recovery, scene editing, completion markers, and final-body handling
are useful product references. Mobile is an input method: its small keyboard,
editor lifecycle, and frequent app switching require different delivery boundaries.
This change implements those behaviors in existing Android components; it imports
no desktop source files, third-party algorithms, schemas, or evaluation samples.

## Decisions

| Desktop capability | Mobile decision | Reason |
| --- | --- | --- |
| Fluent transcription and scene editing | Add SMOOTH beside the existing DIRECT mode; strengthen built-in editing rules | Light editing is useful on a phone; existing direct-input choices must stay free of AI calls and lexical rewriting. |
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
- Prompts are adapted for short mobile input; desktop evaluation scores do not
  transfer. Prompt assertions verify composition, not a model's semantic quality.
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
- Recovery does not resend text, save audio, read surrounding text, or access the
  system clipboard. No new permissions, dependencies, or cloud calls are added
  beyond a user-selected AI scene. No background sync is introduced.
- Full APK compilation and device acceptance remain separate from focused JVM
  checks. Use the [device workflow](../android-studio-final-test-workflow.md) for
  actual microphone, keyboard, network, and host-app behavior.
