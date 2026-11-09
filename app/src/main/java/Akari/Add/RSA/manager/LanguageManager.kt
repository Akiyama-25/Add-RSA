package Akari.Add.RSA.manager

import Akari.Add.RSA.R
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

data class LanguageOption(
    val code: String,
    val titleRes: Int,
    val nativeName: String
)

object LanguageManager {
    private const val PREFS_NAME = "add_rsa_language_prefs"
    private const val KEY_LANG = "app_language"

    val supportedLanguages = listOf(
        LanguageOption("system", R.string.lang_system_default, "跟随系统 / System Default"),
        LanguageOption("zh-CN", R.string.lang_zh_cn, "简体中文"),
        LanguageOption("zh-HK", R.string.lang_zh_hk, "繁體中文 (香港)"),
        LanguageOption("zh-TW", R.string.lang_zh_tw, "繁體中文 (台灣)"),
        LanguageOption("en", R.string.lang_en, "English"),
        LanguageOption("ja", R.string.lang_ja, "日本語")
    )

    fun getSavedLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANG, "system") ?: "system"
    }

    fun applyLanguage(context: Context, langCode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANG, langCode).apply()

        try {
            val locales = if (langCode == "system") {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(langCode)
            }
            AppCompatDelegate.setApplicationLocales(locales)
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val lm = context.getSystemService(android.app.LocaleManager::class.java)
                if (langCode == "system") {
                    lm?.applicationLocales = LocaleList.getEmptyLocaleList()
                } else {
                    lm?.applicationLocales = LocaleList.forLanguageTags(langCode)
                }
            } catch (_: Exception) {}
        }
    }
}
