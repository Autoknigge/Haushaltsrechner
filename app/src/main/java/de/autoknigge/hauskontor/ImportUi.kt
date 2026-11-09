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

private class Row3(val found: Found, var income: Boolean, var checked: Boolean, val dup: Boolean)

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
                    val dup = have.any { it.date == f.date && it.cents == f.cents && it.title.equals(f.title, ignoreCase = true) }
                    rows.add(Row3(f, f.income, !dup, dup))
                }
                statementMode = found.size >= 2
                picked = null
            }
            busy = false
        }
    }

    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) pending?.let { process(it) } }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) process(uri) }

    val guess = remember(scan) { scan?.let { Receipts.receipt(it.text, it.rows) } }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Beleg oder Kontoauszug") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Fotografiere eine Rechnung, einen Kassenbon oder einen Kontoauszug. Die App liest Beträge und Datum und schlägt Buchungen vor. Alles bleibt auf deinem Gerät.", fontSize = 12.5.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val dir = File(ctx.cacheDir, "aufnahmen").also { it.mkdirs() }
                        val f = File(dir, "b_" + System.currentTimeMillis() + ".jpg")
                        val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                        pending = u
                        take.launch(u)
                    }) { Text("📷 Foto") }
                    OutlinedButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Galerie") }
                }
                if (busy) Text("Text wird gelesen …", fontSize = 13.sp)
                if (failed) Text("Es wurde kein Text erkannt. Mache das Foto bei gutem Licht, gerade von oben und möglichst formatfüllend.", fontSize = 12.5.sp)

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
                        Button(enabled = picked != null, onClick = {
                            val t = g.title
                            onDraft(Entry(vm.newId(), g.date ?: defaultDay, t, picked ?: 0L, Receipts.guessCategory(t), false, ""))
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
                                Checkbox(checked = r.checked, onCheckedChange = { rows[i] = Row3(r.found, r.income, it, r.dup) })
                                Column(Modifier.weight(1f).clickable { rows[i] = Row3(r.found, r.income, !r.checked, r.dup) }) {
                                    Text(r.found.title.ifBlank { "(ohne Text)" }, fontSize = 13.sp, maxLines = 2)
                                    Text(Calc.dateText(r.found.date) + (if (r.dup) " · schon vorhanden" else ""), fontSize = 11.sp)
                                }
                                Text((if (r.income) "+ " else "− ") + Calc.eur(r.found.cents), fontSize = 13.sp)
                                TextButton(onClick = { rows[i] = Row3(r.found, !r.income, r.checked, r.dup) }) { Text("±") }
                            }
                        }
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
                    vm.saveEntries(chosen.map { r ->
                        Entry(
                            vm.newId(), r.found.date, r.found.title, r.found.cents,
                            if (r.income) Category.SONSTIGES else Receipts.guessCategory(r.found.title),
                            r.income, "per Foto importiert", payer, if (r.income) IncomeKind.SONSTIGES else null
                        )
                    })
                    onDone()
                }) { Text(chosen.size.toString() + " übernehmen") }
            }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Schließen") } }
    )
}
