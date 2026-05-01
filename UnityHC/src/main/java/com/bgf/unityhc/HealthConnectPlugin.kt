package com.bgf.unityhc

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant

/**
 * Singleton entry point called from Unity via [AndroidJavaClass].
 *
 * All public methods are annotated with [JvmStatic] so they are reachable
 * through Unity's `AndroidJavaClass("com.bgf.unityhc.HealthConnectPlugin")`.
 *
 * Results are delivered back to Unity asynchronously via
 * `UnityPlayer.UnitySendMessage(gameObject, method, json)`. Set the
 * GameObject name once with [initFromUnity]; default is "HealthConnectManager".
 */
object HealthConnectPlugin {

    private const val TAG = "HealthConnectPlugin"
    private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

    /** All Health Connect permissions this plugin can request. */
    val ALL_PERMISSIONS: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getWritePermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class),
    )

    @Volatile
    private var unityGameObject: String = "HealthConnectManager"

    @Volatile
    private var activity: Activity? = null

    @Volatile
    internal var client: HealthConnectClient? = null
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ---------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------

    /**
     * Initialise the bridge. Must be called once from Unity before anything
     * else, e.g. `new AndroidJavaClass(...).CallStatic("initFromUnity", "HealthConnectManager")`.
     *
     * @param gameObjectName name of the Unity GameObject that will receive
     *  callbacks via [SendUnityMessage]. Pass `null` or empty to keep the
     *  default ("HealthConnectManager").
     */
    @JvmStatic
    @JvmOverloads
    fun initFromUnity(gameObjectName: String? = null) {
        if (!gameObjectName.isNullOrEmpty()) {
            unityGameObject = gameObjectName
        }
        val act = resolveCurrentActivity()
        if (act == null) {
            sendUnity(
                CB_INIT,
                errorJson("UnityPlayer.currentActivity not resolvable. Tried: " +
                    UNITY_PLAYER_CLASSES.joinToString(", "))
            )
            return
        }
        activity = act
        pluginLog("I", "initFromUnity: GameObject='$unityGameObject', currentActivity='${act.javaClass.name}'")

        // Install the crash logger as a fallback (the dedicated
        // Application subclass installs it earlier; this catches the
        // case where the user has not wired up that Application class).
        runCatching { UnityHCCrashLogger.install(act) }

        // If the previous run died with an uncaught exception, dump
        // the recorded stack trace into the on-screen log so the user
        // can read it without adb.
        val prior = runCatching { UnityHCCrashLogger.readLastCrash(act) }.getOrNull()
        if (!prior.isNullOrBlank()) {
            pluginLog("E", "Previous crash recovered:\n$prior")
            runCatching { UnityHCCrashLogger.clearLastCrash(act) }
        }

        checkAvailability()
    }

    /** @return one of [HealthConnectClient.SDK_AVAILABLE] /
     *  [HealthConnectClient.SDK_UNAVAILABLE] /
     *  [HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED]. */
    @JvmStatic
    fun getSdkStatus(): Int {
        val act = activity ?: return HealthConnectClient.SDK_UNAVAILABLE
        return HealthConnectClient.getSdkStatus(act, HEALTH_CONNECT_PACKAGE)
    }

    /** Open Play Store page of the Health Connect provider. */
    @JvmStatic
    fun openHealthConnectInPlayStore() {
        val act = activity ?: return
        act.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    // ---------------------------------------------------------------------
    // Permissions
    // ---------------------------------------------------------------------

    /**
     * Launches [PermissionActivity] which calls Health Connect's permission
     * contract. Result is delivered to Unity via [CB_PERMISSIONS] as a JSON
     * `{ "ok":true, "granted":[...], "missing":[...] }`.
     */
    @JvmStatic
    fun requestPermissions() {
        val act = activity ?: return
        act.startActivity(
            Intent(act, PermissionActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /**
     * Async permission check. Result is delivered to Unity via [CB_HAS_PERMISSIONS]
     * as a JSON `{ "ok":true, "all":bool, "granted":[...], "missing":[...] }`.
     */
    @JvmStatic
    fun hasAllPermissions() {
        val c = client ?: run {
            sendUnity(CB_HAS_PERMISSIONS, errorJson("Health Connect not initialised"))
            return
        }
        scope.launch {
            runCatching {
                val granted = c.permissionController.getGrantedPermissions()
                val missing = ALL_PERMISSIONS - granted
                JSONObject()
                    .put("ok", true)
                    .put("all", missing.isEmpty())
                    .put("granted", granted.toJsonArray())
                    .put("missing", missing.toJsonArray())
                    .toString()
            }.onSuccess { sendUnity(CB_HAS_PERMISSIONS, it) }
                .onFailure { sendUnity(CB_HAS_PERMISSIONS, errorJson(it.message)) }
        }
    }

    // ---------------------------------------------------------------------
    // Reads
    // ---------------------------------------------------------------------

    /**
     * Returns aggregated data for [today 00:00 .. now] in user's local timezone.
     * JSON shape: `{ ok, steps, distanceMeters, activeKcal, totalKcal, hrAvg, hrMin, hrMax }`
     */
    @JvmStatic
    fun getTodaySummary() {
        val c = client ?: run {
            sendUnity(CB_TODAY_SUMMARY, errorJson("Health Connect not initialised"))
            return
        }
        scope.launch {
            sendUnity(CB_TODAY_SUMMARY, HealthDataManager.todaySummary(c))
        }
    }

    /**
     * Read raw records of the given [recordType] in the [startMillis, endMillis]
     * window. Result delivered as a JSON array via [CB_RECORDS].
     */
    @JvmStatic
    fun getRecords(recordType: String, startMillis: Long, endMillis: Long) {
        val c = client ?: run {
            sendUnity(CB_RECORDS, errorJson("Health Connect not initialised"))
            return
        }
        scope.launch {
            sendUnity(
                CB_RECORDS,
                HealthDataManager.readRecords(
                    c, recordType,
                    Instant.ofEpochMilli(startMillis),
                    Instant.ofEpochMilli(endMillis)
                )
            )
        }
    }

    // ---------------------------------------------------------------------
    // Writes
    // ---------------------------------------------------------------------

    @JvmStatic
    fun insertSteps(count: Long, startMillis: Long, endMillis: Long) {
        runWriter(CB_INSERT) {
            HealthDataManager.insertSteps(it, count, startMillis, endMillis)
        }
    }

    @JvmStatic
    fun insertHeartRate(bpm: Long, timestampMillis: Long) {
        runWriter(CB_INSERT) {
            HealthDataManager.insertHeartRate(it, bpm, timestampMillis)
        }
    }

    @JvmStatic
    fun insertDistance(meters: Double, startMillis: Long, endMillis: Long) {
        runWriter(CB_INSERT) {
            HealthDataManager.insertDistance(it, meters, startMillis, endMillis)
        }
    }

    @JvmStatic
    fun insertActiveCalories(kcal: Double, startMillis: Long, endMillis: Long) {
        runWriter(CB_INSERT) {
            HealthDataManager.insertActiveCalories(it, kcal, startMillis, endMillis)
        }
    }

    @JvmStatic
    fun insertTotalCalories(kcal: Double, startMillis: Long, endMillis: Long) {
        runWriter(CB_INSERT) {
            HealthDataManager.insertTotalCalories(it, kcal, startMillis, endMillis)
        }
    }

    // ---------------------------------------------------------------------
    // Tracking (polls today's summary at fixed intervals)
    // ---------------------------------------------------------------------

    @JvmStatic
    @JvmOverloads
    fun startTracking(intervalMillis: Long = 3000L) {
        val c = client ?: run {
            sendUnity(CB_TODAY_SUMMARY, errorJson("Health Connect not initialised"))
            return
        }
        HealthDataManager.startTracking(c, intervalMillis) { json ->
            sendUnity(CB_TODAY_SUMMARY, json)
        }
    }

    @JvmStatic
    fun stopTracking() {
        HealthDataManager.stopTracking()
    }

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

    private fun checkAvailability() {
        val act = activity ?: return
        val status = HealthConnectClient.getSdkStatus(act, HEALTH_CONNECT_PACKAGE)
        pluginLog("I", "HealthConnectClient.getSdkStatus() = $status")
        when (status) {
            HealthConnectClient.SDK_UNAVAILABLE -> {
                sendUnity(CB_INIT, errorJson("Health Connect not available"))
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                sendUnity(CB_INIT, errorJson("Health Connect provider update required"))
                openHealthConnectInPlayStore()
            }
            else -> {
                client = HealthConnectClient.getOrCreate(act)
                pluginLog("I", "HealthConnectClient ready")
                sendUnity(
                    CB_INIT,
                    JSONObject().put("ok", true).put("status", status).toString()
                )
            }
        }
    }

    private inline fun runWriter(
        callback: String,
        crossinline block: suspend (HealthConnectClient) -> String
    ) {
        val c = client ?: run {
            sendUnity(callback, errorJson("Health Connect not initialised"))
            return
        }
        scope.launch {
            sendUnity(callback, block(c))
        }
    }

    /**
     * Send a JSON message back to Unity.
     *
     * In Unity 6 the static `UnitySendMessage` and `currentActivity` were
     * moved from `com.unity3d.player.UnityPlayer` to
     * `com.unity3d.player.UnityPlayerForActivityOrService`. We probe both
     * locations so the bridge keeps working on Unity 2022 and Unity 6+.
     */
    internal fun sendUnity(method: String, payload: String) {
        if (sendUnityRaw(method, payload)) return
        // Avoid recursing into pluginLog (which calls sendUnity again).
        Log.w(TAG, "UnitySendMessage delivery failed for $method on $unityGameObject; tried ${UNITY_PLAYER_CLASSES.joinToString()}")
    }

    private fun sendUnityRaw(method: String, payload: String): Boolean {
        for (className in UNITY_PLAYER_CLASSES) {
            try {
                val cls = Class.forName(className)
                val send = cls.getMethod(
                    "UnitySendMessage",
                    String::class.java, String::class.java, String::class.java
                )
                send.invoke(null, unityGameObject, method, payload)
                return true
            } catch (e: NoSuchMethodException) {
                // try next candidate
            } catch (e: ClassNotFoundException) {
                // try next candidate
            } catch (e: Throwable) {
                Log.w(TAG, "$className.UnitySendMessage($unityGameObject.$method) failed: ${e.message}")
            }
        }
        return false
    }

    /**
     * Mirror a log line into Android logcat AND into Unity (so a host that
     * has no adb access can render plugin logs straight onto a TMP_Text).
     * Levels: "I", "W", "E".
     */
    internal fun pluginLog(level: String, msg: String, t: Throwable? = null) {
        val full = if (t == null) msg else "$msg: ${t.javaClass.simpleName}: ${t.message}"
        when (level) {
            "E" -> if (t != null) Log.e(TAG, msg, t) else Log.e(TAG, msg)
            "W" -> if (t != null) Log.w(TAG, msg, t) else Log.w(TAG, msg)
            else -> Log.i(TAG, full)
        }
        val payload = JSONObject()
            .put("level", level)
            .put("tag", TAG)
            .put("msg", full)
            .toString()
        // sendUnityRaw avoids the warning-log path of sendUnity to keep
        // pluginLog cheap and free of recursion if delivery fails.
        sendUnityRaw(CB_LOG, payload)
    }

    /**
     * Resolve the current foreground Activity exposed by the Unity Player.
     * Tries Unity 6's `UnityPlayerForActivityOrService` first, then falls
     * back to the legacy `UnityPlayer.currentActivity` field.
     */
    private fun resolveCurrentActivity(): Activity? {
        for (className in UNITY_PLAYER_CLASSES) {
            try {
                val cls = Class.forName(className)
                val field = cls.getField("currentActivity")
                val value = field.get(null)
                if (value is Activity) return value
                Log.w(TAG, "$className.currentActivity returned ${value?.javaClass?.name ?: "null"}")
            } catch (e: NoSuchFieldException) {
                Log.w(TAG, "$className has no field 'currentActivity'")
            } catch (e: ClassNotFoundException) {
                Log.w(TAG, "$className not found in app classpath")
            } catch (e: Throwable) {
                Log.w(TAG, "$className.currentActivity lookup failed: ${e.message}")
            }
        }
        return null
    }

    private fun errorJson(message: String?): String =
        JSONObject().put("ok", false).put("error", message ?: "unknown").toString()

    private fun Set<String>.toJsonArray(): org.json.JSONArray {
        val arr = org.json.JSONArray()
        for (s in this) arr.put(s)
        return arr
    }

    // Order matters: Unity 6 first, legacy fallback second.
    private val UNITY_PLAYER_CLASSES = listOf(
        "com.unity3d.player.UnityPlayerForActivityOrService",
        "com.unity3d.player.UnityPlayer",
    )

    // Unity callback method names. Use these from your C# wrapper.
    const val CB_INIT = "OnHealthConnectInit"
    const val CB_PERMISSIONS = "OnPermissionsResult"
    const val CB_HAS_PERMISSIONS = "OnHasPermissionsResult"
    const val CB_TODAY_SUMMARY = "OnHealthSummaryReceived"
    const val CB_RECORDS = "OnRecordsReceived"
    const val CB_INSERT = "OnInsertResult"
    const val CB_LOG = "OnPluginLog"
}
