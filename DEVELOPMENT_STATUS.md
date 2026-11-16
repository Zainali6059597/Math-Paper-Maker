# Development status

Consolidated native Android source package, version **1.0.2-dev**. This remains a development checkpoint, not the final one-time GitHub upload package.

## Latest continuation — 2026-11-13
- Added explicit pattern metadata quotas for chapter, SLO, cognitive level and difficulty, stored as a separate Room table (schema v6) and imported through content-package schemaVersion 3.
- Added a bounded deterministic backtracking solver and connected it to both required-slot and choice-group generation whenever a pattern declares quotas. It enforces exact min/max counts, unique question IDs and strict paper identity; impossible quotas block generation rather than being relaxed.
- Added candidate cognitive-level/difficulty metadata to the selection domain and four unit tests for quota satisfaction, unsatisfiable rules, official verification gating and deterministic seeds.
- Added a Content Review & Approval screen with separate queues for pending sources, patterns and questions.
- Source review requires a five-point primary-source checklist, non-empty issuer/title/URL, and a source type explicitly identifying an official source.
- Pattern approval is blocked until its source is verified and its slot/choice attempted marks pass structural checks.
- Question approval is blocked until its source is verified; blank text/chapter, invalid marks and non-summative questions cannot be approved for official generation.
- Review status changes and checklist/note/timestamp are written atomically to a local audit table.
- Upgraded Room database to schema v4 for review audit records, then v5 for saved-draft pattern pinning.
- Saved drafts now retain pattern ID, version and source ID, so a later content-pack version does not silently replace the pattern provenance of an existing draft. Old drafts migrate with null pattern references and display that limitation.
- Updated app version to 0.3.0-dev and documentation.

## Checks actually performed in this workspace
- Pure Kotlin domain compilation passed, including the new quota-aware solver.
- Standalone smoke tests passed for 12 offered / 9 attempted marks, replacement duplicate exclusion, quota satisfaction, impossible-quota blocking, unique question use and deterministic seed behaviour.
- SQLite migration SQL chain from a representative v1 schema through v5 was previously checked; schema v6 quota-table migration has been added but still needs a fresh end-to-end migration check.
- ZIP archive integrity checked after packaging.

## Not yet verified
- Android Gradle build, Room KSP schema generation, Compose compilation, APK assembly and install.
- Migration/save/reopen/review workflow on a physical Android device.
- Official source accuracy, complete board-specific content packs, richer paired/mixed-mark structures, embedded fonts/diagrams and Urdu/English PDF shaping.

No APK is included. Do not upload this checkpoint as the final one-time source package yet.

Latest checkpoint update (2026-11-13): added a pure-Kotlin board-pattern compliance audit gate and unit tests. Domain compilation and a standalone smoke test passed. The audit is not yet connected to the app's final export gate. Syllabus exclusions are now stored in Room and filtered from the question-selection pool. Android Gradle build and device testing remain pending.


## Latest continuation (2026-11-13)
- Added Room `syllabus_exclusions` table, keyed by pattern version and exclusion type/value; migration 6→7 is explicit.
- Content package importer accepts schema v4 exclusion entries (`CHAPTER` / `SLO`) and rejects blank/duplicate/unsupported exclusions.
- Package import persists exclusions transactionally with the pattern and question bank.
- Added compliance tests for excluded chapters and malformed/duplicate section rules.
- Android Gradle/KSP build remains unverified in this environment; do not claim device-ready.

## Latest continuation — 2026-11-13 (0.7.0-dev)
- Connected `BoardPatternCompliance.audit` to the editor's actual PDF export action.
- Export now reloads the exact pinned pattern by ID (not merely the newest pattern for that board/session), checks its version/source pin, loads the associated source, choice groups, slots and syllabus exclusions, and audits the current edited question list against the question bank.
- Non-custom drafts are blocked when compliance fails. Official non-custom exports additionally require verified official source/pattern/question records and summative questions. Practice drafts still cannot pass unresolved conflicts or excluded content.
- Custom drafts may export with a visible CUSTOM/NON-STANDARD PDF label and an audit summary, but critical provenance conflicts, excluded syllabus content, identity mismatch and duplicate question IDs remain hard blockers.
- Choice-group sections are audited with separate offered and attempted marks; required-slot patterns are audited as one offered/attempted item per slot.
- Added coverage for multiple sections with different marks and source-conflict blocking in practice mode.
- Pure Kotlin domain compilation passed; standalone smoke test passed for 18 offered / 10 attempted marks across two sections and conflict blocking.
- Bumped app version to 0.7.0-dev. Android Gradle/KSP/Compose compilation and device testing remain unverified; this is still not the final upload package.


## Latest continuation — PDF pagination and text layout (2026-11-13, 0.7.0-dev)
- Reworked the A4 PDF renderer to paginate long headers, instructions, choice instructions and question bodies instead of drawing beyond the page boundary.
- Added repeated continuation headings and page footers, right-aligned marks, indented question continuations and safe splitting of exceptionally long unbroken tokens.
- Added a pure Kotlin `PdfTextLayout` helper and four unit tests covering word wrapping, long tokens, empty text and narrow-width handling.
- Pure Kotlin domain + PDF layout compilation passed. A four-case standalone PDF text-layout smoke test passed. PDF exporter syntax compilation passed against minimal Android API stubs; this is not an Android SDK/Gradle build and does not verify rendered PDF appearance.
- Remaining PDF work: real Android PDF render inspection, font embedding, Urdu shaping, diagram rendering, and visual overflow checks on device.
- Bumped app version to 0.7.0-dev. Full Android Gradle/KSP/Compose build and physical-device testing remain unverified.


## Latest continuation — content-package integrity hardening (2026-11-13, 0.8.0-dev)
- Hardened JSON import validation: source ID/issuer/title/type are mandatory; source session cannot contradict the package session; pattern/source linkage is checked; pattern identity must match the package identity.
- Every slot and choice group must belong to the imported pattern, have valid ordering and marks, and obey the offered/attempted structure.
- Every imported question must match board, grade, stream, subject, session and source exactly; question ID/text, chapter, question type, semantic fingerprint and positive marks are required.
- Added equivalent transaction-boundary checks in Room import so invalid entities cannot bypass JSON importer validation through another caller.
- Missing source session now creates an explicit review warning instead of being silently treated as fully specified. Imports still force all imported source/pattern/question records to NEEDS_REVIEW.
- Updated app version to 0.8.0-dev.
- Android Gradle/KSP/Compose build and device tests remain unverified; this is not the final upload package.


## Latest continuation — paired mixed-mark question bundles (2026-11-13, 0.9.0-dev)
- Added a pure-Kotlin `PairedQuestionBundleEngine` for required multi-part questions such as Q5(a)+Q5(b), where parts may have different marks (for example 4 + 6).
- Each bundle is selected as one required unit; all parts must be supplied, each part retains its own marks, candidate IDs cannot be reused, and board/grade/stream/subject/session identity remains exact.
- Official mode only accepts verified summative questions. Missing candidates, invalid bundle definitions, duplicate IDs and declared-total mismatches block selection.
- Added printed instruction formatting that lists each part's individual marks and the bundle total.
- Added four unit tests for mixed marks, total mismatch, duplicate-candidate prevention and official verification.
- Pure Kotlin domain compilation passed. Standalone smoke test passed for a 4+6-mark paired bundle and all negative cases.
- Important limitation: this is a tested domain-engine foundation only. The JSON package schema, Room persistence, generation UI, compliance/export audit and PDF layout do not yet store or invoke paired bundles. Existing equal-mark choice groups remain unchanged; mixed-mark *attempt-any-N* choice groups are still unsupported and must be blocked rather than approximated.
- Bumped app version to 0.9.0-dev. Android Gradle/KSP/Compose build and physical-device tests remain unverified. This is not the final one-time upload package.


## Latest continuation — required paired-bundle integration (2026-11-13, 1.0.0-dev)
- Added `bundleId` to pattern slots and Room migration 7→8; legacy rows migrate with null bundle IDs.
- Content-package schema v5 can mark required paired parts with the same `bundleId`; importer and Room transaction both require at least two parts per bundle and reject mixing required bundles with optional choice groups.
- Connected the generation screen to `PairedQuestionBundleEngine`. Bundled parts and ordinary standalone slots are assembled into required bundles; every part is selected, marks are preserved, and the existing exact identity/official verification rules apply.
- Saved-draft slot JSON now retains bundle IDs. The editor shows labels such as `Q5(a)` and `Q5(b)`, and the PDF prints those labels with each part's own marks. Export compliance continues to audit every part against its individual pattern slot.
- Pure Kotlin domain compilation passed after integration. Android Gradle/KSP/Compose compilation, Room migration on-device, and rendered PDF inspection remain unverified. Mixed-mark optional choice groups and simultaneous optional-choice-plus-required-bundle patterns remain unsupported.
- Updated package schema documentation and app version to 1.0.0-dev. This is still a development checkpoint, not the final upload package.


## Final consolidation pass — 2026-11-13 (1.0.0-dev)
- Connected metadata quota enforcement to required paired-bundle generation. Paired parts are flattened into the quota solver's offered-slot set, then reconstructed in authored bundle/part order; unsatisfied quotas block generation rather than being skipped. Choice-group generation already applies the same quota solver to all offered slots.
- Fixed customization classification: moving/reordering questions now marks the draft custom/non-standard, ensuring the PDF is labelled accordingly.
- Tightened the custom-export hard blocker so a board/grade/stream/subject/session identity mismatch cannot pass merely because the wording differs from the generic phrase `identity mismatch`.
- PDF now prints the pattern's actual duration when available; saved drafts reload the duration from their pinned pattern.
- Added `RELEASE_READINESS.md`, `content-packs/README.md` and `FINAL_HANDOVER.md` to state the acceptance gates and prevent fabricated board data from being treated as production-ready.
- Pure Kotlin domain + PDF layout compilation passed. Smoke checks passed for required paired mixed-mark selection, quota-constrained paired parts and PDF text wrapping. Room 7→8 `bundleId` migration SQL smoke check passed. ZIP integrity passed.
- **Still not verified:** Android Gradle/KSP/Compose build, APK assembly, physical Android installation, full migration chain on Android, and rendered PDF visual QA. No APK or production-verified board content pack is included.


## Final selection reliability pass — 2026-11-13 (1.0.1-dev)

- Added `UniqueQuestionAssignment`, a bounded deterministic backtracking matcher shared by fixed required slots, choice groups and required paired bundles.
- This prevents a greedy candidate choice from starving a later constrained slot when a valid unique assignment exists.
- Reject duplicate question IDs in the exact board/grade/stream/subject/session bank before selection.
- Added unit tests for backtracking assignment and duplicate-ID rejection.
- Verified all pure Kotlin domain sources compile with `kotlinc`; targeted executable smoke test passed.
- Full Android Gradle/KSP/Compose build, APK and physical-device checks remain pending because this workspace has no Gradle executable or Android SDK.


## CI workflow hardening (2026-11-13)
- Android workflow now runs for pull requests targeting `main` as well as pushes and manual dispatch.
- Added concurrency cancellation, a 35-minute job timeout, and a manifest guard that fails if the offline app requests `android.permission.INTERNET`.
- Gradle test and APK assembly remain CI-only checks; no successful Android build is claimed until GitHub Actions actually runs against the uploaded source.


## Latest continuation — mixed paper-pattern integration (2026-11-14, 1.0.2-dev)
- Added `CompositePatternEngine`, which makes one bounded unique assignment across fixed required slots, required paired-bundle parts and optional choice-group slots.
- The engine calculates attempted marks as all required fixed/paired marks plus `attemptCount × marksPerQuestion` for each choice group; offered marks remain the sum of every offered slot.
- Kept equal-mark choice groups as an explicit rule. Mixed-mark attempt-any-N groups remain unsupported and are not approximated.
- Updated package-import and Room transaction validation to allow distinct fixed, paired and choice slots in one pattern, while rejecting any slot assigned to both a choice group and a paired bundle. It verifies the declared attempted total.
- Connected mixed patterns to the generation screen. Quotas, when present, are still applied across all offered slots by the existing quota solver; otherwise the composite engine performs the single unique assignment. Paired parts receive part labels and choice instructions are retained.
- Added composite-engine unit tests for mixed fixed/paired/choice assignment, unique question use, ambiguous slot rejection and attempted-mark mismatch.
- Checks performed in this workspace: all pure Kotlin domain sources compile with `kotlinc`; a standalone mixed-pattern smoke test passed (16 offered marks, 14 attempted marks, six unique questions).
- Still unverified: Android Gradle/KSP/Compose build, Room import/migration on-device, UI flow and PDF rendering for a real mixed-pattern package.

## Offline textbook ingestion continuation — 2026-11-16

- Fixed the Kotlin whitespace-regex compile error in the textbook exercise parser; GitHub Actions run #38 completed successfully before this continuation.
- Parser now accepts textbook layouts where a question number (for example, `1.`) is on its own line, and joins wrapped continuation lines while retaining the original PDF page number.
- Added a regression test for standalone question markers, wrapped text, page provenance and the `NEEDS_REVIEW` state.
- Added per-file PDF failure isolation: one malformed/unsupported bundled PDF is reported as a scan warning and no longer aborts scanning of every other book.
- Added a regression test for unreadable-PDF warning behavior.
- Added an offline manifest reader. Only chapters whose PDF is physically bundled and whose board, grade, stream, subject and session match the selected paper identity are exposed in the chapter selector. No cross-board manifest fallback is allowed.
- Updated the books folder instructions: copy the example to `book-manifest.json`, and provide exact chapter page ranges and source metadata.
- Standalone Kotlin smoke test passed for standalone markers, wrapped question text, page provenance, `NEEDS_REVIEW`, and isolated unreadable-book warnings.
- **Not done yet:** actual textbook PDFs and a real manifest are still absent; OCR is not implemented; detected candidates are not yet approved or imported into the live question bank; textbook-derived questions do not yet feed paper generation. No APK was built during this continuation.



## Latest code-only continuation — 2026-11-17

- Added a conservative numbered-question segmenter for selectable-text PDF pages. It groups wrapped lines under numbered questions and leaves scanned/unnumbered pages to the existing manual text or crop path.
- Added a checkbox review dialog for detected candidates, with select-all/clear controls and MCQ/short/long classification before adding checked items to the manual paper.
- Added focused unit tests for wrapped question text, standalone number lines, short fragments and unnumbered pages. These tests have been authored but **not run**.
- Expanded the manual maths editor with editable fraction/root/power/subscript/log/trigonometric templates and a larger Unicode maths symbol pad. It remains editable plain text rather than a typeset equation engine.
- Manual text questions may use source page 0; negative pages and page-less image-only questions remain invalid. Board-mode manual questions now require a valid chapter and board marks before they can be added.
- DOCX custom-paper header now reports the sum of the custom question marks instead of the fixed Punjab Board attempted total.
- **No Android build, test workflow, APK assembly or Ramus session was started for this continuation, as requested.** All changes remain source-only and uncompiled/unverified.
- Added a local offline Book Library: imported Class 9/10 PDFs are copied into app-private storage, can be reopened offline, and are retained when changing the active PDF. Open from Device remains available; abandoned partial imports are cleaned up.
- Added a custom vector launcher icon and connected it in the Android manifest.
- Remaining limitations: checkbox segmentation only works on extractable text and only detects clear numbered line starts; scanned pages still require crop selection; OCR is not implemented; complex equations are inserted as editable text templates, not Word equation objects; one-tap PECTAA/PCTB server downloads are still outstanding because the official e-books page is currently returning bot verification and exact direct PDF URLs have not been verified. No textbooks are bundled.


## Source-only continuation — offline OCR and editable Word maths — 2026-11-17

- Added bundled ML Kit Latin-script text recognition and a memory-bounded PDF-page renderer. A teacher can run **OCR SCANNED PAGE · OFFLINE** on the current page; OCR text is passed into the same conservative numbered-question segmenter and checkbox review flow.
- OCR uses an approximately 2-megapixel render cap and releases the bitmap/recognizer after processing. The progress state is reset even if the operation is cancelled.
- Added a Word OMML formatter for the maths keyboard's fraction, square-root, cube-root, power and subscript templates. Recognized structures export as editable Word equations; other content stays editable text. Added tests for equation structures, nested templates and XML escaping.
- DOCX equation text now preserves explicit line breaks and filters XML-invalid control/noncharacter code points before writing.
- **OCR limitation:** the bundled recognizer is Latin-script; Urdu-script OCR is not included. OCR may misread mathematical notation, so every candidate must be reviewed and corrected before adding it. It only proposes clearly numbered question candidates; it does not auto-generate or auto-approve questions.
- **No compilation, Gradle build, CI workflow, APK assembly, or Ramus testing was started.** All changes above are source-only and remain uncompiled/unverified, as requested.

- Added in-place text correction in the checkbox review list, so teachers can fix OCR/extraction mistakes before adding a candidate to the paper.
