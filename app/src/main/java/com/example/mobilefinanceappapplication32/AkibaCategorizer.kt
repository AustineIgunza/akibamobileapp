package com.example.mobilefinanceappapplication32

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.exp

/**
 * On-device Multinomial Naive Bayes categorizer (v3).
 * Uses description keywords + KES amount bins + time features.
 */
class AkibaCategorizer(context: Context) {

    private val classes: List<String>
    private val classLogPrior: List<Double>
    private val features: List<String>
    private val categories: Map<String, List<String>>
    private val featureLogProb: List<List<Double>>
    private val amountBinEdges: List<Double>
    private val amountBinLabels: List<String>
    private val vocabulary: List<String>

    init {
        val json = context.assets.open("model.json").bufferedReader().readText()
        val model = JSONObject(json)
        classes = model.getJSONArray("classes").let { a -> (0 until a.length()).map { a.getString(it) } }
        classLogPrior = model.getJSONArray("class_log_prior").let { a -> (0 until a.length()).map { a.getDouble(it) } }
        features = model.getJSONArray("features").let { a -> (0 until a.length()).map { a.getString(it) } }
        val catObj = model.getJSONObject("categories")
        categories = features.associateWith { name ->
            catObj.getJSONArray(name).let { a -> (0 until a.length()).map { a.get(it).toString() } }
        }
        featureLogProb = model.getJSONArray("feature_log_prob").let { rows ->
            (0 until rows.length()).map { r ->
                rows.getJSONArray(r).let { cols -> (0 until cols.length()).map { cols.getDouble(it) } }
            }
        }
        amountBinEdges = model.getJSONArray("amount_bin_edges").let { a -> (0 until a.length()).map { a.getDouble(it) } }
        amountBinLabels = model.getJSONArray("amount_bin_labels").let { a -> (0 until a.length()).map { a.getString(it) } }
        vocabulary = if (model.has("vocabulary")) {
            model.getJSONArray("vocabulary").let { a -> (0 until a.length()).map { a.getString(it) } }
        } else emptyList()
    }

    data class Prediction(
        val category: String,
        val confidence: Double,
        val allProbabilities: List<Pair<String, Double>>,
        val explanation: String,
        val isConfident: Boolean
    )

    private val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    private fun extractKeywords(description: String): Set<String> {
        return description.lowercase()
            .replace(",", "").replace(".", "")
            .split("\\s+".toRegex())
            .filter { it.length >= 3 }
            .take(3)
            .toSet()
    }

    private fun buildFeatures(amount: Double, date: LocalDate, description: String): Map<String, String> {
        val amountBin = amountBinLabels.zip(amountBinEdges.drop(1))
            .firstOrNull { (_, upper) -> amount <= upper }?.first ?: amountBinLabels.last()
        val keywords = extractKeywords(description)
        val result = mutableMapOf(
            "amount_bin" to amountBin,
            "day_of_week" to dayNames[date.dayOfWeek.value - 1],
            "month" to date.monthValue.toString(),
            "is_weekend" to if (date.dayOfWeek.value >= 6) "1" else "0"
        )
        for (kw in vocabulary) {
            result["kw_$kw"] = if (kw in keywords) "1" else "0"
        }
        return result
    }

    private fun predict(values: Map<String, String>): Pair<List<Pair<String, Double>>, List<String>> {
        val scores = classLogPrior.toMutableList()
        var column = 0
        val unseen = mutableListOf<String>()

        for (name in features) {
            val seen = categories[name]!!
            val value = values[name] ?: run { column += seen.size; unseen.add(name); continue }
            val idx = seen.indexOf(value)
            if (idx >= 0) {
                val j = column + idx
                for (i in scores.indices) {
                    scores[i] += featureLogProb[i][j]
                }
            } else {
                unseen.add(name)
            }
            column += seen.size
        }

        // softmax
        val top = scores.max()
        val weights = scores.map { exp(it - top) }
        val total = weights.sum()
        val probs = classes.zip(weights.map { it / total })
        return probs.sortedByDescending { it.second } to unseen
    }

    fun categorize(amount: Double, date: LocalDate, description: String = ""): Prediction {
        val values = buildFeatures(amount, date, description)
        val (probs, unseen) = predict(values)
        val (bestCategory, bestProb) = probs.first()
        val isConfident = bestProb >= LOW_CONFIDENCE

        val explanation = buildExplanation(values, bestCategory, bestProb, probs, unseen, description)

        return Prediction(
            category = bestCategory,
            confidence = bestProb,
            allProbabilities = probs,
            explanation = explanation,
            isConfident = isConfident
        )
    }

    private fun buildExplanation(
        values: Map<String, String>,
        category: String,
        confidence: Double,
        probs: List<Pair<String, Double>>,
        unseen: List<String>,
        description: String
    ): String {
        val pct = "%.0f".format(confidence * 100)
        val dayName = values["day_of_week"]!!
        val amountBin = values["amount_bin"]!!
        val isWeekend = values["is_weekend"] == "1"
        val matchedKws = extractKeywords(description).filter { it in vocabulary }

        val parts = mutableListOf<String>()
        parts.add("The model predicts \"$category\" with $pct% confidence.")

        if (matchedKws.isNotEmpty()) {
            parts.add("Keywords matched: ${matchedKws.joinToString(", ")}.")
        }
        parts.add("Amount range: $amountBin KES on $dayName${if (isWeekend) " (weekend)" else ""}.")

        if (!isConfidentEnough(confidence)) {
            parts.add("Confidence is below ${(LOW_CONFIDENCE * 100).toInt()}% — please confirm the category.")
        }

        if (probs.size >= 2) {
            val runner = probs[1]
            parts.add("Next best: ${runner.first} (${"%.0f".format(runner.second * 100)}%).")
        }

        return parts.joinToString(" ")
    }

    private fun isConfidentEnough(confidence: Double) = confidence >= LOW_CONFIDENCE

    companion object {
        const val LOW_CONFIDENCE = 0.5
    }
}
