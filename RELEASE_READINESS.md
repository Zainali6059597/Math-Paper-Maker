# Release readiness and final handover

## What this source package contains

- Offline-first native Android application (Kotlin, Jetpack Compose, Room/SQLite).
- Local content-package import, validation, review/approval workflow and saved drafts.
- Deterministic paper generation for fixed required slots, equal-mark offered/attempted choice groups, required mixed-mark paired bundles, and declared count-based metadata quotas.
- Syllabus exclusions applied to candidate filtering and pre-export audit.
- Customization, question replacement, reorder/remove/edit, and A4 PDF export with pagination.
- GitHub Actions workflow for unit tests and debug APK artifact creation.

## Important release blockers — do not misrepresent these

1. **No production-ready board content pack is bundled.** The app cannot generate a meaningful paper until a teacher imports a locally authored package. All imported sources, patterns and questions start in `NEEDS_REVIEW`.
2. **No successful Android Gradle/KSP/Compose build is recorded in this workspace.** The current environment has Kotlin compiler but no Android SDK or Gradle installation. The included GitHub Actions workflow is the first supported full Android build route.
3. **No APK or physical-device test is included.** CI build, APK installation, offline-mode testing, database migration testing on Android, PDF visual inspection and real printer-independent save/share checks remain required.
4. **Mixed-mark optional choice groups are not supported.** Required paired bundles may have mixed marks; choice groups require equal marks per offered question. The importer rejects a package that tries to mix these structures.
5. **ToS percentage optimization is not implemented.** The quota solver enforces explicit exact min/max question counts for chapter, SLO, cognitive level and difficulty. It does not infer counts from percentages.
6. **PDF typography is basic.** Embedded fonts, Urdu shaping, complex equation layout and diagram rendering need real-device QA and additional implementation.
7. **Content approvals are manual.** A local checklist and audit trail do not constitute independent certification. A reviewer must inspect the cited primary documents and resolve source conflicts.

## One-time GitHub upload

After the release blockers are resolved, upload the *contents* of the project folder to the repository root in one commit. Keep `.github/workflows/android.yml`. Then run **Actions → Build Android APK** and download the `maths-paper-maker-debug-apk` artifact. Do not commit private keys, credentials or personal data.

## Acceptance checklist

- [ ] GitHub Actions `testDebugUnitTest` succeeds.
- [ ] GitHub Actions `assembleDebug` succeeds and artifact is downloadable.
- [ ] APK installs on a real Android 9+ phone.
- [ ] App launches and works in airplane mode.
- [ ] JSON import success, malformed import rejection and duplicate import behavior tested.
- [ ] Fresh install and every supported Room migration tested.
- [ ] Practice paper generation tested for fixed slots, choice groups, paired bundles and quotas.
- [ ] Official generation remains blocked until source, pattern and all questions are independently reviewed and approved.
- [ ] Exclusions and unresolved conflicts block inappropriate generation/export.
- [ ] Edit, replace, reorder, remove, revert, save and reopen tested.
- [ ] Multi-page A4 PDF inspected for clipping, marks, labels, page numbers and time limit.
- [ ] Urdu/English and mathematical notation requirements assessed against actual textbooks and school needs.


## Latest source verification (1.0.1-dev)

- All pure Kotlin domain sources compile with the available Kotlin compiler.
- A targeted executable smoke test passed for backtracking assignment and duplicate-ID rejection.
- This does not replace the required GitHub Actions Android build, instrumentation/device tests or PDF visual inspection.
