package de.autoknigge.hauskontor

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun BookScreen(vm: MainViewModel, ym: YearMonth, onMonth: (Int) -> Unit) {
    var sub by rememberSaveable { mutableIntStateOf(0) }
    var entryDialog by remember { mutableStateOf<Entry?>(null) }
    var newEntry by remember { mutableStateOf(false) }
    var recDialog by remember { mutableStateOf<Recurring?>(null) }
    var newRec by remember { mutableStateOf(false) }
    val d = vm.data

    Scroller {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = sub == 0, onClick = { sub = 0 }, label = { Text("Buchungen") })
            FilterChip(selected = sub == 1, onClick = { sub = 1 }, label = { Text("Fixkosten") })
        }

        if (sub == 0) {
            MonthBar(ym, onMonth)
            val lines = Calc.monthLines(d, ym)
            val s = Calc.sum(lines)
            HCard(null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("Ausgaben", Calc.eur(s.expense), Red)
                    Stat("Einnahmen", Calc.eur(s.income), Green)
                    Stat("Saldo", Calc.eur(s.income - s.expense), if (s.income - s.expense >= 0) Green else Red)
                }
            }
            Button(onClick = { newEntry = true }) { Text("+ Buchung") }
            if (lines.isEmpty()) Hint("Keine Buchungen in diesem Monat.")
            HCard(null) {
                lines.forEach { l ->
                    val entry = if (l.fixed) null else d.entries.firstOrNull { it.id == l.sourceId }
                    Row2(
                        l.title.ifBlank { l.category.label },
                        (if (l.income) "+ " else "") + Calc.eur(l.cents),
                        if (l.income) Green else Ink,
                        sub = String.format("%02d.%02d. · %s", l.date.dayOfMonth, l.date.monthValue, l.category.label) + (if (l.fixed) " · Fixkosten" else ""),
                        onClick = if (entry != null) ({ entryDialog = entry }) else if (l.fixed) ({ sub = 1; recDialog = d.recurrings.firstOrNull { it.id == l.sourceId } }) else null
                    )
                }
            }
        } else {
            val monthly = d.recurrings.sumOf { Calc.perMonth(it) }
            HCard("Fixkosten, Versicherungen und Nebenkosten") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("Je Monat (Durchschnitt)", Calc.eur(Math.round(monthly)), Gold)
                    Stat("Je Jahr", Calc.eur(Math.round(monthly * 12)))
                }
                Hint("Wiederkehrende Zahlungen werden automatisch in dem Monat gezählt, in dem sie fällig sind.")
            }
            Button(onClick = { newRec = true }) { Text("+ Fixkosten") }
            if (d.recurrings.isEmpty()) Hint("Noch keine Fixkosten. Tippe auf „+ Fixkosten“, dort gibt es Vorlagen wie Grundsteuer oder Gebäudeversicherung.")
            HCard(null) {
                d.recurrings.sortedBy { it.title }.forEach { r ->
                    Row2(r.title, Calc.eur(r.cents), sub = r.interval.label + " · " + r.category.label + " · ab " + Calc.dateText(r.start) + (r.end?.let { " bis " + Calc.dateText(it) } ?: ""), onClick = { recDialog = r })
                }
            }
        }
    }

    if (newEntry || entryDialog != null) {
        EntryDialog(
            initial = entryDialog,
            defaultDay = if (YearMonth.from(LocalDate.now()) == ym) LocalDate.now().toEpochDay() else ym.atDay(1).toEpochDay(),
            newId = vm::newId,
            onSave = { vm.saveEntry(it); newEntry = false; entryDialog = null },
            onDelete = { entryDialog?.let { e -> vm.deleteEntry(e.id) }; newEntry = false; entryDialog = null },
            onDismiss = { newEntry = false; entryDialog = null }
        )
    }
    if (newRec || recDialog != null) {
        RecurringDialog(
            initial = recDialog,
            newId = vm::newId,
            onSave = { vm.saveRecurring(it); newRec = false; recDialog = null },
            onDelete = { recDialog?.let { r -> vm.deleteRecurring(r.id) }; newRec = false; recDialog = null },
            onDismiss = { newRec = false; recDialog = null }
        )
    }
}

@Composable
private fun EntryDialog(initial: Entry?, defaultDay: Long, newId: () -> String, onSave: (Entry) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var amount by remember { mutableStateOf(initial?.let { Calc.centsText(it.cents) } ?: "") }
    var date by remember { mutableStateOf(initial?.date ?: defaultDay) }
    var cat by remember { mutableStateOf(initial?.category ?: Category.EINKAUF) }
    var income by remember { mutableStateOf(initial?.income ?: false) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    val cents = Calc.parseCents(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Neue Buchung" else "Buchung ändern") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Bezeichnung (z. B. Supermarkt)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it }, label = { Text("Betrag in Euro") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                )
                DateField("Datum", date) { date = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Einnahme statt Ausgabe", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Switch(checked = income, onCheckedChange = { income = it })
                }
                if (!income) Choice(Category.values().toList(), cat, { it.label }) { cat = it }
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Notiz (optional)") }, modifier = Modifier.fillMaxWidth())
                if (initial != null) TextButton(onClick = onDelete) { Text("Buchung löschen", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = cents != null && cents > 0, onClick = {
                onSave(Entry(initial?.id ?: newId(), date, title.trim(), cents ?: 0L, if (income) Category.SONSTIGES else cat, income, note.trim()))
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

private data class Preset(val title: String, val cat: Category, val interval: Interval)

private val presets = listOf(
    Preset("Grundsteuer", Category.NEBENKOSTEN, Interval.QUARTERLY),
    Preset("Müllgebühren", Category.NEBENKOSTEN, Interval.QUARTERLY),
    Preset("Abwasser / Niederschlagswasser", Category.NEBENKOSTEN, Interval.YEARLY),
    Preset("Straßenreinigung", Category.NEBENKOSTEN, Interval.YEARLY),
    Preset("Schornsteinfeger", Category.NEBENKOSTEN, Interval.YEARLY),
    Preset("Gebäudeversicherung", Category.VERSICHERUNG, Interval.YEARLY),
    Preset("Hausratversicherung", Category.VERSICHERUNG, Interval.YEARLY),
    Preset("Haftpflichtversicherung", Category.VERSICHERUNG, Interval.YEARLY),
    Preset("Miete oder Kreditrate", Category.FIXKOSTEN, Interval.MONTHLY),
    Preset("Strom-Abschlag", Category.FIXKOSTEN, Interval.MONTHLY),
    Preset("Gas-Abschlag", Category.FIXKOSTEN, Interval.MONTHLY),
    Preset("Internet und Telefon", Category.FIXKOSTEN, Interval.MONTHLY),
    Preset("Rundfunkbeitrag", Category.ABBUCHUNG, Interval.QUARTERLY)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecurringDialog(initial: Recurring?, newId: () -> String, onSave: (Recurring) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var amount by remember { mutableStateOf(initial?.let { Calc.centsText(it.cents) } ?: "") }
    var cat by remember { mutableStateOf(initial?.category ?: Category.FIXKOSTEN) }
    var interval by remember { mutableStateOf(initial?.interval ?: Interval.MONTHLY) }
    var start by remember { mutableStateOf(initial?.start ?: LocalDate.now().toEpochDay()) }
    var hasEnd by remember { mutableStateOf(initial?.end != null) }
    var end by remember { mutableStateOf(initial?.end ?: LocalDate.now().plusYears(1).toEpochDay()) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    val cents = Calc.parseCents(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Neue Fixkosten" else "Fixkosten ändern") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initial == null) {
                    Text("Vorlagen", fontSize = 12.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        presets.forEach { p ->
                            AssistChip(onClick = { title = p.title; cat = p.cat; interval = p.interval }, label = { Text(p.title, fontSize = 12.sp) })
                        }
                    }
                }
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Bezeichnung") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it }, label = { Text("Betrag je Zahlung in Euro") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                )
                Text("Rhythmus", fontSize = 12.sp)
                Choice(Interval.values().toList(), interval, { it.label }) { interval = it }
                DateField("Erste Fälligkeit", start) { start = it }
                Text("Kategorie", fontSize = 12.sp)
                Choice(Category.values().filter { it != Category.EINKAUF }, cat, { it.label }) { cat = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Endet am", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Switch(checked = hasEnd, onCheckedChange = { hasEnd = it })
                }
                if (hasEnd) DateField("Letzte Fälligkeit bis", end) { end = it }
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Notiz (optional)") }, modifier = Modifier.fillMaxWidth())
                if (initial != null) TextButton(onClick = onDelete) { Text("Fixkosten löschen", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = cents != null && cents > 0 && title.isNotBlank(), onClick = {
                onSave(Recurring(initial?.id ?: newId(), title.trim(), cents ?: 0L, cat, interval, start, if (hasEnd) end else null, note.trim()))
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
