package com.example.callrec

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Environment
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The core of the app.
 *
 * Why an AccessibilityService? Since Android 10, ordinary background apps are
 * not allowed to use the microphone during a phone call. An enabled
 * accessibility service is treated specially by the system and is allowed to
 * keep capturing audio, which is the same trick apps like Cube ACR use.
 *
 * Limitation: this records from the MICROPHONE, not the call stream itself
 * (that needs system/root access). Your voice will be clear; the other side is
 * picked up from the earpiece/speaker, so put calls on speaker for best results.
 */
class CallRecorderService : AccessibilityService() {

    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var telephony: TelephonyManager? = null
    private var callStateCallback: TelephonyCallback? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Service connected")

        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_PHONE_STATE not granted - open the app and grant permissions")
            return
        }

        val tm = getSystemService(TelephonyManager::class.java)
        val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                when (state) {
                    // OFFHOOK = a call is active (answered incoming, or outgoing dialing)
                    TelephonyManager.CALL_STATE_OFFHOOK -> startRecording()
                    // IDLE = no call any more
                    TelephonyManager.CALL_STATE_IDLE -> stopRecording()
                    // RINGING: do nothing until the call is answered
                }
            }
        }
        tm.registerTelephonyCallback(mainExecutor, cb)
        telephony = tm
        callStateCallback = cb
    }

    private fun startRecording() {
        if (recorder != null) return // already recording
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO not granted")
            return
        }

        val dir = recordingsDir(this)
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val file = File(dir, "call_$stamp.m4a")

        // Try the cleanest source first, then fall back.
        val sources = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC
        )
        for (source in sources) {
            val r = MediaRecorder(this)
            try {
                r.setAudioSource(source)
                r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                r.setAudioSamplingRate(44100)
                r.setAudioEncodingBitRate(128_000)
                r.setOutputFile(file.absolutePath)
                r.prepare()
                r.start()
                recorder = r
                currentFile = file
                Log.i(TAG, "Recording started (source=$source): ${file.name}")
                return
            } catch (e: Exception) {
                Log.w(TAG, "Source $source failed: ${e.message}")
                r.release()
                file.delete()
            }
        }
        Log.e(TAG, "Could not start recording with any audio source")
    }

    private fun stopRecording() {
        val r = recorder ?: return
        try {
            r.stop()
            Log.i(TAG, "Recording saved: ${currentFile?.name}")
        } catch (e: RuntimeException) {
            // stop() throws if almost nothing was recorded; the file is unusable
            Log.w(TAG, "Recording too short, deleting")
            currentFile?.delete()
        } finally {
            r.release()
            recorder = null
            currentFile = null
        }
    }

    // We don't actually need any accessibility events; the service only
    // exists so the system lets us use the mic during calls.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        stopRecording()
        callStateCallback?.let { telephony?.unregisterTelephonyCallback(it) }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CallRec"

        /** App-private folder: no storage permission needed. */
        fun recordingsDir(ctx: android.content.Context): File {
            val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: ctx.filesDir
            dir.mkdirs()
            return dir
        }
    }
}
