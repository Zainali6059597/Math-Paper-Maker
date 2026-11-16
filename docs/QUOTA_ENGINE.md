# Metadata quota engine

Content package schemaVersion 3 may add `pattern.quotas`. Each quota declares one exact metadata value and an inclusive minimum/maximum number of offered questions. Supported dimensions:

- `CHAPTER` → `QuestionEntity.chapterId`
- `SLO` → `QuestionEntity.sloId`
- `COGNITIVE_LEVEL` → `QuestionEntity.cognitiveLevel`
- `DIFFICULTY` → `QuestionEntity.difficulty`

Example:

```json
"quotas": [
  { "dimension": "CHAPTER", "value": "CH3", "minimum": 2, "maximum": 4 },
  { "dimension": "COGNITIVE_LEVEL", "value": "APPLICATION", "minimum": 3, "maximum": 5 }
]
```

The importer validates dimension names, nonblank values, unique dimension/value keys, and bounds relative to offered slots. Quotas are persisted in `pattern_quotas` and loaded with the exact pattern version selected by the app.

`QuotaAwareSelectionEngine` uses deterministic bounded backtracking. It chooses only questions matching the complete board/grade/stream/subject/session identity, slot constraints and generation-mode status. A question ID can be used once. Every quota must be satisfied; the engine returns `QUOTA_UNSATISFIABLE` or `SEARCH_LIMIT_REACHED` rather than relaxing a rule. The default search cap is 50,000 nodes.

Important limitations:

- These are exact count quotas. Do not convert curriculum percentages into quotas without an authoritative paper-specific TOS and an explicit approved allocation.
- Quotas currently apply to all offered slots, not just the attempted subset.
- This is not yet a full percentage-weighted TOS optimizer or cognitive-level distribution optimizer with weighted tolerances.
- Paired long-question bundles, mixed-mark choice groups, and complex cross-section conditional constraints remain unsupported.
- Imported quotas are not official until their source and pattern are manually reviewed and approved.
