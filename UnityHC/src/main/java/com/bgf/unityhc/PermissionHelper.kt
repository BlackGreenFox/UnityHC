package com.example.healthconnect

import android.app.Activity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord

object PermissionHelper {

    fun requestPermissions(activity: Activity, client: HealthConnectClient) {

        val permissions = setOf(
            HealthPermission.getReadPermission(StepsRecord::class)
        )

        val contract = androidx.health.connect.client.PermissionController
            .createRequestPermissionResultContract()

        val launcher = activity.activityResultRegistry.register(
            "hc_permissions",
            contract
        ) { granted ->
            val result = granted.containsAll(permissions)
            com.unity3d.player.UnityPlayer.UnitySendMessage(
                "HealthConnectManager",
                "OnPermissionsResult",
                result.toString()
            )
        }

        launcher.launch(permissions)
    }
}