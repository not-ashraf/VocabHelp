package com.example.vocabhelp.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.vocabhelp.ui.add.AddWordScreen
import com.example.vocabhelp.ui.detail.WordDetailScreen
import com.example.vocabhelp.ui.home.HomeScreen
import com.example.vocabhelp.ui.quiz.QuizScreen
import com.example.vocabhelp.ui.settings.SettingsScreen

@Composable
fun VocabNavHost(
    openQuizSignal: Boolean = false,
    onOpenQuizConsumed: () -> Unit = {},
) {
    val nav = rememberNavController()

    LaunchedEffect(openQuizSignal) {
        if (openQuizSignal) {
            nav.navigate(Route.Quiz) {
                // If we're already on Quiz (warm tap), don't stack it.
                launchSingleTop = true
            }
            onOpenQuizConsumed()
        }
    }

    NavHost(navController = nav, startDestination = Route.Home) {
        composable<Route.Home> {
            HomeScreen(
                onAdd = { word -> nav.navigate(Route.AddWord(word)) },
                onWordClick = { id -> nav.navigate(Route.WordDetail(id)) },
                onQuiz = { nav.navigate(Route.Quiz) },
                onSettings = { nav.navigate(Route.Settings) },
            )
        }
        composable<Route.AddWord> { backStackEntry ->
            val args = backStackEntry.toRoute<Route.AddWord>()
            AddWordScreen(
                initialWord = args.word,
                onBack = { nav.popBackStack() },
                onOpenSaved = { id ->
                    nav.navigate(Route.WordDetail(id)) {
                        popUpTo<Route.AddWord> { inclusive = true }
                    }
                },
            )
        }
        composable<Route.WordDetail> { backStackEntry ->
            val args = backStackEntry.toRoute<Route.WordDetail>()
            WordDetailScreen(id = args.id, onBack = { nav.popBackStack() })
        }
        composable<Route.Quiz> {
            QuizScreen(onBack = { nav.popBackStack() })
        }
        composable<Route.Settings> {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
