# Implementation status — honest release gate

This is a consolidated native Android development source package, version 1.0.0-dev. It is not yet a release-complete application.

## Implemented in source
- Native Kotlin + Jetpack Compose Android app; no web runtime, WebView or INTERNET permission.
- Local Room database schema v8 with sources, questions, paper patterns, slots, choice groups, required-bundle slot IDs, metadata quotas, exclusions, saved drafts and review audit records.
- JSON content import with structural validation and transactional persistence; all imported records are forced to NEEDS_REVIEW.
- Deterministic exact-identity question selection, offered/attempted equal-mark choice groups, required paired mixed-mark bundles, and bounded quota-aware selection for explicit chapter/SLO/cognitive-level/difficulty min/max counts.
- Visible question replacement picker constrained by identity, marks, chapter, SLO, type and duplicate exclusion.
- Finalization editor with reorder/remove/edit/revert, custom/non-standard PDF warning and local draft save/reopen/delete.
- Saved drafts pin pattern ID, version and source ID. Legacy drafts without those fields are identified as unpinned.
- Content Review & Approval console with source-first manual checklists, source gates, pattern/marks checks, summative-question gate and atomic local review audit history.
- A4 PDF export using Android PdfDocument and the system document picker.
- GitHub Actions workflow for unit tests and debug APK assembly.

## Still required before production-ready
1. Mixed-mark optional choice groups, nested alternatives and a full percentage-weighted ToS optimizer.
2. Full percentage-weighted ToS optimizer, syllabus exclusion enforcement and richer cross-section constraints. The current count-based quota solver is not a substitute for these.
3. Verified, provenance-complete content packs for each requested board, grade, stream and session; conflicts must remain blocked.
4. Rich official pattern audit against original model papers, ToS, marking schemes and notifications; curriculum weightage must not be treated as a paper pattern.
5. Real-device PDF rendering checks, diagrams, embedded fonts and Urdu/English shaping/layout verification (basic pagination and text overflow handling are now implemented).
6. Automated tests for importer, Room, review workflow, draft migration, editor and PDF output.
7. Successful GitHub Actions Gradle build, physical-device install, airplane-mode workflow test and PDF inspection.

## Build/test evidence
- Pure Kotlin domain compilation passed for the quota solver; standalone smoke tests passed for choice/replacement and quota satisfaction, unsatisfiable-quota blocking, uniqueness and deterministic seeds.
- A representative SQLite migration chain through v5 was previously checked; the new v5→v6 quota table/index SQL was separately exercised with SQLite. This does not replace full Room KSP or on-device migration testing.
- Full Android Gradle build has not been run in this workspace because Gradle/Android SDK are unavailable.
- No APK is included.

## Release decision
Do not call this a final production release until the remaining content, engine, PDF, CI and device-test gates are passed. Do not ask the user to upload this intermediate checkpoint as the final one-time package.

## Run update — Board pattern compliance gate (2026-11-13)
- Added `domain/BoardPatternCompliance.kt`, a strict audit model for section offered/attempted counts, declared attempted marks, exact board/grade/stream/subject/session identity, duplicate question IDs, question marks, official source/pattern/question verification, summative-only official questions, unresolved source conflicts, and explicit chapter/SLO exclusions.
- Added `BoardPatternComplianceTest.kt` covering 12 offered / 9 attempted, unresolved source conflict, wrong stream, excluded SLO, wrong offered count, and practice-mode treatment of unverified versus conflicted content.
- Verified all pure Kotlin domain sources compile with local `kotlinc`.
- Executed a standalone smoke test: 12 offered / 9 attempted (24 offered marks / 18 attempted marks), source-conflict block, syllabus-exclusion block, and offered-count mismatch block all passed.
- Important limitation: this new audit is currently a domain component with tests; it is NOT yet wired into the Compose approval/export flow or backed by persisted syllabus-exclusion tables. Do not claim the UI currently enforces these rules until integration and Android build tests pass.
- Full Android Gradle/KSP/Compose build and physical-device tests remain unverified.


### Latest: syllabus exclusion persistence
Room schema v8 stores chapter/SLO exclusions per exact pattern version and required-bundle IDs on pattern slots. JSON package schema v4 can import explicit exclusions, with validation and transaction-safe persistence. The pure domain compliance audit already evaluates exclusions. UI editing/review of exclusions and end-to-end Room-to-audit wiring remain pending.

## Latest continuation — pre-export audit integration (2026-11-13)
- The editor's export action now loads the exact pinned pattern, source, slots, choice groups and exclusions and runs the compliance audit before opening the PDF destination picker.
- Unmodified drafts are blocked on any audit failure. Official unmodified drafts also require verified official source/pattern/questions and summative questions.
- Custom drafts export with the PDF's CUSTOM/NON-STANDARD label and a visible audit notice; content-integrity failures (conflicts, excluded content, identity mismatch, duplicate IDs) remain blocked.
- Choice groups retain distinct offered versus attempted marks in the export audit. Required-slot patterns use one-item sections.
- Added a multi-section/mixed-marks compliance test and a standalone smoke test. All pure Kotlin domain sources compile.
- Version is now 0.7.0-dev. This integration has not yet been validated by Android Gradle/KSP/Compose compilation or on a physical device; those remain mandatory release gates.


## Latest continuation — PDF pagination/text wrapping (2026-11-13)
- PDF generation now paginates the heading, metadata, instructions, section-choice instructions and long question text across A4 pages.
- Continuation pages include a compact repeated title; every page gets a footer and page number. Question marks remain right-aligned on the first line, with continuation lines indented.
- Long unbroken tokens are split to avoid drawing outside the text column.
- Added pure Kotlin `PdfTextLayout` and four tests for word wrapping, long tokens, empty strings and narrow widths. Domain/layout compile and standalone smoke tests passed; PDF exporter syntax compiled against minimal Android stubs.
- This does not verify actual Android rendering, PDF visual quality, Urdu/Arabic shaping, embedded fonts, diagrams or Android Gradle/KSP/Compose compilation.


### Content package integrity — 0.8.0-dev
The importer and Room transaction both enforce exact source/pattern/question identity and provenance linkage. Required source metadata, positive question marks, nonblank question text/chapter/type/fingerprint, unique IDs, and pattern ownership/order are checked before persistence. A missing source session is surfaced for review; a contradictory session is rejected. Imported verification statuses remain NEEDS_REVIEW.


Required paired bundles are now supported in content-package schema v5 and Room schema v8 via a nullable `bundleId` on pattern slots. The generation UI invokes the required-bundle engine, and labels/marks flow into saved drafts and PDF output. A package cannot currently mix these bundles with optional choice groups. Android build and device verification remain pending.


## Current paired-bundle status (1.0.0-dev)
Required paired bundles are now supported end-to-end in source: JSON schema v5, Room migration 7→8, generation, saved-draft slot metadata, editor labels, PDF labels/individual marks, and the existing per-slot export audit. Optional choice groups remain equal-mark only. The historical run notes above document how the feature evolved; they should not be read as the current implementation status.
