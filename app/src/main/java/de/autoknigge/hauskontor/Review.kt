package de.autoknigge.hauskontor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.YearMonth

fun groupKey(title: String): String = Receipts.keyOf(title).ifBlank { title.lowercase().trim().take(24) }

/** Mehrere Buchungen desselben Zahlers oder Empfängers. */
class Cand(val key: String, val income: Boolean, val entries: List<Entry>) {
    val last: Entry get() = entries.last()
    val id: String get() = key + "|" + income

    /** Abstände in Tagen zwischen den Buchungen. */
    fun gaps(): List<Int> = entries.zipWithNext { a, b -> (b.date - a.date).toInt() }

    /**
     * Vermuteter Rhythmus: Jeder Abstand wird dem nächsten Rhythmus (30, 91, 182, 365 Tage) zugeordnet, auch wenn Monate fehlen
     * (60 Tage = zwei Monate). Der häufigste Rhythmus gewinnt. Null bei nur einer Buchung oder ohne erkennbares Muster.
     */
    fun guess(): Interval? {
        val g = gaps()
        if (g.isEmpty()) return null
        val votes = HashMap<Interval, Int>()
        g.forEach { d ->
            val best = Interval.values().minByOrNull { iv ->
                val base = iv.months * 30.4
                val k = Math.max(1.0, Math.round(d / base).toDouble())
                Math.abs(d - k * base) / (k * base) + (k - 1) * 0.02
            }
            if (best != null) {
                val base = best.months * 30.4
                val k = Math.max(1.0, Math.round(d / base).toDouble())
                if (Math.abs(d - k * base) / (k * base) < 0.2) votes[best] = (votes[best] ?: 0) + 1
            }
        }
        return votes.maxByOrNull { it.value }?.key
    }

    /** Kurze Begründung für die Anzeige. */
    fun why(): String {
        val g = gaps()
        if (g.isEmpty()) return "Nur eine Buchung, Rhythmus bitte selbst wählen."
        val iv = guess() ?: return "Abstände unregelmäßig (" + g.takeLast(4).joinToString(", ") + " Tage), Rhythmus bitte selbst wählen."
        return "Erkannt: " + iv.label + " (Abstände " + g.takeLast(4).joinToString(", ") + " Tage)"
    }
}

/** Alle Zahler und Empfänger, beginnend mit der letzten Zahlung rückwärts. Schon regelmäßige und abgelehnte fehlen. */
fun reviewCandidates(d: Data): List<Cand> {
    val covered = d.recurrings.map { groupKey(it.title) + "|" + it.income }.toSet()
    return d.entries.filter { it.title.isNotBlank() }
        .groupBy { groupKey(it.title) to it.income }
        .map { (k, list) -> Cand(k.first, k.second, list.sortedBy { it.date }) }
        .filter { it.id !in covered && it.id !in d.dismissed }
        .sortedByDescending { it.last.date }
}

/** Nächster Termin nach der letzten Zahlung; war der schon lange fällig, wird weitergerückt. */
private fun nextDue(last: Long, iv: Interval): Long {
    var next = Calc.day(last).plusMonths(iv.months.toLong())
    val today = LocalDate.now()
    while (next.plusDays(31).isBefore(today)) next = next.plusMonths(iv.months.toLong())
    return next.toEpochDay()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecurringReviewDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val cands = reviewCandidates(vm.data)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Forest, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Regelmäßige Zahlungen finden", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Gold)
                Text(
                    "Du entscheidest, was regelmäßig ist. Die Liste beginnt mit den letzten Zahlungen und geht rückwärts. Tippe, wie oft die Zahlung kommt, oder „Nein“. " +
                        "Deine bisherigen Buchungen bleiben unverändert. Die regelmäßige Zahlung gilt ab dem nächsten Termin und erscheint dann in der Geld-Vorschau.",
                    fontSize = 13.sp, color = Ink
                )
                if (cands.isEmpty()) Text("Keine weiteren Zahlungen zum Prüfen.", fontSize = 14.sp, color = Ink)
                else Text(cands.size.toString() + " Zahler und Empfänger offen", fontSize = 12.sp, color = Muted)
                cands.take(40).forEach { c ->
                    val l = c.last
                    val cents = c.entries.map { it.cents }
                    val range = if (cents.min() != cents.max()) " · " + Calc.eur(cents.min()) + " bis " + Calc.eur(cents.max()) else ""
                    HCard(null, bg = if (c.income) CardTeal else O9, dark = !c.income) {
                        Row2(
                            l.title.ifBlank { c.key }, (if (c.income) "+ " else "") + Calc.eur(l.cents),
                            sub = "zuletzt " + Calc.dateText(l.date) + " · " + c.entries.size + " Buchung" + (if (c.entries.size == 1) "" else "en") + range
                        )
                        val g = c.guess()
                        Text(c.why(), fontSize = 12.sp, color = LocalMuted.current)
                        fun make(iv: Interval) = vm.saveRecurring(
                            Recurring(vm.newId(), l.title, l.cents, l.category, iv, nextDue(l.date, iv), null, l.note, l.income, l.incomeKind, l.personId)
                        )
                        if (g != null) Button(modifier = Modifier.fillMaxWidth(), onClick = { make(g) }) { Text("✓ " + g.label + " übernehmen") }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (g != null) Text("anders:", fontSize = 12.sp, color = LocalMuted.current, modifier = Modifier.padding(top = 10.dp))
                            Interval.values().filter { it != g }.forEach { iv ->
                                androidx.compose.material3.AssistChip(onClick = { make(iv) }, label = { Text(iv.label, fontSize = 13.sp) })
                            }
                            OutlinedButton(onClick = { vm.dismissGroup(c.id) }) { Text("Nein", color = LocalInk.current) }
                        }
                    }
                }
                if (cands.size > 40) Text("Die nächsten erscheinen, sobald du diese entschieden hast.", fontSize = 12.sp, color = Muted)
                if (vm.data.dismissed.isNotEmpty()) TextButton(onClick = { vm.resetDismissed() }) { Text("Abgelehnte wieder anzeigen (" + vm.data.dismissed.size + ")", color = Ink) }
                TextButton(onClick = onDismiss) { Text("Fertig", color = Gold, fontSize = 16.sp) }
            }
        }
    }
}

private fun sgn(c: Long): String = (if (c > 0) "+ " else if (c < 0) "− " else "") + Calc.eur(Math.abs(c))

/** Geld-Vorschau: welche regelmäßigen Zahlungen kommen wann, und wie viel muss dafür auf dem Konto sein. */
@Composable
fun CashPreview(vm: MainViewModel, ym: YearMonth, onMonth: (Int) -> Unit, onRecurring: (String) -> Unit = {}) {
    val d = vm.data
    val fixed = Calc.monthLines(d, ym).filter { it.fixed }.sortedBy { it.date }

    if (fixed.isEmpty()) Hint("Für " + Calc.monthText(ym) + " sind keine regelmäßigen Zahlungen eingetragen.")
    else {
        var run = 0L
        var low = 0L
        val rows = fixed.map { l ->
            run += if (l.income) l.cents else -l.cents
            low = minOf(low, run)
            l to run
        }
        val out = fixed.filter { !it.income }.sumOf { it.cents }
        val inn = fixed.filter { it.income }.sumOf { it.cents }
        HCard("Geld-Vorschau " + Calc.monthText(ym), bg = CardTeal) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("Abbuchungen", Calc.eur(out))
                Stat("Eingänge", Calc.eur(inn))
                Stat("Bleibt", sgn(inn - out))
            }
            Text(
                if (low < 0) "Zu Monatsbeginn müssen mindestens " + Calc.eur(-low) + " auf dem Konto sein, damit alle regelmäßigen Zahlungen gedeckt sind."
                else "Die regelmäßigen Eingänge decken die Abbuchungen jederzeit im Monat.",
                fontSize = 13.sp, color = LocalInk.current, fontWeight = FontWeight.SemiBold
            )
            rows.forEach { (l, bal) ->
                Row2(
                    String.format("%02d.%02d. ", l.date.dayOfMonth, l.date.monthValue) + l.title.ifBlank { l.category.label },
                    (if (l.income) "+ " else "− ") + Calc.eur(l.cents),
                    sub = "danach gegenüber Monatsbeginn: " + sgn(bal),
                    onClick = { onRecurring(l.sourceId) }
                )
            }
        }
    }

    // Die nächsten Monate im Überblick
    HCard("Die nächsten 12 Monate", bg = O9, dark = true) {
        Hint("Nur regelmäßige Zahlungen und Einnahmen. Tippe auf einen Monat für die Einzelheiten.")
        val start = YearMonth.now()
        (0..11).forEach { i ->
            val m = start.plusMonths(i.toLong())
            val ls = Calc.monthLines(d, m).filter { it.fixed }.sortedBy { it.date }
            var r = 0L
            var lo = 0L
            ls.forEach { l -> r += if (l.income) l.cents else -l.cents; lo = minOf(lo, r) }
            val o = ls.filter { !it.income }.sumOf { it.cents }
            val n = ls.filter { it.income }.sumOf { it.cents }
            Row2(
                Calc.monthText(m), "− " + Calc.eur(o),
                sub = "Eingänge " + Calc.eur(n) + (if (lo < 0) " · Konto zu Monatsbeginn mind. " + Calc.eur(-lo) else " · gedeckt"),
                onClick = { onMonth((m.year * 12 + m.monthValue) - (ym.year * 12 + ym.monthValue)) }
            )
        }
    }
}
