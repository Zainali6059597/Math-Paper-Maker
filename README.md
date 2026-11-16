# Maths Paper Maker — consolidated native Android project

**Target:** private, single-user, offline Android app for Pakistani Grade 9–10 mathematics teachers.

**Source checkpoint:** 1.0.1-dev — consolidated source handover; Android release verification still pending.

This is a native Kotlin + Jetpack Compose Android project. It is not a website, PWA, Chrome shortcut or WebView wrapper. The manifest requests no `INTERNET` permission. The app's question bank and pattern database are stored locally in Room/SQLite; PDF files are created on-device with Android `PdfDocument` and saved using Android's document picker.

## What is in this consolidated package

- Native Android application shell with a dark, professional Compose interface.
- Board, grade, stream, subject, session and generation-mode setup.
- Local Room database for sources, questions, patterns, required slots, choice groups, explicit metadata quotas, chapter/SLO exclusions, required paired-bundle slot metadata, saved drafts, review audit records and pinned pattern references (database schema v8).
- JSON content-package importer with validation and a database transaction; schema v5 can define pattern-specific chapter/SLO exclusions and required mixed-mark paired bundles.
- Import safety: package contents are always forced to `NEEDS_REVIEW`; imported files cannot self-assert official verification.
- Deterministic local question-selection engine with exact board/grade/stream/subject/session matching and official/practice gating.
- Draft finalization screen: edit school/exam title/instructions, reorder/remove/replace questions, live offered and attempted marks, custom-paper warning.
- Local saved drafts with save/reopen/delete; saved records retain the exact pattern ID, version and source ID used to generate them.
- Content Review & Approval console: source-first review, pattern/question review checklists, source/mark/summative gates and a local audit history. Imported data never self-certifies.
- Pre-export compliance audit against the exact pinned pattern/source, current question records, choice structure and persisted chapter/SLO exclusions. Non-custom drafts are blocked on audit failure; custom exports are visibly labelled.
- A4 PDF export using Android's native `PdfDocument` and system save picker.
- Unit tests for selection constraints and a new choice-group domain engine (offered vs attempted marks).
- GitHub Actions workflow: tests, debug APK build and downloadable APK artifact.

## Important content status

No question bank or board pairing scheme is silently fabricated or pre-labelled official. A content package must be imported locally. Imported content starts as `NEEDS_REVIEW`. The review console requires a manual checklist for each source, pattern and question; patterns/questions cannot be approved before their source. Approval actions are stored in a local audit table. This is a single-user manual review workflow, not an external cryptographic certification. The current package still has no populated, production-verified board content packs; mixed-mark optional choice groups, percentage-weighted ToS optimization, exclusion authoring/review UI, and production PDF typography/diagram/Urdu QA remain outstanding. Required mixed-mark paired bundles are now supported through content schema v5 and Room schema v8. Chapter/SLO exclusions already import, filter generation candidates and participate in the pre-export audit. Android Gradle/KSP/Compose compilation and physical-device testing have not yet been completed.

See `RELEASE_READINESS.md` for the exact remaining acceptance gates. This source handover is not a verified APK release: no production board content pack or built APK is included.

## Build an APK with GitHub Actions

1. Create/open the repository and upload the **contents of this extracted folder** to the repository root (not the enclosing folder).
2. Keep `.github/workflows/android.yml` in the repository.
3. Commit to `main`, or open **Actions → Build Android APK → Run workflow**.
4. Wait for tests and `assembleDebug` to finish.
5. Open the successful workflow run and download the `maths-paper-maker-debug-apk` artifact. Extract the artifact ZIP to get the APK.

The CI runner downloads Gradle, Android SDK and build dependencies during compilation. That is a build-time requirement only; it does not make the installed app internet-dependent.

## Content package format (schemaVersion 1–5; Room database schema v8)

Create a UTF-8 JSON file on your computer/phone and import it from the app. Replace the sample values with real, reviewed content. `verificationStatus` fields in the file are ignored for approval: importer forces source, pattern and question rows to `NEEDS_REVIEW`.

```json
{
  "schemaVersion": 1,
  "identity": {
    "board": "Punjab",
    "grade": 9,
    "stream": "MATHEMATICS",
    "subject": "Mathematics",
    "session": "2026-27"
  },
  "source": {
    "sourceId": "source-example-001",
    "issuer": "Issuing board / authority",
    "title": "Exact title printed on the official document",
    "sourceUrl": "https://example.org/document.pdf",
    "sourceType": "OFFICIAL_PAIRING_SCHEME",
    "session": "2026-27",
    "pageReference": "Page 4"
  },
  "pattern": {
    "patternId": "punjab-g9-maths-2026-27-v1",
    "version": 1,
    "totalMarks": 2,
    "durationMinutes": 150,
    "sourcePage": "Page 4",
    "slots": [
      { "slotId": "Q1", "chapterId": "CH1", "sloId": "SLO-001", "questionType": "SHORT", "marks": 2, "displayOrder": 0 }
    ]
  },
  "questions": [
    {
      "questionId": "question-example-001",
      "chapterId": "CH1",
      "topic": "Replace with topic",
      "sloId": "SLO-001",
      "cognitiveLevel": "UNDERSTANDING",
      "difficulty": "AVERAGE",
      "questionType": "SHORT",
      "marks": 2,
      "questionText": "Replace this illustrative text with a properly sourced question.",
      "answerText": "Optional answer or marking guidance",
      "summative": true,
      "copyrightStatus": "REVIEW_REQUIRED",
      "sourcePage": "Page 5",
      "semanticFingerprint": "example-fingerprint-001"
    }
  ]
}
```

**Schema version 2** adds optional choice groups. For a choice-based paper, replace the pattern's `totalMarks` with the *attempted* total, add a `choiceGroups` array, and assign each offered slot to exactly one group using `groupId`:

```json
"pattern": {
  "patternId": "board-g9-maths-v2",
  "version": 2,
  "totalMarks": 18,
  "durationMinutes": 150,
  "sourcePage": "Page 4",
  "choiceGroups": [
    { "groupId": "SHORT-A", "offeredCount": 12, "attemptCount": 9, "marksPerQuestion": 2, "displayOrder": 0 }
  ],
  "slots": [
    { "slotId": "SHORT-A-01", "groupId": "SHORT-A", "chapterId": "CH1", "sloId": "SLO-001", "questionType": "SHORT", "marks": 2, "displayOrder": 0 }
  ]
}
```

Provide 12 distinct slot objects for that group. The importer checks that offeredCount equals the number of slots, every slot has the group's marks, and `totalMarks` equals the sum of attemptCount × marksPerQuestion. Version 1 remains supported for legacy required-slot patterns. Choice-group records are stored locally in Room and the generation screen uses the choice-aware selector. **Schema version 3** adds optional explicit metadata quotas under `pattern.quotas`. **Schema version 4** adds explicit syllabus exclusions under `pattern.exclusions`; each item has `type` (`CHAPTER` or `SLO`), `value`, and optional `reason` and `sourcePage`. Exclusions are stored against the exact pattern version and filtered out of the local question-selection pool before generation. **Schema version 5** adds `bundleId` on pattern slots for required multi-part questions with independently assigned marks. All slots sharing a `bundleId` are selected as one required bundle; each bundle must contain at least two parts. Example: `Q5(a)` can carry 4 marks and `Q5(b)` 6 marks. Do not set `bundleId` on optional choice-group slots. The importer rejects packages that mix required bundles with optional choice groups in the current release. The app stores bundle IDs in Room and saved drafts, invokes the paired-bundle selector, shows part labels in the editor/PDF, and retains each part's marks for the export audit. Quotas are exact minimum/maximum counts among all offered questions, not inferred from curriculum percentages. Supported dimensions are `CHAPTER`, `SLO`, `COGNITIVE_LEVEL`, and `DIFFICULTY`:

```json
"quotas": [
  { "dimension": "CHAPTER", "value": "CH3", "minimum": 2, "maximum": 4 },
  { "dimension": "COGNITIVE_LEVEL", "value": "APPLICATION", "minimum": 3, "maximum": 5 }
]
```

The local bounded backtracking solver enforces every declared quota while preserving unique questions and exact board/class/stream/subject/session identity. If a quota cannot be met, generation is blocked; it never silently relaxes the rule. A 50,000-node default safety cap prevents runaway search. This is count-based quota enforcement, not a complete percentage-weighted TOS optimizer. Mixed-mark optional choice groups remain unsupported and must not be approximated. Required paired bundles are supported through schema v5. Declared count-based metadata quotas are enforced for fixed-slot, choice-group and required paired-bundle offered questions; an unsatisfied quota blocks generation. See `docs/CHOICE_ENGINE.md` and `docs/QUOTA_ENGINE.md`.

## Privacy and safety

- No account, sign-in, analytics, advertising SDK, cloud backend or remote question retrieval.
- No internet permission in the Android manifest.
- The GitHub Actions workflow is only a build service. It does not run inside the installed app.
- Do not commit personal data, credentials, signing keys or private keystores to this repository.

## Build configuration

- Android Gradle Plugin 8.7.3
- Kotlin 2.0.21
- Gradle 8.9 via GitHub Actions
- JDK 17
- compileSdk 35 / minSdk 23


### Import integrity checks
The JSON importer and database transaction both enforce exact board/grade/stream/subject/session identity, source linkage, positive marks, unique IDs and required question metadata. A source with no session is flagged for manual review; a source session that contradicts the package is rejected. Imported records always enter `NEEDS_REVIEW`.


### Paired mixed-mark question bundles (development status)
Required paired bundles are integrated in development build 1.0.1-dev: schema v5 imports per-slot `bundleId`, Room v8 persists it, generation invokes the paired-bundle engine, saved drafts retain the mapping, and the editor/PDF show labels such as Q5(a) and Q5(b) with individual marks. The export audit checks each part against its individual slot. Mixed-mark optional choice groups remain unsupported; choice groups still require equal marks per offered question.


### Selection reliability update (1.0.1-dev)

The fixed-slot, choice-group and required paired-bundle selectors now share a bounded backtracking assignment helper. This avoids false “insufficient bank” failures caused by greedy selection when a flexible early slot consumes the only candidate for a constrained later slot. Duplicate question IDs in the exact identity bank are rejected before assignment. Pure Kotlin domain compilation and a targeted assignment/duplicate-ID smoke test passed; the Android Gradle build remains unverified.
