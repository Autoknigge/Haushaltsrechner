# Hauskontor

Android-App für Haushalt, Ausgaben und Zählerstände (Kotlin, Jetpack Compose). Paketname `de.autoknigge.hauskontor`.

## Funktionen

- **Zähler:** Strom, Gas, Wasser, Wärme. Ablesen per Foto (Texterkennung auf dem Gerät mit ML Kit, Vorschläge zum Antippen) oder von Hand, Foto als Beleg, Verbrauch und Kostenschätzung aus den Ablesungen.
- **Haushaltsbuch:** Buchungen (Ausgaben und Einnahmen) mit Kategorien, Fixkosten und Versicherungen als wiederkehrende Zahlungen (monatlich, vierteljährlich, halbjährlich, jährlich), Vorlagen wie Grundsteuer, Müllgebühren, Gebäudeversicherung.
- **Übersicht:** Monatsübersicht, Ausgaben nach Kategorie, Fixkosten des Monats, Wohnnebenkosten im Jahr, Verbrauch aus Zählerständen, Jahresverlauf.
- **Export:** CSV (Excel) und JSON-Sicherung.
- Alle Daten liegen als `hauskontor.json` im App-Speicher. Nichts wird hochgeladen.

## Datenmodell (`Model.kt`)

| Klasse | Felder |
|---|---|
| `Entry` | id, date (Epochentag), title, cents, category, income, note |
| `Recurring` | id, title, cents, category, interval, start, end?, note |
| `Meter` | id, name, type, number, priceCt, baseEurMonth |
| `Reading` | id, meterId, date, value, photo?, viaOcr, note |
| `Data` | Listen aller vier Klassen, wird als eine JSON-Datei gespeichert |

Beträge sind in Cent (`Long`). Eine wiederkehrende Zahlung zählt in dem Monat, in dem sie nach Rhythmus und erstem Fälligkeitstag fällig ist (`Calc.dueIn`).

## Bauen

GitHub Actions baut bei jedem Push auf `main` und legt das Artefakt „Hauskontor“ mit `Hauskontor.apk` und `Hauskontor.aab` ab. Die Version ist `1.0.<Build-Nummer>`, der Versionscode die Build-Nummer.

Secrets für die signierte Fassung (Repository, Settings, Secrets): `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Ohne sie wird mit dem Debug-Schlüssel signiert, das ist für Google Play nicht geeignet.

AdMob-Kennungen stehen in `app/build.gradle.kts` (öffentliche Kennungen) und lassen sich mit den Gradle-Properties `ADMOB_APP_ID`, `ADMOB_BANNER_ID`, `ADMOB_START_ID` überschreiben.

## Datenschutz

https://autoknigge.de/hauskontor/datenschutz.html
