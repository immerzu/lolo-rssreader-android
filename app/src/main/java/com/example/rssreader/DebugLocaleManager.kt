package com.example.rssreader

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

enum class DebugLocaleOption(val languageTag: String?) {
    SYSTEM(null),
    GERMAN("de"),
    ENGLISH("en")
}

object DebugLocaleManager {
    private const val PREFS_NAME = "debug_locale"
    private const val KEY_LANGUAGE_TAG = "language_tag"

    fun wrap(base: Context): Context {
        if (!BuildConfig.DEBUG) {
            return base
        }
        val option = getSelectedOption(base)
        val tag = option.languageTag ?: return base
        val locale = Locale.forLanguageTag(tag)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLocales(LocaleList(locale))
        return base.createConfigurationContext(configuration)
    }

    fun getSelectedOption(context: Context): DebugLocaleOption {
        if (!BuildConfig.DEBUG) {
            return DebugLocaleOption.SYSTEM
        }
        val tag = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE_TAG, null)

        return when (tag) {
            DebugLocaleOption.GERMAN.languageTag -> DebugLocaleOption.GERMAN
            DebugLocaleOption.ENGLISH.languageTag -> DebugLocaleOption.ENGLISH
            else -> DebugLocaleOption.SYSTEM
        }
    }

    fun setSelectedOption(context: Context, option: DebugLocaleOption) {
        if (!BuildConfig.DEBUG) {
            return
        }
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE_TAG, option.languageTag)
            .apply()
    }
}
