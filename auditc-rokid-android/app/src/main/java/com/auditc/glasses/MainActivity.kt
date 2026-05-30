package com.auditc.glasses

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val client = OkHttpClient()
    private val handler = Handler(Looper.getMainLooper())
    private val jsonMediaType = "application/json".toMediaType()

    private lateinit var headerConnectionDot: View
    private lateinit var currentStepText: TextView
    private lateinit var statusBoxText: TextView
    private lateinit var overlay: View
    private lateinit var overlayText: TextView

    private lateinit var tts: TextToSpeech
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null

    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startAudioRecording()
        } else {
            statusBoxText.text = "(microphone permission denied)"
        }
    }

    private val sopSteps = listOf(
        "Step 1: Sample Collection",
        "Step 2: Reagent Preparation",
        "Step 3: Sample Transfer",
        "Step 4: PCR Machine Run",
        "Step 5: Result Analysis",
    )
    private var currentStep = 0
    private val stepPassed = MutableList(sopSteps.size) { false }

    private val recognizerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val spokenText = data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()

        if (spokenText.isBlank()) {
            statusBoxText.text = "(no speech recognized)"
            return@registerForActivityResult
        }

        statusBoxText.text = spokenText
        handleSpokenText(spokenText)
    }

    private val keyReceiver = KeyReceiver().apply {
        listener = { type ->
            when (type) {
                KeyType.CLICK -> startVoiceRecognition()
                KeyType.LONG_PRESS -> generateReport()
                KeyType.TWO_FINGER_SINGLE_TAP -> showObservationDialog()
                KeyType.TWO_FINGER_SWIPE_FORWARD -> advanceStep(1)
                KeyType.TWO_FINGER_SWIPE_BACK -> advanceStep(-1)
                KeyType.AI_START -> generateReport()
            }
        }
    }

    private var overlayTimer: Runnable? = null

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.ENGLISH)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
        }
        try {
            recognizerLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            startAudioRecording()
        }
    }

    private fun startAudioRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (recorder != null) {
            return
        }

        val output = File(cacheDir, "voice-input.m4a")
        try {
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioChannels(1)
                setOutputFile(output.absolutePath)
                prepare()
                start()
            }
            recordingFile = output
            statusBoxText.text = "Listening... speak now"
            handler.postDelayed({ stopAudioRecordingAndTranscribe() }, RECORDING_DURATION_MS)
        } catch (_: Exception) {
            recorder?.release()
            recorder = null
            statusBoxText.text = "(microphone recording unavailable)"
            showDemoVoiceInputDialog()
        }
    }

    private fun stopAudioRecordingAndTranscribe() {
        val activeRecorder = recorder ?: return
        recorder = null
        try {
            activeRecorder.stop()
        } catch (_: Exception) {
            activeRecorder.release()
            statusBoxText.text = "(no speech recorded)"
            return
        }
        activeRecorder.release()

        val audioFile = recordingFile ?: return
        statusBoxText.text = "Transcribing..."
        transcribeAudio(audioFile)
    }

    private fun transcribeAudio(audioFile: File) {
        val request = Request.Builder()
            .url("$BASE_URL/transcribe")
            .post(audioFile.asRequestBody("audio/mp4".toMediaType()))
            .build()

        Thread {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Transcription failed: ${response.code}")
                    }
                    val spokenText = JSONObject(response.body?.string().orEmpty())
                        .optString("transcript")
                        .trim()
                    if (spokenText.isBlank()) {
                        throw IllegalStateException("No speech recognized")
                    }
                    runOnUiThread {
                        statusBoxText.text = spokenText
                        handleSpokenText(spokenText)
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    statusBoxText.text = "(transcription unavailable)"
                    showDemoVoiceInputDialog()
                }
            } finally {
                audioFile.delete()
            }
        }.start()
    }

    private fun showDemoVoiceInputDialog() {
        val phrases = arrayOf(
            "step one done sample collected",
            "contamination detected",
            "generate report",
        )
        AlertDialog.Builder(this)
            .setTitle("Demo Voice Input")
            .setItems(phrases) { _, index ->
                val spokenText = phrases[index]
                statusBoxText.text = spokenText
                handleSpokenText(spokenText)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        headerConnectionDot = findViewById(R.id.connectionDot)
        currentStepText = findViewById(R.id.currentStepText)
        statusBoxText = findViewById(R.id.statusBoxText)
        overlay = findViewById(R.id.overlay)
        overlayText = findViewById(R.id.overlayText)

        updateStepDisplay()
        updateConnectionStatus(false)

        findViewById<View>(R.id.micButton).setOnClickListener {
            startVoiceRecognition()
        }
        findViewById<View>(R.id.logObservationButton).setOnClickListener {
            showObservationDialog()
        }
        findViewById<View>(R.id.generateReportButton).setOnClickListener {
            generateReport()
        }

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.ENGLISH
            }
        }

        registerReceiver(
            keyReceiver,
            IntentFilter().apply {
                KeyType.entries.forEach { addAction(it.action) }
                priority = 100
            }
        )

        // TODO: Replace with Rokid CXR-M SDK connection at app startup
        setupCxrBridge()
    }

    override fun onDestroy() {
        unregisterReceiver(keyReceiver)
        recorder?.release()
        recorder = null
        if (::tts.isInitialized) {
            tts.shutdown()
        }
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> {
                startVoiceRecognition()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun updateConnectionStatus(connected: Boolean) {
        val color = if (connected) "#00FF41" else "#FF0000"
        headerConnectionDot.setBackgroundColor(Color.parseColor(color))
    }

    private fun updateStepDisplay() {
        currentStepText.text = sopSteps.getOrNull(currentStep) ?: "(no step)"
    }

    private fun advanceStep(delta: Int) {
        currentStep = (currentStep + delta).coerceIn(0, sopSteps.size - 1)
        updateStepDisplay()
    }

    private fun showPassOverlay() {
        // TODO: Replace with Rokid AR overlay API above overlay display code
        stepPassed[currentStep] = true
        showOverlay(
            background = "#CC00FF41",
            text = "✓ VERIFIED",
            durationMs = 2000,
        )
        if (currentStep == sopSteps.lastIndex) {
            tts.speak("All steps verified. Audit report is ready.", TextToSpeech.QUEUE_FLUSH, null, null)
            handler.postDelayed({ showCompletionReport() }, 2000)
        } else {
            tts.speak("Step verified. Proceeding to next step.", TextToSpeech.QUEUE_FLUSH, null, null)
            advanceStep(1)
        }
    }

    private fun showFailOverlay() {
        // TODO: Replace with Rokid AR overlay API above overlay display code
        showOverlay(
            background = "#CCFF0000",
            text = "⚠ WARNING — ISSUE DETECTED",
            durationMs = 3000,
        )
        tts.speak("Warning. Issue detected. Please review this step.", TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun showIssueFlaggedOverlay() {
        // TODO: Replace with Rokid AR overlay API above overlay display code
        showOverlay(
            background = "#CCFF0000",
            text = "ISSUE FLAGGED",
            durationMs = 3000,
        )
        tts.speak("Warning. Issue detected. Please review this step.", TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun showOverlay(background: String, text: String, durationMs: Long) {
        overlayTimer?.let { handler.removeCallbacks(it) }
        overlay.setBackgroundColor(Color.parseColor(background))
        overlayText.text = text
        overlay.visibility = View.VISIBLE
        val hide = Runnable { overlay.visibility = View.GONE }
        overlayTimer = hide
        handler.postDelayed(hide, durationMs)
    }

    private fun showCompletionReport() {
        overlayTimer?.let { handler.removeCallbacks(it) }
        overlayTimer = null
        overlay.setBackgroundColor(Color.BLACK)
        overlayText.textSize = 24f
        overlayText.text = buildString {
            appendLine("AUDIT C REPORT")
            appendLine()
            stepPassed.forEachIndexed { index, passed ->
                appendLine("STEP ${index + 1}: ${if (passed) "PASS" else "PENDING"}")
            }
            appendLine()
            append("ALL STEPS VERIFIED")
        }
        overlay.visibility = View.VISIBLE
    }

    private fun verifyStep(spokenText: String) {
        val body = """{"spoken_text":${JSONObject.quote(spokenText)}}""".toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$BASE_URL/verify-step")
            .post(body)
            .build()

        Thread {
            try {
                client.newCall(request).execute().use { response ->
                    val json = JSONObject(response.body?.string().orEmpty())
                    val result = json.optString("result", "fail")
                    val stepName = json.optString("step_name", sopSteps.getOrNull(currentStep).orEmpty())
                    runOnUiThread {
                        if (result == "pass") {
                            showPassOverlay()
                            sendResultToPhone(result, stepName)
                        } else {
                            showFailOverlay()
                            sendResultToPhone(result, stepName)
                        }
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    showFailOverlay()
                }
            }
        }.start()
    }

    private fun handleSpokenText(spokenText: String) {
        if (spokenText.lowercase(Locale.ENGLISH).contains("generate report")) {
            generateReport()
        } else {
            verifyStep(spokenText)
        }
    }

    private fun showObservationDialog() {
        val input = EditText(this).apply {
            hint = "Enter your observation"
            setTextColor(Color.parseColor("#66FF88"))
            setHintTextColor(Color.parseColor("#447755"))
        }

        AlertDialog.Builder(this)
            .setTitle("Log Observation")
            .setView(input)
            .setPositiveButton("Submit") { _, _ ->
                val text = input.text?.toString()?.trim().orEmpty()
                if (text.isNotBlank()) {
                    logObservation(text)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun logObservation(text: String) {
        val body = """{"spoken_text":${JSONObject.quote(text)},"step_number":${currentStep + 1}}""".toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$BASE_URL/log-observation")
            .post(body)
            .build()

        Thread {
            try {
                client.newCall(request).execute().use { response ->
                    val json = JSONObject(response.body?.string().orEmpty())
                    val entry = json.optJSONObject("entry")
                    val flagged = entry?.optBoolean("flagged", false) ?: false
                    runOnUiThread {
                        if (flagged) {
                            showIssueFlaggedOverlay()
                        } else {
                            tts.speak("Observation logged.", TextToSpeech.QUEUE_FLUSH, null, null)
                        }
                    }
                }
            } catch (_: Exception) {
                runOnUiThread { showFailOverlay() }
            }
        }.start()
    }

    private fun generateReport() {
        val request = Request.Builder()
            .url("$BASE_URL/generate-report")
            .post("{}".toRequestBody(jsonMediaType))
            .build()

        Thread {
            try {
                client.newCall(request).execute().use { response ->
                    val json = JSONObject(response.body?.string().orEmpty())
                    val reportPath = json.optString("report_path", "pathguard_report.pdf")
                    runOnUiThread {
                        tts.speak("Audit report generated successfully.", TextToSpeech.QUEUE_FLUSH, null, null)
                        AlertDialog.Builder(this)
                            .setTitle("Report Generated")
                            .setMessage("Report path:\n$reportPath")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            } catch (_: Exception) {
                runOnUiThread { showFailOverlay() }
            }
        }.start()
    }

    private fun setupCxrBridge() {
        try {
            cxrBridge.setStatusListener(statusListener)
            cxrBridge.subscribe("audit_c_commands", msgCallback)
        } catch (_: Exception) {
            updateConnectionStatus(false)
        }
    }

    private val cxrBridge = com.rokid.cxr.CXRServiceBridge()

    private val statusListener = object : com.rokid.cxr.CXRServiceBridge.StatusListener {
        override fun onConnected(name: String, type: Int) {
            runOnUiThread { updateConnectionStatus(true) }
        }

        override fun onDisconnected() {
            runOnUiThread { updateConnectionStatus(false) }
        }

        override fun onARTCStatus(health: Float, reset: Boolean) {
        }
    }

    private val msgCallback = object : com.rokid.cxr.CXRServiceBridge.MsgCallback {
        override fun onReceive(name: String, args: com.rokid.cxr.Caps, value: ByteArray?) {
        }
    }

    fun sendResultToPhone(result: String, stepName: String) {
        try {
            val caps = com.rokid.cxr.Caps().apply {
                write(result)
                write(stepName)
            }
            cxrBridge.sendMessage("audit_c_result", caps)
        } catch (_: Exception) {
        }
    }

    enum class KeyType(val action: String) {
        CLICK("com.android.action.ACTION_SPRITE_BUTTON_CLICK"),
        LONG_PRESS("com.android.action.ACTION_SPRITE_BUTTON_LONG_PRESS"),
        TWO_FINGER_SINGLE_TAP("com.android.action.ACTION_TWO_FINGER_SINGLE_TAP"),
        TWO_FINGER_SWIPE_FORWARD("com.android.action.ACTION_TWO_FINGER_SWIPE_FORWARD"),
        TWO_FINGER_SWIPE_BACK("com.android.action.ACTION_TWO_FINGER_SWIPE_BACK"),
        AI_START("com.android.action.ACTION_AI_START"),
    }

    class KeyReceiver : BroadcastReceiver() {
        var listener: ((KeyType) -> Unit)? = null
        override fun onReceive(context: Context?, intent: Intent?) {
            val type = KeyType.entries.firstOrNull { it.action == intent?.action } ?: return
            listener?.invoke(type)
            abortBroadcast()
        }
    }

    companion object {
        private const val BASE_URL = "http://127.0.0.1:8000"
        private const val RECORDING_DURATION_MS = 4000L
    }
}
