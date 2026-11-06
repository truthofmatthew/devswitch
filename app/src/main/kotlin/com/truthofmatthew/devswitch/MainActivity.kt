package com.truthofmatthew.devswitch

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** One button that turns developer options, USB and wireless debugging, and Stay awake off or on together. */
class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var button: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        status = TextView(this).apply { textSize = 18f; setPadding(0, 0, 0, pad) }
        button = Button(this).apply { setOnClickListener { flip() } }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(status); addView(button)
        })
    }

    override fun onResume() { super.onResume(); render() }

    private fun render() {
        if (!allowed()) {
            status.text = "DevSwitch needs one permission that only a computer can give. " +
                "Plug the phone into your computer with USB debugging on and run:\n\n$GRANT\n\nThen come back here."
            button.text = "Copy the command"
            return
        }
        val on = Dev.isOn(this)
        status.text = Dev.SETTINGS.joinToString("\n") { (label, key) ->
            "$label: " + if (Dev.read(this, key) != 0) "on" else "off"
        }
        button.text = if (on) "Turn off (for banking apps)" else "Turn back on"
    }

    private fun flip() {
        if (!allowed()) {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("adb", GRANT))
            return
        }
        Dev.set(this, !Dev.isOn(this))
        render()
    }

    private fun allowed() = checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val GRANT = "adb shell pm grant com.truthofmatthew.devswitch android.permission.WRITE_SECURE_SETTINGS"
    }
}

object Dev {
    // adb_wifi_enabled is a hidden constant, so it's spelled out.
    val SETTINGS = listOf(
        "Developer options" to Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
        "USB debugging" to Settings.Global.ADB_ENABLED,
        "Wireless debugging" to "adb_wifi_enabled",
        "Stay awake while charging" to Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
    )
    // AC, USB, wireless and dock, the same value the Stay awake switch writes.
    private const val STAY_AWAKE = 1 or 2 or 4 or 8

    fun read(activity: Activity, key: String) = Settings.Global.getInt(activity.contentResolver, key, 0)

    /** Banking apps look at developer options and USB debugging, so those decide whether we count as on. */
    fun isOn(activity: Activity) =
        read(activity, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED) != 0 || read(activity, Settings.Global.ADB_ENABLED) != 0

    fun set(activity: Activity, on: Boolean) {
        val cr = activity.contentResolver
        // Debugging goes off before developer options and comes on after them, the order Settings uses.
        val order = if (on) SETTINGS else SETTINGS.reversed()
        for ((_, key) in order) {
            val value = if (!on) 0 else if (key == Settings.Global.STAY_ON_WHILE_PLUGGED_IN) STAY_AWAKE else 1
            Settings.Global.putInt(cr, key, value)
        }
    }
}
