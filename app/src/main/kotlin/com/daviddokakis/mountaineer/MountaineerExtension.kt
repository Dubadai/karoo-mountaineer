package com.daviddokakis.mountaineer

import com.dubadai.karoo.common.*
import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.extension.KarooExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import timber.log.Timber

/** Entry point the Karoo system binds to. */
class MountaineerExtension : KarooExtension(EXTENSION_ID, "1") {

    private lateinit var karoo: KarooSystemService
    private lateinit var session: ClimbSession
    private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val types: List<DataTypeImpl> by lazy {
        listOf(TinyClimbDataType(extension, session))
    }

    override fun onCreate() {
        super.onCreate()
        Log.i("BuildStamp", "$packageName ${BuildConfig.VERSION_NAME} build ${BuildConfig.BUILD_STAMP}")
        if (Timber.treeCount == 0) Timber.plant(Timber.DebugTree())
        karoo = KarooSystemService(this)
        session = ClimbSession(this, karoo)
        karoo.connect { connected -> Timber.i("Karoo system connected: $connected") }
        session.start()
        // Not riding: tell the rider when a newer release is on GitHub (see UpdateCheck).
        UpdateCheck(this, karoo, appName = "Mountaineer").start(updateScope)
    }

    override fun onDestroy() {
        updateScope.cancel()
        session.stop()
        karoo.disconnect()
        super.onDestroy()
    }

    companion object {
        const val EXTENSION_ID = "mountaineer"
    }
}
