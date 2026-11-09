package de.autoknigge.hauskontor

import org.json.JSONArray
import org.json.JSONObject

/** Kategorie einer Ausgabe. Frei erweiterbar: jeder Name ist erlaubt. */
@JvmInline
value class Category(val label: String) {
    val name: String get() = label

    companion object {
        val FIXKOSTEN = Category("Fixkosten")
        val VERSICHERUNG = Category("Versicherung")
        val EINKAUF = Category("Einkauf")
        val ABBUCHUNG = Category("Abbuchung")
        val NEBENKOSTEN = Category("Wohnnebenkosten")
        val SONSTIGES = Category("Sonstiges")
        val defaults = listOf(FIXKOSTEN, VERSICHERUNG, EINKAUF, ABBUCHUNG, NEBENKOSTEN, SONSTIGES)

        /** Liest auch die alten Enum-Namen aus früheren Sicherungen. */
        fun of(s: String?, fallback: Category): Category {
            if (s.isNullOrBlank()) return fallback
            return defaults.firstOrNull { it.label == s } ?: when (s) {
                "FIXKOSTEN" -> FIXKOSTEN
                "VERSICHERUNG" -> VERSICHERUNG
                "EINKAUF" -> EINKAUF
                "ABBUCHUNG" -> ABBUCHUNG
                "NEBENKOSTEN" -> NEBENKOSTEN
                "SONSTIGES" -> SONSTIGES
                else -> Category(s)
            }
        }
    }
}

enum class Interval(val label: String, val months: Int) {
    MONTHLY("monatlich", 1),
    QUARTERLY("vierteljährlich", 3),
    HALF("halbjährlich", 6),
    YEARLY("jährlich", 12)
}

/** Kategorie (Quelle) einer Einnahme. Ebenfalls frei erweiterbar. */
@JvmInline
value class IncomeKind(val label: String) {
    val name: String get() = label

    companion object {
        val LOHN = IncomeKind("Lohn / Gehalt")
        val RENTE = IncomeKind("Rente / Pension")
        val ZINSEN = IncomeKind("Zinsen / Dividende")
        val MIETE = IncomeKind("Mieteinnahmen")
        val KINDERGELD = IncomeKind("Kindergeld")
        val SONSTIGES = IncomeKind("Sonstige Einnahmen")
        val defaults = listOf(LOHN, RENTE, ZINSEN, MIETE, KINDERGELD, SONSTIGES)

        fun of(s: String?, fallback: IncomeKind): IncomeKind {
            if (s.isNullOrBlank()) return fallback
            return defaults.firstOrNull { it.label == s } ?: when (s) {
                "LOHN" -> LOHN
                "RENTE" -> RENTE
                "ZINSEN" -> ZINSEN
                "MIETE" -> MIETE
                "SONSTIGES" -> SONSTIGES
                else -> IncomeKind(s)
            }
        }
    }
}

enum class SplitMode(val label: String) {
    EQUAL("Zu gleichen Teilen"),
    BY_INCOME("Nach Einkommen")
}

enum class MeterType(val label: String, val unit: String) {
    STROM("Strom", "kWh"),
    GAS("Gas", "m³"),
    WASSER("Wasser", "m³"),
    WAERME("Wärme", "kWh")
}

private inline fun <reified T : Enum<T>> enumOf(s: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == s } ?: fallback

/** Mitglied des Haushalts (Paar oder WG, bis zu vier Personen). */
data class Person(val id: String, val name: String) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name)

    companion object {
        const val MAX = 4
        fun fromJson(o: JSONObject) = Person(o.getString("id"), o.optString("name"))
    }
}

/**
 * Einzelne Buchung im Haushaltsbuch. [date] ist ein Epochentag, [cents] immer positiv.
 * [personId]: bei Ausgaben, wer gezahlt hat, bei Einnahmen, wessen Einnahme. Leer heißt gemeinsame Kasse.
 */
data class Entry(
    val id: String,
    val date: Long,
    val title: String,
    val cents: Long,
    val category: Category,
    val income: Boolean = false,
    val note: String = "",
    val personId: String? = null,
    val incomeKind: IncomeKind? = null
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("date", date).put("title", title)
        .put("cents", cents).put("category", category.name).put("income", income).put("note", note)
        .put("person", personId ?: JSONObject.NULL).put("kind", incomeKind?.name ?: JSONObject.NULL)

    companion object {
        fun fromJson(o: JSONObject) = Entry(
            o.getString("id"), o.getLong("date"), o.optString("title"), o.getLong("cents"),
            Category.of(o.optString("category"), Category.SONSTIGES), o.optBoolean("income"), o.optString("note"),
            if (o.isNull("person")) null else o.optString("person"),
            if (o.isNull("kind")) null else IncomeKind.of(o.optString("kind"), IncomeKind.SONSTIGES)
        )
    }
}

/** Wiederkehrende Zahlung (Fixkosten, Versicherung, Grundsteuer ...). Fällig ab [start], im Rhythmus [interval]. */
data class Recurring(
    val id: String,
    val title: String,
    val cents: Long,
    val category: Category,
    val interval: Interval,
    val start: Long,
    val end: Long? = null,
    val note: String = "",
    val income: Boolean = false,
    val incomeKind: IncomeKind? = null,
    val personId: String? = null
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("title", title).put("cents", cents)
        .put("category", category.name).put("interval", interval.name).put("start", start)
        .put("end", end ?: JSONObject.NULL).put("note", note).put("income", income)
        .put("kind", incomeKind?.name ?: JSONObject.NULL).put("person", personId ?: JSONObject.NULL)

    companion object {
        fun fromJson(o: JSONObject) = Recurring(
            o.getString("id"), o.optString("title"), o.getLong("cents"),
            Category.of(o.optString("category"), Category.FIXKOSTEN), enumOf(o.optString("interval"), Interval.MONTHLY),
            o.getLong("start"), if (o.isNull("end")) null else o.getLong("end"), o.optString("note"),
            o.optBoolean("income"),
            if (o.isNull("kind")) null else IncomeKind.of(o.optString("kind"), IncomeKind.SONSTIGES),
            if (o.isNull("person")) null else o.optString("person")
        )
    }
}

/** Zähler. [priceCt] ist der Arbeitspreis in ct je Einheit, [baseEurMonth] der Grundpreis je Monat. */
data class Meter(
    val id: String,
    val name: String,
    val type: MeterType,
    val number: String = "",
    val priceCt: Double = 0.0,
    val baseEurMonth: Double = 0.0
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("type", type.name)
        .put("number", number).put("priceCt", priceCt).put("base", baseEurMonth)

    companion object {
        fun fromJson(o: JSONObject) = Meter(
            o.getString("id"), o.optString("name"), enumOf(o.optString("type"), MeterType.STROM),
            o.optString("number"), o.optDouble("priceCt", 0.0), o.optDouble("base", 0.0)
        )
    }
}

data class Reading(
    val id: String,
    val meterId: String,
    val date: Long,
    val value: Double,
    val photo: String? = null,
    val viaOcr: Boolean = false,
    val note: String = ""
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("meter", meterId).put("date", date).put("value", value)
        .put("photo", photo ?: JSONObject.NULL).put("ocr", viaOcr).put("note", note)

    companion object {
        fun fromJson(o: JSONObject) = Reading(
            o.getString("id"), o.getString("meter"), o.getLong("date"), o.getDouble("value"),
            if (o.isNull("photo")) null else o.optString("photo"), o.optBoolean("ocr"), o.optString("note")
        )
    }
}

/** Gelernte Zuordnung: Taucht [key] im Namen des Zahlers oder Empfängers auf, wird [category] vorgeschlagen. */
data class Rule(val key: String, val category: String, val income: Boolean = false) {
    fun toJson(): JSONObject = JSONObject().put("key", key).put("category", category).put("income", income)
    companion object {
        fun fromJson(o: JSONObject): Rule {
            val inc = o.optBoolean("income")
            val c = o.optString("category")
            return Rule(o.getString("key"), if (inc) IncomeKind.of(c, IncomeKind.SONSTIGES).label else Category.of(c, Category.SONSTIGES).label, inc)
        }
    }
}

/** Alle Daten der App. Sie liegen als eine JSON-Datei im App-Speicher. */
data class Data(
    val entries: List<Entry> = emptyList(),
    val recurrings: List<Recurring> = emptyList(),
    val meters: List<Meter> = emptyList(),
    val readings: List<Reading> = emptyList(),
    val persons: List<Person> = emptyList(),
    val split: SplitMode = SplitMode.EQUAL,
    val household: String = "",
    val address: String = "",
    val rules: List<Rule> = emptyList()
) {
    /** Alle bekannten Ausgaben-Kategorien: Vorgaben, gelernte und bereits benutzte. */
    fun expenseCategories(): List<Category> =
        (Category.defaults + rules.filter { !it.income }.map { Category(it.category) } +
            entries.filter { !it.income }.map { it.category } + recurrings.filter { !it.income }.map { it.category }).distinct()

    /** Alle bekannten Einnahme-Kategorien. */
    fun incomeCategories(): List<IncomeKind> =
        (IncomeKind.defaults + rules.filter { it.income }.map { IncomeKind(it.category) } +
            entries.filter { it.income }.mapNotNull { it.incomeKind } + recurrings.filter { it.income }.mapNotNull { it.incomeKind }).distinct()

    fun toJson(): JSONObject = JSONObject()
        .put("version", 1)
        .put("entries", JSONArray().also { a -> entries.forEach { a.put(it.toJson()) } })
        .put("recurrings", JSONArray().also { a -> recurrings.forEach { a.put(it.toJson()) } })
        .put("meters", JSONArray().also { a -> meters.forEach { a.put(it.toJson()) } })
        .put("readings", JSONArray().also { a -> readings.forEach { a.put(it.toJson()) } })
        .put("persons", JSONArray().also { a -> persons.forEach { a.put(it.toJson()) } })
        .put("split", split.name)
        .put("household", household)
        .put("address", address)
        .put("rules", JSONArray().also { a -> rules.forEach { a.put(it.toJson()) } })

    companion object {
        private fun <T> list(o: JSONObject, key: String, f: (JSONObject) -> T): List<T> {
            val a = o.optJSONArray(key) ?: return emptyList()
            return (0 until a.length()).mapNotNull { i -> try { f(a.getJSONObject(i)) } catch (e: Exception) { null } }
        }

        fun fromJson(o: JSONObject) = Data(
            list(o, "entries", { Entry.fromJson(it) }), list(o, "recurrings", { Recurring.fromJson(it) }),
            list(o, "meters", { Meter.fromJson(it) }), list(o, "readings", { Reading.fromJson(it) }),
            list(o, "persons", { Person.fromJson(it) }), enumOf(o.optString("split"), SplitMode.EQUAL),
            o.optString("household"), o.optString("address"),
            list(o, "rules", { Rule.fromJson(it) })
        )
    }
}
