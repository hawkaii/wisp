package app.wispnotes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.wispnotes.note.PropertyType
import app.wispnotes.ui.theme.WispCard
import app.wispnotes.ui.theme.WispColors

/** Which field a tapped variable lands in. The last field that had focus wins. */
private enum class Field { Name, Trigger, Folder, NoteName, Content }

/**
 * The template maker.
 *
 * Two tabs over one file: a form for the fields people change, and the raw JSON. The form writes into
 * the parsed JSON rather than replacing it, so a key the form does not show survives a save.
 *
 * The variables panel shows what the link actually provides, filled or grey, so a template is never
 * built around a value that will always be empty.
 */
@Composable
fun TemplateEditorScreen(
    file: String?,
    url: String,
    caption: String,
    onBack: () -> Unit,
    vm: TemplateEditorViewModel = viewModel(),
) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val form by vm.form.collectAsStateWithLifecycle()
    val json by vm.json.collectAsStateWithLifecycle()
    val tab by vm.tab.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val variables by vm.variables.collectAsStateWithLifecycle()
    val reading by vm.reading.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val name by vm.fileName.collectAsStateWithLifecycle()
    var focus by remember { mutableStateOf<Field?>(null) }

    LaunchedEffect(file) { vm.open(file, url, caption) }
    LaunchedEffect(saved) { if (saved) onBack() }

    fun insert(variable: Variable) {
        val token = "{{${variable.name}}}"
        val f = form
        vm.edit(
            when (focus) {
                Field.Name -> f.copy(name = f.name + token)
                Field.Trigger -> f.copy(triggers = f.triggers + token)
                Field.Folder -> f.copy(folder = f.folder + token)
                Field.NoteName -> f.copy(noteName = f.noteName + token)
                else -> f.copy(content = f.content + token)
            },
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(48.dp).pressable(onClick = onBack).semantics { contentDescription = "Back" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
            }
            Text(
                name ?: (if (file == null) "New template" else file),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WispChip("Form", selected = tab == TemplateTab.Form) { vm.switchTo(TemplateTab.Form) }
            WispChip("JSON", selected = tab == TemplateTab.Json) { vm.switchTo(TemplateTab.Json) }
            Box(Modifier.weight(1f))
            WispButton("Save", { vm.save() }, compact = true)
        }

        error?.let { Text(it, color = WispColors.Pumpkin, style = MaterialTheme.typography.bodySmall) }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (tab) {
                TemplateTab.Form -> {
                    Box(Modifier.fillMaxWidth()) {
                        FieldBox(
                            label = "Name",
                            value = form.name,
                            onChange = { vm.edit(form.copy(name = it)) },
                            onFocus = { focus = Field.Name },
                        )
                    }
                    FieldBox(
                        label = "Triggers, one per line",
                        value = form.triggers,
                        onChange = { vm.edit(form.copy(triggers = it)) },
                        onFocus = { focus = Field.Trigger },
                        lines = 3,
                    )
                    FieldBox(
                        label = "Folder (one name, no slashes)",
                        value = form.folder,
                        onChange = { vm.edit(form.copy(folder = it)) },
                        onFocus = { focus = Field.Folder },
                    )
                    FieldBox(
                        label = "Note name",
                        value = form.noteName,
                        onChange = { vm.edit(form.copy(noteName = it)) },
                        onFocus = { focus = Field.NoteName },
                    )
                    FieldBox(
                        label = "Note text",
                        value = form.content,
                        onChange = { vm.edit(form.copy(content = it)) },
                        onFocus = { focus = Field.Content },
                        lines = 8,
                    )

                    WispCard(Modifier.fillMaxWidth(), color = WispColors.Ink) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Properties", style = MaterialTheme.typography.titleMedium)
                            if (form.properties.isEmpty()) {
                                Text("None.", color = WispColors.Lavender, style = MaterialTheme.typography.bodySmall)
                            }
                            form.properties.forEachIndexed { index, row ->
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FieldBox(
                                            label = "key",
                                            value = row.name,
                                            modifier = Modifier.weight(1f),
                                            onChange = {
                                                val next = form.properties.toMutableList()
                                                next[index] = row.copy(name = it)
                                                vm.edit(form.copy(properties = next))
                                            },
                                            onFocus = {},
                                        )
                                        WispChip(typeLabel(row.type), selected = true) {
                                            val next = form.properties.toMutableList()
                                            next[index] = row.copy(type = nextType(row.type))
                                            vm.edit(form.copy(properties = next))
                                        }
                                        WispButton("×", {
                                            vm.edit(form.copy(properties = form.properties - row))
                                        }, compact = true, color = WispColors.Purple, textColor = Color.White)
                                    }
                                    FieldBox(
                                        label = "value",
                                        value = row.value,
                                        onChange = {
                                            val next = form.properties.toMutableList()
                                            next[index] = row.copy(value = it)
                                            vm.edit(form.copy(properties = next))
                                        },
                                        onFocus = { focus = Field.Content },
                                    )
                                }
                            }
                            WispButton(
                                "+ Property",
                                { vm.edit(form.copy(properties = form.properties + PropertyRow("", "", PropertyType.TEXT.wire))) },
                                compact = true,
                                color = WispColors.Purple,
                                textColor = Color.White,
                            )
                        }
                    }
                }

                TemplateTab.Json -> {
                    Text(
                        "The file exactly as it is written. Unknown keys are kept.",
                        color = WispColors.Lavender,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    FieldBox(
                        label = "",
                        value = json,
                        onChange = { vm.editJson(it) },
                        onFocus = {},
                        lines = 20,
                    )
                }
            }

            VariablesPanel(
                variables = variables,
                reading = reading,
                onInsert = ::insert,
                onCopy = { clipboard.setText(AnnotatedString("{{${it.name}}}")) },
                onTest = { vm.inspect(url, caption) },
                canTest = url.isNotBlank(),
            )
        }
    }
}

@Composable
private fun VariablesPanel(
    variables: List<Variable>?,
    reading: Boolean,
    onInsert: (Variable) -> Unit,
    onCopy: (Variable) -> Unit,
    onTest: () -> Unit,
    canTest: Boolean,
) {
    WispCard(Modifier.fillMaxWidth(), color = WispColors.Ink) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("This link gives you", style = MaterialTheme.typography.titleMedium)
            when {
                reading -> Text("Reading the page…", color = WispColors.Lavender, style = MaterialTheme.typography.bodySmall)
                variables == null -> {
                    Text(
                        if (canTest) "Not read yet." else "Open a template from a share to see its values.",
                        color = WispColors.Lavender,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (canTest) WispButton("Read this link", onTest, compact = true)
                }
                else -> {
                    variables.filter { it.filled || it.name in ALWAYS_SHOWN }.forEach { variable ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                variable.name,
                                color = if (variable.filled) Color.White else WispColors.Lavender,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                variable.value.take(48).ifBlank { "—" },
                                color = if (variable.filled) WispColors.Lavender else WispColors.Purple,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(2f),
                                maxLines = 1,
                            )
                            WispChip("+", selected = variable.filled) { onInsert(variable) }
                            Box(Modifier.size(4.dp))
                            WispChip("copy") { onCopy(variable) }
                        }
                    }
                    Text(
                        "Tapping + adds {{name}} to the field you last touched. {selector:css} works too.",
                        color = WispColors.Lavender,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/** Always listed, even when empty: these are the ones a template is built from. */
private val ALWAYS_SHOWN = setOf("title", "url", "caption", "content", "author", "site", "date")

@Composable
private fun FieldBox(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
    lines: Int = 1,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label.isNotEmpty()) {
            Text(label, color = WispColors.Lavender, style = MaterialTheme.typography.bodySmall)
        }
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(WispColors.Void)
                .padding(12.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = SolidColor(WispColors.Pumpkin),
                modifier = Modifier.fillMaxWidth().heightIn(min = (18 * lines).dp).onFocusChanged { if (it.isFocused) onFocus() },
                maxLines = lines.coerceAtLeast(1),
            )
        }
    }
}

private fun typeLabel(type: String): String = when (type) {
    PropertyType.NUMBER.wire -> "number"
    PropertyType.CHECKBOX.wire -> "checkbox"
    PropertyType.DATE.wire -> "date"
    PropertyType.DATETIME.wire -> "datetime"
    PropertyType.MULTITEXT.wire -> "multitext"
    else -> "text"
}

private fun nextType(type: String): String {
    val order = listOf(
        PropertyType.TEXT.wire,
        PropertyType.MULTITEXT.wire,
        PropertyType.DATE.wire,
        PropertyType.DATETIME.wire,
        PropertyType.NUMBER.wire,
        PropertyType.CHECKBOX.wire,
    )
    return order[(order.indexOf(type).coerceAtLeast(0) + 1) % order.size]
}
