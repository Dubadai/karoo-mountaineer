package com.daviddokakis.mountaineer

import kotlin.math.min

/**
 * DEBUG ONLY, pictures only (field gallery, SquashCheckActivity): Sa Calobra and a test route around it.
 * The field never shows these: the Sa Calobra test ride over USB was removed on 2026-11-10 (only
 * real routes; test data comes from FakeWatts).
 */
object SaCalobra {
    /** Elevation every 100 m: EU-DEM 25 m along the Ma-2141 (OSRM route), smoothed, grades
     *  evened (3–12.5 %), scaled to the published 9.4 km / 668 m. Design notes: docs/design/tiny-climb. */
    private val ELEVATIONS = listOf(14.0, 16.9, 21.0, 25.9, 31.7, 37.2, 43.0, 48.8, 54.3, 59.1, 63.4, 68.1, 74.2, 81.6, 90.5, 100.9, 111.9, 123.5, 134.3, 142.9, 148.5, 151.7, 154.6, 157.5, 160.4, 163.3, 166.2, 170.1, 176.7, 186.4, 198.0, 209.1, 219.4, 228.8, 238.3, 246.5, 255.6, 263.8, 273.0, 280.5, 289.1, 295.9, 301.6, 306.4, 310.8, 315.6, 319.6, 324.4, 329.0, 334.4, 341.7, 352.0, 362.8, 374.0, 385.2, 395.1, 404.2, 410.3, 417.0, 422.7, 429.2, 435.6, 442.9, 452.0, 461.3, 468.8, 474.9, 480.9, 486.5, 492.4, 498.3, 505.5, 512.8, 519.2, 525.8, 531.4, 538.2, 545.0, 553.4, 563.0, 574.2, 585.8, 594.5, 600.8, 604.4, 608.0, 610.9, 614.5, 620.3, 628.8, 639.0, 648.7, 658.9, 669.5, 681.3)
    val profile = ClimbProfile("Sa Calobra", 9400.0, ELEVATIONS)

    /** The route graph [m] metres from Sa Calobra's start (negative: before it), on the test route. */
    fun route(m: Double): RouteView? = DemoRoute.view(DemoRoute.CALOBRA_AT + m, 1, onClimb = m >= 0)

    private const val MASS = 75.0 + Physics.BIKE_KG
    /** The PR: an even 235 W up the whole climb. */
    private const val PR_WATTS = 235.0
    private val prSec: Int by lazy {
        var t = 0.0
        for (i in 1 until ELEVATIONS.size) {
            val a = (i - 1) * 100.0; val b = min(i * 100.0, profile.lengthM)
            t += (b - a) / Physics.speed(PR_WATTS, profile.grade(a, b), MASS)
        }
        t.toInt()
    }

    /** A fixed moment for pictures: [km] up, [gap] seconds behind (+) or ahead (−). */
    fun at(km: Double, gap: Int): ClimbState {
        val m = km * 1000
        return ClimbState(
            hasRoute = true, climb = profile, doneM = m, number = 1, total = 1,
            etaSec = Physics.timeToTop(profile, m, 240.0, MASS).toInt(), prSec = prSec, gapSec = gap,
            afterTop = ClimbProfile.fromRoute(DemoRoute.points, DemoRoute.CALOBRA_AT + profile.lengthM, 1000.0, null),
        )
    }
}

/**
 * The test route (round 48): 17.4 km, a short climb (510 m, about 4.7 %, like the 2026-11-10 ride's)
 * at 2.5 km, Sa Calobra from 7 km, a bit of road at the top. For the whole route and the close-up.
 */
object DemoRoute {
    const val SHORT_AT = 2500.0
    const val SHORT_M = 510.0
    const val CALOBRA_AT = 7000.0
    const val TOTAL = 17400.0
    private val ANCHORS = listOf(0.0 to 10.0, 2500.0 to 12.0, 3010.0 to 36.0, 3600.0 to 34.0, 7000.0 to 14.0)

    private fun elev(d: Double): Double = when {
        d >= CALOBRA_AT + SaCalobra.profile.lengthM -> SaCalobra.profile.elevations.last() + (d - CALOBRA_AT - SaCalobra.profile.lengthM) * 0.008
        d >= CALOBRA_AT -> SaCalobra.profile.elevAt(d - CALOBRA_AT)
        else -> {
            val i = ANCHORS.indexOfLast { it.first <= d }.coerceIn(0, ANCHORS.size - 2)
            val (d0, e0) = ANCHORS[i]; val (d1, e1) = ANCHORS[i + 1]
            val onShort = d in SHORT_AT..(SHORT_AT + SHORT_M)
            e0 + (d - d0) / (d1 - d0) * (e1 - e0) + if (onShort) 1.2 * kotlin.math.sin((d - SHORT_AT) / 90) else 2.5 * kotlin.math.sin(d / 380)
        }
    }

    val points: List<Pair<Double, Double>> = (0..(TOTAL / 25).toInt()).map { k -> (k * 25.0) to elev(k * 25.0) }
    val climbs: List<Pair<Double, ClimbProfile>> = listOf(
        SHORT_AT to ClimbProfile.fromRoute(points, SHORT_AT, SHORT_M, null)!!,
        CALOBRA_AT to SaCalobra.profile,
    )

    fun view(you: Double?, index: Int, onClimb: Boolean): RouteView? = RouteViews.view(points, climbs, TOTAL, you, index, onClimb)
}

/**
 * A long route for pictures (round 52), shaped like the first 120 km of our Mallorca 312 test route:
 * the climbs the Karoo listed there (start, length, average grade), rolling road between them.
 */
object LongRoute {
    const val TOTAL = 120_000.0
    private val CLIMBS = listOf(
        Triple(22890.0, 18510.0, 3.2), Triple(47520.0, 8490.0, 4.1), Triple(70980.0, 4470.0, 4.7),
        Triple(79170.0, 990.0, 3.0), Triple(81930.0, 6480.0, 4.6), Triple(91410.0, 2970.0, 4.4),
        Triple(101910.0, 660.0, 4.6), Triple(105870.0, 2340.0, 3.6), Triple(109950.0, 870.0, 4.4),
        Triple(112980.0, 4170.0, 4.6),
    )
    val points: List<Pair<Double, Double>> = run {
        val out = ArrayList<Pair<Double, Double>>()
        var e = 20.0
        var d = 0.0
        while (d <= TOTAL) {
            out += d to e
            val c = CLIMBS.firstOrNull { d >= it.first && d < it.first + it.second }
            val g = if (c != null) c.third + 1.4 * kotlin.math.sin((d - c.first) / 160)
                else if (e > 40) -2.5 + 0.8 * kotlin.math.sin(d / 700) else 0.6 * kotlin.math.sin(d / 900)
            e = maxOf(5.0, e + g / 100 * 50)
            d += 50
        }
        out
    }
    val climbs: List<Pair<Double, ClimbProfile>> = CLIMBS.map { (s, l, _) -> s to ClimbProfile.fromRoute(points, s, l, null)!! }

    /** A route with no climbs (round 60): you at 40 km, off route or not. */
    fun noClimbs(off: Boolean): ClimbState = ClimbState(hasRoute = true, total = 0, offRoute = off, toFinishM = if (off) null else TOTAL - 40_000.0,
        route = RouteViews.whole(points, emptyList(), TOTAL, if (off) null else 40_000.0))

    /** All climbs done (round 60): at 118 km, 2 km before the finish. */
    fun allDone(): ClimbState = ClimbState(hasRoute = true, total = climbs.size, toFinishM = TOTAL - 118_000.0,
        route = RouteViews.whole(points, climbs, TOTAL, 118_000.0))

    /** Off route (round 55): 1.8 km over a hill back to the route at 3 km, before climb 1. */
    fun offRoute(): ClimbState {
        val off = OffRoute((0..72).map { k -> (k * 25.0) to (20 + 28 * kotlin.math.sin(k / 72.0 * Math.PI)) }, 3000.0)
        val (start, c) = climbs[0]
        return ClimbState(hasRoute = true, climb = c, nextInM = off.lengthM + (start - off.rejoinM), number = 1, total = 30, offRoute = true,
            route = RouteViews.view(points, climbs, TOTAL, null, 0, onClimb = false, off = off))
    }

    /** Before climb [index] with you at [you] (route metres). */
    fun before(you: Double, index: Int): ClimbState {
        val (start, c) = climbs[index]
        return ClimbState(hasRoute = true, climb = c, nextInM = start - you, number = index + 1, total = 30,
            route = RouteViews.view(points, climbs, TOTAL, you, index, onClimb = false))
    }
}
