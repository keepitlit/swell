package ca.keepitlit.swell

import android.app.Activity
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.PinItemRequest
import android.os.Bundle
import android.util.Log
import android.widget.Toast

/**
 * The other half of "Add to Home screen".
 *
 * A launcher does not *find* this feature; it **advertises** it. `isRequestPinShortcutSupported()`
 * is true only when the current default home app declares an activity handling
 * `android.content.pm.action.CONFIRM_PIN_SHORTCUT`, and every browser (Chrome, Brave, Firefox)
 * checks that before it will even show the menu item. Without this activity the option is not
 * greyed out or broken, it is absent, which reads to the user as the *browser* lacking the
 * feature. That is why the symptom is identical across three unrelated engines.
 *
 * ### No confirmation dialog
 *
 * The system name for this is "confirm", and a launcher with fixed places has to ask: it needs
 * somewhere to put the icon and the user has to choose where. Swell has no such question. Every
 * seatable thing already gets a seat automatically, and rank is earned by use rather than chosen,
 * so the only honest dialog would offer no decisions. It accepts, says so, and lets the new
 * shortcut enter the book at the bottom like anything freshly installed.
 *
 * Themed transparent and finishing in `onCreate`: no frame of this is ever drawn.
 */
class PinShortcutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accept()
        finish()
    }

    private fun accept() {
        val launcherApps =
            getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
        if (launcherApps == null) {
            Log.w(TAG, "pin request with no LauncherApps service")
            return
        }

        val request = try {
            launcherApps.getPinItemRequest(intent)
        } catch (e: Exception) {
            Log.w(TAG, "malformed pin request: $e")
            null
        }

        if (request == null || request.requestType != PinItemRequest.REQUEST_TYPE_SHORTCUT) {
            // Only CONFIRM_PIN_SHORTCUT is declared, so a widget request should never arrive here.
            Log.w(TAG, "ignoring pin request of type ${request?.requestType}")
            return
        }

        // A request goes stale if the asking app died or the user backed out of it; accepting an
        // invalid one throws rather than returning false.
        if (!request.isValid) {
            Log.i(TAG, "pin request expired before it reached us")
            return
        }

        val label = request.shortcutInfo?.let {
            it.shortLabel?.toString().orEmpty().ifBlank { it.longLabel?.toString().orEmpty() }
        }.orEmpty()

        val accepted = try {
            request.accept()
        } catch (e: Exception) {
            Log.w(TAG, "pin accept failed: $e")
            false
        }

        if (!accepted) {
            Log.w(TAG, "pin request refused by the system")
            return
        }

        // The sweep is what turns the now-pinned shortcut into a seat, and it is driven by
        // LauncherApps callbacks that a pin does NOT fire: pinning changes no package. Ask for
        // one explicitly; MainActivity also re-sweeps on resume, so this only buys the case where
        // Swell is already running behind the browser.
        SwellModel.live?.resweep()

        Toast.makeText(
            this,
            if (label.isBlank()) getString(R.string.pin_added_unnamed)
            else getString(R.string.pin_added, label),
            Toast.LENGTH_SHORT,
        ).show()
    }

    private companion object {
        const val TAG = "Swell"
    }
}
