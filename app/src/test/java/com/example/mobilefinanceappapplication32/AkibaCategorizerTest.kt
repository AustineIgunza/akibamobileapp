package com.example.mobilefinanceappapplication32

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Runs the shipped model.json on the JVM, so these tests cover the real trained model. */
class AkibaCategorizerTest {

    companion object {
        private lateinit var categorizer: AkibaCategorizer
        private val WEDNESDAY = LocalDate.of(2026, 11, 18)
        private val SATURDAY = LocalDate.of(2026, 11, 21)

        @BeforeClass
        @JvmStatic
        fun loadModel() {
            categorizer = AkibaCategorizer(File("src/main/assets/model.json").readText())
        }
    }

    @Test
    fun fillerWordsDoNotHideCategoryKeywords() {
        assertEquals("Health", categorizer.categorize(2000.0, WEDNESDAY, "Paid for the doctor consultation").category)
        assertEquals("Education", categorizer.categorize(1000.0, WEDNESDAY, "Sent money for the school books").category)
    }

    @Test
    fun keywordsAreFoundAfterTheThirdWord() {
        val prediction = categorizer.categorize(850.0, WEDNESDAY, "Paid for lunch at Java House")

        assertEquals("Food & dining", prediction.category)
        assertTrue(prediction.explanation, "Keywords matched: lunch, java, house." in prediction.explanation)
    }

    @Test
    fun punctuationIsStrippedFromKeywords() {
        val prediction = categorizer.categorize(450.0, WEDNESDAY, "Lunch @ KFC!")

        assertTrue(prediction.explanation, "Keywords matched: lunch, kfc." in prediction.explanation)
    }

    @Test
    fun hyphenatedVocabularyWordsAreKept() {
        val prediction = categorizer.categorize(3000.0, WEDNESDAY, "X-ray at the clinic")

        assertEquals("Health", prediction.category)
        assertTrue(prediction.explanation, "Keywords matched: x-ray, clinic." in prediction.explanation)
    }

    @Test
    fun probabilitiesAreANormalisedRankingOfEveryCategory() {
        val prediction = categorizer.categorize(500.0, WEDNESDAY, "")

        assertEquals(10, prediction.allProbabilities.size)
        assertEquals(1.0, prediction.allProbabilities.sumOf { it.second }, 1e-9)
        assertEquals(prediction.allProbabilities.sortedByDescending { it.second }, prediction.allProbabilities)
        assertEquals(prediction.allProbabilities.first(), prediction.category to prediction.confidence)
        assertEquals(prediction.confidence >= AkibaCategorizer.LOW_CONFIDENCE, prediction.isConfident)
    }

    @Test
    fun amountBinUpperEdgesAreInclusive() {
        assertTrue("Amount range: 0-100 KES" in categorizer.categorize(100.0, WEDNESDAY).explanation)
        assertTrue("Amount range: 100-300 KES" in categorizer.categorize(100.5, WEDNESDAY).explanation)
        assertTrue("Amount range: 30K+ KES" in categorizer.categorize(50_000.0, WEDNESDAY).explanation)
    }

    @Test
    fun weekendIsDetectedFromTheDate() {
        assertTrue("on Saturday (weekend)" in categorizer.categorize(500.0, SATURDAY).explanation)
        assertFalse("(weekend)" in categorizer.categorize(500.0, WEDNESDAY).explanation)
    }
}
