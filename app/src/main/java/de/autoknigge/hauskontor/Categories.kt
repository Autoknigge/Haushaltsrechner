package de.autoknigge.hauskontor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.YearMonth

/** Eigenes Kriterium für die automatische Zuordnung: Taucht der Text im Namen oder Verwendungszweck auf, gilt die Kategorie. */
@Composable
fun RuleField(key: String, onKey: (String) -> Unit, applyAll: Boolean, onApplyAll: (Boolean) -> Unit) {
    OutlinedTextField(
        value = key, onValueChange = onKey, singleLine = true, modifier = Modifier.fillMaxWidth(),
        label = { Text("Künftig automatisch zuordnen, wenn der Text enthält:") },
        trailingIcon = { if (key.isNotEmpty()) TextButton(onClick = { onKey("") }) { Text("✕", fontSize = 16.sp) } }
    )
    Text("Zum Beispiel ein Name, eine Kundennummer oder ein Teil des Verwendungszwecks. Leer lassen = nichts merken.", fontSize = 12.sp)
    if (key.isNotBlank()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = applyAll, onCheckedChange = onApplyAll)
            Text("Auch vorhandene Buchungen mit diesem Text umstellen", fontSize = 13.sp)
        }
    }
}

private fun labelOf(l: Posting): String = if (l.income) (l.kind ?: IncomeKind.SONSTIGES).label else l.category.label

/** Alle Zahlungen einer Kategorie in einem Jahr. Tippen auf eine Zeile öffnet sie zum Ändern. */
@Composable
fun CategoryYearDialog(vm: MainViewModel, year: Int, label: String, income: Boolean, onDismiss: () -> Unit) {
    val d = vm.data
    val lines = (1..12).flatMap { Calc.monthLines(d, YearMonth.of(year, it)) }.filter { it.income == income && labelOf(it) == label }.sortedBy { it.date }
    var edit by remember { mutableStateOf<Posting?>(null) }
    var moveAll by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$label $year") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (lines.isEmpty()) Text("Keine Zahlungen.", fontSize = 13.sp)
                else {
                    Text("Summe: " + Calc.eur(lines.sumOf { it.cents }) + " · " + lines.size + " Zahlungen", fontSize = 13.sp)
                    Text("Tippe auf eine Zahlung, um Text oder Kategorie zu ändern.", fontSize = 12.sp)
                }
                lines.forEach { l ->
                    Row2(
                        l.title.ifBlank { label }, (if (l.income) "+ " else "") + Calc.eur(l.cents),
                        sub = Calc.dateText(l.date.toEpochDay()) + (if (l.fixed) " · regelmäßig" else ""),
                        onClick = { edit = l }
                    )
                }
                if (lines.isNotEmpty()) TextButton(onClick = { moveAll = true }) { Text("Alle in eine andere Kategorie verschieben") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } }
    )

    edit?.let { l ->
        var t by remember(l) { mutableStateOf(l.title) }
        var c by remember(l) { mutableStateOf(label) }
        var k by remember(l) { mutableStateOf(vm.data.rules.lastOrNull { it.income == income && it.key.isNotBlank() && l.title.lowercase().contains(it.key) }?.key ?: "") }
        var ap by remember(l) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text("Zahlung ändern") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = t, onValueChange = { t = it }, label = { Text("Text") }, modifier = Modifier.fillMaxWidth())
                    CategoryField(
                        if (income) "Kategorie der Einnahme" else "Kategorie", c,
                        if (income) vm.data.incomeCategories().map { it.label } else vm.data.expenseCategories().map { it.label }
                    ) { c = it; if (k.isBlank()) k = Receipts.keyOf(t) }
                    RuleField(k, { k = it }, ap) { ap = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cat = c.trim().ifBlank { if (income) IncomeKind.SONSTIGES.label else Category.SONSTIGES.label }
                    vm.data.entries.firstOrNull { it.id == l.sourceId }?.let { e ->
                        vm.saveEntry(if (income) e.copy(title = t.trim(), incomeKind = IncomeKind(cat)) else e.copy(title = t.trim(), category = Category(cat)))
                    }
                    vm.data.recurrings.firstOrNull { it.id == l.sourceId }?.let { r ->
                        vm.saveRecurring(if (income) r.copy(title = t.trim(), incomeKind = IncomeKind(cat)) else r.copy(title = t.trim(), category = Category(cat)))
                    }
                    if (k.isNotBlank()) {
                        val rule = Rule(k.trim().lowercase(), cat, income)
                        vm.learn(listOf(rule))
                        if (ap) vm.applyRule(rule)
                    }
                    edit = null
                }) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("Abbrechen") } }
        )
    }

    if (moveAll) {
        var c by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { moveAll = false },
            title = { Text("Alle verschieben") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Alle " + lines.size + " Zahlungen aus „$label $year“ bekommen die neue Kategorie. Regelmäßige Zahlungen werden dabei ganz umgestellt.", fontSize = 13.sp)
                    CategoryField(
                        "Neue Kategorie", c,
                        if (income) vm.data.incomeCategories().map { it.label } else vm.data.expenseCategories().map { it.label }
                    ) { c = it }
                }
            },
            confirmButton = {
                TextButton(enabled = c.isNotBlank(), onClick = {
                    vm.recategorize(lines.map { it.sourceId }.toSet(), income, c.trim())
                    moveAll = false
                    onDismiss()
                }) { Text("Verschieben") }
            },
            dismissButton = { TextButton(onClick = { moveAll = false }) { Text("Abbrechen") } }
        )
    }
}

/** Vorschlag: Mehrere gleiche Buchungen im festen Rhythmus sind vermutlich eine regelmäßige Zahlung. */
class Suggestion(val key: String, val entries: List<Entry>, val interval: Interval, val cents: Long)

fun suggestRecurring(d: Data): List<Suggestion> {
    val known = d.recurrings.map { Receipts.keyOf(it.title) + it.income }.toSet()
    val out = ArrayList<Suggestion>()
    d.entries.filter { Receipts.keyOf(it.title).length >= 3 }.groupBy { Receipts.keyOf(it.title) to it.income }.forEach { (g, list) ->
        if ((g.first + g.second) in known || list.size < 2) return@forEach
        val sorted = list.sortedBy { it.date }
        val gaps = sorted.zipWithNext { a, b -> (b.date - a.date).toInt() }
        val amounts = sorted.map { it.cents }.sorted()
        val med = amounts[amounts.size / 2]
        if (med <= 0 || amounts.any { Math.abs(it - med) > med * 0.2 }) return@forEach
        val iv = when {
            sorted.size >= 3 && gaps.all { it in 26..35 } -> Interval.MONTHLY
            sorted.size >= 3 && gaps.all { it in 84..97 } -> Interval.QUARTERLY
            sorted.size >= 2 && gaps.all { it in 355..376 } -> Interval.YEARLY
            else -> null
        } ?: return@forEach
        out.add(Suggestion(g.first, sorted, iv, med))
    }
    return out.sortedByDescending { it.cents }
}
