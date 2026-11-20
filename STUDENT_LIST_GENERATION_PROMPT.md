# Prompt — Generate a Hodoor student list + QR codes

Copy everything below the line into the other AI.

---

You are a senior Python engineer and data generator. Produce a **single self-contained script** that generates a realistic student list and matching QR codes that the Android app **Hodoor** can import and scan offline. Follow the app's exact rules below — do not invent formats.

## App facts you must obey (hard requirements)

Hodoor imports **CSV (UTF-8)** or **XLSX**. The first non-empty row is the header. Header names are matched case- and accent-insensitively, with whitespace collapsed. Recognized names:

- **Code**: `code`, `massar`, `code massar`, `numero`, `numéro`, or any header containing `massar`
- **First name**: `prenom`, `prénom`, `first name`, `firstname`
- **Family name**: `nom`, `nom de famille`, `family name`, `lastname`, `surname`, `nom famille`

Use exactly this header, in this order:

```
code,prenom,nom
```

Rules the app enforces:

1. `code` must be non-empty and **unique after normalization** (lowercase, accents stripped, whitespace collapsed). Later duplicates are silently skipped.
2. `code` must be **plain ASCII**: letters + digits only. No spaces, accents, punctuation, or Arabic-Indic digits. Recommended pattern: one prefix letter from `G`, `K`, `F` followed by 12 digits, e.g. `G184729305612` (length 8–16). The leading letter also prevents Excel from turning the code into a number and dropping leading zeros.
3. Names may be Latin or Arabic. If a field contains a comma, quote, or newline, quote it with standard CSV rules and escape `"` as `""`.
4. Extra columns are allowed and ignored by the app, but the three required columns must come first in the order above. You may append an optional `classe` column last.
5. Write CSV as UTF-8 **without BOM**.

QR rules:

- The QR payload must be **exactly the code string** — no URL, no JSON, no prefix, no surrounding whitespace, no newline.
- Use a standard **QR Code** (not Data Matrix, Aztec, or PDF417).
- Error correction level **M**.
- Quiet zone of at least **4 modules**.
- Print size at least **2.5 cm** per side; at 300 dpi use a module size of at least **4 px**.
- The app tries an exact code match first, then substring; exact matching is fastest and is what you must guarantee.

## Deliverables

Return one complete Python 3 script named `generate_hodoor.py`. Use only:

- the Python standard library, plus
- `qrcode` (install with `pip install "qrcode[pil]"`; this also provides Pillow)

No network access at runtime.

The script must support these CLI options:

- `--count` total students (default `200`)
- `--classes` number of classes (default `0` = ignore grouping)
- `--per-class` students per class (default `0`; when both are > 0, `count` is ignored and total = classes × per-class)
- `--seed` deterministic seed (default `2026`)
- `--out` output directory (default `./output`)
- `--lang` `fr` | `ar` | `mix` (default `mix`) for name style
- `--xlsx` optional flag to also write an `.xlsx` using `openpyxl` if it is installed; skip gracefully if it is not

Behavior:

1. Generate realistic, non-repeating `(first name, family name)` pairs in a Moroccan school context. With `--lang mix`, blend Arabic and Latin-script names. Never repeat a full name.
2. Generate unique ASCII codes in the pattern described above.
3. Write `output/students.csv` with header `code,prenom,nom` (and `classe` when grouping is used).
4. Write one PNG per student to `output/qr/<code>.png` at 300 dpi, with the exact code as the QR payload and a 4-module quiet zone.
5. Write `output/qr_sheet.html`: a printable A4 grid (about 4–5 cells per row) where each cell shows the QR image, the code, and the full name, with page-break-friendly CSS and a print button.
6. Print a verification summary:
   - total rows written,
   - number of unique codes,
   - number of duplicate codes (must be 0),
   - confirmation that every encoded payload string equals its code,
   - if `pyzbar` is importable, decode each PNG and confirm it returns exactly its code; otherwise say decoding was skipped.

## Acceptance tests (state these at the end of your answer)

- `python generate_hodoor.py --count 2000 --seed 7 --out out`
- `out/students.csv` has 2001 lines, 3 columns, all codes unique and ASCII-only.
- `out/qr/` contains 2000 PNG files.
- If `pyzbar` is available, every decoded QR equals exactly its code.
- Importing `out/students.csv` into Hodoor adds 2000 students with 0 skipped.
- Scanning any generated QR in Hodoor opens that student's page.

## Output format

Return, in this order:

1. The full `generate_hodoor.py` in a single fenced code block (no placeholders, no truncation).
2. A short README with the exact install and run commands, including how to print `qr_sheet.html`.

Do not add extra dependencies beyond `qrcode[pil]` and the optional `openpyxl` / `pyzbar` mentioned above. Do not change the header names or the QR payload rule.
