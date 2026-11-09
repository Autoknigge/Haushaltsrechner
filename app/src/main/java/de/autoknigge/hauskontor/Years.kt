package de.autoknigge.hauskontor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

private fun e0(c: Long): String = String.format(Locale.GERMANY, "%,.0f €", c / 100.0)
private fun signed0(c: Long): String = (if (c > 0) "+" else if (c < 0) "−" else "±") + String.format(Locale.GERMANY, "%,.0f €", Math.abs(c) / 100.0)
private fun pct(a: Double, b: Double): String = if (a == 0.0) "–" else String.format(Locale.GERMANY, "%+.0f %%", (b - a) / Math.abs(a) * 100.0).replace("-", "−")

/** Zeitraum aus [n] Monaten (1 = Monat, 3 = Quartal, 12 = Jahr). Er wird über eine laufende Nummer angesprochen. */
private fun idxOf(ym: YearMonth, n: Int) = (ym.year * 12 + ym.monthValue - 1) / n
private fun startOf(idx: Int, n: Int): YearMonth = (idx * n).let { YearMonth.of(it / 12, it % 12 + 1) }
private fun labelOf(idx: Int, n: Int): String {
    val s = startOf(idx, n)
    return when (n) {
        1 -> Calc.monthText(s)
        3 -> "Q" + ((s.monthValue - 1) / 3 + 1) + " " + s.year
        else -> s.year.toString()
    }
}

private fun linesOf(d: Data, idx: Int, n: Int): List<Posting> =
    (0 until n).flatMap { Calc.monthLines(d, startOf(idx, n).plusMonths(it.toLong())) }

private fun usageOf(d: Data, m: Meter, idx: Int, n: Int): Double? {
    val rs = Calc.readingsOf(d, m.id)
    var sum = 0.0
    var any = false
    for (i in 0 until n) Calc.consumption(rs, startOf(idx, n).plusMonths(i.toLong()))?.let { sum += it; any = true }
    return if (any) sum else null
}

@Composable
private fun Stepper(label: String, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onPrev) { Text("‹", fontSize = 22.sp) }
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        TextButton(onClick = onNext) { Text("›", fontSize = 22.sp) }
    }
}

@Composable
fun YearsScreen(vm: MainViewModel) {
    var sub by rememberSaveable { mutableIntStateOf(0) }
    Scroller {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = sub == 0, onClick = { sub = 0 }, label = { Text("Jahr") })
            FilterChip(selected = sub == 1, onClick = { sub = 1 }, label = { Text("Mehrere Jahre") })
            FilterChip(selected = sub == 2, onClick = { sub = 2 }, label = { Text("Vergleich") })
        }
        when (sub) {
            0 -> YearView(vm.data)
            1 -> MultiYearView(vm.data)
            else -> CompareView(vm.data)
        }
    }
}

@Composable
private fun YearView(d: Data) {
    var year by rememberSaveable { mutableIntStateOf(LocalDate.now().year) }
    Stepper(year.toString(), { year-- }, { year++ })
    val lines = (1..12).flatMap { Calc.monthLines(d, YearMonth.of(year, it)) }
    val s = Calc.sum(lines)

    HCard("Jahresübersicht $year", bg = TileToday) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("Ausgaben", Calc.eur(s.expense))
            Stat("Einnahmen", Calc.eur(s.income))
            Stat("Saldo", (if (s.income - s.expense >= 0) "+ " else "− ") + Calc.eur(Math.abs(s.income - s.expense)))
        }
        Hint("Ø Ausgaben je Monat: " + Calc.eur(s.expense / 12) + (if (s.income > 0) " · Ø Einnahmen: " + Calc.eur(s.income / 12) else ""))
    }

    if (s.byCategory.isNotEmpty()) {
        HCard("Ausgaben nach Kategorie", bg = O4, dark = true) {
            val max = s.byCategory.values.maxOrNull() ?: 1L
            s.byCategory.forEach { (cat, c) ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(cat.label, fontSize = 13.sp, color = LocalInk.current, modifier = Modifier.weight(1f))
                        Text(Calc.eur(c) + " · " + (if (s.expense > 0) Math.round(c * 100.0 / s.expense) else 0) + " %", fontSize = 13.sp, color = LocalInk.current)
                    }
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(LocalInk.current.copy(alpha = 0.18f))) {
                        Box(Modifier.fillMaxWidth((c.toFloat() / max).coerceIn(0.02f, 1f)).fillMaxHeight().background(LocalInk.current))
                    }
                }
            }
        }
    }

    if (s.incomeByKind.isNotEmpty()) {
        HCard("Einnahmen nach Quelle", bg = CardTeal) {
            s.incomeByKind.forEach { (k, c) -> Row2(k.label, Calc.eur(c)) }
        }
    }

    HCard("Monate im Überblick", bg = O9, dark = true) {
        Row(Modifier.fillMaxWidth()) {
            Text("", Modifier.weight(0.8f))
            Text("Ausgaben", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = LocalInk.current, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            Text("Einnahmen", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = LocalInk.current, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
        (1..12).forEach { m ->
            val ms = Calc.sum(Calc.monthLines(d, YearMonth.of(year, m)))
            Row(Modifier.fillMaxWidth()) {
                Text(Calc.monthShort(m), fontSize = 13.sp, color = LocalInk.current, modifier = Modifier.weight(0.8f))
                Text(Calc.eur(ms.expense), fontSize = 13.sp, color = LocalInk.current, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                Text(Calc.eur(ms.income), fontSize = 13.sp, color = LocalInk.current, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            }
        }
    }

    if (d.meters.isNotEmpty()) {
        HCard("Verbrauch $year", bg = CardGreen, dark = true) {
            d.meters.forEach { m ->
                val u = usageOf(d, m, year, 12)
                Row2(m.name, if (u == null) "–" else Calc.num(u, 0) + " " + m.type.unit, sub = if (u == null) "keine Ablesungen" else "aus den Ablesungen berechnet")
            }
        }
    }
}

@Composable
private fun MultiYearView(d: Data) {
    val now = LocalDate.now().year
    val firstYears = listOfNotNull(
        d.entries.minOfOrNull { Calc.day(it.date).year },
        d.recurrings.minOfOrNull { Calc.day(it.start).year },
        d.readings.minOfOrNull { Calc.day(it.date).year }
    )
    val from = (firstYears.minOrNull() ?: now).coerceAtMost(now)
    val years = (from..now).toList()
    val sums = years.associateWith { y -> Calc.sum((1..12).flatMap { Calc.monthLines(d, YearMonth.of(y, it)) }) }
    val max = (sums.values.maxOfOrNull { it.expense } ?: 0L).coerceAtLeast(1L)

    HCard("Ausgaben je Jahr", bg = TileToday) {
        years.forEach { y ->
            val e = sums[y]!!.expense
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(y.toString(), fontSize = 13.sp, color = LocalInk.current, modifier = Modifier.weight(1f))
                    Text(Calc.eur(e), fontSize = 13.sp, color = LocalInk.current)
                }
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(LocalInk.current.copy(alpha = 0.2f))) {
                    Box(Modifier.fillMaxWidth((e.toFloat() / max).coerceIn(0.02f, 1f)).fillMaxHeight().background(LocalInk.current))
                }
            }
        }
        if (years.size == 1) Hint("Sobald Daten aus weiteren Jahren da sind, siehst du hier den Verlauf.")
    }

    HCard("Jahre im Vergleich", bg = O9, dark = true) {
        years.reversed().forEach { y ->
            val s = sums[y]!!
            val prev = sums[y - 1]
            Row2(
                y.toString(),
                "Saldo " + signed0(s.income - s.expense),
                sub = "Ausgaben " + e0(s.expense) + " · Einnahmen " + e0(s.income) +
                    (if (prev != null && prev.expense > 0) " · Ausgaben zum Vorjahr " + signed0(s.expense - prev.expense) + " (" + pct(prev.expense.toDouble(), s.expense.toDouble()) + ")" else "")
            )
        }
        Hint("Regelmäßige Zahlungen zählen ab ihrem ersten Fälligkeitstag. Für das laufende Jahr sind die noch kommenden Fixkosten schon mit enthalten.")
    }
}

@Composable
private fun CmpRow(label: String, a: Double, b: Double, fmt: (Double) -> String, higherIsBad: Boolean, bold: Boolean = false) {
    val delta = b - a
    val color = when {
        Math.abs(delta) < 0.005 -> LocalInk.current
        (delta > 0) == higherIsBad -> BadRed
        else -> IncomeGreen
    }
    val w = if (bold) FontWeight.Bold else FontWeight.Normal
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, fontWeight = w, color = LocalInk.current, modifier = Modifier.weight(1.2f))
        Text(fmt(a), fontSize = 12.sp, fontWeight = w, color = LocalInk.current, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        Text(fmt(b), fontSize = 12.sp, fontWeight = w, color = LocalInk.current, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        Column(Modifier.weight(1.1f), horizontalAlignment = Alignment.End) {
            Text((if (delta > 0.004) "+" else if (delta < -0.004) "−" else "±") + fmt(Math.abs(delta)), fontSize = 12.sp, fontWeight = w, color = color)
            Text(pct(a, b), fontSize = 10.5.sp, color = color)
        }
    }
}

@Composable
private fun CompareView(d: Data) {
    var type by rememberSaveable { mutableIntStateOf(0) }
    var aBack by rememberSaveable { mutableIntStateOf(1) }
    var bBack by rememberSaveable { mutableIntStateOf(0) }
    val n = when (type) { 0 -> 1; 1 -> 3; else -> 12 }
    val cur = idxOf(YearMonth.now(), n)
    val ia = cur - aBack
    val ib = cur - bBack

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = type == 0, onClick = { type = 0; aBack = 1; bBack = 0 }, label = { Text("Monate") })
        FilterChip(selected = type == 1, onClick = { type = 1; aBack = 1; bBack = 0 }, label = { Text("Quartale") })
        FilterChip(selected = type == 2, onClick = { type = 2; aBack = 1; bBack = 0 }, label = { Text("Jahre") })
    }
    Text("Zeitraum A", fontSize = 12.sp, color = Muted)
    Stepper(labelOf(ia, n), { aBack++ }, { aBack-- })
    Text("Zeitraum B", fontSize = 12.sp, color = Muted)
    Stepper(labelOf(ib, n), { bBack++ }, { bBack-- })

    val la = linesOf(d, ia, n)
    val lb = linesOf(d, ib, n)
    val sa = Calc.sum(la)
    val sb = Calc.sum(lb)

    HCard("Vergleich", bg = O9, dark = true) {
        Row(Modifier.fillMaxWidth()) {
            Text("", Modifier.weight(1.2f))
            Text("A", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LocalInk.current, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            Text("B", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LocalInk.current, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            Text("B gegen A", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LocalInk.current, modifier = Modifier.weight(1.1f), textAlign = TextAlign.End)
        }
        val money: (Double) -> String = { e0(Math.round(it)) }
        CmpRow("Ausgaben", sa.expense.toDouble(), sb.expense.toDouble(), money, true, true)
        (sa.byCategory.keys + sb.byCategory.keys).forEach { c ->
            val x = sa.byCategory[c] ?: 0L
            val y = sb.byCategory[c] ?: 0L
            if (x != 0L || y != 0L) CmpRow("  " + c.label, x.toDouble(), y.toDouble(), money, true)
        }
        CmpRow("Einnahmen", sa.income.toDouble(), sb.income.toDouble(), money, false, true)
        (sa.incomeByKind.keys + sb.incomeByKind.keys).forEach { k ->
            val x = sa.incomeByKind[k] ?: 0L
            val y = sb.incomeByKind[k] ?: 0L
            if (x != 0L || y != 0L) CmpRow("  " + k.label, x.toDouble(), y.toDouble(), money, false)
        }
        CmpRow("Saldo", (sa.income - sa.expense).toDouble(), (sb.income - sb.expense).toDouble(), money, false, true)
        Hint("A und B sind zwei Zeiträume gleicher Länge. Rot heißt: mehr Ausgaben beziehungsweise weniger Einnahmen als in A.")
    }

    val withUse = d.meters.mapNotNull { m ->
        val x = usageOf(d, m, ia, n)
        val y = usageOf(d, m, ib, n)
        if (x == null && y == null) null else Triple(m, x ?: 0.0, y ?: 0.0)
    }
    if (withUse.isNotEmpty()) {
        HCard("Verbrauch im Vergleich", bg = CardGreen, dark = true) {
            withUse.forEach { (m, x, y) -> CmpRow(m.name, x, y, { Calc.num(it, 0) + " " + m.type.unit }, true) }
        }
    }
}
