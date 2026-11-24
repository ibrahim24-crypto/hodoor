package com.himo.hodoor.ui.settings

import android.content.Context

object AppSettings {
    private const val Preferences = "hodoor_settings"
    private const val KeyTheme = "theme"
    private const val KeyLastRecordDate = "last_record_date"
    private const val KeyImageScan = "developer_image_scan"
    private const val KeyScanMode = "scan_mode"

    private fun prefs(context: Context) =
        context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)

    fun theme(context: Context): String =
        prefs(context).getString(KeyTheme, "system")?.takeIf { it.isNotBlank() } ?: "system"

    fun setTheme(context: Context, value: String) {
        prefs(context).edit().putString(KeyTheme, value).apply()
    }

    /** Epoch day of the last date used in a record editor, or -1 when never picked. */
    fun lastRecordDate(context: Context): Long =
        prefs(context).getLong(KeyLastRecordDate, -1L)

    fun setLastRecordDate(context: Context, epochDay: Long) {
        prefs(context).edit().putLong(KeyLastRecordDate, epochDay).apply()
    }

    /** Developer option: replace the live camera with a gallery image picker. */
    fun imageScanEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KeyImageScan, false)

    fun setImageScanEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KeyImageScan, value).apply()
    }

    /** Scanner mode: "single" (frame-assisted) or "multi" (shutter photo). */
    fun scanMode(context: Context): String =
        prefs(context).getString(KeyScanMode, "single")
            ?.takeIf { it == "single" || it == "multi" } ?: "single"

    fun setScanMode(context: Context, value: String) {
        prefs(context).edit()
            .putString(KeyScanMode, if (value == "multi") "multi" else "single")
            .apply()
    }
}
