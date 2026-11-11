package com.daviddokakis.mountaineer

import com.dubadai.karoo.common.*
import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.widget.RemoteViews
import com.dubadai.karoo.common.KarooField
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/** The Mountaineer field (typeId "tiny-climb", its first name: never change it, ride pages keep the field by it): any size, drawn as one bitmap by [ClimbRenderer]. */
class TinyClimbDataType(
    extension: String,
    private val session: ClimbSession,
) : DataTypeImpl(extension, TYPE_ID) {

    /**
     * Numeric fallback (plain-number places): seconds to the top. Always Streaming (empty values
     * without an ETA): anything else makes the Karoo cover our view with its UNKNOWN / No Sensor tile.
     */
    override fun startStream(emitter: Emitter<StreamState>) {
        val job = CoroutineScope(Dispatchers.Default).launch {
            // Only when the ETA changes: the state is new every second, and an empty point every
            // second is thrown away by the Karoo ("Filtering invalid datapoint", ride 2026-11-10)
            session.state.map { it.etaSec }.distinctUntilChanged().collect { eta ->
                val values = eta?.let { mapOf(DataType.Field.SINGLE to it.toDouble()) } ?: emptyMap()
                emitter.onNext(StreamState.Streaming(DataPoint(dataTypeId, values = values)))
            }
        }
        emitter.setCancellable { job.cancel() }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        Timber.d("View started: $config")
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        val (w, h) = config.viewSize
        val renderer = ClimbRenderer(context)
        if (config.preview) {
            // Page editor / field chooser: one still picture. The Karoo doesn't always stop preview
            // views, and a running one's updates can land in the live tile.
            emitter.updateView(fieldViews(context, renderer, renderer.render(ExampleClimb.state, Settings.read(context), w, h, isNight(context)), h))
            return
        }
        val job = CoroutineScope(Dispatchers.Default).launch {
            // The Karoo drops updates closer than ~0.9 s apart: redraw when something changes,
            // at most about once a second.
            var lastRender = 0L
            var shown: Any? = null
            var wasOnScreen = true
            // Sleep until the climb or the page on screen changes (karoo-common FieldPacer), no polling
            val pace = FieldPacer(listOf(session.state, session.activePage))
            val pagePace = FieldPacer(listOf(session.activePage))
            while (isActive) {
                pace.mark()
                pagePace.mark()
                // Only while this field's page is on screen; back on screen: draw at once
                val onScreen = onScreen(config)
                if (onScreen != wasOnScreen) { wasOnScreen = onScreen; if (onScreen) shown = null }
                if (!onScreen) { pagePace.sleep(FieldPacer.CAP_MS); continue }
                val s = session.state.value
                val prefs = Settings.read(context)
                val night = isNight(context)
                // Navigating: the Karoo's navigation bar shortens every row (~13 %) without telling us; draw at
                // the height it shows, so the graph and its flags keep their shape (round 56)
                val hDraw = if (session.navigating) kotlin.math.round(h * NAV_ROW_FACTOR).toInt() else h
                val key = Triple(s, prefs, night) to hDraw
                val since = SystemClock.elapsedRealtime() - lastRender
                if ((key != shown && since >= MIN_GAP_MS) || since >= IDLE_REDRAW_MS) {
                    try {
                        emitter.updateView(fieldViews(context, renderer, renderer.render(s, prefs, w, hDraw, night), hDraw))
                        shown = key
                    } catch (e: Exception) {
                        Timber.e(e, "Render failed")
                    }
                    lastRender = SystemClock.elapsedRealtime()
                }
                pace.sleep(FieldPacer.nextMs(key != shown, SystemClock.elapsedRealtime() - lastRender, MIN_GAP_MS, IDLE_REDRAW_MS))
            }
        }
        emitter.setCancellable { job.cancel() }
    }

    /** Is this view's field on the ride page on screen? The shared page-rows rule ([KarooType.onPage]). True until the Karoo says. */
    private fun onScreen(config: ViewConfig): Boolean {
        val page = session.activePage.value ?: return true
        return KarooType.onPage(
            page.elements.map { it.gridSize },
            page.elements.filter { it.dataTypeId == dataTypeId }.map { it.gridSize },
            config.gridSize,
        )
    }

    private fun isNight(context: Context) =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    companion object {
        const val TYPE_ID = "tiny-climb"
        /** Rows while navigating, shown / reported, measured on our test Karoo 2026-11-10: 216 → 188, 168 → 145, 141 → 123. */
        const val NAV_ROW_FACTOR = 0.87
        private const val MIN_GAP_MS = 920L
        private const val IDLE_REDRAW_MS = 5_000L
    }
}

/**
 * The picture as RemoteViews that survive a shortened row (a map page with a route: the Karoo
 * makes a 141 px row 123 px without telling us). The header and the number (or the half
 * screen's bar) keep their height; only the climb graph below them stretches. Empty states keep
 * the kit's header band and stay in proportion. (karoo-common KarooField, 2026-11-10 ride.)
 */
internal fun fieldViews(context: Context, renderer: ClimbRenderer, bmp: android.graphics.Bitmap, h: Int): RemoteViews =
    if (renderer.splitPx > 0) {
        // Two tiles: the graph stretches, the text column keeps its header and shrinks the rest in proportion
        val graph = android.graphics.Bitmap.createBitmap(bmp, 0, 0, renderer.splitPx, bmp.height)
        val column = android.graphics.Bitmap.createBitmap(bmp, renderer.splitPx, 0, bmp.width - renderer.splitPx, bmp.height)
        RemoteViews(context.packageName, R.layout.field_wide).apply {
            addView(R.id.graph, KarooField.views(context, graph, renderer.graphHeaderPx, body = KarooField.Body.STRETCH))
            addView(R.id.text, KarooField.views(context, column, renderer.columnHeaderPx, body = KarooField.Body.END))
        }
    } else if (renderer.fixedTopPx > 0) KarooField.views(context, bmp, renderer.fixedTopPx, body = KarooField.Body.STRETCH, footerPx = renderer.fixedBottomPx)
    else KarooField.views(context, bmp, KarooField.headerBandPx(h), body = KarooField.Body.CENTER)
