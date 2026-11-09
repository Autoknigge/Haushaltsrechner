package de.autoknigge.hauskontor

import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** Eine Zeile im Monat: gebuchter Eintrag oder fällige wiederkehrende Zahlung. */
data class Posting(
    val date: LocalDate,
    val title: String,
    val cents: Long,
    val category: Category,
    val income: Boolean,
    val fixed: Boolean,
    val sourceId: String,
    val personId: String? = null,
    val kind: IncomeKind? = null
)

object Calc {
    fun eur(cents: Long): String = String.format(Locale.GERMANY, "%,.2f €", cents / 100.0)

    fun num(v: Double, digits: Int = 1): String = String.format(Locale.GERMANY, "%,." + digits + "f", v)

    /** "12,50", "12.50", "1.234,56 €" -> Cent. */
    fun parseCents(s: String): Long? {
        var t = s.trim().replace("€", "").replace(" ", "")
        if (t.isEmpty()) return null
        t = if (t.contains(',')) t.replace(".", "").replace(',', '.') else t
        val v = t.toDoubleOrNull() ?: return null
        if (v < 0) return null
        return Math.round(v * 100.0)
    }

    fun parseNumber(s: String): Double? {
        val t = s.trim().replace(" ", "")
        if (t.isEmpty()) return null
        return (if (t.contains(',')) t.replace(".", "").replace(',', '.') else t).toDoubleOrNull()
    }

    fun centsText(c: Long): String = String.format(Locale.GERMANY, "%.2f", c / 100.0)

    fun day(d: Long): LocalDate = LocalDate.ofEpochDay(d)

    fun dateText(d: Long): String = day(d).let { String.format(Locale.GERMANY, "%02d.%02d.%d", it.dayOfMonth, it.monthValue, it.year) }

    private val monthNames = arrayOf("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember")

    fun monthText(ym: YearMonth): String = monthNames[ym.monthValue - 1] + " " + ym.year

    fun monthShort(m: Int): String = monthNames[m - 1].take(3)

    /** Ist die wiederkehrende Zahlung in diesem Monat fällig? Der Tag richtet sich nach dem ersten Fälligkeitstag, am Monatsende gekürzt. */
    fun dueIn(r: Recurring, ym: YearMonth): LocalDate? {
        val s = day(r.start)
        val sm = YearMonth.from(s)
        if (ym.isBefore(sm)) return null
        val diff = (ym.year - sm.year) * 12 + (ym.monthValue - sm.monthValue)
        if (diff % r.interval.months != 0) return null
        val date = ym.atDay(minOf(s.dayOfMonth, ym.lengthOfMonth()))
        if (r.end != null && date.toEpochDay() > r.end) return null
        return date
    }

    fun monthLines(d: Data, ym: YearMonth): List<Posting> {
        val out = ArrayList<Posting>()
        for (e in d.entries) {
            if (YearMonth.from(day(e.date)) == ym) out.add(
                Posting(day(e.date), e.title, e.cents, e.category, e.income, false, e.id, e.personId, if (e.income) (e.incomeKind ?: IncomeKind.SONSTIGES) else null)
            )
        }
        for (r in d.recurrings) {
            val date = dueIn(r, ym) ?: continue
            out.add(Posting(date, r.title, r.cents, r.category, r.income, true, r.id, r.personId, if (r.income) (r.incomeKind ?: IncomeKind.SONSTIGES) else null))
        }
        return out.sortedByDescending { it.date }
    }

    data class Sum(val expense: Long, val income: Long, val byCategory: Map<Category, Long>, val incomeByKind: Map<IncomeKind, Long>)

    fun sum(lines: List<Posting>): Sum {
        var ex = 0L
        var inc = 0L
        val by = LinkedHashMap<Category, Long>()
        val ik = LinkedHashMap<IncomeKind, Long>()
        for (l in lines) {
            if (l.income) {
                inc += l.cents
                val k = l.kind ?: IncomeKind.SONSTIGES
                ik[k] = (ik[k] ?: 0L) + l.cents
            } else {
                ex += l.cents
                by[l.category] = (by[l.category] ?: 0L) + l.cents
            }
        }
        return Sum(ex, inc, by.entries.sortedByDescending { it.value }.associate { it.key to it.value },
            ik.entries.sortedByDescending { it.value }.associate { it.key to it.value })
    }

    data class Share(val person: Person, val income: Long, val paid: Long, val share: Long, val balance: Long)

    /** Kostenteilung im Haushalt: Wer hat wie viel gezahlt, wie viel wäre sein Anteil, wer überweist wem. Null bei weniger als zwei Personen. */
    fun settle(d: Data, lines: List<Posting>): Pair<List<Share>, List<String>>? {
        val ps = d.persons
        if (ps.size < 2) return null
        val inc = ps.associate { p -> p.id to lines.filter { it.income && it.personId == p.id }.sumOf { it.cents } }
        val paid = ps.associate { p -> p.id to lines.filter { !it.income && it.personId == p.id }.sumOf { it.cents } }
        val total = paid.values.sum()
        val incSum = inc.values.sum()
        val byIncome = d.split == SplitMode.BY_INCOME && incSum > 0
        val shares = ps.map { p ->
            val w = if (byIncome) (inc[p.id] ?: 0L).toDouble() / incSum else 1.0 / ps.size
            Share(p, inc[p.id] ?: 0L, paid[p.id] ?: 0L, Math.round(total * w), (paid[p.id] ?: 0L) - Math.round(total * w))
        }
        val creditors = shares.filter { it.balance > 0 }.map { it.person.name to it.balance }.toMutableList()
        val debtors = shares.filter { it.balance < 0 }.map { it.person.name to -it.balance }.toMutableList()
        val transfers = ArrayList<String>()
        var i = 0
        var j = 0
        while (i < debtors.size && j < creditors.size) {
            val amt = minOf(debtors[i].second, creditors[j].second)
            if (amt > 0) transfers.add(debtors[i].first + " überweist " + creditors[j].first + " " + eur(amt))
            debtors[i] = debtors[i].first to debtors[i].second - amt
            creditors[j] = creditors[j].first to creditors[j].second - amt
            if (debtors[i].second <= 0) i++
            if (j < creditors.size && creditors[j].second <= 0) j++
        }
        return shares to transfers
    }

    /** Monatsäquivalent einer wiederkehrenden Zahlung (Cent je Monat). */
    fun perMonth(r: Recurring): Double = r.cents.toDouble() / r.interval.months

    fun readingsOf(d: Data, meterId: String): List<Reading> = d.readings.filter { it.meterId == meterId }.sortedBy { it.date }

    /** Verbrauch im Monat: Der Verbrauch zwischen zwei Ablesungen wird gleichmäßig auf die Tage verteilt. Null, wenn keine Ablesungen den Monat abdecken. */
    fun consumption(rs: List<Reading>, ym: YearMonth): Double? {
        val from = ym.atDay(1).toEpochDay()
        val to = ym.plusMonths(1).atDay(1).toEpochDay()
        var sum = 0.0
        var covered = false
        for (i in 1 until rs.size) {
            val a = rs[i - 1]
            val b = rs[i]
            if (b.date <= a.date) continue
            val perDay = (b.value - a.value) / (b.date - a.date)
            val overlap = minOf(to, b.date) - maxOf(from, a.date)
            if (overlap > 0) {
                sum += perDay * overlap
                covered = true
            }
        }
        return if (covered) sum else null
    }

    fun cost(m: Meter, consumption: Double): Double = consumption * m.priceCt / 100.0 + m.baseEurMonth

    fun csv(d: Data): String {
        fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        val sb = StringBuilder("Datum;Titel;Kategorie;Art;Betrag in Euro;Person;Notiz\n")
        fun who(id: String?) = d.persons.firstOrNull { it.id == id }?.name ?: ""
        d.entries.sortedBy { it.date }.forEach {
            sb.append(dateText(it.date)).append(';').append(q(it.title)).append(';')
                .append(if (it.income) (it.incomeKind ?: IncomeKind.SONSTIGES).label else it.category.label).append(';')
                .append(if (it.income) "Einnahme" else "Ausgabe").append(';').append(centsText(it.cents)).append(';')
                .append(q(who(it.personId))).append(';').append(q(it.note)).append('\n')
        }
        d.recurrings.forEach {
            sb.append(dateText(it.start)).append(';').append(q(it.title)).append(';')
                .append(if (it.income) (it.incomeKind ?: IncomeKind.SONSTIGES).label else it.category.label).append(';')
                .append((if (it.income) "Regelmäßige Einnahme, " else "Wiederkehrend, ") + it.interval.label).append(';').append(centsText(it.cents)).append(';')
                .append(q(who(it.personId))).append(';').append(q(it.note)).append('\n')
        }
        sb.append("\nZähler;Datum;Stand;Einheit\n")
        d.meters.forEach { m ->
            readingsOf(d, m.id).forEach { r ->
                sb.append(q(m.name)).append(';').append(dateText(r.date)).append(';').append(String.format(Locale.GERMANY, "%.2f", r.value)).append(';').append(m.type.unit).append('\n')
            }
        }
        return sb.toString()
    }
}
