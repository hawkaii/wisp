package app.wispnotes.ui

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wispnotes.MainViewModel
import app.wispnotes.ui.theme.WispColors

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Review("Review", Icons.Filled.Refresh),
    Notes("Notes", Icons.Filled.Menu),
}

@Composable
fun WispApp(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(WispColors.Void).statusBarsPadding()) {
        when {
            !state.loaded -> Unit
            state.onboarded && state.vaultOk -> MainScaffold()
            else -> OnboardingScreen(vm, state)
        }
    }
}

@Composable
private fun MainScaffold() {
    var index by rememberSaveable { mutableIntStateOf(0) }
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
                Tab.Home -> HomeScreen()
                Tab.Review -> ReviewScreen()
                Tab.Notes -> NotesScreen()
            }
        }
        BottomBar(index) { index = it }
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
                    Modifier.weight(1f).fillMaxSize().clickable(role = Role.Tab) { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val pill = RoundedCornerShape(12.dp)
                    Box(
                        Modifier
                            .then(if (on) Modifier.background(WispColors.Kiro, pill).border(2.dp, WispColors.Void, pill) else Modifier)
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                    ) { Icon(tab.icon, null, tint = tint) }
                    Text(tab.label, style = MaterialTheme.typography.labelMedium, color = tint)
                }
            }
        }
    }
}
