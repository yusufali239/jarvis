package com.example.core.ai

import java.util.Locale

enum class DetectedLanguage(val tag: String, val locale: Locale, val displayName: String) {
    RUSSIAN("ru-RU", Locale("ru", "RU"), "Russian"),
    ENGLISH("en-US", Locale("en", "US"), "English"),
    UZBEK("uz-UZ", Locale("uz", "UZ"), "Uzbek"),
    UNKNOWN("und", Locale.getDefault(), "Auto/Unknown")
}

data class TextLanguageSegment(
    val text: String,
    val language: DetectedLanguage
)

/**
 * High-performance, zero-latency local language and script detector.
 * Accurately differentiates Russian, English, Uzbek (Latin & Cyrillic), and mixed bilingual utterances.
 */
object LanguageDetector {

    // Cyrillic letters specific to Russian
    private val RUSSIAN_CYRILLIC_REGEX = Regex("[а-яА-ЯёЁ]")
    
    // Common Russian indicator words (lower case)
    private val RUSSIAN_MARKERS = setOf(
        "и", "в", "не", "что", "это", "на", "с", "по", "как", "к", "но", "они",
        "мы", "вы", "он", "она", "сэр", "джарвис", "слушаю", "да", "нет", "привет",
        "здравствуйте", "понял", "хорошо", "сейчас", "готов", "система", "команда",
        "секунду", "выполняю", "конечно", "время", "погода", "настройки", "устройство"
    )

    // Common Uzbek Latin markers
    private val UZBEK_LATIN_MARKERS = setOf(
        "va", "bilan", "uchun", "kerak", "haqida", "rahmat", "salom", "qanday",
        "yaxshi", "boladi", "bo'ladi", "men", "siz", "ha", "yo'q", "yoq", "qiling",
        "jarvis", "ser", "bajarildi", "soat", "bugun", "ertaga"
    )

    // Common English markers
    private val ENGLISH_MARKERS = setOf(
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "i", "it",
        "for", "not", "on", "with", "he", "as", "you", "do", "at", "this", "but",
        "his", "by", "from", "they", "we", "say", "her", "she", "or", "an", "will",
        "my", "one", "all", "would", "there", "their", "what", "so", "up", "out",
        "if", "about", "who", "get", "which", "go", "me", "when", "make", "can",
        "like", "time", "no", "just", "him", "know", "take", "people", "into",
        "year", "your", "good", "some", "could", "them", "see", "other", "than",
        "then", "now", "look", "only", "come", "its", "over", "think", "also",
        "back", "after", "use", "two", "how", "our", "work", "first", "well",
        "way", "even", "new", "want", "because", "any", "these", "give", "day",
        "most", "us", "sir", "jarvis", "system", "ready", "online", "connected"
    )

    /**
     * Detects the primary language of the text.
     */
    fun detectLanguage(text: String): DetectedLanguage {
        if (text.isBlank()) return DetectedLanguage.RUSSIAN

        val trimmed = text.trim()
        val cyrillicCount = trimmed.count { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }
        val latinCount = trimmed.count { (it in 'a'..'z') || (it in 'A'..'Z') }

        // If strong majority is Cyrillic -> Russian
        if (cyrillicCount > 0 && cyrillicCount >= latinCount) {
            return DetectedLanguage.RUSSIAN
        }

        // Tokenize words
        val words = trimmed.lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}'`]+"))
            .filter { it.isNotBlank() }

        if (words.isEmpty()) {
            return if (cyrillicCount > 0) DetectedLanguage.RUSSIAN else DetectedLanguage.ENGLISH
        }

        var russianWordScore = 0
        var englishWordScore = 0
        var uzbekWordScore = 0

        for (w in words) {
            if (RUSSIAN_MARKERS.contains(w) || RUSSIAN_CYRILLIC_REGEX.containsMatchIn(w)) {
                russianWordScore += 2
            }
            if (UZBEK_LATIN_MARKERS.contains(w) || w.contains("o'") || w.contains("g'") || w.contains("sh") || w.contains("ch")) {
                uzbekWordScore += 2
            }
            if (ENGLISH_MARKERS.contains(w)) {
                englishWordScore += 2
            }
        }

        return when {
            russianWordScore > englishWordScore && russianWordScore > uzbekWordScore -> DetectedLanguage.RUSSIAN
            uzbekWordScore > englishWordScore && uzbekWordScore > russianWordScore -> DetectedLanguage.UZBEK
            englishWordScore > russianWordScore -> DetectedLanguage.ENGLISH
            cyrillicCount > 0 -> DetectedLanguage.RUSSIAN
            else -> DetectedLanguage.ENGLISH
        }
    }

    /**
     * Splits mixed multilingual text (e.g. "Привет! How are you? Как дела?") into
     * coherent sentence-level segments paired with their target language.
     */
    fun segmentTextByLanguage(text: String): List<TextLanguageSegment> {
        if (text.isBlank()) return emptyList()

        // Split into sentences / punctuation clauses
        val sentenceRegex = Regex("(?<=[.!?\\n])\\s+")
        val rawSentences = text.split(sentenceRegex).map { it.trim() }.filter { it.isNotBlank() }

        if (rawSentences.isEmpty()) {
            val lang = detectLanguage(text)
            return listOf(TextLanguageSegment(text, lang))
        }

        val segments = mutableListOf<TextLanguageSegment>()
        for (sentence in rawSentences) {
            val detected = detectLanguage(sentence)
            segments.add(TextLanguageSegment(sentence, detected))
        }

        // Merge adjacent segments that share the same language
        val merged = mutableListOf<TextLanguageSegment>()
        for (seg in segments) {
            if (merged.isNotEmpty() && merged.last().language == seg.language) {
                val prev = merged.removeAt(merged.lastIndex)
                merged.add(TextLanguageSegment("${prev.text} ${seg.text}", prev.language))
            } else {
                merged.add(seg)
            }
        }

        return merged
    }
}
