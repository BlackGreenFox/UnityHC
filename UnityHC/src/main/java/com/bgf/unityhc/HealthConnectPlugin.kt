package com.bgf.unityhc

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.health.connect.client.HealthConnectClient

object HealthConnectPlugin {

    private const val TAG = "HealthConnectPlugin"
    private const val UNITY_OBJECT = "HealthConnectManager"
    private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

    private var activity: Activity? = null
    private var client: HealthConnectClient? = null

    @JvmStatic
    fun initFromUnity() {
        try {
            val unityPlayer = Class.forName("com.unity3d.player.UnityPlayer")
            val act = unityPlayer.getField("currentActivity").get(null) as Activity
            activity = act
            checkAvailability()
        } catch (e: Exception) {
            Log.e(TAG, "Init failed", e)
        }
    }

    @JvmStatic
    fun requestPermissions() {
        val act = activity ?: return
        act.startActivity(Intent(act, PermissionActivity::class.java))
    }

    @JvmStatic
    fun startTracking() {
        val c = client ?: return
        HealthDataManager.startTracking(c)
    }

    @JvmStatic
    fun stopTracking() {
        HealthDataManager.stopTracking()
    }

    private fun checkAvailability() {
        val act = activity ?: return

        val status = HealthConnectClient.getSdkStatus(act, HEALTH_CONNECT_PACKAGE)

        when (status) {
            HealthConnectClient.SDK_UNAVAILABLE -> {
                sendUnity("OnHealthSummaryReceived", """{"ok":false,"error":"Not available"}""")
            }

            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                val uri = "market://details?id=$HEALTH_CONNECT_PACKAGE"
                act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
            }

            else -> {
                client = HealthConnectClient.getOrCreate(act)
            }
        }
    }

    fun sendUnity(method: String, message: String) {
        try {
            val unityPlayer = Class.forName("com.unity3d.player.UnityPlayer")
            val sendMessage = unityPlayer.getMethod(
                "UnitySendMessage",
                String::class.java,
                String::class.java,
                String::class.java
            )

            sendMessage.invoke(null, UNITY_OBJECT, method, message)

        } catch (e: Exception) {
            Log.e(TAG, "Unity callback failed", e)
        }
    }
}