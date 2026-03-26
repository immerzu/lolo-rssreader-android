package com.example.rssreader.data.network

data class ParsedFeed(
    val title: String,
    val siteUrl: String?,
    val iconUrl: String?,
    val items: List<ParsedArticle>
)

data class ParsedArticle(
    val uniqueKey: String,
    val title: String,
    val link: String,
    val publishedAt: Long?,
    val plainText: String,
    val contentHtml: String,
    val imageUrls: List<String>,
    val author: String? = null,
    val contentSource: ParsedContentSource = ParsedContentSource.NONE
)

enum class ParsedContentSource {
    CONTENT_ENCODED,
    DESCRIPTION,
    CONTENT,
    SUMMARY,
    NONE
}


========================================================================================================================