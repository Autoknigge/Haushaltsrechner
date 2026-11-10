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

    private fun toEpoch(d: String, m: String, y: String?, today: LocalDate, hint: Int? = null): Long? = try {
        val year = when {
            y == null || y.isEmpty() -> hint ?: today.year
            y.length == 2 -> 2000 + y.toInt()
            else -> y.toInt()
        }
        var date = LocalDate.of(year, m.toInt(), d.toInt())
        if ((y == null || y.isEmpty()) && hint == null && date.isAfter(today.plusDays(7))) date = date.minusYears(1)
        date.toEpochDay()
    } catch (e: Exception) {
        null
    }

    /** Jahr des Auszugs, wenn die Buchungszeilen keins nennen: aus „Auszug 2025“ oder dem häufigsten vollständigen Datum (Zeitraum). */
    private fun detectYear(rows: List<String>): Int? {
        val auszug = Regex("(?i)(?:auszug|abrechnung|zeitraum|umsätze).{0,40}?(20\\d{2})")
        rows.firstNotNullOfOrNull { auszug.find(it)?.groupValues?.get(1)?.toIntOrNull() }?.let { return it }
        val full = Regex("(?<![\\d.])\\d{1,2}\\.\\d{1,2}\\.(20\\d{2})(?!\\d)")
        return rows.flatMap { full.findAll(it).map { m -> m.groupValues[1].toInt() }.toList() }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    }

    /** Zeilen mit Salden oder Summen sind keine Zahlungen. */
    private val balanceRx = Regex("saldo|kontostand|anfangsbestand|endbestand|schlusssaldo|tagessaldo|summe|umsätze gesamt", RegexOption.IGNORE_CASE)

    /**
     * Zahlungen aus den Zeilen eines Kontoauszugs. Eine Zeile mit Datum am Anfang beginnt eine neue Buchung.
     * Folgezeilen ohne Datum (Verwendungszweck) gehören dazu; steht der Betrag erst dort, wird er dort gelesen.
     */
    fun statement(rows: List<String>, today: LocalDate = LocalDate.now()): List<Found> = statementFull(rows, today).first

    /** Betrag in einer Zeile: Cent, Einnahme, Rest des Textes. */
    fun amountIn(row: String): Triple<Long, Boolean, String>? {
        val am = amountRx(true).find(row) ?: return null
        val sign = am.groupValues[1] + am.groupValues[4]
        return Triple(toCents(am.groupValues[2], am.groupValues[3]), sign.contains('+') || sign.contains('H'), row.replace(am.value, " ").replace(dateRx, " ").replace(Regex("\\s+"), " ").trim())
    }

    /** Wie [statement], liefert aber zusätzlich die Zeilen mit Betrag, die keiner Buchung zugeordnet werden konnten. */
    private val months = listOf("jan", "feb", "mär", "mar", "apr", "mai", "jun", "jul", "aug", "sep", "okt", "nov", "dez")
    private val monthNo = mapOf("jan" to 1, "feb" to 2, "mär" to 3, "mar" to 3, "apr" to 4, "mai" to 5, "jun" to 6, "jul" to 7, "aug" to 8, "sep" to 9, "okt" to 10, "nov" to 11, "dez" to 12)
    private val isoRx = Regex("(\\d{4})-(\\d{2})-(\\d{2})")
    private val nameRx = Regex("(?<![\\d])(\\d{1,2})\\.?\\s+(${months.joinToString("|")})[a-zäöü]*\\.?\\s+(\\d{4})", RegexOption.IGNORE_CASE)

    /** Andere Datumsformen (2025-10-01, 1. Okt. 2025) werden in 01.10.2025 umgeschrieben. */
    private fun normalizeDates(row: String): String =
        row.replace(isoRx) { "${it.groupValues[3]}.${it.groupValues[2]}.${it.groupValues[1]}" }
            .replace(nameRx) { m -> "%02d.%02d.%s".format(m.groupValues[1].toInt(), monthNo[m.groupValues[2].lowercase().take(3)] ?: 1, m.groupValues[3]) }

    fun statementFull(rowsIn: List<String>, today: LocalDate = LocalDate.now()): Pair<List<Found>, List<String>> {
        val rows = rowsIn.map { normalizeDates(it) }
        val yearHint = detectYear(rows)
        class Cur(val date: Long, val parts: MutableList<String>, var cents: Long? = null, var income: Boolean = false)
        val out = ArrayList<Found>()
        val rx = amountRx(true)
        val left = ArrayList<String>()
        var pending: Pair<Long, Boolean>? = null
        var cur: Cur? = null
        fun finish() {
            val c = cur ?: return
            val cents = c.cents
            if (cents != null && cents > 0) {
                var title = c.parts.joinToString(" ").replace(dateRx, " ").replace(Regex("\\s+"), " ").trim().trim('-', '+', '|').trim()
                if (title.length > 160) title = title.take(160)
                out.add(Found(c.date, title, cents, c.income))
            }
            cur = null
        }
        for (row in rows) {
            if (balanceRx.containsMatchIn(row)) continue
            val dm = dateRx.find(row)
            val am = rx.findAll(row).firstOrNull { dm == null || it.range.first > dm.range.last - 1 } ?: rx.find(row)
            // Zweites Datum (Wertstellung) steht oft in der Folgezeile derselben Buchung: kein neuer Posten.
            val rowDate = dm?.let { toEpoch(it.groupValues[1], it.groupValues[2], it.groupValues[3], today, yearHint) }
            val sameDay = rowDate != null && cur?.date == rowDate && am == null
            val startsBooking = dm != null && !sameDay && (dm.range.first <= 3 || am != null)
            if (startsBooking) {
                finish()
                val date = toEpoch(dm!!.groupValues[1], dm.groupValues[2], dm.groupValues[3], today, yearHint) ?: continue
                val c = Cur(date, mutableListOf())
                var text = row
                if (am != null) {
                    val sign = am.groupValues[1] + am.groupValues[4]
                    c.income = sign.contains('+') || sign.contains('H')
                    c.cents = toCents(am.groupValues[2], am.groupValues[3])
                    text = text.replace(am.value, " ")
                } else pending?.let { c.cents = it.first; c.income = it.second }
                pending = null
                c.parts.add(text.replace(dm.value, " "))
                cur = c
            } else {
                val c = cur
                var text = row
                if (am != null) {
                    val sign = am.groupValues[1] + am.groupValues[4]
                    val inc = sign.contains('+') || sign.contains('H')
                    val cents = toCents(am.groupValues[2], am.groupValues[3])
                    val rest = text.replace(am.value, " ")
                    if (c != null && c.cents == null) {
                        c.income = inc
                        c.cents = cents
                        text = rest
                    } else if (rest.count { it.isLetter() } < 3) {
                        // Betrag steht allein in einer Zeile: gehört vermutlich zur nächsten Buchung.
                        pending = cents to inc
                        continue
                    } else left.add(row)
                }
                if (c != null && c.parts.size < 8) c.parts.add(text)
            }
        }
        finish()
        return out.distinct() to left
    }

    /** Für die Fehlersuche: Buchstaben werden zu x, lange Zahlen (IBAN, Konto) zu 9; Datum, Beträge und Aufbau bleiben. */
    fun anonymize(row: String): String =
        row.replace(Regex("[A-ZÄÖÜ]")) { "X" }.replace(Regex("[a-zäöüß]")) { "x" }.replace(Regex("\\d{7,}")) { "9".repeat(it.value.length) }

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
        bestRule(rules, title, false)?.let { return Category(it.category) }
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
        bestRule(rules, title, true)?.let { return IncomeKind(it.category) }
        return when {
            listOf("kindergeld", "familienkasse").any { t.contains(it) } -> IncomeKind.KINDERGELD
            listOf("gehalt", "lohn", "bezüge", "besoldung").any { t.contains(it) } -> IncomeKind.LOHN
            listOf("rente", "pension", "versorgung").any { t.contains(it) } -> IncomeKind.RENTE
            listOf("zins", "dividende", "ertrag").any { t.contains(it) } -> IncomeKind.ZINSEN
            listOf("miete", "mieter").any { t.contains(it) } -> IncomeKind.MIETE
            else -> IncomeKind.SONSTIGES
        }
    }

    private val generic = setOf(
        "lastschrift", "gutschrift", "überweisung", "ueberweisung", "übertrag", "dauerauftrag", "kartenzahlung", "sepa", "basislastschrift",
        "folgelastschrift", "entgelt", "abschluss", "referenz", "mandat", "rechnung", "zahlung", "gmbh", "ihre", "belastung", "umbuchung",
        "eingang", "ausgang", "end-to-end-ref", "mandatsref", "core", "und", "der", "die", "das", "von", "für", "bei"
    )

    /** Bausteine eines Buchungstextes zum Antippen: Wörter und Nummern. */
    fun tokens(title: String): List<String> =
        title.split(Regex("[\\s/,;:|]+")).map { it.trim('.', '-', '+', '(', ')') }
            .filter { it.length >= 3 && it.lowercase() !in generic && it.any { c -> c.isLetterOrDigit() } }
            .distinctBy { it.lowercase() }.take(14)

    /** Vorschlag für das Kriterium: die ersten beiden Namenswörter, meist der Absender. Nummern fügt man bei Bedarf selbst hinzu. */
    fun keyOf(title: String): String {
        val words = title.lowercase().split(Regex("[^a-zäöüß]+")).filter { it.length >= 3 && it !in generic }
        return words.take(2).joinToString(" ")
    }

    private fun terms(key: String): List<String> = key.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }

    /** Das Kriterium trifft, wenn alle seine Teile im Text vorkommen ("karl meyer 123" trifft nicht "Karl Meyer 345"). */
    fun matches(key: String, title: String): Boolean {
        val ts = terms(key)
        val t = title.lowercase()
        return ts.isNotEmpty() && ts.all { t.contains(it) }
    }

    /** Die passendste Regel: je mehr Teile, desto genauer; bei Gleichstand die neuere. */
    fun bestRule(rules: List<Rule>, title: String, income: Boolean): Rule? =
        rules.withIndex().filter { it.value.income == income && matches(it.value.key, title) }
            .maxWithOrNull(compareBy({ terms(it.value.key).size }, { it.value.key.length }, { it.index }))?.value
}
