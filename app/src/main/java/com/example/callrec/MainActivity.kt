package com.example.callrec

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

/**
 * Simple screen with no layout XML, so it's easy to read:
 *  1. Ask for mic + phone-state permissions
 *  2. Button to open Accessibility settings (turn on "CallRec")
 *  3. List of saved recordings - tap one to play/stop
 */
class MainActivity : Activity() {

    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private var player: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }

        status = TextView(this).apply { textSize = 16f }
        root.addView(status)

        root.addView(Button(this).apply {
            text = "1. Grant permissions"
            setOnClickListener { askPermissions() }
        })
        root.addView(Button(this).apply {
            text = "2. Turn on in Accessibility"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        root.addView(TextView(this).apply {
            text = "\nRecordings (tap to play / stop):"
            textSize = 16f
        })

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun askPermissions() {
        requestPermissions(
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_PHONE_STATE),
            1
        )
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        refresh()
    }

    private fun refresh() {
        val granted = listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_PHONE_STATE)
            .all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        val enabled = isServiceEnabled()
        status.text = buildString {
            append(if (granted) "✅ Permissions granted\n" else "❌ Permissions missing\n")
            append(if (enabled) "✅ Recorder is ON" else "❌ Recorder is OFF (step 2)")
        }

        list.removeAllViews()
        val files = CallRecorderService.recordingsDir(this)
            .listFiles { f -> f.extension == "m4a" }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

        if (files.isEmpty()) {
            list.addView(TextView(this).apply { text = "No recordings yet." })
        }
        for (f in files) {
            list.addView(TextView(this).apply {
                text = "▶  ${f.name}  (${f.length() / 1024} KB)"
                textSize = 15f
                setPadding(0, 24, 0, 24)
                setOnClickListener { togglePlay(f) }
            })
        }
    }

    private fun togglePlay(f: File) {
        player?.let {
            it.release()
            player = null
            return
        }
        player = MediaPlayer().apply {
            setDataSource(f.absolutePath)
            prepare()
            start()
            setOnCompletionListener { release(); player = null }
        }
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains("$packageName/")
    }

    override fun onDestroy() {
        player?.release()
        super.onDestroy()
    }

    @Suppress("unused")
    private fun View.dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
