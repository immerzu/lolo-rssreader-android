package com.example.rssreader.data.translation

import java.util.Locale

private val languageStopwords = mapOf(
    "de" to setOf("der", "die", "das", "und", "ist", "nicht", "mit", "ein", "eine", "den", "dem", "des", "im", "am", "zu", "von", "auf", "fuer", "für", "bei"),
    "en" to setOf("the", "and", "is", "are", "with", "for", "that", "this", "from", "you", "your", "was", "were", "have", "has", "not", "will", "into", "about"),
    "fr" to setOf("le", "la", "les", "et", "est", "avec", "pour", "une", "des", "dans", "sur", "pas", "plus", "par", "vous", "qui", "que", "du"),
    "es" to setOf("el", "la", "los", "las", "y", "con", "para", "una", "por", "del", "que", "est", "como", "más", "sus", "sin", "sobre", "entre"),
    "it" to setOf("il", "lo", "la", "gli", "le", "e", "con", "per", "una", "del", "della", "che", "non", "piu", "più", "come", "sono", "sul"),
    "nl" to setOf("de", "het", "een", "en", "met", "voor", "van", "dat", "die", "niet", "zijn", "was", "bij", "als", "ook", "maar", "naar"),
    "pt" to setOf("o", "a", "os", "as", "e", "com", "para", "uma", "que", "não", "nao", "mais", "por", "dos", "das", "como", "sobre", "entre")
)

fun shouldOfferTranslation(
    title: String,
    body: String,
    systemLanguage: String = Locale.getDefault().language
): Boolean {
    val normalizedSystemLanguage = normalizeLanguageForVisibility(systemLanguage)
    val articleLanguage = estimateLanguageForTranslation("$title $body")
    if (articleLanguage == null) {
        return true
    }
    return articleLanguage != normalizedSystemLanguage
}

private fun estimateLanguageForTranslation(text: String): String? {
    val tokens = text.lowercase(Locale.ROOT)
        .replace(wordCleanupRegex, " ")
        .split(wordSplitRegex)
        .filter { it.length >= 2 }

    if (tokens.size < 8) {
        return null
    }

    val scores = languageStopwords.mapValues { (_, stopwords) ->
        tokens.count { token -> token in stopwords }
    }.filterValues { score -> score > 0 }

    val best = scores.maxByOrNull { it.value } ?: return null
    val secondBestScore = scores.entries
        .filter { it.key != best.key }
        .maxOfOrNull { it.value }
        ?: 0

    // Nur bei klarer Tendenz ausblenden, sonst den Button lieber zeigen.
    return if (best.value >= 3 && best.value >= secondBestScore + 2) {
        best.key
    } else {
        null
    }
}

private fun normalizeLanguageForVisibility(languageCode: String): String {
    return languageCode.trim()
        .lowercase(Locale.ROOT)
        .substringBefore('-')
        .substringBefore('_')
        .ifBlank { "de" }
}

private val wordCleanupRegex = Regex("[^\\p{L}\\s]")
private val wordSplitRegex = Regex("\\s+")
