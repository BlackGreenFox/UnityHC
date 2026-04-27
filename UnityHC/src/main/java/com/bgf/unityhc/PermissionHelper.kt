package com.example.healthconnect

import android.app.Activity
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.StepsRecord

object PermissionHelper {
    private const val TAG = "PermissionHelper"

    fun requestPermissions(activity: Activity, client: HealthConnectClient) {
        val permissions = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
        )

        val host = activity as? ComponentActivity
        if (host == null) {
            Log.e(TAG, "Activity must inherit from ComponentActivity to request permissions")
            return
        }

        val contract = androidx.health.connect.client.PermissionController
            .createRequestPermissionResultContract()

        val launcher = host.activityResultRegistry.register(
            "hc_permissions_${System.currentTimeMillis()}",
            contract,
        ) { granted ->
            val result = granted.containsAll(permissions)
            runCatching {
                val unityPlayer = Class.forName("com.unity3d.player.UnityPlayer")
                val sendMessage = unityPlayer.getMethod(
                    "UnitySendMessage",
                    String::class.java,
                    String::class.java,
                    String::class.java,
                )
                sendMessage.invoke(null, "HealthConnectManager", "OnPermissionsResult", result.toString())
            }.onFailure { Log.e(TAG, "Unity callback failed", it) }
        }

        launcher.launch(permissions)
    }
}