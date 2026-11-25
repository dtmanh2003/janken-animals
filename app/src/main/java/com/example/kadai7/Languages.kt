package com.example.kadai7

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * アプリ内の言語切り替え
 *
 * 選んだ言語は AppCompatDelegate が保存するので、次にアプリを開いたときもその言語になる。
 * 言語名はどの言語で表示していても読めるよう、それぞれの言語で書く。
 */
object Languages {

    /** tag が空文字のときは「端末の設定に合わせる」 */
    private data class Option(val tag: String, val label: String)

    private val OPTIONS = listOf(
        Option("vi", "🇻🇳  Tiếng Việt"),
        Option("ja", "🇯🇵  日本語"),
        Option("en", "🇺🇸  English"),
        Option("ko", "🇰🇷  한국어"),
        Option("zh", "🇨🇳  中文"),
    )

    /** いま選ばれている言語の短い表示（ボタン用）。端末の設定のときは null */
    fun currentTag(): String? =
        AppCompatDelegate.getApplicationLocales().get(0)?.language?.takeIf { it.isNotEmpty() }

    fun showPicker(context: Context) {
        val current = currentTag()
        val labels = listOf(context.getString(R.string.lang_system)) + OPTIONS.map { it.label }
        val checked = OPTIONS.indexOfFirst { it.tag == current }.let { if (it < 0) 0 else it + 1 }

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.lang_title)
            .setSingleChoiceItems(labels.toTypedArray(), checked) { dialog, which ->
                dialog.dismiss()
                if (which == checked) return@setSingleChoiceItems
                val locales = if (which == 0) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(OPTIONS[which - 1].tag)
                }
                // 画面が作り直されて、新しい言語で表示される
                AppCompatDelegate.setApplicationLocales(locales)
            }
            .setNegativeButton(R.string.reset_cancel, null)
            .show()
    }
}
