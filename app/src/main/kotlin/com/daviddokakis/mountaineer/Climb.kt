package com.daviddokakis.mountaineer

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * One climb: its elevation every [STEP_M] metres from the bottom (the last step may be shorter).
 * [name] is null for climbs from a route (the Karoo doesn't name them).
 */
data class ClimbProfile(
    val name: String?,
    val lengthM: Double,
    val elevations: List<Double>,
    /** The direction of travel (degrees, 0 = north) of each [STEP_M] step, from the route's line. Null: unknown. */
    val headings: List<Double>? = null,
    /** The Karoo's own height gain and grade for this climb (route test 2026-11-10): shown where we have them. */
    val karooGainM: Double? = null,
    val karooGrade: Double? = null,
) {
    val gainM: Double get() = elevations.last() - elevations.first()
    val shownGainM: Double get() = karooGainM ?: gainM
    val shownGrade: Double get() = karooGrade ?: avgGrade
    val avgGrade: Double get() = if (lengthM > 0) gainM / lengthM * 100 else 0.0

    /** Elevation [m] metres from the bottom (clamped to the climb). */
    fun elevAt(m: Double): Double {
        val x = m.coerceIn(0.0, lengthM) / STEP_M
        val i = min(floor(x).toInt(), elevations.size - 2).coerceAtLeast(0)
        val f = x - i
        val stepEnd = min((i + 1) * STEP_M, lengthM)
        val stepLen = stepEnd - i * STEP_M
        val frac = if (stepLen > 0) (f * STEP_M / stepLen).coerceIn(0.0, 1.0) else 0.0
        return elevations[i] + frac * (elevations[i + 1] - elevations[i])
    }

    /** Average grade (%) between [a] and [b] metres from the bottom. */
    fun grade(a: Double, b: Double): Double {
        val b2 = min(b, lengthM)
        return if (b2 - a < 1) 0.0 else (elevAt(b2) - elevAt(a)) / (b2 - a) * 100
    }

    companion object {
        const val STEP_M = 100.0

        /** A profile from (distance, elevation) points of a route, [start]..[start]+[length]. */
        fun fromRoute(points: List<Pair<Double, Double>>, start: Double, length: Double, name: String?): ClimbProfile? {
            if (points.size < 2 || length < 100) return null
            fun at(d: Double): Double {
                if (d <= points.first().first) return points.first().second
                if (d >= points.last().first) return points.last().second
                var lo = 0; var hi = points.size - 1
                while (hi - lo > 1) { val mid = (lo + hi) / 2; if (points[mid].first <= d) lo = mid else hi = mid }
                val (d0, e0) = points[lo]; val (d1, e1) = points[hi]
                return if (d1 - d0 < 0.01) e0 else e0 + (d - d0) / (d1 - d0) * (e1 - e0)
            }
            val n = kotlin.math.ceil(length / STEP_M).toInt()
            val el = (0..n).map { k -> at(start + min(k * STEP_M, length)) }
            return ClimbProfile(name, length, el)
        }
    }
}

/**
 * The route graph (round 48): the stretch [fromM]..[toM] of the route (metres along it), its
 * elevation in [profile] (from [fromM]), the Karoo's climbs (route start to climb), where you are,
 * and the next climb's start for its flag. [whole]: the whole route; else the close-up of a climb.
 */
data class RouteView(
    val profile: ClimbProfile,
    val fromM: Double,
    val toM: Double,
    val climbs: List<Pair<Double, ClimbProfile>>,
    val youM: Double?,
    val flagM: Double?,
    val whole: Boolean,
    /** The summit of the climb shown (route metres): two tiles end their graph there (round 49). */
    val topM: Double = toM,
    /** Off route (round 55): the way back to the route runs from [offFromM] (you) to [offToM] (where it rejoins). */
    val offFromM: Double? = null,
    val offToM: Double? = null,
)

/**
 * Off route with turn-by-turn (round 55): the Karoo's way back to the route (its rejoin polyline) as
 * (metres along it, height) from you, and where it meets the route ([rejoinM], route metres). Heights
 * from a ground-height service; without one, a straight line from your altitude to the route's.
 */
data class OffRoute(val points: List<Pair<Double, Double>>, val rejoinM: Double) {
    val lengthM: Double get() = points.lastOrNull()?.first ?: 0.0
}

/** Which stretch of the route the graph shows (round 48, picked on 2026-11-10). */
object RouteViews {
    /** The close-up starts this far before a climb. */
    const val CLOSE_BEFORE_M = 1000.0
    /** The close-up is at least this wide, so a short climb looks short; a longer climb on its own fills it. */
    const val CLOSE_MIN_M = 2000.0
    /** Room left and right of what the close-up must show, as a share of its width. */
    private const val PAD = 0.05

    /**
     * Off route with a way back but no climb after where it rejoins (route test 2026-11-10, pass 3): the red
     * way back and the route on from there to the finish.
     */
    fun ahead(points: List<Pair<Double, Double>>, climbs: List<Pair<Double, ClimbProfile>>, total: Double?, off: OffRoute): RouteView? {
        val end = total ?: points.lastOrNull()?.first ?: return null
        val you = off.rejoinM - off.lengthM
        val merged = off.points.map { (d, e) -> (you + d) to e } + points.filter { it.first > off.rejoinM }
        val from = you - (end - you) * PAD / (1 - PAD)
        val profile = ClimbProfile.fromRoute(merged, from, end - from, null) ?: return null
        return RouteView(profile, from, end, climbs, you, null, whole = true, topM = end, offFromM = you, offToM = off.rejoinM)
    }

    /** All climbs done (round 60): the whole route, done climbs faded, you on it. */
    fun whole(points: List<Pair<Double, Double>>, climbs: List<Pair<Double, ClimbProfile>>, total: Double?, you: Double?): RouteView? {
        val end = total ?: points.lastOrNull()?.first ?: return null
        val profile = ClimbProfile.fromRoute(points, 0.0, end, null) ?: return null
        return RouteView(profile, 0.0, end, climbs, you, null, whole = true, topM = end)
    }

    /** The close-up of the climb [start]..[start]+[len] with you at [you]: the road up to it, the climb, some road after. */
    fun closeWindow(start: Double, len: Double, you: Double?): Pair<Double, Double> {
        val lead = you?.let { max(0.0, start - it) } ?: 0.0
        val span = max(CLOSE_MIN_M, (len + lead) / (1 - 2 * PAD))
        var from = start - (span - len) * 0.6
        if (you != null && you < from + span * PAD) from = you - span * PAD
        return from to from + span
    }

    /**
     * The graph for climb [index] of [climbs] with you at [you] (route metres; null: unknown), on it
     * or before it. Null: draw the climb on its own, as before round 48 (a climb longer than the
     * close-up while you're on it), or no route elevation.
     */
    fun view(points0: List<Pair<Double, Double>>, climbs: List<Pair<Double, ClimbProfile>>, total: Double?, you0: Double?, index: Int, onClimb: Boolean, off: OffRoute? = null): RouteView? {
        val (start, c) = climbs.getOrNull(index) ?: return null
        // off route: the way back laid in front of the rejoin point, you at its start (route metres before it)
        val you = if (off != null) off.rejoinM - off.lengthM else you0
        val points = if (off == null) points0 else
            off.points.map { (d, e) -> (you!! + d) to e } + points0.filter { it.first > off.rejoinM }
        val end = total ?: points.lastOrNull()?.first ?: return null
        val close = onClimb || (you != null && start - you <= CLOSE_BEFORE_M)
        if (onClimb && c.lengthM >= CLOSE_MIN_M) return null
        // round 52: not the whole route (on a 312 km route its climbs were slivers) but the road from you
        // to the next climb's top; close: the 2 km close-up
        val (from, to) = if (close) closeWindow(start, c.lengthM, you) else {
            val here = you ?: 0.0
            val top = min(end, start + c.lengthM)
            (here - (top - here) * PAD / (1 - PAD)) to top
        }
        // round 58: a bit more than the window, for the road after the summit
        val profile = ClimbProfile.fromRoute(points, from, (to - from) * 1.2, null) ?: return null
        return RouteView(profile, from, to, climbs, you, if (onClimb) null else start, whole = !close, topM = start + c.lengthM,
            offFromM = off?.let { you }, offToM = off?.rejoinM)
    }
}

/** What the field shows. Rendering only reads this. */
data class ClimbState(
    /** A route with climbs is loaded. */
    val hasRoute: Boolean = false,
    /** The climb shown: the one you're on, or the next one. Null: none (left). */
    val climb: ClimbProfile? = null,
    /** On the climb: metres from its bottom. Null: before it. */
    val doneM: Double? = null,
    /** Before the climb: metres to its start. */
    val nextInM: Double? = null,
    /** The route graph (whole route or a close-up); null: the climb on its own. */
    val route: RouteView? = null,
    val number: Int? = null,
    val total: Int? = null,
    val etaSec: Int? = null,
    /** Strava PR on the segment you're on (Karoo Live Segments), and how far behind it you are (+ behind). */
    val prSec: Int? = null,
    val gapSec: Int? = null,
    /** All climbs done (round 60): metres to the finish (the Karoo's distance to destination). */
    val toFinishM: Double? = null,
    /** A long climb drawn on its own: the route's road after its top (round 58; null: none). */
    val afterTop: ClimbProfile? = null,
    /** Off the route (round 55): the name line says OFF ROUTE in red. */
    val offRoute: Boolean = false,
    /** At the summit (round 50): what you rode, shown for the summit minute. */
    val summary: ClimbSummary? = null,
    /** The rider's 30 s power from the Karoo (not drawn; the debug test ride climbs at it). */
    val powerW: Double? = null,
) {
    val onClimb: Boolean get() = climb != null && doneM != null
    val toTopM: Double? get() = if (climb != null && doneM != null) max(0.0, climb.lengthM - doneM) else null
    val elevToTopM: Double? get() = if (climb != null && doneM != null) max(0.0, climb.elevations.last() - climb.elevAt(doneM)) else null
    /** The 100 m grade you're on now. */
    val gradeNow: Double? get() = if (climb != null && doneM != null) climb.grade(floor(doneM / 100) * 100, floor(doneM / 100) * 100 + 100) else null
    val displayName: String? get() = climb?.name ?: number?.let { n -> total?.let { "Climb $n of $it" } ?: "Climb $n" }
}

/** A climb you just topped (round 50): your time on it, average power (with a power meter), VAM (m/h). */
data class ClimbSummary(val timeSec: Int, val avgPowerW: Int?, val vamMh: Int) {
    companion object {
        fun of(timeSec: Int, gainM: Double, avgPowerW: Int?) =
            ClimbSummary(timeSec, avgPowerW, if (timeSec > 0) (max(0.0, gainM) / timeSec * 3600).roundToInt() else 0)
    }
}

/** The wind (forecast, 10 m up): speed and where it blows from (degrees, 0 = from the north). */
data class Wind(val speedMs: Double, val fromDeg: Double)

/**
 * Cycling physics, for the time to top: speed from power on a grade (gravity, rolling, air with
 * the head- or tailwind), and power from speed.
 */
object Physics {
    private const val G = 9.81
    private const val CRR = 0.005
    private const val CDA = 0.36
    private const val RHO = 1.2
    /** The bike weight when the rider hasn't set one (Settings › Time to top). */
    const val BIKE_KG = 8.0
    /** Forecast wind is 10 m up and in the open; a rider on a climb gets less of it. */
    const val WIND_AT_RIDER = 0.6

    /** Power for [v] on [gradePct] with [headwindMs] against you (negative: tailwind). */
    fun power(v: Double, gradePct: Double, massKg: Double, headwindMs: Double = 0.0): Double {
        val th = atan(gradePct / 100)
        val air = v + headwindMs
        return v * (massKg * G * (sin(th) + CRR * cos(th)) + 0.5 * RHO * CDA * air * abs(air))
    }

    /** Speed (m/s) for [watts] on [gradePct] with [headwindMs]. */
    fun speed(watts: Double, gradePct: Double, massKg: Double, headwindMs: Double = 0.0): Double {
        var lo = 0.3; var hi = 25.0
        repeat(40) { val mid = (lo + hi) / 2; if (power(mid, gradePct, massKg, headwindMs) > watts) hi = mid else lo = mid }
        return (lo + hi) / 2
    }

    /** The part of [w] against a rider heading [headingDeg], as felt on the road (negative: tailwind). */
    fun headwind(w: Wind?, headingDeg: Double?): Double {
        if (w == null || headingDeg == null) return 0.0
        return w.speedMs * WIND_AT_RIDER * cos(Math.toRadians(w.fromDeg - headingDeg))
    }

    /**
     * Seconds to ride from [fromM] to the top of [c] at [watts], 100 m at a time on each step's grade
     * and wind. [speedFactor]: how much faster than the model the rider has really been ([SpeedCorrection]).
     */
    fun timeToTop(
        c: ClimbProfile, fromM: Double, watts: Double, massKg: Double,
        wind: Wind? = null, speedFactor: Double = 1.0,
    ): Double {
        var t = 0.0
        var m = fromM
        while (m < c.lengthM - 0.5) {
            val step = floor(m / ClimbProfile.STEP_M).toInt()
            val next = min(c.lengthM, (step + 1) * ClimbProfile.STEP_M)
            val head = headwind(wind, c.headings?.getOrNull(step))
            t += (next - m) / (speed(watts, c.grade(m, next), massKg, head) * speedFactor)
            m = next
        }
        return t
    }
}

/**
 * How much faster (or slower) the rider really climbs than the physics says at their power: wind
 * the forecast missed, a draft, the bike, tyres, position, a power meter that reads high or low.
 * Learned second by second on the climb; moves slowly, so the time to top stays steady.
 */
class SpeedCorrection(start: Double = 1.0) {
    var factor = start
        private set

    /** One second of riding: the speed you did and the speed the model gives your power. */
    fun learn(actualMs: Double, modelMs: Double) {
        if (actualMs < MIN_MS || modelMs < MIN_MS) return
        val ratio = actualMs / modelMs
        if (ratio < MIN_RATIO || ratio > MAX_RATIO) return
        factor += (ratio - factor) * RATE
    }

    fun reset(start: Double = 1.0) { factor = start }

    private companion object {
        const val MIN_MS = 1.5
        const val MIN_RATIO = 0.6
        const val MAX_RATIO = 1.6
        /** About a minute to settle (1 − 0.97^60 ≈ 0.84). */
        const val RATE = 0.03
    }
}

/**
 * Improve over time (Settings): what one climb taught, carried into the next. Each climb moves the
 * learned factor 30 % of the way to what it measured; climbs under 3 minutes with power teach nothing.
 */
object Learning {
    private const val WEIGHT = 0.3
    private const val MIN_SECONDS = 180
    private const val MIN = 0.7
    private const val MAX = 1.4

    fun blend(old: Double, climb: Double, seconds: Int): Double =
        if (seconds < MIN_SECONDS) old else (old + (climb - old) * WEIGHT).coerceIn(MIN, MAX)
}

object Fmt {
    fun clock(sec: Int): String {
        val s = abs(sec)
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }
    /** "+ 0:18" behind, "− 0:24" ahead, with a thin space after the sign (round 44: Relative's own space is a digit wide). */
    fun gap(sec: Int): String = (if (sec >= 0) "+\u2009" else "−\u2009") + clock(sec)
    /**
     * Every distance in the field, one rule (round 53, feedback: "as long as we are consistent"): under
     * 1 km in metres to 10 m (800 m), from 1 km in km with one decimal (1.2 km). Value and unit apart,
     * for a big number with a smaller unit.
     */
    fun distance(m: Double): Pair<String, String> {
        val r10 = (m / 10).roundToInt() * 10
        return if (r10 < 1000) "$r10" to "m" else String.format(java.util.Locale.US, "%.1f", m / 1000) to "km"
    }
    fun metres(m: Double): String = m.roundToInt().toString()
    /** A distance in a line of text: value, a thin space, unit ([distance]'s rule). */
    fun length(m: Double): String = distance(m).let { (v, u) -> "$v\u2009$u" }
}

/** The mockups' made-up climb (Col du Lac, 6.4 km, 3.8 km up): Settings Example and field previews. */
object ExampleClimb {
    private val GRADES = doubleArrayOf(4.0, 6.0, 7.0, 8.0, 9.0, 7.0, 6.0, 10.0, 12.0, 9.0, 8.0, 7.0, 11.0, 8.0, 6.0, 5.0)
    val climb = ClimbProfile(
        "Col du Lac", 6400.0,
        (0..64).runningFold(0.0) { e, i -> if (i == 0) e else e + GRADES[(i - 1) / 4] }.drop(1).map { it + 200 },
    )
    val state = ClimbState(
        hasRoute = true, climb = climb, doneM = 3800.0, number = 1, total = 3,
        etaSec = 582, prSec = 1452, gapSec = 18,
    )
}
