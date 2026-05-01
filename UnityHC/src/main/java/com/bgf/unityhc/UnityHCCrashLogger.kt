package com.bgf.unityhc

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lightweight crash recorder. Installs a process-wide
 * [Thread.UncaughtExceptionHandler] that dumps every fatal exception to
 * a plain text file before the JVM dies, so a host without `adb`/logcat
 * access can still recover the stack trace.
 *
 * The dump lands in TWO locations (best effort):
 *
 * 1. `<context.getExternalFilesDir(null)>/UnityHC-crash.txt` — always
 *    writable by the app itself; reachable from a desktop via MTP at
 *    `Android/data/<package>/files/UnityHC-crash.txt`.
 * 2. `Downloads/UnityHC-crash.txt` via the MediaStore Downloads
 *    collection — reachable from any on-device file manager on
 *    Android 10+. May silently fail on older devices, never throws.
 *
 * On the next successful launch, [HealthConnectPlugin.initFromUnity]
 * reads the file via [readLastCrash] and forwards the contents to Unity
 * through the OnPluginLog channel so it shows up on-screen.
 */
object UnityHCCrashLogger {

    private const val TAG = "UnityHCCrashLogger"
    const val FILE_NAME = "UnityHC-crash.txt"

    @Volatile
    private var installed = false

    /**
     * Install the global handler. Idempotent — calling more than once
     * is a no-op. The previous handler (e.g. Unity's own crash
     * reporter) is preserved and invoked after we have written the
     * dump, so existing reporting still works.
     */
    @JvmStatic
    fun install(context: Context) {
        if (installed) return
        installed = true

        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
            try {
                val dump = formatDump(thread, ex)
                writeToExternalFiles(appContext, dump)
                writeToPublicDownloads(appContext, dump)
                Log.e(TAG, "Crash dumped to external files and Downloads")
            } catch (t: Throwable) {
                Log.e(TAG, "Crash dump failed", t)
            } finally {
                previous?.uncaughtException(thread, ex)
            }
        }
        Log.i(TAG, "UncaughtExceptionHandler installed; crashes go to <externalFilesDir>/$FILE_NAME and Downloads/$FILE_NAME")
    }

    /** @return the contents of the last crash dump, or `null` if none. */
    @JvmStatic
    fun readLastCrash(context: Context): String? {
        val file = File(context.getExternalFilesDir(null) ?: context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        return runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    @JvmStatic
    fun clearLastCrash(context: Context) {
        val file = File(context.getExternalFilesDir(null) ?: context.filesDir, FILE_NAME)
        runCatching { file.delete() }
    }

    private fun formatDump(thread: Thread, ex: Throwable): String {
        val sw = StringWriter()
        ex.printStackTrace(PrintWriter(sw))
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        return buildString {
            append("===== UnityHC crash @ ").append(timestamp).append(" =====\n")
            append("thread: ").append(thread.name).append("\n")
            append("device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" (Android ").append(Build.VERSION.RELEASE).append("/API ")
                .append(Build.VERSION.SDK_INT).append(")\n")
            append("exception: ").append(ex.javaClass.name).append(": ")
                .append(ex.message ?: "").append("\n\n")
            append(sw.toString())
            append("\n\n")
        }
    }

    private fun writeToExternalFiles(context: Context, dump: String) {
        runCatching {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            dir.mkdirs()
            File(dir, FILE_NAME).writeText(dump)
        }.onFailure { Log.w(TAG, "writeToExternalFiles failed: ${it.message}") }
    }

    private fun writeToPublicDownloads(context: Context, dump: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        runCatching {
            val resolver = context.contentResolver
            // Replace any prior dump with the same name.
            val deletion = MediaStore.Downloads.DISPLAY_NAME + "=?"
            resolver.delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                deletion,
                arrayOf(FILE_NAME)
            )
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download")
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching
            resolver.openOutputStream(uri)?.use { os -> os.write(dump.toByteArray()) }
        }.onFailure { Log.w(TAG, "writeToPublicDownloads failed: ${it.message}") }
    }
}
