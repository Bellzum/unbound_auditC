package com.auditc.glasses

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.view.WindowInsets
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient()

    private val sopSteps = listOf(
        "Sample Collection",
        "RNA Extraction",
        "Reverse Transcription",
        "PCR Master Mix Preparation",
        "Thermal Cycling",
        "Detection and Analysis",
    )

    private var currentStepIndex = 0

    private lateinit var stepText: TextView
    private lateinit var statusText: TextView
    private lateinit var overlay: View
    private lateinit var overlayTitle: TextView
    private lateinit var overlaySubtitle: TextView

    private var speechRecognizer: SpeechRecognizer? = null

    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        hideSystemUi()

        // TODO: Replace with Rokid CXR-M SDK connection at app startup

        stepText = findViewById(R.id.stepText)
        statusText = findViewById(R.id.statusText)
        overlay = findViewById(R.id.overlay)
        overlayTitle = findViewById(R.id.overlayTitle)
        overlaySubtitle = findViewById(R.id.overlaySubtitle)

        updateStepUi()

        findViewById<View>(R.id.micButton).setOnClickListener {
            if (!ensureAudioPermission()) {
                return@setOnClickListener
            }
            startSpeechRecognitionForVerify()
        }

        findViewById<View>(R.id.logObservationButton).setOnClickListener {
            showLogObservationDialog()
        }

        findViewById<View>(R.id.generateReportButton).setOnClickListener {
            generateReport()
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }

    private fun hideSystemUi() {
        window.decorView.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(0, 0, 0, 0)
            WindowInsets.CONSUMED
        }
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
    }

    private fun ensureAudioPermission(): Boolean {
        val alreadyGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!alreadyGranted) {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
            return false
        }

        return true
    }

    private fun updateStepUi() {
        val stepNumber = currentStepIndex + 1
        val stepName = sopSteps.getOrNull(currentStepIndex) ?: "Step"
        stepText.text = "STEP $stepNumber — $stepName"
    }

    private fun showOverlaySuccess(title: String, subtitle: String, durationMs: Long, onDismiss: (() -> Unit)? = null) {
        // TODO: Replace with Rokid AR overlay API above overlay display code
        overlay.setBackgroundColor(0xAA00FF66.toInt())
        overlayTitle.text = title
        overlaySubtitle.text = subtitle
        overlay.visibility = View.VISIBLE
        handler.postDelayed({
            overlay.visibility = View.GONE
            onDismiss?.invoke()
        }, durationMs)
    }

    private fun showOverlayDanger(title: String, subtitle: String, durationMs: Long, onDismiss: (() -> Unit)? = null) {
        // TODO: Replace with Rokid AR overlay API above overlay display code
        overlay.setBackgroundColor(0xAAFF0000.toInt())
        overlayTitle.text = title
        overlaySubtitle.text = subtitle
        overlay.visibility = View.VISIBLE
        handler.postDelayed({
            overlay.visibility = View.GONE
            onDismiss?.invoke()
        }, durationMs)
    }

    private fun startSpeechRecognitionForVerify() {
        // TODO: Replace with Rokid Speech SDK above SpeechRecognizer code
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_LONG).show()
            return
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        }

        statusText.text = "Listening..."
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                statusText.text = "Speech error: $error"
            }

            override fun onResults(results: Bundle?) {
                val matches = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.filterNotNull()
                    ?: emptyList()
                val text = matches.firstOrNull()?.trim().orEmpty()
                if (text.isBlank()) {
                    statusText.text = "No speech recognized"
                    return
                }
                statusText.text = text
                verifyStep(text)
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    private fun verifyStep(spokenText: String) {
        val json = JSONObject().apply {
            put("spoken_text", spokenText)
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$BASE_URL/verify-step")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread {
                    showOverlayDanger("⚠ WARNING", "NETWORK ERROR", 3000)
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val responseBody = response.body?.string().orEmpty()
                val data = try {
                    JSONObject(responseBody)
                } catch (_: Exception) {
                    JSONObject()
                }

                val result = data.optString("result").lowercase()

                runOnUiThread {
                    if (result == "pass") {
                        val stepName = sopSteps.getOrNull(currentStepIndex).orEmpty()
                        showOverlaySuccess("VERIFIED ✓", stepName, 2000) {
                            if (currentStepIndex < sopSteps.size - 1) {
                                currentStepIndex += 1
                                updateStepUi()
                            }
                        }
                    } else {
                        showOverlayDanger("⚠ WARNING", "ISSUE DETECTED", 3000)
                    }
                }
            }
        })
    }

    private fun showLogObservationDialog() {
        val input = layoutInflater.inflate(R.layout.dialog_observation, null)
        val editText = input.findViewById<android.widget.EditText>(R.id.observationInput)

        AlertDialog.Builder(this)
            .setTitle("Log Observation")
            .setView(input)
            .setPositiveButton("Submit") { _, _ ->
                val text = editText.text?.toString()?.trim().orEmpty()
                if (text.isBlank()) {
                    Toast.makeText(this, "Observation is empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                postObservation(text)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun postObservation(observation: String) {
        val json = JSONObject().apply {
            put("spoken_text", observation)
            put("step_number", currentStepIndex + 1)
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$BASE_URL/log-observation")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread {
                    showOverlayDanger("⚠ WARNING", "LOG FAILED", 3000)
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val responseBody = response.body?.string().orEmpty()
                val data = try {
                    JSONObject(responseBody)
                } catch (_: Exception) {
                    JSONObject()
                }

                val entry = data.optJSONObject("entry")
                val flagged = entry?.optBoolean("flagged", false) ?: false

                runOnUiThread {
                    if (flagged) {
                        showOverlayDanger("ISSUE FLAGGED", "Observation indicates a problem", 3000)
                    } else {
                        Toast.makeText(this@MainActivity, "Observation logged", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun generateReport() {
        val json = JSONObject().apply {
            put("specimen_id", "DEMO")
            put("technician_name", "Audit C Glasses")
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$BASE_URL/generate-report")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread {
                    showOverlayDanger("⚠ WARNING", "REPORT FAILED", 3000)
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val responseBody = response.body?.string().orEmpty()
                val data = try {
                    JSONObject(responseBody)
                } catch (_: Exception) {
                    JSONObject()
                }

                val reportPath = data.optString("report_path", "pathguard_report.pdf")

                runOnUiThread {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Report Generated")
                        .setMessage("Report path:\n$reportPath")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        })
    }

    companion object {
        private const val BASE_URL = "http://192.168.1.11:8000"
    }
}
