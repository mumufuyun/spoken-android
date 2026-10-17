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
   One combined commit of `项目评审` is not the same training signal.
2. Select `项目` again. Expect `评审` among the personal suggestions even if it
   was absent from the shipped next-word list. Selecting it must insert exactly
   the displayed text. Repeat with enough alternatives to exercise paging.
3. Restart the app and repeat step 2. Move the cursor, change editors, delete,
   or insert punctuation between two words; these must not establish a pair.
   Password and no-personalized-learning fields must not update the new store.

Pairs require at least two observations, rank by frequency then last use, and
use at most 32 Han characters per committed word. Context is limited to adjacent
commits within two minutes and is separated by scheme and configured script.
Disabling prediction hides personal suggestions as well as native predictions.

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
