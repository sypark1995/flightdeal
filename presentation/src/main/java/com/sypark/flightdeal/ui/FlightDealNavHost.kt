package com.sypark.flightdeal.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sypark.flightdeal.calendar.CalendarScreen
import com.sypark.flightdeal.feed.DealFeedScreen
import com.sypark.flightdeal.tracking.TrackingScreen
import com.sypark.flightdeal.ui.theme.FlightDealTheme
import kotlinx.coroutines.flow.MutableStateFlow

private enum class Tab(val route: String, val label: String) {
    Deals("deals", "특가"),
    Tracking("tracking", "추적"),
    // "검색"이던 라벨을 "달력"으로 바꾼다. 이 화면은 자유 검색이 아니라 날짜별
    // 최저가를 격자로 보여주는 캘린더다 — 이름이 하는 일과 맞아야 한다.
    Search("search", "달력"),
    Profile("profile", "내정보"),
}

@Composable
fun FlightDealNavHost(openTracking: MutableStateFlow<Boolean> = MutableStateFlow(false)) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val shouldOpenTracking by openTracking.collectAsStateWithLifecycle()
    LaunchedEffect(shouldOpenTracking) {
        if (shouldOpenTracking) {
            navController.navigate(Tab.Tracking.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            // 한 번만 이동한다. 소비하지 않으면 회전할 때마다 탭이 되돌아간다.
            openTracking.value = false
        }
    }

    Scaffold(
        containerColor = FlightDealTheme.colors.background,
        bottomBar = {
            NavigationBar(containerColor = FlightDealTheme.colors.background) {
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
                            selectedTextColor = FlightDealTheme.colors.indigo,
                            unselectedTextColor = FlightDealTheme.colors.textSecondary,
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
            composable(Tab.Deals.route) {
                DealFeedScreen(
                    onSearch = {
                        navController.navigate(Tab.Search.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(Tab.Tracking.route) { TrackingScreen() }
            composable(Tab.Search.route) { CalendarScreen() }
            composable(Tab.Profile.route) { PlaceholderScreen() }
        }
    }
}
