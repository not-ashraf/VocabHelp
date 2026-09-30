package com.example.vocabhelp

import android.app.Application
import android.content.Context
import com.example.vocabhelp.data.db.VocabDatabase
import com.example.vocabhelp.data.db.WordDao
import com.example.vocabhelp.data.prefs.SettingsRepository
import com.example.vocabhelp.data.remote.DatamuseApi
import com.example.vocabhelp.data.remote.WiktionaryApi
import com.example.vocabhelp.data.repo.DictionaryLookup
import com.example.vocabhelp.data.repo.WordRepository
import com.example.vocabhelp.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class VocabHelpApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        val scheduler = WorkScheduler(container)
        scheduler.enqueuePendingLookup()

        appScope.launch {
            val settings = container.settingsRepository.settings.first()
            if (settings.enabled) {
                scheduler.scheduleDailyReminder(settings.hour, settings.minute)
            }
        }
    }
}

class AppContainer(context: Context) {
    val context: Context = context.applicationContext
    private val appContext: Context get() = context

    private val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val okHttp: OkHttpClient = OkHttpClient.Builder()
        // Wikimedia's API policy asks clients to identify themselves.
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "VocabHelp/${BuildConfig.VERSION_NAME} (personal Android app)")
                    .build(),
            )
        }
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BASIC
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            },
        )
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val dictionaryLookup = DictionaryLookup(
        wiktionary = retrofit(WiktionaryApi.BASE_URL).create(WiktionaryApi::class.java),
        datamuse = retrofit(DatamuseApi.BASE_URL).create(DatamuseApi::class.java),
    )

    val database: VocabDatabase = VocabDatabase.get(appContext)
    val wordDao: WordDao = database.wordDao()

    val wordRepository: WordRepository = WordRepository(wordDao, dictionaryLookup)
    val settingsRepository: SettingsRepository = SettingsRepository(appContext)
    val workScheduler: WorkScheduler by lazy { WorkScheduler(appContext) }
}

fun Context.appContainer(): AppContainer =
    (applicationContext as VocabHelpApp).container
