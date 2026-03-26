package com.example.rssreader.data.translation

import com.example.rssreader.data.db.ArticleEntity

fun ArticleEntity.translationSourceText(): String {
    val normalizedPlainText = plainText.trim()
    if (normalizedPlainText.isNotBlank()) {
        return normalizedPlainText
    }

    return translationAnyHtmlTagRegex.replace(
        translationScriptTagRegex.replace(
            translationStyleTagRegex.replace(contentHtml, " "),
            " "
        ),
        " "
    ).replace(translationWhitespaceRegex, " ").trim()
}

private val translationStyleTagRegex = Regex(
    "<style\\b[^>]*>.*?</style>",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)

private val translationScriptTagRegex = Regex(
    "<script\\b[^>]*>.*?</script>",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)

private val translationAnyHtmlTagRegex = Regex("<[^>]+>")
private val translationWhitespaceRegex = Regex("\\s+")
