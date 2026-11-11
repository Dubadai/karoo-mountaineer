package com.daviddokakis.mountaineer

import com.dubadai.karoo.common.*
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws the Mountaineer field as one bitmap at any size (design: docs/design/tiny-climb, rounds 8–27):
 * - one tile: the Karoo's header (ClimbER mountain + label), the number, a small climb graph;
 * - two tiles: the graph full height with the climb name on it; label, big number, small line right;
 * - nearly half (250 px+): TO TOP bar, the whole climb, PR / TIME VS PR / TIME TO TOP on navy;
 * - half and up (330 px+): the same plus the 500 M bracket and the next 500 m as five sliding boxes.
 * No climb right now: an empty state (no route / all climbs done) instead.
 * The graph is drawn like the Karoo's own (ClimbER); icons, names and type are the Karoo's
 * (KarooType: Relative values, header rules, the ride app's glyphs).
 * Layouts are drawn in design pixels (238 or 478 wide, the Karoo 3's tiles) and scaled to the field.
 */
class ClimbRenderer(context: Context) {

    private val cond: Typeface = context.resources.getFont(R.font.plex_condensed_medium)
    private val med: Typeface = context.resources.getFont(R.font.plex_medium)
    /** The Karoo's value font, Relative (from the system, never bundled; KarooType). */
    private val num: Typeface = KarooType.value
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fp = Paint(Paint.ANTI_ALIAS_FLAG)
    /** The one-tile graph is drawn without its navy box (round 42): marks in the text colour, the ring's hole in the tile's. */
    private var bareGraph = false
    private var markInk = Color.WHITE
    private var holeInk = CB700
    private val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }

    /**
     * From the last [render]: how many rows from the top must keep their height (the header and the
     * number, or the half screen's bar). The Karoo can make a row shorter than it reported (a map
     * page with a route: 141 → 123 px); [TinyClimbDataType] then lets only the rows below this
     * stretch (the graph), via karoo-common's KarooField. 0: use the kit's header band.
     */
    var fixedTopPx = 0
        private set
    /**
     * From the last [render], two tiles only: where the graph ends and the text column starts (px;
     * [WIDE_SPLIT] of the design width), and the text column's header band height. 0: not two tiles.
     */
    var splitPx = 0
        private set
    var columnHeaderPx = 0
        private set
    private var columnHeader = 0f
    /** Two tiles: the graph side's top band, the climb's name, kept at true size above the stretching graph (round 51). */
    var graphHeaderPx = 0
        private set
    private var graphHeader = 0f
    private var drewRouteOnly = false

    /** From the last [render]: rows at the bottom that keep their height too (0: none). */
    var fixedBottomPx = 0
        private set
    private var fixedTop = 0f
    private var fixedBottom = 0f

    fun render(s: ClimbState, prefs: Prefs, w: Int, h: Int, night: Boolean): Bitmap {
        fixedTop = 0f; fixedBottom = 0f; columnHeader = 0f; graphHeader = 0f; splitPx = 0
        val bmp = Bitmap.createBitmap(max(w, 1), max(h, 1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        // Stay inside the Karoo's tile (1 px border, 14 px corners): anything painted there covers it
        val inset = TILE_BORDER_PX
        c.clipPath(Path().apply {
            addRoundRect(RectF(inset, inset, w - inset, h - inset), TILE_RADIUS_PX - inset, TILE_RADIUS_PX - inset, Path.Direction.CW)
        })
        val kind = when {
            h >= 250 -> Kind.HALF
            w >= 400 -> Kind.WIDE
            else -> Kind.SMALL
        }
        val designW = if (kind == Kind.SMALL) 238f else 478f
        val k = w / designW
        c.scale(k, k)
        val r = RectF(0f, 0f, designW, h / k)
        val ink = if (night) Color.WHITE else Color.BLACK
        val empty = when {
            !s.hasRoute -> Empty.NO_ROUTE
            s.climb == null && s.total == 0 -> Empty.NO_CLIMBS   // only drawn without route elevation (else routeOnly)
            s.climb == null -> Empty.DONE
            else -> null
        }
        val routeOnlyView = s.route
        drewRouteOnly = empty != null && empty != Empty.NO_ROUTE && routeOnlyView != null
        if (drewRouteOnly && routeOnlyView != null) routeOnly(c, r, s, routeOnlyView, kind)   // round 60: the map stays
        else if (empty != null) emptyField(c, r, s, empty, night, ink, kind == Kind.SMALL)
        else when (kind) {
            // round 49: every tile on the Karoo's climb blue, light and dark alike (one tile navy; two tiles: the text side in the lighter blue)
            Kind.SMALL -> small(c, r, s, prefs, night, Color.WHITE)
            Kind.WIDE -> wide(c, r, s, prefs, night, Color.WHITE)
            Kind.HALF -> half(c, r, s, prefs)
        }
        fixedTopPx = kotlin.math.ceil(fixedTop * k).toInt().coerceIn(0, h)
        fixedBottomPx = kotlin.math.ceil(fixedBottom * k).toInt().coerceIn(0, h - fixedTopPx)
        columnHeaderPx = kotlin.math.ceil(columnHeader * k).toInt().coerceIn(0, h - 1)
        graphHeaderPx = kotlin.math.ceil(graphHeader * k).toInt().coerceIn(0, h - 1)
        // two tiles split into graph and text halves, also for the route-only map (pass 3: it was scaled whole)
        if (kind == Kind.WIDE && (empty == null || drewRouteOnly)) splitPx = kotlin.math.round(WIDE_SPLIT * k).toInt()
        return bmp
    }

    private enum class Kind { SMALL, WIDE, HALF }
    private enum class Empty { NO_ROUTE, NO_CLIMBS, DONE }

    /** The Karoo's header rules for a tile (KarooType): label size, baseline, icon box. */
    /**
     * A header label like the Karoo's: always at full size; too wide for one line, it wraps onto two
     * (baselines 14 px above and 15 px below the one-line baseline, icons stay; measured on native
     * LAP AVG / POWER). Returns the last line's baseline.
     */
    private fun headerLabel(c: Canvas, label0: String, size: Float, base: Float, right: Float, maxW: Float, ink: Int, labelTop: Float): Float {
        val label = label0
        tp.typeface = cond; tp.textSize = size; tp.textScaleX = 1f; tp.letterSpacing = 0f
        if (tp.measureText(label) <= maxW || !label.contains(' ')) {
            text(c, label, cond, size, ink, right, base, maxW); return base
        }
        val words = label.split(' ')
        val splits = (1 until words.size).map { i -> words.take(i).joinToString(" ") to words.drop(i).joinToString(" ") }
        val (l1, l2) = splits.lastOrNull { tp.measureText(it.first) <= maxW } ?: splits.first()   // like the Karoo: fill the first line
        // short full-width rows have no room above: both lines move down until the first clears the top
        val caps = size * 0.72f
        val b1 = max(base - 14, labelTop + 6 + caps)
        text(c, l1, cond, size, ink, right, b1, maxW)
        text(c, l2, cond, size, ink, right, b1 + 29, maxW)
        return b1 + 29
    }

    private fun header(r: RectF, wide: Boolean): Triple<Float, Float, Float> {
        val h = r.height().toInt()
        val caps = KarooType.headerCapsPx(h)
        val size = KarooType.labelPaint(caps, outline = true).textSize
        val base = r.top + KarooType.headerCapsTop(h, wide) + caps
        return Triple(size, base, KarooType.headerIconBoxPx(caps).toFloat())
    }

    // ---- One tile ---------------------------------------------------------------------------

    private fun small(c: Canvas, r: RectF, s: ClimbState, p: Prefs, night: Boolean, ink: Int) {
        val show = when {
            !s.onClimb -> "next"
            p.oneTile == OneTile.DIST -> "dist"
            p.oneTile == OneTile.GAP -> "gap"
            else -> "eta"
        }
        fp.color = CB700; c.drawRect(r, fp)
        val (lsize, base, box) = header(r, false)
        val x0 = r.left + KarooType.HEADER_ICON_LEFT_PX
        val sum = s.summary
        val label = if (sum != null) "SUMMIT" else when (show) { "next" -> NEXT_LABEL; "dist" -> "DIST TO TOP"; "gap" -> "VS PR"; else -> "TIME TO TOP" }
        val labelRight = r.right - KarooType.HEADER_RIGHT_INSET_PX
        // round 54: the mountain only when the label still fits on one line next to it (DIST TO CLIMB on a
        // 168 px tile needs 209 px; with the mountain there are 185): the label never wraps on one tile
        tp.typeface = cond; tp.textSize = lsize; tp.textScaleX = 1f; tp.letterSpacing = 0f
        val labelW = tp.measureText(label)
        val climberW = box * 0.96f * CLIMBER_ASPECT
        val withIcon = labelRight - (x0 + climberW + 4) >= labelW
        val iw = if (withIcon) headerClimber(c, x0, base, box, ink) else -4f
        // the label's room by the shared rule: from 4 px after the icon(s) to the label's right edge
        val left = if (show == "gap") x0 + iw + 6 + glyph(c, KarooType.Glyph.PR, x0 + iw + 6, base, box, ink) + 4 else x0 + iw + 4
        val lastBase = headerLabel(c, label, lsize, base, labelRight, labelRight - left, ink, r.top)
        // the number right under the header (8 px), digits ≈ 0.72 of the size in Relative
        val numSize = min(56f, r.height() * 0.34f)
        val numBase = lastBase + 8 + numSize * 0.72f
        fixedTop = numBase + 6   // header and number keep their size in a shortened row
        val right = r.right - 9
        if (sum != null) text(c, Fmt.clock(sum.timeSec), num, numSize, ink, right, numBase, r.width() - 16)   // round 50: your time up the climb
        else when (show) {
            "next", "dist" -> {
                val m = if (show == "next") s.nextInM else s.toTopM
                if (m == null) text(c, "--", num, numSize, ink, right, numBase, r.width() - 16)
                else {
                    val (v, u) = Fmt.distance(m)
                    val kmW = text(c, u, cond, 24f, ink, right, numBase)
                    text(c, v, num, numSize, ink, right - kmW - UNIT_GAP, numBase, r.width() - 50)
                }
            }
            "gap" -> {
                val g = s.gapSec
                text(c, g?.let(Fmt::gap) ?: "--", num, numSize, g?.let(::gapNavy) ?: ink, right, numBase, r.width() - 16)
            }
            else -> text(c, s.etaSec?.let(Fmt::clock) ?: "--", num, numSize, ink, right, numBase, r.width() - 16)
        }
        val g = RectF(r.left, numBase + 10, r.right, r.bottom)   // to the tile's edges, like the half screen (round 38)
        // no navy box on one tile (round 42): marks in the text colour, the ring's hole in the tile's
        bareGraph = true; markInk = ink; holeInk = CB700
        graphBox(c, g, 0f) { b ->
            climbGraph(c, b, s, ring = 6f, hatch = 1.6f, head = 0.15f, seg = 400.0)
        }
        bareGraph = false; markInk = Color.WHITE; holeInk = CB700
    }

    // ---- Two tiles --------------------------------------------------------------------------

    /**
     * Two tiles (rounds 12, 26, 27): the graph full height on the left with the climb name on it;
     * on the right like a Karoo field: label, big number under it, and a small line on the Karoo's
     * value baseline (14 px above the bottom).
     */
    private fun wide(c: Canvas, r: RectF, s: ClimbState, p: Prefs, night: Boolean, ink: Int) {
        // round 49: the whole tile navy (the text side too); labels white, the small line in the light blue
        val muted = CB400
        val sub = SUB_BLUE
        val show = when {
            !s.onClimb -> "next"
            p.twoTiles == TwoTiles.DIST -> "dist"
            p.twoTiles == TwoTiles.GAP -> "gap"
            p.twoTiles == TwoTiles.BOTH -> if (s.prSec != null) "both" else "eta"   // no PR: no "-- VS PR" line (round 51b)
            else -> "eta"
        }
        val graphW = WIDE_SPLIT - 6
        val gb = RectF(r.left, r.top, r.left + 6 + graphW, r.bottom)   // to the tile's edges (round 38)
        fp.color = CB700; c.drawRect(r, fp)
        graphBox(c, gb, 0f) { b ->
            // round 49: the graph ends at the climb's summit, marked by a white line with a checkered flag
            climbGraph(c, b, s, ring = 7f, hatch = 1.8f, head = 0.42f, seg = 200.0)
            // round 51d: the name in the label's type and size, on the label's baseline; the PR rosette only with a PR
            val (nameSize, nameBase, _) = header(r, true)
            nameFor(s)?.let { nameOnGraph(c, b, it, (show == "gap" || show == "both") && s.prSec != null, nameSize, nameBase) }
            graphHeader = nameBase + 6   // the name keeps its size in a shortened row
        }
        val right = r.right - 9
        // the text column's full width (round 53: DIST TO CLIMB on one line, 209 px at the Karoo's label size)
        val maxW = r.right - KarooType.HEADER_RIGHT_INSET_PX - (WIDE_SPLIT + 6)
        val (lsize, labelBase, _) = header(r, true)
        val sum = s.summary
        val label = if (sum != null) "SUMMIT" else when (show) { "next" -> NEXT_LABEL; "dist" -> "DIST TO TOP"; "gap" -> "TIME VS PR"; else -> "TIME TO TOP" }
        val lastBase = headerLabel(c, label, lsize, labelBase, r.right - KarooType.HEADER_RIGHT_INSET_PX, maxW, ink, r.top)
        columnHeader = lastBase + 6   // the text column's header band (shortened rows)
        val subBase = r.bottom - VALUE_BOTTOM
        // No bottom band here: header, number and the small line fill the whole height, so in a shorter
        // row something must give; the small line does (the number and header don't).
        // round 50, the summit: your time, under it the gap to the PR, else your average power, else VAM
        if (sum != null) {
            val g = s.gapSec
            if (g != null) {
                val w = text(c, "VS PR", cond, 24f, muted, right, subBase)
                text(c, Fmt.gap(g), med, 26f, gapNavy(g), right - w - 8, subBase)
            } else text(c, sum.avgPowerW?.let { "$it\u2009W avg" } ?: "${sum.vamMh}\u2009m/h VAM", med, SUB_PX, sub, right, subBase, maxW)
            val numBase = subBase - 32
            text(c, Fmt.clock(sum.timeSec), num, min(110f, (numBase - (lastBase + 10)) / 0.72f), ink, right, numBase, maxW)
            return
        }
        when (show) {
            "both" -> {
                val g = s.gapSec
                val w = text(c, "VS PR", cond, 24f, muted, right, subBase)
                text(c, g?.let(Fmt::gap) ?: "--", med, 26f, g?.let(::gapNavy) ?: muted, right - w - 8, subBase)
            }
            "eta" -> { val t = s.toTopM; val e = s.elevToTopM
                if (t != null && e != null) text(c, "${Fmt.length(t)} · +\u2009${Fmt.metres(e)}\u2009m", med, SUB_PX, sub, right, subBase, maxW) }
            "dist" -> s.elevToTopM?.let { text(c, "${Fmt.metres(it)}\u2009m to climb", med, SUB_PX, sub, right, subBase, maxW) }
            "gap" -> s.prSec?.let { text(c, "PR ${Fmt.clock(it)}", med, SUB_PX, sub, right, subBase, maxW) }
            "next" -> s.climb?.let { text(c, "${Fmt.length(it.lengthM)} · ${String.format(java.util.Locale.US, "%.1f", it.shownGrade)}%", med, SUB_PX, sub, right, subBase, maxW) }
        }
        val numBase = subBase - 32
        fixedTop = numBase + 6   // label and number keep their size in a shortened row
        val size = min(110f, (numBase - (lastBase + 10)) / 0.72f)
        when (show) {
            "next", "dist" -> {
                val m = if (show == "next") s.nextInM else s.toTopM
                if (m == null) text(c, "--", num, size, ink, right, numBase, maxW)
                else {
                    val (v, u) = Fmt.distance(m)
                    val kmW = text(c, u, cond, 26f, ink, right, numBase)
                    text(c, v, num, size, ink, right - kmW - UNIT_GAP, numBase, maxW - kmW)
                }
            }
            "gap" -> { val g = s.gapSec
                text(c, g?.let(Fmt::gap) ?: "--", num, size, g?.let(::gapNavy) ?: ink, right, numBase, maxW) }
            else -> text(c, s.etaSec?.let(Fmt::clock) ?: "--", num, size, ink, right, numBase, maxW)
        }
    }

    // ---- Half screen ------------------------------------------------------------------------

    private fun half(c: Canvas, r: RectF, s: ClimbState, p: Prefs) {
        val barH = 56f
        // the blue bar and the climb's name under it keep their size in a shortened row (round 51)
        fixedTop = r.top + barH + if (p.halfName) HALF_NAME_BAND else 0f
        val climb = s.climb!!
        fp.color = CB600; c.drawRect(r.left, r.top, r.right, r.top + barH, fp)
        val bb = r.top + barH * 0.74f
        val sum = s.summary
        if (sum != null) {
            // round 50: the summit minute: SUMMIT REACHED with the summit flag, no numbers to the top
            val fs = barH * 0.56f
            val fw = checkerFlag(c, r.left + 12, bb, fs * 0.72f)
            text(c, SUMMIT, cond, fs, Color.WHITE, r.left + 12 + fw + 10, bb, align = Paint.Align.LEFT)
        } else if (s.onClimb) {
            // TO TOP, then the Karoo's distance and elevation glyphs in its field-icon green, the
            // numbers and their units: [dist] 2.6 KM  [elev] +196 M (rounds 22, 24)
            text(c, "TO TOP", cond, barH * 0.56f, Color.WHITE, r.left + 12, bb, align = Paint.Align.LEFT)
            var x = r.right - 12
            val icon = barH * 0.46f
            val iconBase = r.top + (barH + icon) / 2
            x -= barUnit(c, "M", x, bb, barH) + 5
            x -= barNum(c, "+ " + Fmt.metres(s.elevToTopM ?: 0.0), x, bb, barH) + 8
            x -= glyph(c, KarooType.Glyph.ELEVATION, x - icon, iconBase, icon, KarooType.GLYPH_GREEN_NIGHT) + 18
            val (tv, tu) = Fmt.distance(s.toTopM ?: 0.0)
            x -= barUnit(c, tu.uppercase(), x, bb, barH) + 5
            x -= barNum(c, tv, x, bb, barH) + 8
            glyph(c, KarooType.Glyph.DISTANCE, x - icon, iconBase, icon, KarooType.GLYPH_GREEN_NIGHT)
        } else {
            text(c, NEXT_LABEL, cond, BAR_LABEL_PX, Color.WHITE, r.left + 12, bb, align = Paint.Align.LEFT)
            val next = s.nextInM?.let(Fmt::distance)
            val uw = barUnit(c, next?.second?.uppercase() ?: "KM", r.right - 12, bb, barH)
            barNum(c, next?.first ?: "--", r.right - 12 - uw - 5, bb, barH)
        }
        // Graph: always the whole climb (like the Karoo). Biggest size only: the 500 M bracket and
        // the next 500 m as five sliding boxes under it.
        val rowH = 92f
        // the grade boxes (when they show) and the PR row keep their height
        fixedBottom = rowH + if (r.height() >= BIG_H && s.doneM != null && s.summary == null) 52f else 0f
        val g = RectF(r.left, r.top + barH, r.right, r.bottom - rowH)
        val big = r.height() >= BIG_H
        c.save(); c.clipRect(g)
        val done = s.doneM
        // the next 500 m boxes under the graph on a climb (biggest size, not at the summit)
        val stripH = if (big && done != null && s.summary == null) 52f else 0f
        climbGraph(c, g, s, ring = 10f, hatch = 2.2f, head = if (s.route != null) 0.32f else if (big) 0.45f else 0.32f,
            seg = if (done != null) 100.0 else 200.0, strip = stripH, marker = stripH > 0)
        if (p.halfName) nameFor(s)?.let { nameOnGraph(c, g, it, (p.halfPr || p.halfGap) && s.prSec != null, BAR_LABEL_PX) }   // round 51d: as the bar's STARTS IN
        c.restore()
        // PR / TIME VS PR / TIME TO TOP (the Karoo's names), no dividers, values on its baseline
        val y = g.bottom
        fp.color = CB700; c.drawRect(r.left, y, r.right, r.bottom, fp)
        // null: an empty column (no PR), so the others keep their places (round 51b)
        val cols = ArrayList<Triple<String, String, Int>?>()
        if (sum != null) {
            // round 50: your time, then the gap to the PR (else average power, else nothing), VAM
            cols += Triple("TIME", Fmt.clock(sum.timeSec), Color.WHITE)
            val g = s.gapSec
            if (g != null) cols += Triple("VS PR", Fmt.gap(g), gapNavy(g))
            else sum.avgPowerW?.let { cols += Triple("AVG POWER", "$it\u2009W", Color.WHITE) }
            cols += Triple("VAM", "${sum.vamMh}", Color.WHITE)
        } else if (!s.onClimb) {
            // round 51b: no PR (not a Strava segment, or none yet): no PR column
            cols += s.prSec?.let { Triple("PR", Fmt.clock(it), Color.WHITE) }
            cols += Triple("LENGTH", Fmt.length(climb.lengthM), Color.WHITE)
            cols += Triple("GAIN", "${Fmt.metres(climb.shownGainM)}\u2009m", Color.WHITE)   // round 53: not a third "climb"
        } else {
            if (p.halfPr) cols += s.prSec?.let { Triple("PR", Fmt.clock(it), Color.WHITE) }
            if (p.halfGap) cols += s.prSec?.let { Triple("TIME VS PR", s.gapSec?.let(Fmt::gap) ?: "--", s.gapSec?.let(::gapNavy) ?: Color.WHITE) }
            if (p.halfEta) cols += Triple("TIME TO TOP", s.etaSec?.let(Fmt::clock) ?: "--", Color.WHITE)
        }
        if (cols.all { it == null }) return
        val cw = r.width() / cols.size
        val size = when (cols.size) { 3 -> 52f; 2 -> 60f; else -> 66f }
        cols.forEachIndexed { i, cell ->
            val (l, v, col) = cell ?: return@forEachIndexed
            val right = r.left + cw * (i + 1) - 10
            text(c, l, cond, 23f, CB400, right, y + 27)
            text(c, v, num, size, col, right, r.bottom - VALUE_BOTTOM, cw - 20)
        }
    }

    // ---- Empty states (rounds 28–31): no route, all climbs done -------------------------------

    /**
     * Like the Karoo's own empty field: its header (the ClimbER mountain + MOUNTAINEER), the words in
     * the label font at the shared empty-state size (KarooType.emptyPaint), and a grey hint under them,
     * the two centred together in the space under the header (round 31).
     */
    private fun emptyField(c: Canvas, r: RectF, s: ClimbState, e: Empty, night: Boolean, ink: Int, small: Boolean) {
        val (lsize, base, box) = header(r, !small)
        val x0 = r.left + KarooType.HEADER_ICON_LEFT_PX
        val iw = headerClimber(c, x0, base, box, ink)
        text(c, "MOUNTAINEER", cond, lsize, ink, r.right - KarooType.HEADER_RIGHT_INSET_PX, base, r.right - x0 - iw - 16)
        val t = s.total
        val words = when (e) {
            Empty.NO_ROUTE -> "No Route"
            Empty.NO_CLIMBS -> "No Climbs"
            Empty.DONE -> if (t == null) "All Climbs Done" else if (small) "$t/$t Climbs Done" else "All $t Climbs Done"
        }
        // round 59 (feedback: "don't have the subtitle unless needed"): only No Route says what to do
        val hint = if (e == Empty.NO_ROUTE) (if (small) "Load a route" else "Load a route with climbs") else null
        val wp = KarooType.emptyPaint(words, r.width().toInt())
        val hs = SUB_PX   // never below the smallest readable size (was 18 / 20 px)
        val capW = KarooType.EMPTY_CAPS_PX.toFloat(); val capH = hs * 0.7f; val gap = 12f
        val block = if (hint != null) capW + gap + capH else capW
        val top = base + (r.bottom - base - block) / 2
        text(c, words, cond, wp.textSize, ink, r.centerX(), top + capW, align = Paint.Align.CENTER)
        if (hint != null) text(c, hint, med, hs, if (night) SUB_NIGHT else SUB_DAY, r.centerX(), top + capW + gap + capH, r.width() - 16, align = Paint.Align.CENTER)
    }

    /**
     * A route loaded but no climb to show (round 60, feedback: "only show the empty state if there is no route
     * loaded"; "if I am done climbing, show the full elevation map still"): all climbs done, none on the route,
     * or off route before the Karoo has listed any. The whole route (done climbs faded, you on it) and
     * DIST TO FINISH; two tiles' small line and the half screen's bottom row say how many climbs are done.
     */
    private fun routeOnly(c: Canvas, r: RectF, s: ClimbState, v: RouteView, kind: Kind) {
        fp.color = CB700; c.drawRect(r, fp)
        val t = s.total ?: 0
        val finish = s.toFinishM?.let(Fmt::distance)
        fun bigDistance(size: Float, unitSize: Float, right: Float, base: Float, maxW: Float) {
            if (finish == null) { text(c, "--", num, size, Color.WHITE, right, base, maxW); return }
            val uw = text(c, finish.second, cond, unitSize, Color.WHITE, right, base)
            text(c, finish.first, num, size, Color.WHITE, right - uw - UNIT_GAP, base, maxW - uw)
        }
        when (kind) {
            Kind.SMALL -> {
                val (lsize, base, _) = header(r, false)
                val lastBase = headerLabel(c, FINISH_LABEL, lsize, base, r.right - KarooType.HEADER_RIGHT_INSET_PX, r.right - KarooType.HEADER_RIGHT_INSET_PX - (r.left + 8), Color.WHITE, r.top)   // from 8 px, so DIST TO FINISH (221 px) fits
                val numSize = min(56f, r.height() * 0.34f)
                val numBase = lastBase + 8 + numSize * 0.72f
                bigDistance(numSize, 24f, r.right - 9, numBase, r.width() - 16)
                fixedTop = numBase + 6
                bareGraph = true; markInk = Color.WHITE; holeInk = CB700
                graphBox(c, RectF(r.left, numBase + 10, r.right, r.bottom), 0f) { b -> routeGraph(c, b, v, ring = 6f, hatch = 1.6f, head = 0.15f, numbers = false) }
                bareGraph = false
            }
            Kind.WIDE -> {
                val gb = RectF(r.left, r.top, r.left + WIDE_SPLIT, r.bottom)
                val (lsize, labelBase, _) = header(r, true)
                graphBox(c, gb, 0f) { b -> routeGraph(c, b, v, ring = 7f, hatch = 1.8f, head = 0.42f, numbers = false) }
                // the graph side's top band, as with a name line (pass 3: without one the graph didn't stretch right)
                graphHeader = labelBase + 6
                val maxW = r.right - KarooType.HEADER_RIGHT_INSET_PX - (WIDE_SPLIT + 6)
                val lastBase = headerLabel(c, FINISH_LABEL, lsize, labelBase, r.right - KarooType.HEADER_RIGHT_INSET_PX, maxW, Color.WHITE, r.top)
                columnHeader = lastBase + 6
                val subBase = r.bottom - VALUE_BOTTOM
                if (t > 0) text(c, "All $t climbs done", med, SUB_PX, SUB_BLUE, r.right - 9, subBase, maxW)
                val numBase = subBase - 32
                bigDistance(min(110f, (numBase - (lastBase + 10)) / 0.72f), 26f, r.right - 9, numBase, maxW)
            }
            Kind.HALF -> {
                val barH = 56f
                fixedTop = r.top + barH
                fp.color = CB600; c.drawRect(r.left, r.top, r.right, r.top + barH, fp)
                val bb = r.top + barH * 0.74f
                text(c, FINISH_LABEL, cond, BAR_LABEL_PX, Color.WHITE, r.left + 12, bb, align = Paint.Align.LEFT)
                val uw = barUnit(c, finish?.second?.uppercase() ?: "KM", r.right - 12, bb, barH)
                barNum(c, finish?.first ?: "--", r.right - 12 - uw - 5, bb, barH)
                val rowH = if (t > 0) 92f else 0f
                fixedBottom = rowH
                val g = RectF(r.left, r.top + barH, r.right, r.bottom - rowH)
                routeGraph(c, g, v, ring = 10f, hatch = 2.2f, head = 0.32f, numbers = false)
                if (t > 0) {
                    val right = r.right - 10
                    text(c, "CLIMBS DONE", cond, 23f, CB400, right, g.bottom + 27)
                    text(c, "$t/$t", num, 66f, Color.WHITE, right, r.bottom - VALUE_BOTTOM, r.width() - 20)
                }
            }
        }
    }

    /** The blue bar's numbers and units: in the label's type, like TO TOP / STARTS IN (round 40 C). */
    private fun barNum(c: Canvas, v: String, x: Float, bb: Float, barH: Float): Float = text(c, v, cond, barH * 0.56f, Color.WHITE, x, bb)

    private fun barUnit(c: Canvas, u: String, x: Float, bb: Float, barH: Float): Float = text(c, u, cond, barH * 0.56f, Color.WHITE, x, bb)

    // ---- The Karoo's climb graph ------------------------------------------------------------

    private inline fun graphBox(c: Canvas, box: RectF, radius: Float, draw: (RectF) -> Unit) {
        c.save()
        c.clipPath(Path().apply { addRoundRect(box, radius, radius, Path.Direction.CW) })
        draw(box)
        c.restore()
    }

    /**
     * The graph at every size (round 52): the road to the climb shown ([ClimbState.route]: from you, or
     * the close-up) or a long climb on its own, ending at its summit with the checkered flag; [strip]
     * px at the bottom for the next 500 m boxes (on a climb), [marker] the 500 M bracket.
     */
    private fun climbGraph(c: Canvas, b: RectF, s: ClimbState, ring: Float, hatch: Float, head: Float, seg: Double, strip: Float = 0f, marker: Boolean = false) {
        // room after the summit: the flag, and (round 58) a bit of the road after the top in the road blue
        val room = max(summitRoom(b.height() - strip), b.width() * AFTER_TOP_SHARE)
        val v = s.route
        val climb = s.climb ?: return
        if (v != null) {
            val span = max(1.0, v.topM - v.fromM)
            routeGraph(c, RectF(b.left, b.top, b.right, b.bottom - strip), v.copy(toM = v.topM + span * room / (b.width() - room)),
                ring = ring, hatch = hatch, head = head, numbers = false, summitM = v.topM)
            val done = s.doneM
            if (strip > 0 && done != null) drawStrip(c, b.left, b.bottom - strip, b.right, b.bottom, climb, done)
        } else {
            graph(c, b, climb, if (s.onClimb) s.doneM else null, to = climb.lengthM * b.width() / (b.width() - room),
                seg = seg, strip = strip, marker = marker, ring = ring, hatch = hatch, head = head, summit = true, after = s.afterTop)
        }
    }

    /**
     * The route graph (round 48): the stretch of the route in [v], drawn like the climb graph. The
     * road in the muted blue, the Karoo's climbs in their grade colours by 100 m (each at least
     * [MIN_CLIMB_PX] wide, so a short one still shows on a long route), the next climb's start as a
     * flag on a pole (just before the start, so it never hides a short climb), you as the ring. On
     * the whole route the ridden road is a quiet tint and ridden climbs fade; in the close-up the
     * ridden part is hatched, as on a climb. [numbers]: each climb's number over it (half screen).
     */
    private fun routeGraph(c: Canvas, b: RectF, v: RouteView, ring: Float, hatch: Float, head: Float, numbers: Boolean, summitM: Double? = null) {
        val gh = b.height(); val bottom = b.bottom
        if (!bareGraph) { fp.color = CB700; c.drawRect(b.left, b.top, b.right, bottom, fp) }
        val span = max(1.0, v.toM - v.fromM)
        fun x(d: Double) = (b.left + (d - v.fromM) / span * b.width()).toFloat()
        fun e(d: Double) = v.profile.elevAt(d - v.fromM)
        val step = span / b.width() * 2
        var lo = Double.MAX_VALUE; var hi = -Double.MAX_VALUE
        var d = v.fromM
        val end = v.toM   // round 58: the road goes on past the summit (in the road blue), under the flag
        while (d <= end + 0.1) { val el = e(d); lo = min(lo, el); hi = max(hi, el); d += step }
        // the lowest road sits high enough for your dot to clear the tile's rounded corner; a flat road stays flat
        val floor = max(0.1, (ring + 7.0) / gh)
        val flagH = if (v.flagM != null || summitM != null) (gh * 0.24f).coerceIn(9f, 16f) + 12f else 0f
        val top = 1 - max(head.toDouble(), ((flagH + 4) / gh).toDouble())
        fun y(el: Double) = (bottom - (floor + (top - floor) * (el - lo) / max(MIN_RISE_M, hi - lo)) * gh).toFloat()
        val profile = Path().apply {
            moveTo(b.left, bottom)
            var k = v.fromM
            while (k < end) { lineTo(x(k), y(e(k))); k += step }
            lineTo(x(end), y(e(end))); lineTo(x(end), bottom); close()
        }
        val you = v.youM
        val rodeTo = you?.let { min(b.right, max(b.left, x(it))) } ?: b.left
        c.save(); c.clipPath(profile)
        fp.color = ROAD_MUTED; c.drawRect(b.left, b.top, b.right, bottom, fp)
        if (v.whole && rodeTo > b.left) { fp.color = if (bareGraph && holeInk != CB700) (if (holeInk == Color.BLACK) RIDDEN_NIGHT else RIDDEN_DAY) else RIDDEN_NAVY; c.drawRect(b.left, b.top, rodeTo, bottom, fp) }
        // off route (round 55): the way back to the route in red; no route climbs drawn under it
        val offFrom = v.offFromM; val offTo = v.offToM
        if (offFrom != null && offTo != null) { fp.color = OFF_ROUTE; c.drawRect(max(b.left, x(offFrom)), b.top, x(offTo), bottom, fp) }
        for ((start, cl) in v.climbs) {
            if (start > v.toM || start + cl.lengthM < v.fromM) continue
            if (offTo != null && start + cl.lengthM <= offTo) continue
            val faded = v.whole && you != null
            val band = bandM(span, b.width())
            var k0 = 0.0
            while (k0 < cl.lengthM - 0.5) {
                // a last bit under half a block joins the block before it (its own grade is noise: a red sliver)
                val k1 = if (cl.lengthM - (k0 + band) < band / 2) cl.lengthM else k0 + band
                fp.color = gradeColor(cl.grade(k0, k1))
                if (faded && start + k1 <= you!!) fp.alpha = RIDDEN_ALPHA
                c.drawRect(x(start + k0), b.top, kotlin.math.ceil(x(start + k1).toDouble()).toFloat(), bottom, fp)
                k0 = k1
            }
            val x0 = x(start); val x1 = x(start + cl.lengthM)
            if (x1 - x0 < MIN_CLIMB_PX) {
                val mid = (x0 + x1) / 2
                fp.color = gradeColor(cl.avgGrade)
                if (faded && start + cl.lengthM <= you!!) fp.alpha = RIDDEN_ALPHA
                c.drawRect(mid - MIN_CLIMB_PX / 2, b.top, mid + MIN_CLIMB_PX / 2, bottom, fp)
            }
        }
        if (!v.whole && rodeTo > b.left) {
            c.clipRect(b.left, b.top, rodeTo, bottom)
            sp.color = CB700; sp.strokeWidth = hatch * 1.3f; sp.strokeCap = Paint.Cap.BUTT
            var hx = b.left - gh
            while (hx < rodeTo + gh) { c.drawLine(hx, bottom, hx + gh, b.top, sp); hx += hatch * 2.3f }
            sp.strokeCap = Paint.Cap.ROUND
        }
        c.restore()
        // the climbs' numbers over them (half screen, whole route); done ones in the muted blue
        if (numbers) v.climbs.forEachIndexed { i, (start, cl) ->
            val mid = start + cl.lengthM / 2
            if (mid < v.fromM || mid > v.toM) return@forEachIndexed
            val done = you != null && start + cl.lengthM <= you
            text(c, "${i + 1}", cond, NUMBER_PX, if (done) CB400_MUTED else Color.WHITE, x(mid), max(b.top + NUMBER_PX, y(e(mid)) - 8f), align = Paint.Align.CENTER)
        }
        // the next climb's start: a solid pole with a ▶ flag, standing on the road just before the start (rounds 38, 46, 48)
        v.flagM?.takeIf { it in v.fromM..v.toM }?.let { start ->
            val lw = max(3f, ring * 0.44f)
            val th = (gh * 0.24f).coerceIn(9f, 16f); val tw = th * 0.9f
            val px = x(start) - lw / 2 - 1
            val road = y(e(start))
            var peak = Float.MAX_VALUE
            var fx = px
            while (fx <= px + lw / 2 + tw + 3) { peak = min(peak, y(e(v.fromM + (fx - b.left) / b.width() * span))); fx += 1f }
            val tTop = max(b.top + 4f, min(road - th - 10f, peak - 4f - th))
            fp.color = markInk; c.drawRect(px - lw / 2, tTop, px + lw / 2, road, fp)
            c.drawPath(Path().apply { moveTo(px + lw / 2, tTop); lineTo(px + lw / 2 + tw, tTop + th / 2); lineTo(px + lw / 2, tTop + th); close() }, fp)
        }
        summitM?.let { fadeAfterTop(c, x(it), b.right, b.top, bottom); summitMark(c, b, x(it), y(e(it)), ring) }
        // you
        if (you != null && you in v.fromM..v.toM) {
            // never into the tile's rounded corner (as before round 48)
            val px = min(max(x(you), b.left + ring + 7), b.right - ring - 7); val py = y(e(you))
            fp.color = markInk
            c.drawRect(px - ring * 0.22f, py, px + ring * 0.22f, bottom, fp)
            c.drawCircle(px, py, ring, fp)
            fp.color = holeInk; c.drawCircle(px, py, ring * 0.5f, fp)
        }
    }

    /**
     * The graph in [b] for [climb] between [from] and [to] metres, [seg]-metre columns, [nowM]
     * where you are (null: not on it yet), [strip] the next-500 m boxes' height (0: none),
     * [marker] the 500 M bracket (only with the strip).
     *
     * Drawn like the Karoo's own climb graph (ride app ElevationAndGradeView): one smooth path
     * through the elevation, clipped to; each segment a full-height rectangle in its grade colour
     * (right edge rounded up, so no gaps), so the tops follow the road; the climbed part gets
     * navy stripes on top.
     */
    private fun graph(
        c: Canvas, b: RectF, climb: ClimbProfile, nowM: Double?,
        from: Double = 0.0, to: Double = climb.lengthM, seg: Double, strip: Float = 0f, marker: Boolean = false,
        ring: Float, hatch: Float, head: Float, summit: Boolean = false, after: ClimbProfile? = null,
    ) {
        val gh = b.height() - strip
        val bottom = b.top + gh
        val end = min(to, climb.lengthM)
        if (!bareGraph) { fp.color = CB700; c.drawRect(b.left, b.top, b.right, bottom, fp) }
        val span = max(1.0, to - from)
        fun x(m: Double) = (b.left + (m - from) / span * b.width()).toFloat()
        var lo = Double.MAX_VALUE; var hi = -Double.MAX_VALUE
        var m = from
        while (m <= end + 0.1) { val e = climb.elevAt(m); lo = min(lo, e); hi = max(hi, e); m += 25.0 }
        // the road after the top (round 58), from the route: in the road blue up to the graph's right edge
        val afterEnd = if (after != null && to > climb.lengthM) to else null
        if (afterEnd != null) { var a = climb.lengthM; while (a <= afterEnd) { val e = after!!.elevAt(a - climb.lengthM); lo = min(lo, e); hi = max(hi, e); a += 25.0 } }
        // with the summit mark: room above the top for its flag
        val head2 = if (summit) max(head, ((summitFlagPx(gh) + 16) / gh)) else head
        fun y(e: Double) = (bottom - (0.1 + (1 - head2 - 0.1) * (e - lo) / max(1.0, hi - lo)) * gh).toFloat()
        val now = nowM
        val profile = Path().apply {
            moveTo(x(from), bottom)
            var k = from
            while (k < end) { lineTo(x(k), y(climb.elevAt(k))); k += 25.0 }
            lineTo(x(end), y(climb.elevAt(end))); lineTo(x(end), bottom); close()
        }
        if (afterEnd != null) {
            fp.color = ROAD_MUTED
            c.drawPath(Path().apply {
                moveTo(x(end), bottom); lineTo(x(end), y(climb.elevAt(end)))
                var k = end
                while (k < afterEnd) { lineTo(x(k), y(after!!.elevAt(k - climb.lengthM))); k += 25.0 }
                lineTo(x(afterEnd), y(after!!.elevAt(afterEnd - climb.lengthM))); lineTo(x(afterEnd), bottom); close()
            }, fp)
        }
        c.save(); c.clipPath(profile)
        val band = max(seg, bandM(span, b.width()))
        var k0 = floor(from / band) * band
        while (k0 < end - 0.5) {
            val k1 = if (climb.lengthM - (k0 + band) < band / 2) climb.lengthM else min(k0 + band, climb.lengthM)
            fp.color = gradeColor(climb.grade(k0, k1))
            c.drawRect(x(k0), b.top, kotlin.math.ceil(x(k1).toDouble()).toFloat(), bottom, fp)
            k0 = k1
        }
        if (now != null && x(now) > b.left) {
            c.clipRect(b.left, b.top, x(now), bottom)
            sp.color = CB700; sp.strokeWidth = hatch * 1.3f; sp.strokeCap = Paint.Cap.BUTT
            var hx = b.left - gh
            while (hx < x(now) + gh) { c.drawLine(hx, bottom, hx + gh, b.top, sp); hx += hatch * 2.3f }
            sp.strokeCap = Paint.Cap.ROUND
        }
        c.restore()
        // The bracket (rounds 18–20): as thick as the white line under you, its left end just above
        // the ring, its right end just above the road; says what's left under 500 m, gone on top
        val left = climb.lengthM - (now ?: climb.lengthM)
        if (now != null && marker && left > 20) {
            val n1 = min(now + 500, climb.lengthM)
            val x0 = x(now); val x1 = x(n1)
            var minY = Float.MAX_VALUE
            var k = now
            while (k <= n1) { minY = min(minY, y(climb.elevAt(k))); k += 25.0 }
            val py = y(climb.elevAt(now))
            val lw = ring * 0.44f
            // the label must stay inside the graph: the bracket never rises above room for it
            val yb = max(min(minY, py - ring) - 18, b.top + MARK_LABEL_PX * 0.75f + lw + 10)
            sp.color = Color.WHITE; sp.strokeWidth = lw; sp.strokeCap = Paint.Cap.BUTT; sp.strokeJoin = Paint.Join.MITER
            c.drawPath(Path().apply {
                moveTo(x0, py - ring - 3); lineTo(x0, yb); lineTo(x1, yb); lineTo(x1, max(yb + 6, y(climb.elevAt(n1)) - 3))
            }, sp)
            sp.strokeCap = Paint.Cap.ROUND; sp.strokeJoin = Paint.Join.ROUND
            val label = "${min(500, (left / 10).roundToInt() * 10)} M"
            tp.typeface = cond; tp.textSize = MARK_LABEL_PX; tp.textScaleX = 1f; tp.letterSpacing = 0f
            val tw = tp.measureText(label)
            val lx = min(if (tw < x1 - x0 + 30) (x0 + x1) / 2 - tw / 2 else x0 - lw / 2, b.right - 8 - tw)
            text(c, label, cond, MARK_LABEL_PX, Color.WHITE, lx, yb - lw / 2 - 6, align = Paint.Align.LEFT)
        }
        if (summit) {
            if (afterEnd != null) fadeAfterTop(c, x(climb.lengthM), b.right, b.top, bottom)
            summitMark(c, RectF(b.left, b.top, b.right, bottom), x(climb.lengthM), y(climb.elevAt(climb.lengthM)), ring)
        }
        if (now != null && ring > 0) {
            // never into the tile's right edge or corner (the summit, round 50)
            val px = min(x(now), b.right - ring - 7); val py = y(climb.elevAt(now))
            fp.color = markInk
            c.drawRect(px - ring * 0.22f, py, px + ring * 0.22f, bottom, fp)
            c.drawCircle(px, py, ring, fp)
            fp.color = holeInk; c.drawCircle(px, py, ring * 0.5f, fp)
        }
        if (strip > 0 && now != null) drawStrip(c, b.left, bottom, b.right, b.bottom, climb, now)
    }

    /**
     * The strip (rounds 13, 19): the next 500 m in five big 100 m boxes on navy, sliding left as you
     * climb; right behind the last box SUMMIT REACHED rides in and rests in the middle on top.
     */
    private fun drawStrip(c: Canvas, left: Float, sy: Float, right: Float, bottom: Float, climb: ClimbProfile, now: Double) {
        val b = RectF(left, sy, right, bottom); val strip = bottom - sy
        val bw5 = b.width() / 5
        run {
            c.save(); c.clipRect(b.left, sy, b.right, b.bottom)
            fp.color = CB700; c.drawRect(b.left, sy, b.right, b.bottom, fp)
            var s0 = floor(now / 100) * 100
            while (s0 < now + 500 - 0.5 && s0 < climb.lengthM - 0.5) {
                // a last bit under 50 m joins the box before it (its own grade is noise: a red sliver)
                val s1 = if (climb.lengthM - (s0 + 100) < 50) climb.lengthM else s0 + 100
                val x0 = (b.left + (s0 - now) / 500 * b.width()).toFloat()
                val x1 = x0 + bw5 * ((s1 - s0) / 100).toFloat()
                val g = climb.grade(s0, s1)
                fp.color = gradeColor(g); c.drawRect(x0 + 1, sy + 2, x1 - 1, b.bottom, fp)
                val vis = min(x1, b.right) - max(x0, b.left)
                if (vis >= bw5 * 0.6f) text(c, "%.1f".format(g), num, strip * 0.8f, gradeInk(g), max(x0, b.left) + vis / 2, sy + strip * 0.82f, vis - 10, align = Paint.Align.CENTER)
                s0 = s1
            }
            val xEnd = (b.left + (climb.lengthM - now) / 500 * b.width()).toFloat()
            if (xEnd < b.right) {
                val fs = strip * 0.5f
                tp.typeface = cond; tp.textSize = fs; tp.textScaleX = 1f; tp.letterSpacing = 0f
                val iw = fs * 1.1f; val w = iw + 8 + tp.measureText(SUMMIT)
                val cx = max(xEnd + 16 + w / 2, b.centerX())
                val lx = cx - w / 2; val base = sy + strip * 0.68f
                checkerFlag(c, lx, base, fs * 0.72f)
                text(c, SUMMIT, cond, fs, Color.WHITE, lx + iw + 8, base, align = Paint.Align.LEFT)
            }
            c.restore()
        }
    }

    /**
     * The gradient blocks' length for [span] metres over [widthPx] (round 57: blocks, not stripes): at
     * least [MIN_BAND_PX] wide, rounded up to 100, 200, 500 m or whole km; 100 m in a close-up.
     */
    private fun bandM(span: Double, widthPx: Float): Double {
        val raw = span / max(1f, widthPx) * MIN_BAND_PX
        return listOf(100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0).firstOrNull { it >= raw } ?: 5000.0
    }

    /** The road after the top fades into the navy towards [right] (round 58), so it never ends in a cliff. */
    private fun fadeAfterTop(c: Canvas, from: Float, right: Float, top: Float, bottom: Float) {
        if (right - from < 2) return
        fp.shader = android.graphics.LinearGradient(from, 0f, right, 0f, CB700 and 0x00FFFFFF, CB700, android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(from, top, right, bottom, fp)
        fp.shader = null
    }

    private fun summitFlagPx(gh: Float) = (gh * 0.24f).coerceIn(9f, 16f)

    /** Room right of the summit for the checkered flag (px). */
    private fun summitRoom(gh: Float) = summitFlagPx(gh) / 2 * 3 + 8f

    /**
     * The summit (round 49): a white line from the top of the flag down to the graph's bottom, and a
     * checkered flag (3 × 2 squares, white and black) flying right of it.
     */
    private fun summitMark(c: Canvas, b: RectF, x: Float, road: Float, ring: Float) {
        val lw = max(3f, ring * 0.44f)
        val th = summitFlagPx(b.height()); val q = th / 2
        val tTop = max(b.top + 4f, road - th - 10f)
        fp.color = Color.WHITE; c.drawRect(x - lw / 2, tTop, x + lw / 2, b.bottom, fp)
        val fx = x + lw / 2
        for (row in 0..1) for (col in 0..2) {
            fp.color = if ((row + col) % 2 == 0) Color.WHITE else Color.BLACK
            c.drawRect(fx + col * q, tTop + row * q, fx + (col + 1) * q, tTop + (row + 1) * q, fp)
        }
        sp.color = Color.WHITE; sp.strokeWidth = 1f; sp.strokeCap = Paint.Cap.BUTT
        c.drawRect(fx, tTop, fx + 3 * q, tTop + 2 * q, sp)
        sp.strokeCap = Paint.Cap.ROUND
    }

    // Round 53 (our pick): "climb" said once: DIST TO CLIMB as the label (like the Karoo's DIST TO TOP),
    // the ClimbER mountain on the name line instead of the word ("⛰ 1 of 30"; a named climb: its name).
    // round 60 (feedback: "we do show reroutes, so off route feels weird to show"): no OFF ROUTE words, the red way back says it
    private fun nameFor(s: ClimbState): String? = s.climb?.name ?: s.number?.let { n -> s.total?.let { "$n of $it" } ?: "$n" }

    /** The climb name on the graph's sky, after the ClimbER mountain (and the PR rosette). */
    private fun nameOnGraph(c: Canvas, b: RectF, name: String, pr: Boolean, size: Float, baseline: Float? = null) {
        val base = baseline ?: (b.top + 10 + size * 0.85f)
        var x = b.left + 12   // round 51c: on the same left edge as the bar's STARTS IN / TO TOP (12 px)
        // round 51: no mountain before the name (the field is the climb); the PR rosette white, centred on
        // the name's capitals and close to it (round 51b)
        x += glyph(c, KarooType.Glyph.CLIMBER, x, base, size * 0.95f, CB400) + 8   // round 53: the icon says "climb"
        if (pr) {
            val box = size * 0.95f
            val capMid = base - size * 0.36f
            x += glyph(c, KarooType.Glyph.PR, x - 2, capMid + box / 2, box, Color.WHITE) - 2 + 3
        }
        text(c, name, cond, size, Color.WHITE, x, base, b.right - x - 10, align = Paint.Align.LEFT)
    }

    // ---- Icons ------------------------------------------------------------------------------

    /** One of the Karoo's glyphs (KarooType.Glyph), fitted to a [box] px square, bottom on [baseline]; returns its width. */
    /**
     * The header's ClimbER mountain. It's wide and flat: fitted into the icon box it is only ~16 px
     * tall next to the Karoo's 24 px icons. So it's drawn at the Karoo's icon height instead (ink
     * 0.96 × box tall, wider than the box; round 41). Returns the width it takes.
     */
    private fun headerClimber(c: Canvas, x: Float, baseline: Float, box: Float, color: Int): Float {
        val w = box * 0.96f * CLIMBER_ASPECT
        fp.color = color
        KarooType.drawGlyphFit(c, KarooType.Glyph.CLIMBER, x, baseline, w, fp)
        return w
    }

    private fun glyph(c: Canvas, path: String, x: Float, baseline: Float, box: Float, color: Int): Float {
        fp.color = color
        KarooType.drawGlyphFit(c, path, x, baseline, box, fp)
        return box
    }

    /**
     * The summit's checkered flag as an icon (round 51: the one summit symbol, like the graph's summit
     * mark): a white pole [h] tall standing on [baseline], 3 × 2 squares flying right of it. Returns its width.
     */
    private fun checkerFlag(c: Canvas, x: Float, baseline: Float, h: Float): Float {
        val lw = max(2.5f, h * 0.12f); val q = h * 0.3f
        val top = baseline - h
        fp.color = Color.WHITE; c.drawRect(x, top, x + lw, baseline, fp)
        val fx = x + lw
        for (row in 0..1) for (col in 0..2) {
            fp.color = if ((row + col) % 2 == 0) Color.WHITE else Color.BLACK
            c.drawRect(fx + col * q, top + row * q, fx + (col + 1) * q, top + (row + 1) * q, fp)
        }
        sp.color = Color.WHITE; sp.strokeWidth = 1f; sp.strokeCap = Paint.Cap.BUTT
        c.drawRect(fx, top, fx + 3 * q, top + 2 * q, sp)
        sp.strokeCap = Paint.Cap.ROUND
        return lw + 3 * q
    }

    // ---- Text and colours -------------------------------------------------------------------

    /** Draws [s] (shrunk to [maxW]) and returns its width (a value's widest width). */
    private fun text(
        c: Canvas, s: String, tf: Typeface, size: Float, color: Int, x: Float, baseline: Float,
        maxW: Float = Float.MAX_VALUE, align: Paint.Align = Paint.Align.RIGHT,
    ): Float {
        // Relative has no "−" or "–": values get a hyphen (KarooType.valueText)
        val s = if (tf === num) KarooType.valueText(s) else s
        tp.typeface = tf; tp.textSize = size; tp.textScaleX = 1f; tp.color = color; tp.textAlign = align
        tp.letterSpacing = if (tf === num) KarooType.VALUE_LETTER_SPACING else 0f
        // Values fit by their widest form (digits as 8), so the size doesn't jump as they change
        val probe = if (tf === num) s.map { if (it.isDigit()) '8' else it }.joinToString("") else s
        val pw = tp.measureText(probe)
        if (pw > maxW && maxW > 0) tp.textSize = size * maxW / pw
        c.drawText(s, x, baseline, tp)
        // Layouts around a value use its widest width, so neighbours don't move as it changes
        return tp.measureText(probe)
    }

    /** Behind red, ahead green, level with the PR in the text colour. */
    private fun gapColor(night: Boolean, gap: Int) = when {
        gap > 0 -> if (night) 0xFFFF5A4E.toInt() else 0xFFC40404.toInt()
        gap < 0 -> if (night) 0xFF60EEB2.toInt() else 0xFF007A5A.toInt()
        else -> if (night) Color.WHITE else Color.BLACK
    }

    private fun gapNavy(gap: Int) = when { gap > 0 -> 0xFFFF6A5E.toInt(); gap < 0 -> 0xFF60EEB2.toInt(); else -> Color.WHITE }

    companion object {
        /** The Karoo 3's field tile (measured): 1 px #7D7D7D border, 14 px corner radius. */
        const val TILE_BORDER_PX = 1.5f
        const val TILE_RADIUS_PX = 14f
        /** The Karoo's value baseline, 14 px above the tile's bottom (KarooType.valueBaseline). */
        const val VALUE_BOTTOM = 14f
        const val MARK_LABEL_PX = 28f
        /** The half screen bar's STARTS IN, and the climb name under it (round 51d). */
        const val BAR_LABEL_PX = 31f
        /** The half screen's climb name band under the bar (name at 31 px: baseline 36 px down). */
        const val HALF_NAME_BAND = 44f
        /** Round 52: the same word at every size (was SUMMIT REACHED on the half screen). */
        const val SUMMIT = "SUMMIT"
        /** Before a climb (round 53, at every size). */
        const val NEXT_LABEL = "DIST TO CLIMB"
        /** A route with no climb to show (round 60). */
        const val FINISH_LABEL = "DIST TO FINISH"
        /** Off route (round 55): the way back on the graph, and the name line's words and colour. */
        const val OFF_ROUTE = 0xFFE5322D.toInt()

        /** Half screen and up (design px): only here the bracket and the next-500 m strip show. */
        const val BIG_H = 330f
        // The Karoo's climb blue (ride app, ClimbER)
        const val CB400 = 0xFFA3D5FF.toInt()
        const val CB500 = 0xFF2386D9.toInt()
        const val CB600 = 0xFF005297.toInt()
        const val CB700 = 0xFF00294B.toInt()
        /** Two tiles: the graph's share of the 478 design width (field_wide.xml weights 278 / 200). */
        const val WIDE_SPLIT = 240f   // was 278, then 250; the text column fits DIST TO FINISH (221 px) on one line
        private val CLIMBER_ASPECT: Float by lazy {
            val b = RectF(); androidx.core.graphics.PathParser.createPathFromPathData(KarooType.Glyph.CLIMBER).computeBounds(b, true); b.width() / b.height()
        }
        /** The road's colour on the graph (round 33). */
        private const val ROAD_MUTED = 0xFF3D6A93.toInt()
        /** Round 48: the route graph. A climb at least this wide; a stretch at least this much up and down (m) drawn flat. */
        private const val MIN_CLIMB_PX = 3f
        /** Round 57: a gradient block is at least this wide (no stripes when zoomed out). */
        private const val MIN_BAND_PX = 8f
        /** Round 58: the share of the graph after the summit (the road on, under the checkered flag). */
        private const val AFTER_TOP_SHARE = 0.1f
        private const val MIN_RISE_M = 30.0
        /** The ridden road on the whole route: a quiet tint (navy graph; bare one-tile graph by day / night); ridden climbs at 40 %. */
        private const val RIDDEN_NAVY = 0xFF1B4468.toInt()
        private const val RIDDEN_DAY = 0xFFC3D0DB.toInt()
        private const val RIDDEN_NIGHT = 0xFF22384C.toInt()
        private const val RIDDEN_ALPHA = 102
        private const val CB400_MUTED = 0xFF7FA6C9.toInt()
        private const val NUMBER_PX = 22f
        /** The small line under a two-tile number (round 48): 18 px, darker by day, lighter at night. */
        private const val SUB_PX = 24f   // round 51e: larger (was 18, then 20), like the VS PR line it alternates with
        private const val SUB_DAY = 0xFF333333.toInt()
        private const val SUB_NIGHT = 0xFFD0D0D0.toInt()
        /** Round 49: the small line on the blue text side. */
        private const val SUB_BLUE = 0xFFDCEBF8.toInt()
        /** Between a number and its unit (round 48: was 4–6). */
        private const val UNIT_GAP = 2f
        /** The Karoo's grade colours (ride app climb1..7). */
        private val GRADE = intArrayOf(
            0xFF60EEB2.toInt(), 0xFF00B988.toInt(), 0xFFFFF500.toInt(), 0xFFFB8C65.toInt(),
            0xFFFE581F.toInt(), 0xFFD60404.toInt(), 0xFFB700A2.toInt(),
        )

        /** Ride app thresholds (grade rounded to 0.1): <2, <5, <8, <11, <14, <20, else. */
        private fun gradeIndex(g: Double): Int {
            val r = Math.round(g * 10) / 10.0
            return when { r < 2 -> 0; r < 5 -> 1; r < 8 -> 2; r < 11 -> 3; r < 14 -> 4; r < 20 -> 5; else -> 6 }
        }
        fun gradeColor(g: Double) = GRADE[gradeIndex(g)]
        /** Black text on climb1..4, white on 5..7 (as the Karoo). */
        fun gradeInk(g: Double) = if (gradeIndex(g) >= 4) Color.WHITE else Color.BLACK
    }
}
