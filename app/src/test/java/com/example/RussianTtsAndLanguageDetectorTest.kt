package com.example

import com.example.core.ai.DetectedLanguage
import com.example.core.ai.LanguageDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RussianTtsAndLanguageDetectorTest {

    @Test
    fun testDetectRussianLanguage() {
        val text1 = "Здравствуйте, сэр. Все системы функционируют в штатном режиме."
        assertEquals(DetectedLanguage.RUSSIAN, LanguageDetector.detectLanguage(text1))

        val text2 = "Включи фонарик и проверь уведомления."
        assertEquals(DetectedLanguage.RUSSIAN, LanguageDetector.detectLanguage(text2))

        val text3 = "Джарвис, сколько сейчас времени?"
        assertEquals(DetectedLanguage.RUSSIAN, LanguageDetector.detectLanguage(text3))
    }

    @Test
    fun testDetectEnglishLanguage() {
        val text1 = "Hello sir, all neural modules are currently operating at maximum efficiency."
        assertEquals(DetectedLanguage.ENGLISH, LanguageDetector.detectLanguage(text1))

        val text2 = "Please turn on the flashlight and open settings."
        assertEquals(DetectedLanguage.ENGLISH, LanguageDetector.detectLanguage(text2))
    }

    @Test
    fun testDetectUzbekLanguage() {
        val text1 = "Salom ser, hamma tizimlar yaxshi ishlamoqda."
        assertEquals(DetectedLanguage.UZBEK, LanguageDetector.detectLanguage(text1))

        val text2 = "Bugun havo qanday bo'ladi?"
        assertEquals(DetectedLanguage.UZBEK, LanguageDetector.detectLanguage(text2))
    }

    @Test
    fun testSegmentBilingualText() {
        val mixedText = "Здравствуйте, сэр! All systems are ready. Готов к работе."
        val segments = LanguageDetector.segmentTextByLanguage(mixedText)
        assertTrue(segments.isNotEmpty())
        assertEquals(DetectedLanguage.RUSSIAN, segments[0].language)
    }
}
