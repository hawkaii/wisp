package app.wispnotes.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wispnotes.AppState
import app.wispnotes.MainViewModel
import app.wispnotes.ui.theme.Outline
import app.wispnotes.ui.theme.WispColors
import androidx.compose.foundation.border
import androidx.compose.runtime.collectAsState

/** First launch only: welcome, vault, done. Also reached when a saved vault lost its permission. */
@Composable
fun OnboardingScreen(vm: MainViewModel, state: AppState) {
    var page by rememberSaveable { mutableIntStateOf(if (state.vaultLost || state.onboarded) 1 else 0) }
    val error by vm.error.collectAsState()

    LaunchedEffect(state.vaultOk) { if (state.vaultOk && page == 1) page = 2 }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.pickVault(uri)
    }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.createVault(uri)
    }

    Column(Modifier.fillMaxSize().padding(24.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        Ghost(140.dp)
        Spacer(Modifier.height(16.dp))
        AnimatedContent(
            targetState = page,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
            },
            label = "onboarding",
        ) { p ->
            when (p) {
                0 -> Page(
                    "Hi, I'm Wisp",
                    "Save links from any app. Each one becomes a plain Markdown note that you own.",
                ) { Big("Next", primary = true) { page = 1 } }

                1 -> Page(
                    if (state.vaultLost) "I lost your vault" else "Where do notes live?",
                    if (state.vaultLost) "Wisp lost access to your folder. Pick it again to go on."
                    else "Pick a folder. Obsidian can open the same folder.",
                ) {
                    Big("Pick my Obsidian vault", primary = true) { pick.launch(null) }
                    Spacer(Modifier.height(12.dp))
                    Big("Create a new vault", primary = false) { create.launch(null) }
                    error?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = WispColors.Pumpkin, textAlign = TextAlign.Center)
                    }
                }

                else -> Page("All set", "Share any link to Wisp and it shows up in your Inbox folder.") {
                    Big("Start", primary = true) { vm.finish() }
                }
            }
        }
        Dots(page)
    }
}

@Composable
private fun Page(title: String, body: String, actions: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, color = WispColors.Lavender, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        actions()
    }
}

@Composable
private fun Big(label: String, primary: Boolean, onClick: () -> Unit) {
    WispButton(
        label = label,
        onClick = onClick,
        color = if (primary) WispColors.Pumpkin else WispColors.Purple,
        textColor = if (primary) WispColors.Void else androidx.compose.ui.graphics.Color.White,
    )
}

@Composable
private fun Dots(page: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
        repeat(3) { i ->
            Box(
                Modifier.size(12.dp).clip(CircleShape)
                    .background(if (i == page) WispColors.Pumpkin else WispColors.Ink)
                    .border(2.dp, WispColors.Void, CircleShape),
            )
        }
    }
}
