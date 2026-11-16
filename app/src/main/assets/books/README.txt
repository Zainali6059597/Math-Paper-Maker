BUNDLED MATHEMATICS BOOKS

Place legally distributable textbook PDF files directly in this folder:
app/src/main/assets/books/

The Android app reads these files locally from its APK. No network access is used.

Important:
- Use text-based PDFs where possible. For scanned pages, the app includes offline Latin-script OCR: open the page and choose OCR SCANNED PAGE. OCR results are only candidate text; review/correct each question before ticking it into a paper. OCR for Urdu script is not included. Use ADD BOX CROP or the manual question editor when OCR cannot read a formula or diagram.
- Keep the original PDF page numbers; generated question references must point back to the book and page.
- Do not assume extracted paragraphs are exam questions. A separate question-identification and review stage must turn textbook material into candidate questions.
- Copy book-manifest.example.json to book-manifest.json and fill in the exact board, grade, stream, subject, session, edition, language and source details. Chapter entries include chapterId, title, startPage and endPage. The app only exposes manifest chapters when the referenced PDF is present in this same folder and the selected paper identity matches exactly.
- Only include PDFs you are allowed to redistribute inside the app.

No textbook PDFs or active book-manifest.json have been bundled yet. The example manifest is documentation only and is ignored by the app.
