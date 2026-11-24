package com.example.stayfree.ui.common

import com.example.stayfree.domain.score.FocusScore

/** How a focus score is shown: ring fill, colour tone, and the empty state. */
object ScoreDisplay {

    enum class Tone { FOCUS, WARN, DISTRACTION, EMPTY }

    /** null = no data yet. */
    fun toneOf(score: Int?): Tone = when (score?.let(FocusScore::bandOf)) {
        null -> Tone.EMPTY
        FocusScore.Band.GOOD -> Tone.FOCUS
        FocusScore.Band.MIXED -> Tone.WARN
        FocusScore.Band.POOR -> Tone.DISTRACTION
    }

    fun ringFraction(score: Int?): Float = (score ?: 0).coerceIn(0, 100) / 100f

    /** A day with no data shows a dash, never 0 — a zero reads as "you failed". */
    fun text(score: Int?, empty: String): String = score?.coerceIn(0, 100)?.toString() ?: empty
}
