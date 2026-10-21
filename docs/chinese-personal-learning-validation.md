# Chinese Personal Learning Validation

## Scope

Check ordinary Rime word-frequency learning separately from app-owned next-word
learning and custom-phrase frequency. Static configuration checks and JVM tests
do not establish installed-device behavior. Use the app's input playground or a
disposable local editor; never type test content into a live conversation.
Do not clear app data or replace the user's Rime workspace for these checks.

## Existing Rime Learning

1. Record the installed APK version/hash and select Chinese Pinyin T9. Enter the
   complete digits `744744` (`shi shi`). Record the candidate order and choose an
   existing homophone that is not initially first, such as `实施` or `实时`.
2. Select the same word three to five times using the same complete input each
   time. Commit punctuation between repetitions. Compare its rank before and
   after; do not use the abbreviation fallback or a custom phrase for this case.
3. Leave the editor, restart the input method/app without clearing data, and
   repeat the input. Confirm the improved ranking persists.

The main translator inherits Rime's enabled user dictionary. Both abbreviation
fallback translators explicitly disable it. Rime may roll back a recent word
when Backspace immediately follows its commit, so immediate deletion is not a
valid persistence check. Record actual ranking changes rather than assuming a
fixed number of selections always forces the first position.

## Personal Next-Word Learning

1. Enable Predictive Chinese. In one editor, manually select `项目`, then type
   and select `评审`. Commit punctuation and repeat the two separate selections.
   Punctuation confirms the latest observation; immediate deletion does not.
   Train `项目` + `评审` + `安排`, then select `项目评审` in one commit: expect
   `安排` to remain available despite the different word-selection boundaries.
2. Select `项目` again. Expect `评审` among the personal suggestions even if it
   was absent from the shipped next-word list. Selecting it must insert exactly
   the displayed text. Repeat with enough alternatives to exercise paging.
3. Restart the app and repeat step 2. Move the cursor, change editors, delete,
   or insert punctuation between two words; these must not establish a pair.
   Password and no-personalized-learning fields must not update the new store.

Pairs require at least two confirmed observations. Longer suffix contexts take
precedence, then frequency and last use. Each word is at most 32 Han characters;
context spans up to four adjacent commits within two minutes, separated by scheme
and configured script. The latest observation waits for the next accepted unit or
punctuation/Return; cursor changes, deletion, and field changes discard it.
Disabling prediction hides personal suggestions as well as native predictions.

## Automatically Learned Phrases And Candidate Ranking

1. In Pinyin mode select `项目` (`xiang mu`) and `评审` (`ping shen`) separately,
   then commit `。`. Repeat twice. Use complete Pinyin candidates with actual
   readings; abbreviations without complete readings cannot teach pronunciation.
2. Type `942646874647436` or the learned initials `9677`. Expect `项目评审` in the
   personal candidates. Select it and confirm the exact text is inserted once.
   Restart without clearing data and repeat.
3. Repeat training with an unfamiliar phrase but immediately delete/move the caret
   after its last word, before punctuation or a following selection. That latest
   compound should not be promoted. Private/password fields must not teach it.
4. Compare complete `744744` matches with a long custom `shi shi fang an` phrase.
   Exact `实时`/`实施` should stay ahead of the completion. Train a relevant next-word
   context and verify it promotes the matching exact candidate without changing the
   text selected after paging. Check backspace and explicit reading filters too.

Record first-page ranks, selections needed per phrase, full/initial input, and any
noticeable delay. These are personal vocabulary improvements, not evidence of a
new general-purpose sentence model. Rime schema/provisioning is unchanged.

## Custom Phrases

1. Add two different phrases with the same Pinyin. Record their order, select
   the lower phrase, then enter the same digits again. Expect it to move ahead
   within its exact/prefix group. A frequent prefix cannot outrank an exact match.
2. Restart and verify the order persists. Edit another entry and confirm usage
   remains. Delete the learned phrase and confirm its custom candidate and
   personal next-word references disappear.

## Evidence

Report the typed digits, selected text, before/after ranks, whether prediction
was enabled, app version/hash, restart method, and whether the case involved a
custom phrase or an abbreviation. Keep device observations separate from local
JVM results and configuration inspection.
