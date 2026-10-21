# ADR-0006: App-Owned Chinese Custom Dictionaries

- Status: Accepted
- Date: 2026-07-27

## Context

Pinyin T9 already receives Chinese and English candidates from Rime. Users also
need editable custom Chinese phrases, a Chinese-mode custom English dictionary,
optional sharing with Predictive English, and automatic learning when literal
Pinyin is deliberately committed twice.

Writing the settings directly into Rime's `stabledb` would make import easy but
deletion and replacement difficult. Rebuilding a Rime text dictionary after
each edit would require deployment, delay visibility, and couple ordinary
settings changes to engine readiness. Candidate selection also cannot treat an
app-owned phrase as an ordinary Rime index without risking the wrong engine
candidate being committed after paging or an asynchronous refresh.

## Decision

Custom Chinese phrases and Chinese-mode English words are app-owned,
revisioned, in-memory dictionaries with coalesced atomic file persistence.
Chinese phrases store both display text and normalized toneless Pinyin. Both
dictionaries build exact and prefix T9 indexes when their revision changes,
never during the physical-key reducer.

Chinese phrases also retain selection counts and last-use times. Candidate
indexes sort each exact/prefix group by usage, preserving exact-match priority.
Older records default to zero usage. Replacing a management list retains usage
for unchanged text/reading identities and removes usage for deleted identities.
Removing a text entirely also removes app-owned learned prediction pairs that
reference it.

`ChineseT9CustomCandidateSource` merges matching custom entries before the
existing width-budget pager. Every custom entry carries a direct-commit identity
that is resolved by the candidate source session. Rime candidates retain their
real original indices. A custom match may render while a matching engine frame
is pending; once Rime publishes the fresh page, both sources use the same
snapshot, focus, paging, shortcut, and renderer pipeline.

The Chinese-mode English switch filters pure Latin engine candidates at this
same source boundary. It does not alter or redeploy Rime configuration.

Predictive English and Chinese Pinyin retain separate stores. When sharing is
enabled, queries and management expose their union. Replacing the shared list
updates both stores, which makes deletions deterministic and gives each mode a
complete list if sharing is later disabled. Runtime learning remains attributed
to its originating mode.

Literal Pinyin learning is a session policy, not a key-flow rule. A successful
Pinyin literal-code commit records a normalized Latin word only in editors that
allow personalized learning. The first observation is kept in a bounded
process-local set; the second learns the word. File access and dictionary scans
remain outside `PhysicalT9KeyFlow`.

## Consequences

- Dictionary edits are visible without Rime deployment or IME switching.
- Custom candidate deletion is complete and immediate.
- Shared English management has one logical list while unshared modes can
  diverge again.
- Direct custom commits seed app-owned personal prediction context, but do not
  alter Rime's commit history. Subsequent suggestions on this path come from
  learned pairs; the prebuilt Rime predictor remains attached to native commits.
- Personal prediction pairs require two confirmed observations in the same editor.
  Up to four adjacent commits supply suffix contexts, reducing dependence on word
  selection boundaries. The next accepted unit or punctuation/Return confirms the
  latest observation; immediate edits/cancellation discard it. Private editors are excluded.
- Automatically learned phrases live separately from user-managed entries. Only
  actual selected full Pinyin is trusted; 2–16-character sequences of up to four
  commits become candidates after two confirmations. Full/prefix/initial indexes
  keep lookups bounded without a global Rime abbreviation expansion. Full matches
  outrank completions, context ranks within groups, and engine identities survive
  merges. Existing Rime user dictionaries remain unchanged.
- Candidate sources distinguish validated negative direct-commit identities
  from the unselectable `-1` placeholder used by stale candidate frames.
- User-managed phrases require explicit toneless Pinyin. Automatic learning uses
  the selected candidate's reading, validated against full syllables and Han length;
  it never guesses a pronunciation from text or default T9 letters.
