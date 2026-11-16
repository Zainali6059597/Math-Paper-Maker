# Content QA rules

- Every content package must identify exact board, grade, stream, subject and session.
- General Mathematics and Mathematics streams are distinct identifiers and must never cross-match.
- The importer ignores any claimed verification flag and sets all imported sources, patterns and questions to NEEDS_REVIEW.
- Official mode is blocked unless the selected pattern and its source are independently verified. The selector additionally requires VERIFIED_OFFICIAL summative questions.
- A curriculum chapter percentage is not a question count. Do not convert it into slots without an authoritative assessment framework / table of specifications and a documented rule.
- Preserve conflicting model-paper, marking-scheme and ToS values as separate evidence; do not silently choose one.
- Legacy material must be labelled by its actual session and must not be presented as current.
- Verify copyright/permission status before redistributing textbook questions or worked solutions.

## Manual review workflow (database v5)

1. Import always sets source, pattern and question status to `NEEDS_REVIEW`; package-supplied status fields are ignored.
2. Review the source first. The reviewer must confirm the original primary issuing-authority document, issuer/title, session/grade/stream, URL/page reference and official-source status. The app also requires a non-empty URL/issuer/title and a source type containing `OFFICIAL`.
3. A pattern can only be approved after its source is `VERIFIED_OFFICIAL`. The app rechecks positive marks, non-empty slots, and attempted-mark totals (including choice-group totals) before approval.
4. A question can only be approved after its source is verified. It must have text, chapter, positive marks and be marked summative for official paper generation.
5. Every approval stores the previous/new status, checklist JSON, note and timestamp in `review_audit` in the same database transaction as the status change.
6. This is a manual single-user review record, not an external certification. The reviewer must not approve a document they have not actually inspected. Unresolved conflicts must remain unapproved.

Saved paper drafts also retain the exact pattern ID, pattern version and source ID used at generation time. Older drafts migrate with null pin fields and are labelled as legacy/unpinned.
