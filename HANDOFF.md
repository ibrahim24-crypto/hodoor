# Handoff — resume next session

## Repository state
- Branch: `main`. Remote: `git@github.com:ibrahim24-crypto/hodoor.git`.
- Version: **1.1.0** (`versionCode = 2`).
- Last pushed before this handoff: `780e26f Bump version to 1.1.0`.
- This handoff commit contains all later uncommitted work. No release/tag was created.

## What is implemented
### Scanner (verified working by user)
- `Single | Multi` chips persisted in `AppSettings` (`scan_mode`).
- Live QR frames in **both** modes, mapped with CameraX matrices:
  `imageProxy.imageInfo.sensorToBufferTransformMatrix` (inverted) +
  `previewView.getSensorToViewTransform()`, respecting the shared FILL_CENTER `ViewPort`.
  Tilted codes use ML Kit `cornerPoints`.
- Single mode: 700 ms union window in the old build; now **1.5 s** via the "Slow" setting.
  One known code opens the student; 2+ codes capture a photo and open `SnapshotPicker`.
- Multi mode: no centre overlay, `Point at the codes, then tap the button`, big shutter,
  ImageCapture -> `detectAllQrWithRetry` over the whole photo -> `SnapshotPicker` even for
  1 code; 0 codes -> `No code found, try again`. No auto-navigation.
- `DetectionTracker` in `ScannerScreen` keeps boxes for 3 missed frames / 700 ms and eases
  positions, so small hand movement no longer makes every frame disappear.
- Live boxes show **all** detections in Single too; the centre-frame flag (`QrDetection.inScanFrame`)
  is only used for Single navigation, never for display.
- Truncated STEP 2 assumption still in force: if the downscaled photo yields fewer than 2 codes,
  retry once on the original full-resolution capture, then scale boxes back to display size.

### Settings / UI
- Theme button in Settings top bar cycles `System -> Light -> Dark -> System`.
- Reveal refined from GitHub references: reused `Path` + `clipPath`, previous-surface rect,
  target-surface circle, `CubicBezierEasing(0.1f, 0.8f, 0.2f, 1f)`, 550 ms, button spring.
  It is drawn behind the cards (Scaffold/TopAppBar transparent only during the reveal).
  **Import:** `androidx.compose.ui.graphics.drawscope.clipPath` (not `...graphics.clipPath`).
- Time fields auto-format while typing: `1400 -> 14:00`, `140 -> 14:0`; pasted `HH:MM` kept;
  old stored raw values normalized on open and on save (`RecordEditorDialog.kt`).
- Removed developer mode switch and `AppSettings.imageScanEnabled` / `setImageScanEnabled`.
  Camera/Image chips are now always visible in the scanner.
- New `AppSettings.scanConfirmation()` (`slow` default | `photo`) and
  `AppSettings.afterImport()` (`ask` default | `home`).
- In Single + `photo`, no auto-navigation: shutter captures a photo, `forcePicker = false`,
  so 1 known code still opens the student and 2+ open the picker.
- After CSV restore: `ask` shows a dialog (Import more / Home); `home` navigates straight home.
  `SettingsScreen` now takes an extra `onHome` callback supplied by `AppNavigation`.
- Added the previously missing Arabic scanner strings.

## Pending request — WiFi sync (NOT IMPLEMENTED)
User asked: "add ability to export import and sync via wifi for multiple phones".
Confirmed choices:
- Transfer **everything**: students, attendance records, causes (full backup/restore).
- Discovery: **hotspot + IP shown on screen** (one phone hosts, others enter IP/port or scan QR).
- Merge: **one-way from host** — receiving phones replace their data.
- UI: **new Sync screen** with Host / Join, status, progress; entry point in Settings.

No sync code was written.

### Data-layer map for the sync implementation
- Entities (`data/entities/Entities.kt`): `Student(code PK, firstName, familyName, searchName)`;
  `Record(id auto PK, studentCode FK ON DELETE CASCADE, type: RecordType, date, time?, cause: String, note?)`;
  `Cause(id auto PK, label)`. `RecordType` is stored as its enum name via `Converters`;
  `Record.cause` is free text, not a FK. `searchName` can be rebuilt with
  `data/TextUtils.kt: normalizedStudentName(firstName, familyName)`.
- DAOs: `StudentDao` has `allCodes()`, `byCodes()`, `upsertAll()` — no all-rows query and no
  delete-all. `RecordDao` has `all()`, `insertAll()`, `deleteForStudent()`. `CauseDao` has
  `allOnce()`, `insert`, `update`, `delete` — no bulk insert/delete-all.
- `data/repository/DataRepository.kt`: has `allStudentCodes`, `studentsByCodes`, `allRecords`,
  `addRecords`, `addCause`, etc. Missing for a true replace-all restore: all students as full
  objects, a suspend all-causes method, table clearing, and a public transaction wrapper.
  `importStudents` is currently the only user of `db.withTransaction`.
- `data/db/HodoorDatabase.kt`: version 1, `hodoor.db`; `androidx.room.withTransaction` is
  available via `room-ktx`. Restore order: clear `records` -> `students`, then `causes`.
- `data/backup/CsvBackup.kt` covers records only (no ids, students, or causes);
  `SettingsViewModel.restoreFrom` silently drops unknown student codes and appends rows.
- No Gson / Moshi / kotlinx-serialization is declared. Use platform `org.json` to stay
  dependency-free. Likely transport: `ServerSocket` on the host, HTTP/`HttpURLConnection`
  or plain sockets on the client; add `INTERNET` permission and cleartext LAN allowance.
- “Everything” also includes SharedPreferences `hodoor_settings`: `theme`, `last_record_date`,
  `scan_mode`, `scan_confirmation`, `after_import`, `language` (`LocaleHelper`).
- DI entry point: `AppContainer.repository`.

## Build / install state
- Last build: `./gradlew assembleDebug --no-daemon --max-workers=2 --console=plain`
  -> **BUILD SUCCESSFUL** after the settings/reveal/import changes.
- Last installed APK on `7be095be` (CPH1803) was the earlier stability build (1.1.0);
  the newest settings batch was **not installed**.
- USB can drop; if `adb devices` is empty, run `adb reconnect offline`, wait ~3 s, retry.

## Constraints / gotchas
- Build ONLY with the command above; one build at a time. Never `clean`, `lint`, `test`,
  `check`, `build`, or `assembleRelease`. Do not touch `gradle.properties`.
- No new dependencies unless the user explicitly lifts that rule; no Room/repository API
  changes were made so far.
- KSP cache can corrupt (`kspDebugKotlin FAILED` with no useful message). Fix:
  `rm -rf app/build/kspCaches app/build/generated/ksp/debug`, then rebuild.
- CameraX 1.7.0: `FALLBACK_RULE_CLOSEST_HIGHER` fails on device with
  `No available output size is found`; use `FALLBACK_RULE_CLOSEST_LOWER`.
- `Matrix.invert(Matrix)` is the available overload; `Matrix.invert()` alone did not compile.
- Compose 1.10: no `animateColorAsState`; use `animateFloatAsState` + `lerp`.
- `clipPath` lives in `androidx.compose.ui.graphics.drawscope`.
- Arabic is default in strings; Latin digits; RTL/LTR-safe start/end.
