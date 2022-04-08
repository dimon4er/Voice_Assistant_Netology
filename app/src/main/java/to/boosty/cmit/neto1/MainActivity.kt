package to.boosty.cmit.neto1

import android.app.ProgressDialog.show
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.SimpleAdapter
import androidx.core.view.accessibility.AccessibilityEventCompat.setAction
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.wolfram.alpha.WAEngine
import com.wolfram.alpha.WAPlainText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.StringBuilder
import java.util.*
import kotlin.collections.HashMap


class MainActivity : AppCompatActivity() {

    lateinit var requestInput: TextInputEditText
    lateinit var potsAdapter: SimpleAdapter
    lateinit var progressBar: ProgressBar

    lateinit var waEngine: WAEngine

    lateinit var textToSpeech: TextToSpeech

    var isTtsReady = false

    val VOICE_RECOGNITION_REQUEST_CODE: Int = 777

    val pots = mutableListOf<HashMap<String, String>> ()

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_main)

            initViews()
            initWolframEngine()
            initTts()

        }

        fun initViews() {
            val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
            setSupportActionBar(toolbar)

            requestInput = findViewById(R.id.text_input_edit)
            requestInput.setOnEditorActionListener { v, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    pots.clear()
                    potsAdapter.notifyDataSetChanged()

                    val question = requestInput.text.toString()
                    askWolfram(question)
                }

                return@setOnEditorActionListener false

            }
            Log.d("TAG", "$requestInput")
            val potsList: ListView = findViewById(R.id.pots_list)
            Log.d("TAG", "$potsList")
            potsAdapter = SimpleAdapter(
                applicationContext,
                pots,
                R.layout.item_pot,
                arrayOf("Title","Content"),
                intArrayOf(R.id.title2, R.id.content2)
            )
            potsList.adapter = potsAdapter
            potsList.setOnItemClickListener { parent, view, position, id ->
                if (isTtsReady) {
                    val title = pots[position]["Title"]
                    val content = pots[position]["Content"]
                    textToSpeech.speak(content, TextToSpeech.QUEUE_FLUSH, null, title)
                }
            }

            val voiceInputButton: FloatingActionButton = findViewById(R.id.voice_input_button)
            voiceInputButton.setOnClickListener{
                pots.clear()
                potsAdapter.notifyDataSetChanged()

                if (isTtsReady) {
                    textToSpeech.stop()
                }

                showVoiceInputDialog()
            }

            progressBar = findViewById(R.id.progress_bar)
        }

        override fun onCreateOptionsMenu(menu: Menu?): Boolean {
            menuInflater.inflate(R.menu.toolbar_menu, menu)
            return super.onCreateOptionsMenu(menu)
        }

        override fun onOptionsItemSelected(item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.action_clear -> {
                    requestInput.text?.clear()
                    pots.clear()
                    potsAdapter.notifyDataSetChanged()
                    return true
                }
                R.id.action_stop -> {
                    if (isTtsReady) {
                        textToSpeech.stop()
                    }
                    return true
                }
            }
            return super.onOptionsItemSelected(item)
        }

    fun initWolframEngine() {
        waEngine = WAEngine().apply{
            appID = "WAW45V-PE9VXYWQPU"
            addFormat("plaintext")
        }
    }

    fun showSnackbar (message: String) {
        Snackbar.make(findViewById(android.R.id.content), message,Snackbar.LENGTH_INDEFINITE).apply {
            setAction(android.R.string.ok) {
                dismiss()
            }
            show()
        }
    }

    fun askWolfram (request: String) {
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            val query = waEngine.createQuery().apply { input = request }
            kotlin.runCatching {
                waEngine.performQuery(query)
            }.onSuccess { result ->
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (result.isError) {
                        showSnackbar(result.errorMessage)
                        return@withContext
                    }

                    if(!result.isSuccess) {
                        requestInput.error = getString(R.string.error_do_not_understand)
                        return@withContext
                    }

                    for (pod in result.pods) {
                        if (pod.isError) continue
                        val content = StringBuilder()
                        for (subpod in pod.subpods) {
                            for (element in subpod.contents) {
                                if (element is WAPlainText) {
                                    content.append(element.text)
                                }
                            }
                        }
                        pots.add(0, HashMap<String, String>().apply {
                            put("Title", pod.title)
                            put("Content", content.toString())
                        })
                    }

                    potsAdapter.notifyDataSetChanged()
                }
            }.onFailure { t ->
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    showSnackbar(t.message ?: getString(R.string.error_something_went_wrong))
                }
            }
        }

    }

    fun initTts () {
        textToSpeech = TextToSpeech(this) {code ->
            if (code != TextToSpeech.SUCCESS) {
                Log.e("TAG", "TTS error code: $code")
                showSnackbar(getString(R.string.error_tts_is_not_ready))
            } else {
                isTtsReady = true
            }
        }
        textToSpeech.language = Locale.US
    }

    fun showVoiceInputDialog () {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.request_hint))
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
        }

        kotlin.runCatching {
            startActivityForResult(intent, VOICE_RECOGNITION_REQUEST_CODE)
        }.onFailure { t ->
            showSnackbar(t.message ?: getString(R.string.error_voice_recognition_unavailable))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == VOICE_RECOGNITION_REQUEST_CODE && resultCode == RESULT_OK) {
            data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)?.let { question ->
                requestInput.setText(question)
                askWolfram(question)
            }
        }
    }
}


