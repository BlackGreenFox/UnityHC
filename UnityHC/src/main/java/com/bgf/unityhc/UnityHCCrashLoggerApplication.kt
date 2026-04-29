package com.bgf.unityhc

import android.app.Application

/**
 * Application subclass that installs [UnityHCCrashLogger] as early as
 * possible — before any Unity Activity is created — so a fatal Java
 * exception in `UnityPlayerGameActivity.onCreate()` (or anywhere else
 * during process bootstrap) is dumped to disk before the JVM dies.
 *
 * To enable, register this class in your Unity project's custom
 * `Assets/Plugins/Android/AndroidManifest.xml` like so:
 *
 * ```xml
 * <application
 *     android:name="com.bgf.unityhc.UnityHCCrashLoggerApplication"
 *     ... >
 *     ...
 * </application>
 * ```
 *
 * Or, if you already extend Application yourself, just call
 * [UnityHCCrashLogger.install] from your own `onCreate()`.
 */
class UnityHCCrashLoggerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        UnityHCCrashLogger.install(this)
    }
}
