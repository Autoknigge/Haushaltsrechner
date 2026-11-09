package de.autoknigge.hauskontor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth

private fun signed(c: Long): String = (if (c >= 0) "+ " else "− ") + Calc.eur(Math.abs(c))

@Composable
private fun PersonChoice(persons: List<Person>, label: String, selected: String?, onSelect: (String?) -> Unit) {
    if (persons.isEmpty()) return
    Text(label, fontSize = 12.sp)
    Choice<String?>(listOf<String?>(null) + persons.map { it.id }, selected, { id -> persons.firstOrNull { it.id == id }?.name ?: "Gemeinsam" }, onSelect)
}

@Composable
fun BookScreen(vm: MainViewModel, ym: YearMonth, onMonth: (Int) -> Unit) {
    var sub by rememberSaveable { mutableIntStateOf(0) }
    var entryDialog by remember { mutableStateOf<Entry?>(null) }
    var draft by remember { mutableStateOf<Entry?>(null) }
    var newEntry by remember { mutableStateOf(false) }
    var recDialog by remember { mutableStateOf<Recurring?>(null) }
    var newRec by remember { mutableStateOf(false) }
    var importOpen by remember { mutableStateOf(false) }
    val d = vm.data
    val defaultDay = if (YearMonth.from(LocalDate.now()) == ym) LocalDate.now().toEpochDay() else ym.atDay(1).toEpochDay()
    fun who(id: String?) = d.persons.firstOrNull { it.id == id }?.name

    Scroller {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = sub == 0, onClick = { sub = 0 }, label = { Text("Buchungen") })
            FilterChip(selected = sub == 1, onClick = { sub = 1 }, label = { Text("Regelmäßig") })
        }

        if (sub == 0) {
            MonthBar(ym, onMonth)
            val lines = Calc.monthLines(d, ym)
            val s = Calc.sum(lines)
            HCard(null, bg = TileToday) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("Ausgaben", Calc.eur(s.expense))
                    Stat("Einnahmen", Calc.eur(s.income))
                    Stat("Saldo", signed(s.income - s.expense))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { draft = null; newEntry = true }) { Text("+ Buchung") }
                OutlinedButton(onClick = { importOpen = true }) { Text("📷 Beleg / Kontoauszug") }
            }
            if (lines.isEmpty()) Hint("Keine Buchungen in diesem Monat.")
            HCard(null, bg = O9, dark = true) {
                lines.forEach { l ->
                    val entry = if (l.fixed) null else d.entries.firstOrNull { it.id == l.sourceId }
                    val detail = String.format("%02d.%02d. · %s", l.date.dayOfMonth, l.date.monthValue, if (l.income) (l.kind ?: IncomeKind.SONSTIGES).label else l.category.label) +
                        (who(l.personId)?.let { " · $it" } ?: "") + (if (l.fixed) " · regelmäßig" else "")
                    Row2(
                        l.title.ifBlank { l.category.label },
                        (if (l.income) "+ " else "") + Calc.eur(l.cents),
                        if (l.income) IncomeGreen else androidx.compose.ui.graphics.Color.Unspecified,
                        sub = detail,
                        onClick = if (entry != null) ({ entryDialog = entry }) else if (l.fixed) ({ sub = 1; recDialog = d.recurrings.firstOrNull { it.id == l.sourceId } }) else null
                    )
                }
            }
        } else {
            val exp = d.recurrings.filter { !it.income }.sumOf { Calc.perMonth(it) }
            val inc = d.recurrings.filter { it.income }.sumOf { Calc.perMonth(it) }
            HCard("Regelmäßige Zahlungen und Einnahmen", bg = TileToday) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("Ausgaben je Monat", Calc.eur(Math.round(exp)))
                    Stat("Einnahmen je Monat", Calc.eur(Math.round(inc)))
                }
                Hint("Regelmäßige Zahlungen zählen automatisch in dem Monat, in dem sie fällig sind. Beträge sind hier als Monatsdurchschnitt gerechnet.")
            }
            Button(onClick = { newRec = true }) { Text("+ Regelmäßige Zahlung oder Einnahme") }
            if (d.recurrings.isEmpty()) Hint("Noch nichts angelegt. Es gibt Vorlagen wie Grundsteuer, Gebäudeversicherung, Gehalt oder Rente.")
            HCard(null, bg = O9, dark = true) {
                d.recurrings.sortedWith(compareBy({ it.income }, { it.title })).forEach { r ->
                    Row2(
                        r.title, (if (r.income) "+ " else "") + Calc.eur(r.cents),
                        if (r.income) IncomeGreen else androidx.compose.ui.graphics.Color.Unspecified,
                        sub = r.interval.label + " · " + (if (r.income) (r.incomeKind ?: IncomeKind.SONSTIGES).label else r.category.label) +
                            (who(r.personId)?.let { " · $it" } ?: "") + " · ab " + Calc.dateText(r.start) + (r.end?.let { " bis " + Calc.dateText(it) } ?: ""),
                        onClick = { recDialog = r }
                    )
                }
            }
        }
    }

    if (newEntry || entryDialog != null) {
        val isNew = entryDialog == null
        EntryDialog(
            initial = entryDialog ?: draft,
            isNew = isNew,
            persons = d.persons,
            defaultDay = defaultDay,
            newId = vm::newId,
            onSave = { vm.saveEntry(it); newEntry = false; entryDialog = null; draft = null },
            onDelete = { entryDialog?.let { e -> vm.deleteEntry(e.id) }; newEntry = false; entryDialog = null; draft = null },
            onDismiss = { newEntry = false; entryDialog = null; draft = null }
        )
    }
    if (newRec || recDialog != null) {
        RecurringDialog(
            initial = recDialog,
            persons = d.persons,
            newId = vm::newId,
            onSave = { vm.saveRecurring(it); newRec = false; recDialog = null },
            onDelete = { recDialog?.let { r -> vm.deleteRecurring(r.id) }; newRec = false; recDialog = null },
            onDismiss = { newRec = false; recDialog = null }
        )
    }
    if (importOpen) {
        ImportDialog(
            vm = vm,
            defaultDay = defaultDay,
            onDraft = { draft = it; entryDialog = null; newEntry = true; importOpen = false },
            onDone = { importOpen = false }
        )
    }
}

@Composable
private fun EntryDialog(
    initial: Entry?, isNew: Boolean, persons: List<Person>, defaultDay: Long, newId: () -> String,
    onSave: (Entry) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var amount by remember { mutableStateOf(initial?.let { Calc.centsText(it.cents) } ?: "") }
    var date by remember { mutableStateOf(initial?.date ?: defaultDay) }
    var cat by remember { mutableStateOf(initial?.category ?: Category.EINKAUF) }
    var income by remember { mutableStateOf(initial?.income ?: false) }
    var kind by remember { mutableStateOf(initial?.incomeKind ?: IncomeKind.LOHN) }
    var person by remember { mutableStateOf(initial?.personId) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    val id = remember { initial?.id ?: newId() }
    val cents = Calc.parseCents(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Neue Buchung" else "Buchung ändern") },
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
                if (income) {
                    Text("Quelle", fontSize = 12.sp)
                    Choice(IncomeKind.values().toList(), kind, { it.label }) { kind = it }
                } else {
                    Text("Kategorie", fontSize = 12.sp)
                    Choice(Category.values().toList(), cat, { it.label }) { cat = it }
                }
                PersonChoice(persons, if (income) "Wessen Einnahme?" else "Wer hat gezahlt?", person) { person = it }
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Notiz (optional)") }, modifier = Modifier.fillMaxWidth())
                if (!isNew) TextButton(onClick = onDelete) { Text("Buchung löschen", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = cents != null && cents > 0, onClick = {
                onSave(Entry(id, date, title.trim(), cents ?: 0L, if (income) Category.SONSTIGES else cat, income, note.trim(), person, if (income) kind else null))
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

private data class Preset(val title: String, val cat: Category, val interval: Interval, val income: Boolean = false, val kind: IncomeKind? = null)

private val presets = listOf(
    Preset("Gehalt", Category.SONSTIGES, Interval.MONTHLY, true, IncomeKind.LOHN),
    Preset("Rente", Category.SONSTIGES, Interval.MONTHLY, true, IncomeKind.RENTE),
    Preset("Zinsen / Dividende", Category.SONSTIGES, Interval.QUARTERLY, true, IncomeKind.ZINSEN),
    Preset("Mieteinnahmen", Category.SONSTIGES, Interval.MONTHLY, true, IncomeKind.MIETE),
    Preset("Kindergeld", Category.SONSTIGES, Interval.MONTHLY, true, IncomeKind.SONSTIGES),
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
private fun RecurringDialog(initial: Recurring?, persons: List<Person>, newId: () -> String, onSave: (Recurring) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var amount by remember { mutableStateOf(initial?.let { Calc.centsText(it.cents) } ?: "") }
    var cat by remember { mutableStateOf(initial?.category ?: Category.FIXKOSTEN) }
    var income by remember { mutableStateOf(initial?.income ?: false) }
    var kind by remember { mutableStateOf(initial?.incomeKind ?: IncomeKind.LOHN) }
    var person by remember { mutableStateOf(initial?.personId) }
    var interval by remember { mutableStateOf(initial?.interval ?: Interval.MONTHLY) }
    var start by remember { mutableStateOf(initial?.start ?: LocalDate.now().toEpochDay()) }
    var hasEnd by remember { mutableStateOf(initial?.end != null) }
    var end by remember { mutableStateOf(initial?.end ?: LocalDate.now().plusYears(1).toEpochDay()) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    val cents = Calc.parseCents(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Neue regelmäßige Zahlung" else "Regelmäßige Zahlung ändern") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initial == null) {
                    Text("Vorlagen", fontSize = 12.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        presets.forEach { p ->
                            AssistChip(onClick = {
                                title = p.title; cat = p.cat; interval = p.interval; income = p.income
                                if (p.kind != null) kind = p.kind
                            }, label = { Text(p.title, fontSize = 12.sp) })
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Einnahme (z. B. Gehalt)", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Switch(checked = income, onCheckedChange = { income = it })
                }
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Bezeichnung") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it }, label = { Text("Betrag je Zahlung in Euro") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                )
                Text("Rhythmus", fontSize = 12.sp)
                Choice(Interval.values().toList(), interval, { it.label }) { interval = it }
                DateField("Erste Fälligkeit", start) { start = it }
                if (income) {
                    Text("Quelle", fontSize = 12.sp)
                    Choice(IncomeKind.values().toList(), kind, { it.label }) { kind = it }
                } else {
                    Text("Kategorie", fontSize = 12.sp)
                    Choice(Category.values().filter { it != Category.EINKAUF }, cat, { it.label }) { cat = it }
                }
                PersonChoice(persons, if (income) "Wessen Einnahme?" else "Wer zahlt?", person) { person = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Endet am", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Switch(checked = hasEnd, onCheckedChange = { hasEnd = it })
                }
                if (hasEnd) DateField("Letzte Fälligkeit bis", end) { end = it }
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Notiz (optional)") }, modifier = Modifier.fillMaxWidth())
                if (initial != null) TextButton(onClick = onDelete) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = cents != null && cents > 0 && title.isNotBlank(), onClick = {
                onSave(
                    Recurring(
                        initial?.id ?: newId(), title.trim(), cents ?: 0L, if (income) Category.SONSTIGES else cat, interval, start,
                        if (hasEnd) end else null, note.trim(), income, if (income) kind else null, person
                    )
                )
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
