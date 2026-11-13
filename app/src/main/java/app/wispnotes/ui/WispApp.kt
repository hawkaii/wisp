package app.wispnotes.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import app.wispnotes.AppState
import app.wispnotes.review.ReviewWorker
import app.wispnotes.ui.theme.LocalReduceMotion
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import app.wispnotes.R
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wispnotes.MainViewModel
import app.wispnotes.ui.theme.WispColors

private enum class Tab(val label: String, val icon: Int) {
    Home("Home", R.drawable.stk_cauldron),
    Review("Review", R.drawable.stk_calendar),
    Notes("Notes", R.drawable.stk_house),
}

@Composable
fun WispApp(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(WispColors.Void).statusBarsPadding()) {
        when {
            !state.loaded -> Unit
            state.onboarded && state.vaultOk -> MainScaffold(vm, state)
            else -> OnboardingScreen(vm, state)
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MainScaffold(vm: MainViewModel, state: AppState) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    // "" = tabs, "settings", or "note:<file name>"
    var route by rememberSaveable { mutableStateOf("") }
    val ctx = LocalContext.current
    val reduce = LocalReduceMotion.current
    LaunchedEffect(Unit) { ReviewWorker.schedule(ctx) }

    SharedTransitionLayout {
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                if (reduce) EnterTransition.None togetherWith ExitTransition.None
                else fadeIn(tween(220)) togetherWith fadeOut(tween(220))
            },
            label = "route",
        ) { r ->
            val shared = Shared(this@SharedTransitionLayout, this)
            when {
                r.startsWith("note:") -> NoteScreen(r.removePrefix("note:"), shared, onBack = { route = "" })
                r == "whatnow" -> WhatNowScreen(onBack = { route = "" })
                r == "settings" -> SettingsScreen(state.vaultPath, onChangeVault = { vm.pickVault(it); route = "" }, onBack = { route = "" })
                else -> Tabs(index, { index = it }, shared, onOpen = { route = "note:$it" }, onSettings = { route = "settings" }, onWhatNow = { route = "whatnow" })
            }
        }
    }
}

@Composable
private fun Tabs(index: Int, onIndex: (Int) -> Unit, shared: Shared, onOpen: (String) -> Unit, onSettings: () -> Unit, onWhatNow: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = index,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally { it / 4 * dir } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it / 4 * dir } + fadeOut())
            },
            label = "tab",
        ) { i ->
            when (Tab.entries[i]) {
                Tab.Home -> HomeScreen(shared, onOpen, onSettings, onWhatNow)
                Tab.Review -> ReviewScreen()
                Tab.Notes -> NotesScreen(onOpen)
            }
        }
        BottomBar(index, onIndex)
    }
}

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.background(WispColors.Ink).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(WispColors.Void))
        Row(Modifier.height(74.dp)) {
            Tab.entries.forEachIndexed { i, tab ->
                val on = i == selected
                val tint = if (on) Color.White else WispColors.Lavender
                Column(
                    Modifier.weight(1f).fillMaxSize().pressable(role = Role.Tab) { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val pill = RoundedCornerShape(12.dp)
                    Box(
                        Modifier
                            .then(if (on) Modifier.background(WispColors.Kiro, pill).border(2.dp, WispColors.Void, pill) else Modifier)
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                    ) { Icon(painterResource(tab.icon), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.height(30.dp)) }
                    Text(tab.label, style = MaterialTheme.typography.labelMedium, color = tint)
                }
            }
        }
    }
}
