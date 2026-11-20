# Prompt — generate a 40-student test list + QR codes for Hodoor

Copy everything below the line into the other AI.

---

You are a senior Python engineer. Produce a **single self-contained script** that generates a **40-student test dataset** with matching QR codes for the Android app **Hodoor**. The app imports the list offline and scans the QR codes offline. Follow the rules exactly.

## Hard requirements (from the app)

The app imports **CSV (UTF-8)**. The first non-empty row is the header. Header names are matched case- and accent-insensitively, with whitespace collapsed. Recognized names: `code`/`massar`/`numero`, `prenom`/`first name`, `nom`/`family name`/`lastname`/`surname`.

Use exactly this header and order:

```
code,prenom,nom
```

Rules:

1. Exactly **40 students**.
2. Codes: `T001` through `T040`, in order, **unique**, plain ASCII, no spaces.
3. Names: realistic mix of Arabic and Latin-script names in a Moroccan school context. No duplicate full names.
4. If a field contains a comma, quote, or newline, quote it per CSV rules and escape `"` as `""`.
5. Write CSV as UTF-8 **without BOM**.
6. QR payload must be **exactly the code string** (`T001`, `T002`, …). No URL, no JSON, no prefix, no whitespace.
7. Use standard **QR Code** (not Data Matrix / Aztec / PDF417), error correction **M**, quiet zone **≥ 4 modules**, module size **≥ 4 px**, printed size **≥ 2.5 cm**.

## Deliverables

Return one complete Python 3 script `generate_test_40.py`. Use only the standard library plus `qrcode` (`pip install "qrcode[pil]"`, which also installs Pillow). No network at runtime.

The script must:

1. Be deterministic (fixed seed `40`).
2. Write `output/students.csv` with header `code,prenom,nom` and 40 rows.
3. Write `output/qr/T001.png` … `output/qr/T040.png`, 300 dpi, each encoding exactly its code.
4. Write `output/qr_sheet.html`: printable A4 sheet, 4 cells per row, each cell with the QR image, the code, and the full name, plus a print button and page-break-friendly CSS.
5. Print a summary: rows written, unique codes, duplicate count (must be 0), and confirmation that every encoded payload equals its code. If `pyzbar` is importable, also decode every PNG and confirm it returns exactly its code.

## Acceptance checks (print these at the end of your answer)

- `python generate_test_40.py`
- `output/students.csv` has 41 lines (1 header + 40 students) and 3 columns.
- `output/qr/` contains 40 PNGs named `T001.png` … `T040.png`.
- Every QR decodes to exactly its code (if `pyzbar` is available).
- Importing `output/students.csv` into Hodoor adds 40 students, 0 skipped.
- Scanning any generated QR in Hodoor opens that student.

## Output format

Return, in order:

1. The full `generate_test_40.py` in one fenced code block (no placeholders, no truncation).
2. Short run instructions.

Do not change the header names, the `T001`–`T040` codes, or the QR payload rule.
