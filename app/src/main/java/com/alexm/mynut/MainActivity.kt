package com.alexm.mynut

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.alexm.mynut.ui.calendar.CalendarScreen
import com.alexm.mynut.ui.form.FormScreen
import com.alexm.mynut.ui.form.FormViewModel
import com.alexm.mynut.ui.home.HomeScreen
import com.alexm.mynut.ui.home.HomeViewModel
import com.alexm.mynut.ui.theme.MyNutTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyNutTheme {
                val navController = rememberNavController()
                val homeViewModel: HomeViewModel = viewModel()

                NavHost(navController = navController, startDestination = "home") {

                    composable("home") {
                        val savedState = it.savedStateHandle
                        val returnedDate by savedState.getStateFlow("date", "").collectAsStateWithLifecycle()

                        LaunchedEffect(returnedDate) {
                            if (returnedDate.isNotBlank()) {
                                homeViewModel.onDateSelected(returnedDate)
                                savedState["date"] = ""
                            }
                        }

                        HomeScreen(
                            viewModel = homeViewModel,
                            onNavigateToCalendar = { navController.navigate("calendar") },
                            onNavigateToForm = { itemId ->
                                if (itemId != null) {
                                    navController.navigate("form?itemId=$itemId")
                                } else {
                                    navController.navigate("form")
                                }
                            }
                        )
                    }

                    composable("calendar") {
                        val currentDate by homeViewModel.selectedDate.collectAsStateWithLifecycle()

                        CalendarScreen(
                            currentDate = currentDate,
                            onDateSelected = { date ->
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set("date", date)
                                navController.popBackStack()
                            }
                        )
                    }

                    composable(
                        route = "form?itemId={itemId}",
                        arguments = listOf(
                            navArgument("itemId") {
                                type = NavType.LongType
                                defaultValue = 0L
                            }
                        )
                    ) {
                        val formViewModel: FormViewModel = viewModel()

                        FormScreen(
                            viewModel = formViewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}