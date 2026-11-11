package com.daviddokakis.mountaineer

import android.content.Context
import android.graphics.Bitmap

/**
 * DEBUG ONLY: what the field gallery (FieldGallery.kt) draws, with the field's real renderer:
 * Sa Calobra 5.2 km up, 18 s behind the PR, before it on a test route, and at the summit.
 */
object GalleryFields {
    /** A ride up Sa Calobra for the README GIF (seq-00…): metres to the start, then km up with the gap to the PR in seconds. */
    private val SEQ: List<Pair<Double, Int>> = listOf(-1500.0 to 0, -900.0 to 0, -400.0 to 0) +
        listOf(0.4 to 4, 1.4 to 9, 2.6 to 14, 3.8 to 18, 5.0 to 15, 6.1 to 10, 7.2 to 4, 8.2 to -3, 9.0 to -7, 9.4 to -9)

    val variants = listOf("eta", "gap", "ahead", "dist", "next", "near", "short", "far", "far2", "offfar", "offnear", "top100", "summit", "summitshort", "summitvam", "noroute", "offempty", "noclimbs", "done") +
        SEQ.indices.map { "seq-%02d".format(it) }
    val sizes: List<String>? = null

    fun render(context: Context, variant: String, w: Int, h: Int, night: Boolean): Bitmap {
        val seq = variant.removePrefix("seq-").toIntOrNull()?.takeIf { variant.startsWith("seq-") }?.let { SEQ[it] }
        val s = when {
            seq != null && seq.first < 0 -> ClimbState(hasRoute = true, climb = SaCalobra.profile, nextInM = -seq.first, route = SaCalobra.route(seq.first), number = 2, total = 2)
            // at the top: the summit minute with its summary (round 50)
            seq != null && seq.first >= 9.4 -> SaCalobra.at(9.4, seq.second).let { it.copy(etaSec = 0, summary = ClimbSummary.of(it.prSec!! + seq.second, SaCalobra.profile.shownGainM, 248)) }
            seq != null -> SaCalobra.at(seq.first, seq.second)
            else -> when (variant) {
            // round 48: 1.2 km before Sa Calobra (the whole route); 800 m before the short climb (its close-up); 200 m up it
            "next" -> ClimbState(hasRoute = true, climb = SaCalobra.profile, nextInM = 1200.0, route = SaCalobra.route(-1200.0), number = 2, total = 2)
            // round 52: a long route (like Mallorca 312): at its start, 23 km before an 18.5 km climb; at 62 km, 9 km before a 4.5 km one
            // round 55: off route with a way back: 1.8 km over a hill back to the long route at 3 km; 600 m back to the
            // test route at 2.2 km, 300 m before its short climb (so 900 m to the climb: the close-up)
            "offfar" -> LongRoute.offRoute()
            "offnear" -> DemoRoute.climbs[0].let { (start, c) ->
                val off = OffRoute((0..24).map { k -> (k * 25.0) to (6.0 + 4 * kotlin.math.sin(k / 4.0)) }, 2200.0)
                ClimbState(hasRoute = true, climb = c, nextInM = off.lengthM + (start - off.rejoinM), number = 1, total = 2, offRoute = true,
                    route = RouteViews.view(DemoRoute.points, DemoRoute.climbs, DemoRoute.TOTAL, null, 0, onClimb = false, off = off))
            }
            "far" -> LongRoute.before(0.0, 0)
            "far2" -> LongRoute.before(62_000.0, 2)
            "near" -> ClimbState(hasRoute = true, climb = DemoRoute.climbs[0].second, nextInM = 800.0, number = 1, total = 2,
                route = DemoRoute.view(DemoRoute.SHORT_AT - 800, 0, onClimb = false))
            "short" -> ClimbState(hasRoute = true, climb = DemoRoute.climbs[0].second, doneM = 200.0, number = 1, total = 2, etaSec = 65,
                route = DemoRoute.view(DemoRoute.SHORT_AT + 200, 0, onClimb = true))
            "ahead" -> SaCalobra.at(5.2, -24)
            "top100" -> SaCalobra.at(9.3, 18)
            // round 50: the summit minute's summary: with a PR (Sa Calobra), a short climb with a power meter, one with neither (VAM)
            "summit" -> SaCalobra.at(9.4, 18).let { it.copy(etaSec = 0, summary = ClimbSummary.of(it.prSec!! + 18, SaCalobra.profile.gainM, 240)) }
            "summitshort", "summitvam" -> DemoRoute.climbs[0].second.let { c ->
                ClimbState(hasRoute = true, climb = c, doneM = c.lengthM, number = 1, total = 2, etaSec = 0,
                    route = DemoRoute.view(DemoRoute.SHORT_AT + c.lengthM, 0, onClimb = true),
                    summary = ClimbSummary.of(125, c.gainM, if (variant == "summitshort") 262 else null))
            }
            "noroute" -> ClimbState()
            // round 59: a route loaded, no climbs from the Karoo: off route (rerouting), or none on it
            "offempty" -> LongRoute.noClimbs(off = true)
            "noclimbs" -> LongRoute.noClimbs(off = false)
            "done" -> LongRoute.allDone()   // round 60: the map stays when all climbs are done
            else -> SaCalobra.at(5.2, 18)
        }
        }
        val prefs = when (variant) {
            "gap", "ahead" -> Prefs(oneTile = OneTile.GAP, twoTiles = TwoTiles.GAP)
            "dist" -> Prefs(oneTile = OneTile.DIST, twoTiles = TwoTiles.DIST)
            else -> Prefs()
        }
        return ClimbRenderer(context).render(s, prefs, w, h, night)
    }
}
