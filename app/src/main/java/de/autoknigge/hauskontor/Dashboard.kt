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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.YearMonth

@Composable
fun DashboardScreen(vm: MainViewModel, ym: YearMonth, onMonth: (Int) -> Unit) {
    val d = vm.data
    val lines = Calc.monthLines(d, ym)
    val s = Calc.sum(lines)

    Scroller {
        MonthBar(ym, onMonth)

        HCard("Monatsübersicht") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("Ausgaben", Calc.eur(s.expense), Red)
                Stat("Einnahmen", Calc.eur(s.income), Green)
                Stat("Saldo", Calc.eur(s.income - s.expense), if (s.income - s.expense >= 0) Green else Red)
            }
            if (lines.isEmpty()) Hint("Noch keine Buchungen in diesem Monat. Lege unter „Buch“ Buchungen und Fixkosten an.")
        }

        if (s.byCategory.isNotEmpty()) {
            HCard("Ausgaben nach Kategorie") {
                val max = s.byCategory.values.maxOrNull() ?: 1L
                s.byCategory.forEach { (cat, cents) ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(cat.label, fontSize = 13.sp, color = Ink, modifier = Modifier.weight(1f))
                            Text(Calc.eur(cents), fontSize = 13.sp, color = Ink)
                        }
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Panel2)) {
                            Box(Modifier.fillMaxWidth((cents.toFloat() / max).coerceIn(0.02f, 1f)).fillMaxHeight().background(Gold))
                        }
                    }
                }
            }
        }

        val fixed = lines.filter { it.fixed }
        if (fixed.isNotEmpty()) {
            HCard("Fixkosten und Abbuchungen in diesem Monat") {
                fixed.sortedBy { it.date }.forEach {
                    Row2(it.title, Calc.eur(it.cents), sub = String.format("%02d.%02d. · %s", it.date.dayOfMonth, it.date.monthValue, it.category.label))
                }
                Row2("Summe", Calc.eur(fixed.sumOf { it.cents }), Gold)
            }
        }

        // Wohnnebenkosten über das ganze Jahr
        val year = ym.year
        val nk = (1..12).flatMap { m -> Calc.monthLines(d, YearMonth.of(year, m)) }.filter { it.category == Category.NEBENKOSTEN && !it.income }
        HCard("Wohnnebenkosten $year") {
            if (nk.isEmpty()) {
                Hint("Grundsteuer, Müllgebühren, Abwasser, Schornsteinfeger und Co. legst du unter „Buch“ → „Fixkosten“ an. Dann siehst du hier die Jahressumme.")
            } else {
                val total = nk.sumOf { it.cents }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("Summe im Jahr", Calc.eur(total), Gold)
                    Stat("Durchschnitt je Monat", Calc.eur(total / 12))
                }
                nk.groupBy { it.title }.forEach { (title, ls) -> Row2(title, Calc.eur(ls.sumOf { it.cents })) }
            }
        }

        if (d.meters.isNotEmpty()) {
            HCard("Verbrauch aus Zählerständen") {
                d.meters.forEach { m ->
                    val c = Calc.consumption(Calc.readingsOf(d, m.id), ym)
                    if (c == null) {
                        Row2(m.name, "–", sub = "keine Ablesungen für diesen Monat")
                    } else {
                        val cost = if (m.priceCt > 0 || m.baseEurMonth > 0) " · ca. " + Calc.eur(Math.round(Calc.cost(m, c) * 100)) else ""
                        Row2(m.name, Calc.num(c, 1) + " " + m.type.unit, sub = "geschätzt aus den Ablesungen$cost")
                    }
                }
                Hint("Der Verbrauch zwischen zwei Ablesungen wird gleichmäßig auf die Tage verteilt. Die Kosten sind eine Schätzung und stehen nicht in den Ausgaben oben.")
            }
        }

        HCard("Jahresverlauf $year") {
            val months = (1..12).map { m -> Calc.sum(Calc.monthLines(d, YearMonth.of(year, m))).expense }
            val max = (months.maxOrNull() ?: 0L).coerceAtLeast(1L)
            Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                months.forEachIndexed { i, c ->
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Box(
                            Modifier.fillMaxWidth().height((80 * c.toFloat() / max).dp.coerceAtLeast(2.dp)).clip(RoundedCornerShape(3.dp))
                                .background(if (i + 1 == ym.monthValue) Gold else Panel2)
                        )
                        Text(Calc.monthShort(i + 1).take(1), fontSize = 10.sp, color = Muted, modifier = Modifier.padding(top = 3.dp), textAlign = TextAlign.Center)
                    }
                }
            }
            Hint("Ausgaben je Monat, einschließlich der fälligen Fixkosten. Der gewählte Monat ist golden.")
        }
    }
}
