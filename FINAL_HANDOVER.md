# Math Paper Maker — final source handover checkpoint

**Source version:** 1.0.1-dev  
**Platform:** Native Android  
**Runtime:** Offline; no INTERNET permission  
**Build artifact:** Source only; no APK included

This ZIP consolidates the current Android project and its documentation into one source handover package. It is not a release-verified APK: the Android build and device acceptance checks have not run in this environment.

## Implemented

- Native Compose application shell and local Room database.
- Local JSON import with identity, provenance, marks, slot, choice, quota, exclusion and paired-bundle validation.
- Imported content is forced to `NEEDS_REVIEW`; the importer ignores self-asserted verification.
- Source-first manual review console and local audit history.
- Exact identity matching for board, grade, stream, subject and session.
- Deterministic local generation for required slots, equal-mark choice groups and required mixed-mark paired bundles, using bounded backtracking to find unique assignments.
- Explicit min/max quotas for chapter, SLO, cognitive level and difficulty across fixed slots, choice groups and paired bundles.
- Syllabus exclusions applied to candidate filtering and pre-export audit.
- Saved drafts pin their exact pattern/version/source.
- Editing, replacing, reordering, removing and reverting questions.
- Pre-export compliance checks; custom papers are labelled CUSTOM/NON-STANDARD and critical identity/provenance/exclusion problems remain blocked.
- A4 PDF pagination, question part labels, marks, page numbers, and duration when known.
- GitHub Actions workflow for unit tests and debug APK assembly.

## Known limitations

- No production-verified question bank or board patterns are bundled. A locally prepared content package is required before paper generation can produce actual board-specific papers.
- Mixed-mark optional choice groups are not supported. Required paired bundles support mixed marks; ordinary choice groups require equal marks per question.
- Percentage-based ToS optimization is not implemented; explicit integer min/max quotas are supported.
- PDF rendering uses basic system typography. Urdu shaping, embedded font guarantees, diagrams and advanced math notation require additional device-level work.
- Manual approval checklists are a local human review aid, not independent certification.
- Full Android build, APK installation, real-device offline tests and rendered PDF visual inspection remain unverified.

## Exact next release gate

1. Upload the contents of this project folder to the GitHub repository root in one commit.
2. Run the included GitHub Actions workflow.
3. Fix any Gradle/KSP/Compose compile or unit-test failures.
4. Install the generated debug APK on the target phone and run the checklist in `RELEASE_READINESS.md`.
5. Prepare and independently verify the required board content packs before claiming official-paper readiness.
