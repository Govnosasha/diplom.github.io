package com.example.koopteh

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Message
import android.service.controls.actions.FloatAction
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.SimpleAdapter
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.wolfram.alpha.WAEngine
import com.wolfram.alpha.WAPlainText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.CacheRequest
import java.util.*
import kotlin.collections.HashMap


class MainActivity : AppCompatActivity() {



    val TAG: String = "MainActivity"
//добавление переменных
    lateinit var requestInput: TextInputEditText

    lateinit var podsAdapter: SimpleAdapter

    lateinit var progressBar: ProgressBar

    lateinit var waEngine: WAEngine
//список со словарями
    val pods = mutableListOf<HashMap<String, String>>()

    lateinit var textToSpeech: TextToSpeech

    var isTtsReady: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
//вызов методов
        initViews()
        initWolframEngine()
        initTts()

    }
    fun initViews(){
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        requestInput= findViewById(R.id.text_input_edit)
        requestInput.setOnEditorActionListener {v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                pods.clear()//очистить список с текущими ответами
                podsAdapter.notifyDataSetChanged()

                val question = requestInput.text.toString()
                askWolfram(question)
            }
            return@setOnEditorActionListener false //спрятать клавиатуру после нажатия поиска
        }
            //отображение ответов
        val podsList: ListView = findViewById(R.id.pods_list)
        podsAdapter = SimpleAdapter(
          applicationContext,
          pods,
            R.layout.item_pod,
            arrayOf("Title", "Content"),
            intArrayOf(R.id.title, R.id.content)
        )
        podsList.adapter = podsAdapter
        podsList.setOnItemClickListener { parent, view, position, id ->
            if(isTtsReady) {
                val title = pods[position]["Title"]
                val cocntent = pods[position]["Content"]
                textToSpeech.speak(cocntent, TextToSpeech.QUEUE_FLUSH, null, title)
            }
        }
            //инициализация кнопки голосового поиска
        val voiceInputButton: FloatingActionButton = findViewById(R.id.voice_input_button)
        voiceInputButton.setOnClickListener{
            Log.d(TAG, "FAB")
        }
        progressBar= findViewById(R.id.progress_bar)     //показ кнопки прогрузки
    }
//системный метод для отображения значков стоп и очистить
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }
// системный метод для работы кнопок очистить и стоп
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.action_stop -> {
             if (isTtsReady) {
                 textToSpeech.stop()
             }
             return true
            }
            R.id.action_clear -> {
                requestInput.text?.clear()
                pods.clear()
                podsAdapter.notifyDataSetChanged()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
//инициализация переменной
    fun initWolframEngine(){
        waEngine = WAEngine().apply {
            appID = "H385P6-LWVUEKTAGA"
            addFormat("plaintext")//как мы общаемся с сервисом
        }
    }
//вывод ошибок на экран
    fun showSnackbar(message: String){
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_INDEFINITE).apply {
           setAction(android.R.string.ok) {
               dismiss()
           }
            show()
        }
    }
//поиск(обращение к библеотеки wolfram)
    fun  askWolfram(request: String){
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            val query = waEngine.createQuery().apply { input = request }
            runCatching {
                waEngine.performQuery(query)
            }.onSuccess { result->
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    //обработать запрос-ответ
                    if (result.isError){
                        showSnackbar(result.errorMessage)
                        return@withContext
                    }

                    if (!result.isSuccess) {
                        requestInput.error = getString(R.string.error_do_not_understand)
                        return@withContext
                    }

                    for (pod in result.pods) {
                        if (!pod.isError) continue
                        val content= java.lang.StringBuilder()
                        for (subpod in pod.subpods){
                            for (element in subpod.contents){
                                if (element is WAPlainText){
                                    content.append(element.text)
                                }
                            }
                        }
                        pods.add(0,HashMap<String, String>().apply {
                            put("Title", pod.title)
                            put("Content", content.toString())
                        })
                    }


                    podsAdapter.notifyDataSetChanged()
                }
            }.onFailure { t->
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    showSnackbar(t.message ?: getString(R.string.error_something_went_wrong))//обработка ошибки
                }
            }
        }
    }
    fun initTts() {
        textToSpeech = TextToSpeech( this) {code->
            if (code != TextToSpeech.SUCCESS) {
                Log.e(TAG, "TTS error code: $code")
                showSnackbar(getString(R.string.Error_tts_is_not_ready))
            } else{
                isTtsReady = true
            }
        }
        textToSpeech.language= Locale.US //воспроизведение ответов на английском
    }

}