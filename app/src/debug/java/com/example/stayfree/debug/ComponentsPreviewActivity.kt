package com.example.stayfree.debug

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.stayfree.R
import com.example.stayfree.databinding.ActivityComponentsPreviewBinding
import com.example.stayfree.domain.content.ContentSignatures
import com.example.stayfree.ui.content.ContentBlockActivity
import com.example.stayfree.ui.common.HourlyStack
import com.example.stayfree.ui.common.ScoreDisplay

/** Every D4 shared component on one screen, with sample data, for design review. */
class ComponentsPreviewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // `--es content_block <targetId>` shows that content block screen for a screenshot.
        // It only shows a block (no way in), and this activity exists in debug builds only.
        intent.getStringExtra("content_block")?.let { id ->
            ContentSignatures.byId(id)?.let { startActivity(ContentBlockActivity.newIntent(this, it)) }
            finish()
            return
        }
        val b = ActivityComponentsPreviewBinding.inflate(layoutInflater)
        setContentView(b.root)

        val empty = getString(R.string.score_empty)
        listOf(
            Triple(b.ring1, b.score1, 86),
            Triple(b.ring2, b.score2, 62),
            Triple(b.ring3, b.score3, 31),
            Triple(b.ring4, b.score4, null),
        ).forEach { (ring, text, score) ->
            ring.ringWidth = 10 * resources.displayMetrics.density
            ring.setScore(score)
            text.text = ScoreDisplay.text(score, empty)
        }
        b.tvBigScore.text = "76"

        val min = 60_000L
        val hours = List(24) { h ->
            when (h) {
                in 0..6 -> 0L to 0L
                in 7..11 -> (h - 6) * 2 * min to (14 + h) * min
                in 12..17 -> 6 * min to 22 * min
                in 18..20 -> 18 * min to 20 * min
                21 -> 34 * min to 18 * min
                22 -> 20 * min to 8 * min
                else -> 4 * min to 2 * min
            }
        }
        b.bars.setData(hours)
        HourlyStack.peakHour(hours)?.let { b.tvPeak.text = getString(R.string.hourly_peak, it) }

        b.segmented.setOptions(listOf("Dan", "Nedelja"))

        val pm = packageManager
        fun icon(pkg: String) = runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
        b.tile1.bind("Instagram", "com.instagram.android", icon("com.instagram.android"), "1h 12m")
        b.tile2.bind("YouTube", "com.google.android.youtube", icon("com.google.android.youtube"), "48m")
        b.tile3.bind("TikTok", "com.zhiliaoapp.musically", null, "31m")
        b.tile4.bind("Chrome", "com.android.chrome", null, "22m")

        b.metric1.bind("2h 14m", "na ekranu · cilj 3h")
        b.metric2.bind("38", "Otključavanja")
        b.streak.bind(5)

        b.blockedIcon.setImageDrawable(icon("com.instagram.android"))
        b.blockedRing.setUnlockLeft(null)
    }
}
