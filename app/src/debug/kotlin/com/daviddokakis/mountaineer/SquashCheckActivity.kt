package com.daviddokakis.mountaineer

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * DEBUG ONLY: the field in a shortened row (a map page with a route: the Karoo makes a row 18 px
 * shorter than it reported, without telling the app). For each size: the field at its reported
 * height, the old single picture squashed into the shorter row, and the new KarooField split.
 *   adb shell am start -n com.daviddokakis.mountaineer/.SquashCheckActivity [--es variant eta|gap|next|near|summit] [--es size one|two|half]
 */
class SquashCheckActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val variant = intent.getStringExtra("variant") ?: "eta"
        val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val s = when (variant) {
            "next" -> ClimbState(hasRoute = true, climb = SaCalobra.profile, nextInM = 1200.0, route = SaCalobra.route(-1200.0), number = 2, total = 2)
            "near" -> ClimbState(hasRoute = true, climb = DemoRoute.climbs[0].second, nextInM = 800.0, number = 1, total = 2,
                route = DemoRoute.view(DemoRoute.SHORT_AT - 800, 0, onClimb = false))
            "done" -> LongRoute.allDone()
            "offempty" -> LongRoute.noClimbs(off = true)
            "summit" -> SaCalobra.at(9.4, 18).let { it.copy(etaSec = 0, summary = ClimbSummary.of(it.prSec!! + 18, SaCalobra.profile.gainM, 240)) }
            else -> SaCalobra.at(5.2, 18)
        }
        val prefs = if (variant == "gap") Prefs(oneTile = OneTile.GAP, twoTiles = TwoTiles.GAP) else Prefs()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(1, 8, 1, 8) }
        // Each size as reported and as the Karoo shows it on a map page with a route (2026-11-10 on our
        // test Karoo: 238 × 168 shown 145 high, 478 × 216 shown 188)
        val sizes = mapOf(
            "one" to listOf(Triple(236, 141, 123), Triple(238, 168, 145)),
            "two" to listOf(Triple(478, 141, 123), Triple(478, 216, 188)),
            "half" to listOf(Triple(478, 300, 270), Triple(478, 336, 318)),
        )
        for ((w, h, short) in sizes[intent.getStringExtra("size") ?: "two"]!!) {
            col.addView(TextView(this).apply { text = "$w × $h → $short: reported, old squashed, new (top to bottom)"; setTextColor(if (night) Color.WHITE else Color.BLACK) })
            val renderer = ClimbRenderer(this)
            val bmp = renderer.render(s, prefs, w, h, night)
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            fun cell(v: android.view.View, hh: Int) = FrameLayout(this).apply { addView(v, FrameLayout.LayoutParams(w, hh)); setPadding(0, 0, 0, 10) }
            row.addView(cell(ImageView(this).apply { setImageBitmap(bmp); scaleType = ImageView.ScaleType.FIT_XY }, h))
            row.addView(cell(ImageView(this).apply { setImageBitmap(bmp); scaleType = ImageView.ScaleType.FIT_XY }, short))
            val box = FrameLayout(this)
            box.addView(fieldViews(this, renderer, bmp, h).apply(this, box))
            row.addView(cell(box, short))
            col.addView(row)
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(if (night) Color.BLACK else Color.WHITE); addView(col) })
        // also as one picture (files/squash.png; adb exec-out run-as … cat files/squash.png), for checks without scrolling
        col.post {
            val pic = android.graphics.Bitmap.createBitmap(col.width, col.height, android.graphics.Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(pic).apply { drawColor(if (night) Color.BLACK else Color.WHITE); col.draw(this) }
            java.io.File(filesDir, "squash.png").outputStream().use { pic.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
