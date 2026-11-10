package de.autoknigge.hauskontor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

private data class Row3(val found: Found, val income: Boolean, val checked: Boolean, val dup: Boolean, val cat: Category, val title: String, val rule: String = "")

/**
 * Zahlungen aus dem Foto einer Rechnung, eines Kassenbons oder eines Kontoauszugs übernehmen.
 * Die Texterkennung läuft auf dem Gerät, alle Vorschläge werden vor dem Speichern geprüft.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportDialog(vm: MainViewModel, defaultDay: Long, onDraft: (Entry) -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var scan by remember { mutableStateOf<Ocr.Scan?>(null) }
    var statementMode by remember { mutableStateOf(false) }
    var pending by rememberSaveable { mutableStateOf<Uri?>(null) }
    var payer by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<Long?>(null) }
    val rows = remember { mutableListOf<Row3>().toMutableStateList() }
    val persons = vm.data.persons
    var editing by remember { mutableStateOf<Int?>(null) }

    fun guessFor(t: String, inc: Boolean): Category =
        if (inc) Category(Receipts.guessIncome(t, vm.data.rules).label) else Receipts.guessCategory(t, vm.data.rules)

    fun process(uri: Uri) {
        scope.launch {
            busy = true
            failed = false
            val s = Ocr.scan(ctx, uri)
            scan = s
            rows.clear()
            if (s == null || s.rows.isEmpty()) failed = true
            else {
                val found = Receipts.statement(s.rows)
                val have = vm.data.entries
                found.forEach { f ->
                    val dup = have.any { it.date == f.date && it.cents == f.cents }
                    rows.add(Row3(f, f.income, !dup, dup, guessFor(f.title, f.income), f.title))
                }
                statementMode = found.size >= 2
                picked = null
            }
            busy = false
        }
    }

    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) pending?.let { process(it) } }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) process(uri) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) process(uri) }

    val guess = remember(scan) { scan?.let { Receipts.receipt(it.text, it.rows) } }

    editing?.let { i ->
        val r = rows.getOrNull(i)
        if (r == null) editing = null
        else {
            var t by remember(i) { mutableStateOf(r.title) }
            var c by remember(i) { mutableStateOf(r.cat) }
            var k by remember(i) { mutableStateOf(r.rule.ifBlank { Receipts.keyOf(r.title) }) }
            AlertDialog(
                onDismissRequest = { editing = null },
                title = { Text("Zahlung bearbeiten") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = t, onValueChange = { t = it }, label = { Text("Text") }, modifier = Modifier.fillMaxWidth())
                        CategoryField(if (r.income) "Kategorie der Einnahme" else "Kategorie", c.label, (if (r.income) vm.data.incomeCategories().map { it.label } else vm.data.expenseCategories().map { it.label })) { c = Category(it) }
                        OutlinedTextField(value = k, onValueChange = { k = it }, label = { Text("Künftig automatisch zuordnen, wenn der Text enthält") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text("Das kann ein Name, eine Kundennummer oder ein Teil des Verwendungszwecks sein. Leer lassen = nichts merken.", fontSize = 12.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val same = rows.indices.filter { j -> j != i && k.isNotBlank() && rows[j].title.lowercase().contains(k.trim().lowercase()) }
                        rows[i] = r.copy(title = t, cat = Category(c.label.trim().ifBlank { Category.SONSTIGES.label }), rule = k.trim())
                        same.filter { j -> rows[j].income == r.income }.forEach { j -> rows[j] = rows[j].copy(cat = c) }
                        editing = null
                    }) { Text("Übernehmen") }
                },
                dismissButton = { TextButton(onClick = { editing = null }) { Text("Abbrechen") } }
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Beleg oder Kontoauszug") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Fotografiere eine Rechnung, einen Kassenbon oder einen Kontoauszug oder wähle ein PDF (z. B. Online-Kontoauszug). Die App liest Beträge und Datum und schlägt Buchungen vor. Alles bleibt auf deinem Gerät.", fontSize = 12.5.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val dir = File(ctx.cacheDir, "aufnahmen").also { it.mkdirs() }
                        val f = File(dir, "b_" + System.currentTimeMillis() + ".jpg")
                        val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                        pending = u
                        take.launch(u)
                    }) { Text("📷 Foto") }
                    OutlinedButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Galerie") }
                    OutlinedButton(onClick = { pickPdf.launch(arrayOf("application/pdf")) }) { Text("📄 PDF") }
                }
                if (busy) Text("Text wird gelesen …", fontSize = 13.sp)
                if (failed) Text(Ocr.error ?: "Es wurde kein Text erkannt. Mache das Foto bei gutem Licht, gerade von oben und möglichst formatfüllend.", fontSize = 12.5.sp)

                if (scan != null && !failed) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !statementMode, onClick = { statementMode = false }, label = { Text("Rechnung / Beleg") })
                        FilterChip(selected = statementMode, onClick = { statementMode = true }, label = { Text("Kontoauszug") })
                    }
                }

                if (scan != null && !failed && !statementMode) {
                    val g = guess
                    if (g == null || g.amounts.isEmpty()) {
                        Text("Kein Betrag erkannt. Du kannst die Buchung trotzdem von Hand anlegen.", fontSize = 12.5.sp)
                    } else {
                        Text("Erkannte Beträge (tippe auf den richtigen):", fontSize = 12.sp)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            g.amounts.forEach { c ->
                                FilterChip(selected = picked == c, onClick = { picked = c }, label = { Text(Calc.eur(c)) })
                            }
                        }
                        Text(
                            (g.date?.let { "Datum: " + Calc.dateText(it) } ?: "Kein Datum erkannt, heute wird verwendet.") +
                                (if (g.title.isNotBlank()) "\nBezeichnung: " + g.title else ""),
                            fontSize = 12.5.sp
                        )
                        val gd = g.date ?: defaultDay
                        if (picked != null && vm.data.entries.any { it.date == gd && it.cents == picked }) Text("Achtung: Eine Buchung mit gleichem Datum und Betrag gibt es schon. Bitte nicht doppelt anlegen.", fontSize = 12.5.sp)
                        Button(enabled = picked != null, onClick = {
                            val t = g.title
                            onDraft(Entry(vm.newId(), g.date ?: defaultDay, t, picked ?: 0L, Receipts.guessCategory(t, vm.data.rules), false, ""))
                        }) { Text("Als Buchung öffnen") }
                    }
                }

                if (scan != null && !failed && statementMode) {
                    if (rows.isEmpty()) {
                        Text("Keine Zeilen mit Datum und Betrag erkannt. Versuche ein schärferes Foto oder wechsle auf „Rechnung / Beleg“.", fontSize = 12.5.sp)
                    } else {
                        Text("Erkannte Zahlungen. Haken entfernen, was nicht übernommen werden soll. Mit „±“ wechselst du zwischen Ausgabe und Einnahme.", fontSize = 12.5.sp)
                        rows.forEachIndexed { i, r ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = r.checked, onCheckedChange = { rows[i] = r.copy(checked = it) })
                                Column(Modifier.weight(1f).clickable { editing = i }) {
                                    Text(r.title.ifBlank { "(ohne Text)" }, fontSize = 13.sp, maxLines = 2)
                                    Text(Calc.dateText(r.found.date) + (if (r.dup) " · schon vorhanden (gleiches Datum und gleicher Betrag, wird nicht doppelt gezählt)" else ""), fontSize = 11.sp)
                                    Text("→ " + r.cat.label + "  ✎", fontSize = 12.sp)
                                }
                                Text((if (r.income) "+ " else "− ") + Calc.eur(r.found.cents), fontSize = 13.sp)
                                TextButton(onClick = { rows[i] = r.copy(income = !r.income, cat = guessFor(r.title, !r.income)) }) { Text("±") }
                            }
                        }
                        Hint("Tippe auf eine Zeile, um Text oder Kategorie zu ändern. Die App merkt sich die Zuordnung für den nächsten Scan.")
                        if (persons.isNotEmpty()) {
                            Text("Wer hat gezahlt (für alle gewählten)?", fontSize = 12.sp)
                            Choice<String?>(listOf<String?>(null) + persons.map { it.id }, payer, { id -> persons.firstOrNull { it.id == id }?.name ?: "Gemeinsam" }) { payer = it }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val chosen = rows.filter { it.checked }
            if (scan != null && statementMode && chosen.isNotEmpty()) {
                TextButton(onClick = {
                    vm.learn(chosen.filter { it.rule.isNotBlank() }.map { Rule(it.rule.trim().lowercase(), it.cat.label, it.income) })
                    vm.saveEntries(chosen.map { r ->
                        Entry(
                            vm.newId(), r.found.date, r.title, r.found.cents,
                            if (r.income) Category.SONSTIGES else r.cat,
                            r.income, "per Foto importiert", payer, if (r.income) IncomeKind(r.cat.label) else null
                        )
                    })
                    onDone()
                }) { Text(chosen.size.toString() + " übernehmen") }
            }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Schließen") } }
    )
}
