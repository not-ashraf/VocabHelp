package com.example.vocabhelp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.vocabhelp.ui.nav.VocabNavHost
import com.example.vocabhelp.ui.theme.VocabHelpTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : ComponentActivity() {

    private val openQuizSignal = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeOpenQuizExtra(intent)
        setContent {
            VocabHelpTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val openQuiz by openQuizSignal.asStateFlow().collectAsStateWithLifecycle()
                    VocabNavHost(
                        openQuizSignal = openQuiz,
                        onOpenQuizConsumed = { openQuizSignal.value = false },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeOpenQuizExtra(intent)
    }

    private fun consumeOpenQuizExtra(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_QUIZ, false) == true) {
            openQuizSignal.value = true
        }
    }

    companion object {
        const val EXTRA_OPEN_QUIZ = "com.example.vocabhelp.OPEN_QUIZ"
    }
}
