package com.example.healthconnect

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object HealthConnectPlugin {
    private const val TAG = "HealthConnectPlugin"
    private const val UNITY_OBJECT = "HealthConnectManager"

    private var activity: Activity? = null
    private var client: HealthConnectClient? = null

    fun init(act: Activity) {
        activity = act
        checkAvailability()
    }

    private fun checkAvailability() {
        val currentActivity = activity ?: return
        val provider = "com.google.android.apps.healthdata"
        val status = HealthConnectClient.getSdkStatus(currentActivity, provider)

        when (status) {
            HealthConnectClient.SDK_UNAVAILABLE -> Log.e(TAG, "Health Connect is not available")
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                val uri = "market://details?id=$provider&url=healthconnect%3A%2F%2Fonboarding"
                currentActivity.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setPackage("com.android.vending")
                        data = Uri.parse(uri)
                    },
                )
            }

            else -> client = HealthConnectClient.getOrCreate(currentActivity)
        }
    }

    fun requestPermissions() {
        val currentActivity = activity ?: return
        val currentClient = client ?: return
        PermissionHelper.requestPermissions(currentActivity, currentClient)
    }

    fun fetchStepsToday() {
        val currentClient = client ?: return
        HealthDataManager.readSteps(currentClient) { result ->
            sendUnity("OnHealthDataReceived", result)
        }
    }

    @JvmStatic
    fun readTodaySteps(activity: Activity) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val client = HealthConnectClient.getOrCreate(activity)
                val zone = ZoneId.systemDefault()
                val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant()
                val now = Instant.now()

                val response = client.readRecords(
                    ReadRecordsRequest(
                        recordType = StepsRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startOfDay, now),
                    ),
                )

                val total = response.records.sumOf { it.count }
                JSONObject().put("ok", true).put("steps", total).toString()
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
                val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant()
                val now = Instant.now()

                val response = client.readRecords(
                    ReadRecordsRequest(
                        recordType = DistanceRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startOfDay, now),
                    ),
                )

                val meters = response.records.sumOf { it.distance.inMeters }
                JSONObject().put("ok", true).put("distanceMeters", meters).toString()
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
                String::class.java,
            )
            sendMessage.invoke(null, UNITY_OBJECT, method, message)
        } catch (t: Throwable) {
            Log.e(TAG, "UnitySendMessage failed", t)
        }
    }

    private fun errorJson(t: Throwable): String =
        JSONObject().put("ok", false).put("error", t.message ?: t::class.java.simpleName).toString()
}