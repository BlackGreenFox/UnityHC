package com.example.healthconnect

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

object HealthConnectPlugin {

    private var activity: Activity? = null
    private var client: HealthConnectClient? = null

    fun init(act: Activity) {
        activity = act
        checkAvailability()
    }

    private fun checkAvailability() {
        val provider = "com.google.android.apps.healthdata"
        val status = HealthConnectClient.getSdkStatus(activity!!, provider)

        when (status) {
            HealthConnectClient.SDK_UNAVAILABLE -> {
                Log.e("HC", "Not available")
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                val uri = "market://details?id=$provider&url=healthconnect%3A%2F%2Fonboarding"
                activity?.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setPackage("com.android.vending")
                        data = Uri.parse(uri)
                    }
                )
            }
            else -> {
                client = HealthConnectClient.getOrCreate(activity!!)
            }
        }
    }

    fun requestPermissions() {
        PermissionHelper.requestPermissions(activity!!, client!!)
    }

    fun fetchStepsToday() {
        HealthDataManager.readSteps(client!!) { result ->
            UnityPlayer.UnitySendMessage(
                "HealthConnectManager",
                "OnHealthDataReceived",
                result
            )
        }
    }


    @JvmStatic
    fun readTodaySteps(activity: Activity) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val client = HealthConnectClient.getOrCreate(activity)
                val zone = ZoneId.systemDefault()
                val startOfDay = java.time.LocalDate.now(zone).atStartOfDay(zone).toInstant()
                val now = Instant.now()

                val response = client.readRecords(
                    ReadRecordsRequest(
                        recordType = StepsRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startOfDay, now)
                    )
                )

                val total = response.records.sumOf { it.count }
                JSONObject()
                    .put("ok", true)
                    .put("steps", total)
                    .toString()
            }.onSuccess { json ->
                sendUnity("OnReadStepsResult", json)
            }.onFailure {
                sendUnity("OnReadStepsResult", errorJson(it))
            }
        }
    }

    @JvmStatic
    fun readTodayDistance(activity: Activity) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val client = HealthConnectClient.getOrCreate(activity)
                val zone = ZoneId.systemDefault()
                val startOfDay = java.time.LocalDate.now(zone).atStartOfDay(zone).toInstant()
                val now = Instant.now()

                val response = client.readRecords(
                    ReadRecordsRequest(
                        recordType = DistanceRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startOfDay, now)
                    )
                )

                val meters = response.records.sumOf { it.distance.inMeters }
                JSONObject()
                    .put("ok", true)
                    .put("distanceMeters", meters)
                    .toString()
            }.onSuccess { json ->
                sendUnity("OnReadDistanceResult", json)
            }.onFailure {
                sendUnity("OnReadDistanceResult", errorJson(it))
            }
        }
    }

    private fun sendUnity(method: String, message: String) {
        try {
            val unityPlayer = Class.forName("com.unity3d.player.UnityPlayer")
            val sendMessage = unityPlayer.getMethod(
                "UnitySendMessage",
                String::class.java,
                String::class.java,
                String::class.java
            )
            sendMessage.invoke(null, unityGameObjectName, method, message)
        } catch (t: Throwable) {
            Log.e(TAG, "UnitySendMessage failed", t)
        }
    }

    private fun errorJson(t: Throwable): String {
        return JSONObject()
            .put("ok", false)
            .put("error", t.message ?: t::class.java.simpleName)
            .toString()
    }
}