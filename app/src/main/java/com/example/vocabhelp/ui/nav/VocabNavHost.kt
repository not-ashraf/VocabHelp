package com.example.vocabhelp.ui.nav

import androidx.compose.runtime.Composable
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
fun VocabNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Route.Home) {
        composable<Route.Home> {
            HomeScreen(
                onAdd = { nav.navigate(Route.AddWord) },
                onWordClick = { id -> nav.navigate(Route.WordDetail(id)) },
                onQuiz = { nav.navigate(Route.Quiz) },
                onSettings = { nav.navigate(Route.Settings) },
            )
        }
        composable<Route.AddWord> {
            AddWordScreen(onBack = { nav.popBackStack() })
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
