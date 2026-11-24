package com.himo.hodoor.ui.settings

import android.content.Context

object AppSettings {
    private const val Preferences = "hodoor_settings"
    private const val KeyTheme = "theme"
    private const val KeyLastRecordDate = "last_record_date"
    private const val KeyScanMode = "scan_mode"
    private const val KeyScanConfirmation = "scan_confirmation"
    private const val KeyAfterImport = "after_import"

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

    /** Scanner mode: "single" (frame-assisted) or "multi" (shutter photo). */
    fun scanMode(context: Context): String =
        prefs(context).getString(KeyScanMode, "single")
            ?.takeIf { it == "single" || it == "multi" } ?: "single"

    fun setScanMode(context: Context, value: String) {
        prefs(context).edit()
            .putString(KeyScanMode, if (value == "multi") "multi" else "single")
            .apply()
    }

    /**
     * Single-mode confirmation: "slow" waits 1.5 s on live detections, "photo" waits
     * for the user to take a picture before scanning it.
     */
    fun scanConfirmation(context: Context): String =
        prefs(context).getString(KeyScanConfirmation, "slow")
            ?.takeIf { it == "slow" || it == "photo" } ?: "slow"

    fun setScanConfirmation(context: Context, value: String) {
        prefs(context).edit()
            .putString(KeyScanConfirmation, if (value == "photo") "photo" else "slow")
            .apply()
    }

    /** What happens after a successful CSV restore: ask in a dialog or go Home. */
    fun afterImport(context: Context): String =
        prefs(context).getString(KeyAfterImport, "ask")
            ?.takeIf { it == "ask" || it == "home" } ?: "ask"

    fun setAfterImport(context: Context, value: String) {
        prefs(context).edit()
            .putString(KeyAfterImport, if (value == "home") "home" else "ask")
            .apply()
    }
}
