Math Paper Maker bundled offline content directory

Place verified UTF-8 JSON content packages in this directory. The app imports every *.json file from this folder into its local Room database on first launch, without internet access. Existing pattern IDs are skipped on later launches.

Use the exact package schema documented in the repository root README.md (schemaVersion 1-5). Every package must contain a source record, a verified pattern and slots, and a question bank matching the exact board, grade, stream, subject and session. Imported records are always NEEDS_REVIEW and must pass the in-app review workflow before Official mode.

Do not place templates, sample data, invented board patterns, or unverified questions here. This directory currently contains no production-verified JSON content pack; therefore the app will correctly show zero question records until real packs are added.