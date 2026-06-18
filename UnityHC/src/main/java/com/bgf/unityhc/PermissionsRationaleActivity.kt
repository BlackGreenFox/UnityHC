package com.bgf.unityhc

import android.app.Activity
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Privacy-policy / rationale screen required by Health Connect.
 *
 * Health Connect launches this activity (via the
 * `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE` intent) when the user
 * taps the "Privacy policy" link in the permission dialog.
 *
 * The default screen below is a placeholder. **Replace the contents (or the
 * whole activity) with your real privacy policy** before publishing the app
 * to Google Play; otherwise Google will reject the listing.
 */
class PermissionsRationaleActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val padding = (resources.displayMetrics.density * 24).toInt()

        val title = TextView(this).apply {
            text = "Health data usage"
            textSize = 22f
            setPadding(0, 0, 0, padding / 2)
        }

        val body = TextView(this).apply {
            text = buildString {
                append("This app reads and writes data through Android Health Connect ")
                append("to provide step / heart-rate / distance / calories tracking ")
                append("inside the game.\n\n")
                append("Your data never leaves your device unless you explicitly enable ")
                append("a sync feature in the app settings.\n\n")
                append("You can revoke any permission at any time from the Health Connect ")
                append("app: Settings → Apps → Health Connect → App permissions.")
            }
            textSize = 15f
            movementMethod = LinkMovementMethod.getInstance()
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(padding, padding, padding, padding)
            addView(title)
            addView(body)
        }

        setContentView(ScrollView(this).apply { addView(column) })
    }
}
