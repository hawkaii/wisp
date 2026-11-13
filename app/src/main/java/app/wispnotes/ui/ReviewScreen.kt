package app.wispnotes.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.wispnotes.note.Publish
import app.wispnotes.note.Recall
import app.wispnotes.note.Review
import app.wispnotes.R
import app.wispnotes.ui.theme.WispCard
import app.wispnotes.ui.theme.WispColors
import app.wispnotes.ui.theme.WispPanel
import java.time.LocalDate

/** Review = done notes that are due. Recall first (text box), then the note is revealed, then Remembered / Fuzzy / Forgot. */
@Composable
fun ReviewScreen(vm: LibraryViewModel = viewModel()) {
    val all by vm.all.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }
    LifecycleResumeEffect(Unit) {
        vm.rescan()
        onPauseOrDispose {}
    }
    val notes = all
    val due = notes.orEmpty().filter { it.isDue(today) }.sortedBy { Review.dueDate(it.nextReview, it.finished) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Review", style = MaterialTheme.typography.headlineMedium)
            androidx.compose.material3.Icon(painterResource(R.drawable.stk_calendar), null, tint = Color.Unspecified, modifier = Modifier.size(32.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { i -> Box(Modifier.size(14.dp).clip(RoundedCornerShape(50)).background(if (i == 0) WispColors.Pumpkin else WispColors.Ink).border(2.dp, Color.Black, RoundedCornerShape(50))) }
        }
        val entry = due.firstOrNull()
        if (notes == null) return@Column
        if (entry == null) {
            val next = notes.mapNotNull { n -> if (n.status == "done") Review.dueDate(n.nextReview, n.finished) else null }.minOrNull()
            WispCard(Modifier.fillMaxWidth(), color = WispColors.Ink) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Ghost(120.dp)
                    Text("Nothing to revisit.", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (next != null) "The next one comes back on $next." else "Notes you finish come back here after 3, 7, 21 and 60 days.",
                        color = WispColors.Lavender,
                    )
                }
            }
            if (notes.any { it.status == "done" }) ReminderAsk()
            return@Column
        }
        Text("${due.size} to revisit", color = WispColors.Lavender)
        key(entry.fileName) {
            var recall by rememberSaveable { mutableStateOf("") }
            var revealed by rememberSaveable { mutableStateOf(false) }
            var mine by remember { mutableStateOf("") }
            LaunchedEffect(revealed) { if (revealed) mine = vm.myNotes(entry.fileName) }
            WispCard(Modifier.fillMaxWidth(), color = WispColors.Purple) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(entry.title, style = MaterialTheme.typography.titleMedium)
                    Text("What do you remember?", color = WispColors.Haze)
                    WispPanel(Modifier.fillMaxWidth()) {
                        BasicTextField(
                            value = recall,
                            onValueChange = { if (!revealed) recall = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 16.sp, lineHeight = 22.sp),
                            cursorBrush = SolidColor(WispColors.Pumpkin),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp).semantics { contentDescription = "What do you remember?" },
                            decorationBox = { inner ->
                                if (recall.isEmpty()) Text("Write what you can, before you look.", color = WispColors.Lavender)
                                inner()
                            },
                        )
                    }
                    if (!revealed) WispButton("Show my note", { revealed = true }, enabled = recall.isNotBlank())
                    AnimatedVisibility(revealed, enter = fadeIn() + slideInVertically { it / 4 }) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(WispColors.Ink).padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                entry.rating?.let { Text(Publish.stars(it), color = WispColors.Pumpkin) }
                                Text(mine.ifEmpty { "(no notes written)" }, style = MaterialTheme.typography.bodyLarge)
                            }
                            Text("How did you do?", color = WispColors.Haze)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                WispButton("Remembered", { vm.answer(entry, Recall.Remembered) }, Modifier.weight(1f), compact = true)
                                WispButton("Fuzzy", { vm.answer(entry, Recall.Fuzzy) }, Modifier.weight(1f), color = WispColors.Lavender, compact = true)
                                WispButton("Forgot", { vm.answer(entry, Recall.Forgot) }, Modifier.weight(1f), color = WispColors.Ink, textColor = Color.White, compact = true)
                            }
                        }
                    }
                }
            }
        }
        if (notes.any { it.status == "done" }) ReminderAsk()
        Spacer(Modifier.padding(8.dp))
    }
}

private const val PREFS = "wisp-ui"
private const val ASKED = "reminders_asked"

/** A polite one-time ask for notification permission. The app works the same without it. */
@Composable
private fun ReminderAsk() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var asked by remember { mutableStateOf(prefs.getBoolean(ASKED, false)) }
    val done = { asked = true; prefs.edit().putBoolean(ASKED, true).apply() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { done() }
    val granted = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    if (granted || asked) return
    WispCard(Modifier.fillMaxWidth(), color = WispColors.Ink) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Want a nudge when a note is ready to revisit?", style = MaterialTheme.typography.titleMedium)
            Text("One quiet reminder a day, only when something is due. Review works without it.", color = WispColors.Lavender)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                WispButton("Allow reminders", { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }, compact = true)
                WispChip("Not now", onClick = { done() })
            }
        }
    }
}
