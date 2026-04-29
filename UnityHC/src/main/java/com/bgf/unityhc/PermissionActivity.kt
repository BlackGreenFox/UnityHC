package com.bgf.unityhc

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.health.connect.client.PermissionController
import org.json.JSONArray
import org.json.JSONObject

/**
 * Headless activity that launches Health Connect's permission contract and
 * forwards the result back to Unity via [HealthConnectPlugin.sendUnity].
 *
 * Started by [HealthConnectPlugin.requestPermissions]. Finishes itself
 * immediately after the user closes the system dialog.
 */
class PermissionActivity : Activity() {

    private val contract = PermissionController.createRequestPermissionResultContract()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = contract.createIntent(this, HealthConnectPlugin.ALL_PERMISSIONS)
        startActivityForResult(intent, REQ_PERMISSIONS)
    }

    @Deprecated("Required for Activity API; use onActivityResult callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PERMISSIONS) return

        val granted: Set<String> = contract.parseResult(resultCode, data)
        val missing: Set<String> = HealthConnectPlugin.ALL_PERMISSIONS - granted

        val payload = JSONObject()
            .put("ok", true)
            .put("all", missing.isEmpty())
            .put("granted", JSONArray().apply { for (p in granted) put(p) })
            .put("missing", JSONArray().apply { for (p in missing) put(p) })
            .toString()

        HealthConnectPlugin.sendUnity(HealthConnectPlugin.CB_PERMISSIONS, payload)
        finish()
    }

    companion object {
        private const val REQ_PERMISSIONS = 1001
    }
}
