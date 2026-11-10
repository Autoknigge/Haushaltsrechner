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
    var wipeMonth by remember { mutableStateOf(false) }
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
            val monthEntries = d.entries.filter { Calc.day(it.date).let { x -> YearMonth.from(x) == ym } }
            if (monthEntries.isNotEmpty()) TextButton(onClick = { wipeMonth = true }) {
                Text("🗑 Buchungen dieses Monats löschen …", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
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
            val sugg = suggestRecurring(d)
            if (sugg.isNotEmpty()) {
                HCard("Erkannt: das sieht regelmäßig aus", bg = O6, dark = true) {
                    Hint("Mehrere gleiche Buchungen im festen Rhythmus. Mit „Übernehmen“ werden die einzelnen Buchungen durch eine regelmäßige Zahlung ersetzt.")
                    sugg.forEach { s ->
                        val l = s.entries.last()
                        Row2(
                            l.title, (if (l.income) "+ " else "") + Calc.eur(s.cents),
                            sub = s.interval.label + " · " + s.entries.size + " Buchungen seit " + Calc.dateText(s.entries.first().date),
                            onClick = { vm.acceptSuggestion(s) }
                        )
                    }
                    Hint("Antippen übernimmt den Vorschlag.")
                }
            }
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
                partnersFor(d.recurrings.filter { !it.income }.map { it.title to it.category }).forEach { PartnerButton(it) }
            }
            if (partnersFor(d.recurrings.filter { !it.income }.map { it.title to it.category }).isNotEmpty()) PartnerNote()
        }
    }

    if (newEntry || entryDialog != null) {
        val isNew = entryDialog == null
        EntryDialog(
            initial = entryDialog ?: draft,
            isNew = isNew,
            data = d,
            persons = d.persons,
            defaultDay = defaultDay,
            newId = vm::newId,
            onSave = { vm.saveEntry(it); newEntry = false; entryDialog = null; draft = null },
            onRule = { r, ap -> vm.learn(listOf(r)); if (ap) vm.applyRule(r) },
            onRegular = { e, iv -> vm.deleteEntry(e.id); vm.saveRecurring(Recurring(vm.newId(), e.title, e.cents, e.category, iv, e.date, null, e.note, e.income, e.incomeKind, e.personId)); newEntry = false; entryDialog = null; draft = null; sub = 1 },
            onDelete = { entryDialog?.let { e -> vm.deleteEntry(e.id) }; newEntry = false; entryDialog = null; draft = null },
            onDismiss = { newEntry = false; entryDialog = null; draft = null }
        )
    }
    if (newRec || recDialog != null) {
        RecurringDialog(
            initial = recDialog,
            data = d,
            persons = d.persons,
            newId = vm::newId,
            onSave = { vm.saveRecurring(it); newRec = false; recDialog = null },
            onRule = { r, ap -> vm.learn(listOf(r)); if (ap) vm.applyRule(r) },
            onDelete = { recDialog?.let { r -> vm.deleteRecurring(r.id) }; newRec = false; recDialog = null },
            onDismiss = { newRec = false; recDialog = null }
        )
    }
    if (wipeMonth) {
        val inMonth = d.entries.filter { YearMonth.from(Calc.day(it.date)) == ym }
        val imported = inMonth.count { it.note == "per Foto importiert" }
        AlertDialog(
            onDismissRequest = { wipeMonth = false },
            title = { Text("Buchungen löschen · " + Calc.monthText(ym)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("In diesem Monat gibt es " + inMonth.size + " einzelne Buchungen, davon " + imported + " per Foto oder PDF importiert. Regelmäßige Zahlungen bleiben bestehen. Das Löschen lässt sich nicht rückgängig machen.", fontSize = 13.sp)
                    if (imported > 0) TextButton(onClick = { vm.deleteMonth(ym, true); wipeMonth = false }) {
                        Text("Nur die importierten löschen ($imported)", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { vm.deleteMonth(ym, false); wipeMonth = false }) {
                        Text("Alle " + inMonth.size + " Buchungen löschen", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { wipeMonth = false }) { Text("Abbrechen") } }
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
    initial: Entry?, isNew: Boolean, data: Data, persons: List<Person>, defaultDay: Long, newId: () -> String,
    onSave: (Entry) -> Unit, onRule: (Rule, Boolean) -> Unit, onRegular: (Entry, Interval) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var amount by remember { mutableStateOf(initial?.let { Calc.centsText(it.cents) } ?: "") }
    var date by remember { mutableStateOf(initial?.date ?: defaultDay) }
    var cat by remember { mutableStateOf((initial?.category ?: Category.EINKAUF).label) }
    var touched by remember { mutableStateOf(initial != null) }
    var ruleKey by remember {
        mutableStateOf(if (initial == null) "" else Receipts.bestRule(data.rules, initial.title, initial.income)?.key ?: "")
    }
    var ruleTouched by remember { mutableStateOf(false) }
    var applyAll by remember { mutableStateOf(false) }
    var income by remember { mutableStateOf(initial?.income ?: false) }
    var kind by remember { mutableStateOf((initial?.incomeKind ?: IncomeKind.LOHN).label) }
    var person by remember { mutableStateOf(initial?.personId) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    val id = remember { initial?.id ?: newId() }
    val cents = Calc.parseCents(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Neue Buchung" else "Buchung ändern") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it
                    if (!ruleTouched && initial == null) ruleKey = Receipts.keyOf(it)
                    if (!touched) {
                        if (income) kind = Receipts.guessIncome(it, data.rules).label
                        else Receipts.guessCategory(it, data.rules).let { g -> if (g != Category.SONSTIGES) cat = g.label }
                    } }, label = { Text("Bezeichnung (z. B. Supermarkt)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
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
                    CategoryField("Kategorie der Einnahme (z. B. Kindergeld)", kind, data.incomeCategories().map { it.label }) { kind = it; touched = true; if (!ruleTouched && ruleKey.isBlank()) ruleKey = Receipts.keyOf(title) }
                } else {
                    CategoryField("Kategorie (z. B. Einkauf, Hobby)", cat, data.expenseCategories().map { it.label }) { cat = it; touched = true; if (!ruleTouched && ruleKey.isBlank()) ruleKey = Receipts.keyOf(title) }
                }
                RuleField(ruleKey, { ruleKey = it; ruleTouched = true }, applyAll, { applyAll = it }, title)
                PersonChoice(persons, if (income) "Wessen Einnahme?" else "Wer hat gezahlt?", person) { person = it }
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Notiz (optional)") }, modifier = Modifier.fillMaxWidth())
                if (!isNew && cents != null && cents > 0) {
                    Text("Diese Buchung ist regelmäßig:", fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(Interval.MONTHLY, Interval.QUARTERLY, Interval.YEARLY).forEach { iv ->
                            TextButton(onClick = {
                                onRegular(
                                    Entry(id, date, title.trim(), cents, if (income) Category.SONSTIGES else Category(cat.trim().ifBlank { Category.SONSTIGES.label }), income, note.trim(), person, if (income) IncomeKind(kind.trim().ifBlank { IncomeKind.SONSTIGES.label }) else null),
                                    iv
                                )
                            }) { Text(iv.label, fontSize = 12.sp) }
                        }
                    }
                }
                if (!isNew) TextButton(onClick = onDelete) { Text("Buchung löschen", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = cents != null && cents > 0, onClick = {
                val e = Entry(id, date, title.trim(), cents ?: 0L, if (income) Category.SONSTIGES else Category(cat.trim().ifBlank { Category.SONSTIGES.label }), income, note.trim(), person, if (income) IncomeKind(kind.trim().ifBlank { IncomeKind.SONSTIGES.label }) else null)
                if (ruleKey.isNotBlank()) onRule(Rule(ruleKey.trim().lowercase(), if (income) (e.incomeKind ?: IncomeKind.SONSTIGES).label else e.category.label, income), applyAll)
                onSave(e)
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
    Preset("Kindergeld", Category.SONSTIGES, Interval.MONTHLY, true, IncomeKind.KINDERGELD),
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
private fun RecurringDialog(initial: Recurring?, data: Data, persons: List<Person>, newId: () -> String, onSave: (Recurring) -> Unit, onRule: (Rule, Boolean) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var amount by remember { mutableStateOf(initial?.let { Calc.centsText(it.cents) } ?: "") }
    var cat by remember { mutableStateOf((initial?.category ?: Category.FIXKOSTEN).label) }
    var touched by remember { mutableStateOf(initial != null) }
    var ruleKey by remember {
        mutableStateOf(if (initial == null) "" else Receipts.bestRule(data.rules, initial.title, initial.income)?.key ?: "")
    }
    var ruleTouched by remember { mutableStateOf(false) }
    var applyAll by remember { mutableStateOf(false) }
    var income by remember { mutableStateOf(initial?.income ?: false) }
    var kind by remember { mutableStateOf((initial?.incomeKind ?: IncomeKind.LOHN).label) }
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
                                title = p.title; cat = p.cat.label; interval = p.interval; income = p.income; touched = true
                                if (p.kind != null) kind = p.kind.label
                            }, label = { Text(p.title, fontSize = 12.sp) })
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Einnahme (z. B. Gehalt)", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Switch(checked = income, onCheckedChange = { income = it })
                }
                OutlinedTextField(value = title, onValueChange = { title = it
                    if (!ruleTouched && initial == null) ruleKey = Receipts.keyOf(it)
                    if (!touched) {
                        if (income) kind = Receipts.guessIncome(it, data.rules).label
                        else Receipts.guessCategory(it, data.rules).let { g -> if (g != Category.SONSTIGES) cat = g.label }
                    } }, label = { Text("Bezeichnung") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it }, label = { Text("Betrag je Zahlung in Euro") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                )
                Text("Rhythmus", fontSize = 12.sp)
                Choice(Interval.values().toList(), interval, { it.label }) { interval = it }
                DateField("Erste Fälligkeit", start) { start = it }
                if (income) {
                    CategoryField("Kategorie der Einnahme (z. B. Kindergeld)", kind, data.incomeCategories().map { it.label }) { kind = it; touched = true; if (!ruleTouched && ruleKey.isBlank()) ruleKey = Receipts.keyOf(title) }
                } else {
                    CategoryField("Kategorie (z. B. Fixkosten, Strom)", cat, data.expenseCategories().map { it.label }) { cat = it; touched = true; if (!ruleTouched && ruleKey.isBlank()) ruleKey = Receipts.keyOf(title) }
                }
                RuleField(ruleKey, { ruleKey = it; ruleTouched = true }, applyAll, { applyAll = it }, title)
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
                if (ruleKey.isNotBlank()) onRule(Rule(ruleKey.trim().lowercase(), (if (income) IncomeKind(kind.trim().ifBlank { IncomeKind.SONSTIGES.label }).label else Category(cat.trim().ifBlank { Category.SONSTIGES.label }).label), income), applyAll)
                onSave(
                    Recurring(
                        initial?.id ?: newId(), title.trim(), cents ?: 0L, if (income) Category.SONSTIGES else Category(cat.trim().ifBlank { Category.SONSTIGES.label }), interval, start,
                        if (hasEnd) end else null, note.trim(), income, if (income) IncomeKind(kind.trim().ifBlank { IncomeKind.SONSTIGES.label }) else null, person
                    )
                )
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
