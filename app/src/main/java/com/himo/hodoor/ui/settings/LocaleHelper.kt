package com.himo.hodoor.ui.settings

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {
    private const val Preferences = "hodoor_settings"
    private const val KeyLanguage = "language"

    fun language(context: Context): String =
        context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
            .getString(KeyLanguage, "ar").orEmpty().ifEmpty { "ar" }

    fun setLanguage(context: Context, code: String) {
        context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
            .edit()
            .putString(KeyLanguage, code)
            .apply()
    }

    fun wrap(context: Context): Context {
        val code = language(context)
        if (code.isEmpty()) return context
        val locale = Locale.forLanguageTag(code)
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList(locale))
        configuration.setLayoutDirection(locale)
        return context.createConfigurationContext(configuration)
    }
}
