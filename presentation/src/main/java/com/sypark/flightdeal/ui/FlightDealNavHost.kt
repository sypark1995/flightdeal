package com.sypark.flightdeal.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sypark.flightdeal.feed.DealFeedScreen
import com.sypark.flightdeal.tracking.TrackingScreen
import com.sypark.flightdeal.ui.theme.Background
import com.sypark.flightdeal.ui.theme.Indigo
import com.sypark.flightdeal.ui.theme.TextSecondary

private enum class Tab(val route: String, val label: String) {
    Deals("deals", "특가"),
    Tracking("tracking", "추적"),
    Search("search", "검색"),
    Profile("profile", "내정보"),
}

@Composable
fun FlightDealNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        containerColor = Background,
        bottomBar = {
            NavigationBar(containerColor = Background) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        // 아이콘이 없으므로 선택 표시는 라벨 색으로만 한다.
                        // 아이콘 없이 기본 인디케이터를 두면 라벨과 어긋난 알약이 떠 버린다.
                        icon = {},
                        label = { Text(text = tab.label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedTextColor = Indigo,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Deals.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Tab.Deals.route) { DealFeedScreen() }
            composable(Tab.Tracking.route) { TrackingScreen() }
            composable(Tab.Search.route) { PlaceholderScreen() }
            composable(Tab.Profile.route) { PlaceholderScreen() }
        }
    }
}
