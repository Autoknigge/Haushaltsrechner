package de.autoknigge.hauskontor

import java.time.LocalDate

/** Eine erkannte Zahlung (Zeile eines Kontoauszugs). */
data class Found(val date: Long, val title: String, val cents: Long, val income: Boolean)

/** Ergebnis für einen einzelnen Beleg oder eine Rechnung. */
data class ReceiptGuess(val amounts: List<Long>, val date: Long?, val title: String)

/** Einfache Mustererkennung für Beleg und Kontoauszug. Die Ergebnisse sind Vorschläge und werden immer geprüft. */
object Receipts {
    private val dateRx = Regex("(?<![\\d.])(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4}|\\d{2})?(?![\\d])")
    private fun amountRx(commaOnly: Boolean): Regex {
        val sep = if (commaOnly) "," else "[.,]"
        return Regex("(?<![\\d.,])([+-]?)\\s?(\\d{1,3}(?:\\.\\d{3})+|\\d+)$sep(\\d{2})(?![\\d]|\\.\\d)\\s?([+-]|[SH](?![A-Za-z]))?")
    }

    private fun toCents(whole: String, dec: String): Long = (whole.replace(".", "").toLongOrNull() ?: 0L) * 100 + (dec.toLongOrNull() ?: 0L)

    private fun toEpoch(d: String, m: String, y: String?, today: LocalDate): Long? = try {
        val year = when {
            y == null || y.isEmpty() -> today.year
            y.length == 2 -> 2000 + y.toInt()
            else -> y.toInt()
        }
        var date = LocalDate.of(year, m.toInt(), d.toInt())
        if ((y == null || y.isEmpty()) && date.isAfter(today.plusDays(7))) date = date.minusYears(1)
        date.toEpochDay()
    } catch (e: Exception) {
        null
    }

    /** Zeilen mit Salden oder Summen sind keine Zahlungen. */
    private val balanceRx = Regex("saldo|kontostand|anfangsbestand|endbestand|schlusssaldo|übertrag|uebertrag|tagessaldo|summe|umsätze gesamt|guthaben|verfügbar", RegexOption.IGNORE_CASE)

    fun statement(rows: List<String>, today: LocalDate = LocalDate.now()): List<Found> {
        val out = ArrayList<Found>()
        val rx = amountRx(true)
        for (row in rows) {
            if (balanceRx.containsMatchIn(row)) continue
            val dm = dateRx.find(row) ?: continue
            val date = toEpoch(dm.groupValues[1], dm.groupValues[2], dm.groupValues[3], today) ?: continue
            val am = rx.findAll(row).firstOrNull { it.range.first > dm.range.last - 1 } ?: rx.find(row) ?: continue
            val sign = am.groupValues[1] + am.groupValues[4]
            val income = sign.contains('+') || sign.contains('H')
            val cents = toCents(am.groupValues[2], am.groupValues[3])
            if (cents <= 0) continue
            var title = row.replace(am.value, " ").replace(dm.value, " ")
            title = title.replace(dateRx, " ").replace(Regex("\\s+"), " ").trim().trim('-', '+', '|').trim()
            if (title.length > 60) title = title.take(60)
            out.add(Found(date, title, cents, income))
        }
        return out.distinct()
    }

    private val keywords = listOf("summe", "gesamt", "total", "zu zahlen", "endbetrag", "rechnungsbetrag", "brutto", "betrag", "bar", "visa", "ec")

    fun receipt(text: String, rows: List<String>, today: LocalDate = LocalDate.now()): ReceiptGuess {
        val rx = amountRx(false)
        val scored = ArrayList<Pair<Long, Int>>()
        for (row in rows) {
            val low = row.lowercase()
            val bonus = if (keywords.any { low.contains(it) }) 1 else 0
            rx.findAll(row).forEach { m ->
                val c = toCents(m.groupValues[2], m.groupValues[3])
                if (c > 0) scored.add(c to bonus)
            }
        }
        val amounts = scored.sortedWith(compareByDescending<Pair<Long, Int>> { it.second }.thenByDescending { it.first })
            .map { it.first }.distinct().take(6)
        var date: Long? = null
        for (row in rows) {
            val dm = dateRx.find(row) ?: continue
            date = toEpoch(dm.groupValues[1], dm.groupValues[2], dm.groupValues[3], today)
            if (date != null) break
        }
        val title = rows.firstOrNull { r -> r.count { it.isLetter() } >= 4 && r.count { it.isDigit() } <= 3 }?.trim()?.take(40) ?: ""
        return ReceiptGuess(amounts, date, title)
    }

    /** Zuordnung einer Kategorie: zuerst die gelernten Regeln, dann grobe Stichwörter. */
    fun guessCategory(title: String, rules: List<Rule> = emptyList()): Category {
        val t = title.lowercase()
        rules.lastOrNull { !it.income && it.key.isNotBlank() && t.contains(it.key.lowercase()) }?.let { return Category(it.category) }
        return when {
            listOf("versicherung", "allianz", "huk", "axa", "ergo").any { t.contains(it) } -> Category.VERSICHERUNG
            listOf("grundsteuer", "müll", "abwasser", "stadtwerke", "schornstein", "wasser", "strom", "energie", "heizung", "fernwärme", "erdgas").any { t.contains(it) } -> Category.NEBENKOSTEN
            listOf("rewe", "edeka", "aldi", "lidl", "penny", "netto", "dm ", "rossmann", "kaufland", "markt").any { t.contains(it) } -> Category.EINKAUF
            listOf("lastschrift", "abbuchung", "dauerauftrag", "miete", "rundfunk", "telekom", "vodafone").any { t.contains(it) } -> Category.ABBUCHUNG
            else -> Category.SONSTIGES
        }
    }

    /** Einnahme-Kategorie: zuerst gelernte Regeln, dann Stichwörter. */
    fun guessIncome(title: String, rules: List<Rule> = emptyList()): IncomeKind {
        val t = title.lowercase()
        rules.lastOrNull { it.income && it.key.isNotBlank() && t.contains(it.key.lowercase()) }?.let { return IncomeKind(it.category) }
        return when {
            listOf("kindergeld", "familienkasse").any { t.contains(it) } -> IncomeKind.KINDERGELD
            listOf("gehalt", "lohn", "bezüge", "besoldung").any { t.contains(it) } -> IncomeKind.LOHN
            listOf("rente", "pension", "versorgung").any { t.contains(it) } -> IncomeKind.RENTE
            listOf("zins", "dividende", "ertrag").any { t.contains(it) } -> IncomeKind.ZINSEN
            listOf("miete", "mieter").any { t.contains(it) } -> IncomeKind.MIETE
            else -> IncomeKind.SONSTIGES
        }
    }

    private val generic = setOf("lastschrift", "gutschrift", "überweisung", "ueberweisung", "dauerauftrag", "kartenzahlung", "sepa", "basislastschrift", "folgelastschrift", "entgelt", "abschluss", "referenz", "mandat", "rechnung", "zahlung", "gmbh", "ihre")

    /** Vorschlag für ein Stichwort aus dem Buchungstext: das längste Wort ohne Zahlen, das nicht allgemein ist. */
    fun keyOf(title: String): String =
        title.lowercase().split(Regex("[^a-zäöüß]+")).filter { it.length >= 3 && it !in generic }.maxByOrNull { it.length } ?: ""
}
