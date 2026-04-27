package com.bgf.unityhc

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.DistanceRecord

class PermissionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissions = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
        )

        val intent = PermissionController.createRequestPermissionResultContract()
            .createIntent(this, permissions)

        startActivityForResult(intent, 1001)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 1001) {

            val granted = PermissionController
                .createRequestPermissionResultContract()
                .parseResult(resultCode, data)

            val success = granted.containsAll(
                setOf(
                    HealthPermission.getReadPermission(StepsRecord::class),
                    HealthPermission.getReadPermission(DistanceRecord::class),
                )
            )

            HealthConnectPlugin.sendUnity(
                "OnPermissionsResult",
                success.toString()
            )

            finish()
        }
    }
}