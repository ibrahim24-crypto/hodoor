# Hodoor

<div dir="rtl">

## نظرة عامة

**Hodoor** تطبيق أندرويد مدرسي يعمل دون اتصال بالإنترنت لمتابعة تأخيرات وغيابات التلاميذ. يستورد قائمة التلاميذ من Excel أو CSV، ويمسح رموز QR، ويعرض إحصائيات لكل تلميذ، ويحتفظ بنسخة احتياطية CSV. الواجهة العربية هي الافتراضية مع دعم RTL وأرقام لاتينية، والفرنسية متاحة أيضاً.

### الميزات

- استيراد/تحديث التلاميذ من Excel أو CSV مع معاينة وربط الأعمدة.
- مسح QR بإطار صغير، مع قبول الرموز الموجودة داخل الإطار فقط.
- عند اكتشاف عدة رموز: إيقاف الكاميرا وعرض قائمة بأسماء التلاميذ لاختيار التلميذ المطلوب.
- وضع المطوّر: تفعيل «المسح من صورة» من الإعدادات، ثم اختيار **الكاميرا** أو **صورة** في شاشة المسح؛ الصورة قد تحتوي على رمز واحد أو على كل الرموز.
- بحث بالاسم أو اللقب أو الرمز، وتصفية السجلات حسب النوع والفترة والسبب.
- تسجيل وقت واحد أو فترة زمنية، وتصدير/استعادة CSV.
- ثيمات: النظام / فاتح / داكن.

### البناء

يلزم JDK 21 و Android SDK (compileSdk/targetSdk 37).

```bash
./gradlew assembleDebug --no-daemon --max-workers=2 --console=plain
```

لإنتاج نسخة موقّعة:

```bash
./gradlew assembleRelease --no-daemon --max-workers=2 --console=plain
```

ملف `keystore.properties` (غير مودع في Git) يجب أن يحتوي على مسار Keystore وكلمة المرور والاسم المستعار. إن لم يوجد، يمكن بناء نسخة Debug بشكل طبيعي.

APK الناتج: `app/build/outputs/apk/release/app-release.apk`.

### التقنيات

Kotlin · Jetpack Compose · Material 3 · Room + KSP · CameraX · ML Kit Barcode Scanning · Navigation Compose · MVVM.

ملاحظات الإصدار: [`RELEASE_NOTES.md`](RELEASE_NOTES.md).

</div>

---

## Overview

**Hodoor** is an offline Android attendance app for tracking student lates and absences. It imports students from Excel/CSV, scans QR codes, shows per-student statistics, and keeps a CSV backup. Arabic is the default UI with RTL and Latin digits; French is also available.

### Features

- Import/update students from Excel or CSV with preview and column mapping.
- Small-frame QR scanning; only codes inside the frame are accepted.
- Multiple QR codes: the camera stops and a list of matched students is shown so the right one can be picked.
- Developer mode: enable “Scan from image” in Settings, then choose **Camera** or **Image** on the scan screen; the image can contain one code or a whole sheet.
- Search by name/family name/code, and filter records by type, period and cause.
- Single time or time range, plus CSV export/restore.
- Themes: system / light / dark.

### Build

Requires JDK 21 and Android SDK (compileSdk/targetSdk 37).

```bash
./gradlew assembleDebug --no-daemon --max-workers=2 --console=plain
```

For a signed release:

```bash
./gradlew assembleRelease --no-daemon --max-workers=2 --console=plain
```

`keystore.properties` (not committed) must contain the keystore path, passwords and alias. Debug builds work without it.

Release APK: `app/build/outputs/apk/release/app-release.apk`.

### Stack

Kotlin · Jetpack Compose · Material 3 · Room + KSP · CameraX · ML Kit Barcode Scanning · Navigation Compose · MVVM.

Release notes: [`RELEASE_NOTES.md`](RELEASE_NOTES.md).
