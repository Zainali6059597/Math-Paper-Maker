# Official Punjab mathematics sources (verified 16 November 2026)

This catalogue records the upstream documents used by the offline paper generator. The app must not fetch these URLs at runtime. Approved files are prepared for release, validated, and bundled under `app/src/main/assets/`.

## PECTAA — syllabus, pairing schemes and model papers

- Official Curriculum & Compliance resource page: https://pectaa.edu.pk/curriculum-compliance/
- Official Downloads page: https://pectaa.edu.pk/downloads-2-2/
- Grade 9 ALP / deleted topics / pairing schemes / model papers, session 2026–27, BISE Annual Examination 2027:
  https://pectaa.edu.pk/wp-content/uploads/2026/11/ALP-9-Notification-06.11.2026-F_Redacted.pdf
- Grade 10 ALP / deleted topics / pairing schemes / model papers, session 2026–27, BISE Annual Examination 2027:
  https://pectaa.edu.pk/wp-content/uploads/2026/11/ALPs-Notification-14.11.2026-1257.pdf
- Official Model Papers page: https://pectaa.edu.pk/model-papers/
- PECTAA Item Bank System overview (describes MCQ, short and constructed-response item development and review): https://pectaa.edu.pk/item-bank-system/

The current Grade 9 2026–27 notification explicitly states a 75-mark Mathematics paper, 15 objective marks plus 60 subjective marks, and 2 hours 30 minutes total (20 minutes objective + 2 hours 10 minutes subjective). Its paper instructions and pairing scheme are the authority for the Punjab Grade 9 2027 template. Do not silently reuse the 2025–26 template.

## PECTAA / PCTB — textbooks

- Official PECTAA E-Books page (includes Class IX and Class X Mathematics, English and Urdu-medium links): https://pectaa.edu.pk/curriculum-compliance/
- Official PECTAA Books and Publications page: https://pectaa.edu.pk/books-and-publications/
- The official Class IX list exposes legacy 2020 Mathematics Science Group PDFs hosted on pctb.punjab.gov.pk and current 2025–26 Mathematics English/Urdu PDFs linked from the same official PECTAA resource page.
- The official Class X list exposes Mathematics English/Urdu resources. Verify the edition/session on each actual PDF before assigning it to a paper identity.
- Supporting index that lists direct PCTB e-book links (not an issuing authority): https://literaria.edu.pk/pctb-e-books/

## Integration rules

1. Each textbook entry must identify board, grade, stream, subject, language, edition/session, publisher, source URL, page count, SHA-256, and permission/licence status.
2. A book may be bundled only after redistribution rights for this app (including paid distribution) are confirmed. Publicly downloadable does not automatically mean permission to redistribute inside a commercial APK.
3. No placeholder or sample PDF may be presented as an actual textbook.
4. Chapter/page manifests must be generated from the exact bundled edition, then tested against the PDF page count.
5. Smart-syllabus exclusions must be stored separately from the textbook's full contents. A question sourced from a deleted topic/exercise must be excluded for the matching session.
6. Board patterns are versioned by exact board, grade, stream, subject and session. Punjab/PECTAA data must never be used as a fallback for FBISE, Sindh, KPK, Balochistan or AJK.
7. When a new notification supersedes an older one, retain the older pattern as historical and make the new session the current default only after validating the complete Mathematics section (MCQs, short questions, long questions, choices, marks and timing).

## Current implementation status

- [x] Official PECTAA Grade 9 2026–27 notification located and indexed.
- [x] Official PECTAA Class IX Mathematics English/Urdu textbook links located.
- [x] Official PECTAA Class X e-book resources located.
- [ ] Download and inspect the actual current Mathematics PDFs.
- [ ] Confirm PECTAA/PCTB redistribution permission for bundling textbooks in a paid app.
- [ ] Validate every Grade 9 and Grade 10 2026–27 Mathematics pattern section and encode only source-backed rules.
- [ ] Bundle licensed PDFs and generate the matching book manifest.
- [ ] Run extraction/OCR quality checks on every bundled book and retain page provenance.

Last checked: 2026-11-16.
