# Eva Arbeitszeit

Android-App, die automatisch erfasst, wann Eva an einer ihrer Stationen ankommt und wieder fährt:

- **Total Gütersloh** – Senner Straße 8, 33335 Gütersloh
- **Elan Herzebrock** – Clarholzer Straße 28, 33442 Herzebrock-Clarholz

## Was die App kann

- Erkennt Ankommen und Gehen automatisch im Hintergrund (Umkreis 150 m, einstellbar)
- Zeigt je Station: heute, diese Woche, dieser Monat – Stunden und Anzahl Besuche
- Monatsansicht mit Wochen (KW), Arbeitstagen und allen Einzelbesuchen
- Einträge antippen zum Ändern oder Löschen, vergessene Zeiten nachtragen
- Export als Tabelle (CSV, öffnet in Excel / Google Sheets)
- Kurzes Vorbeifahren (unter 3 Minuten) wird ignoriert, kurzes Rausgehen (unter 10 Minuten) zählt als ein Besuch
- Alle Daten bleiben nur auf dem Handy

## Einrichten auf dem Handy

1. APK aus „Releases“ herunterladen und öffnen, Installation aus unbekannter Quelle erlauben
2. App öffnen und den gelben Hinweisen folgen:
   - Standort erlauben → **„Immer zulassen“** wählen
   - Akku-Optimierung für die App ausschalten
   - Mitteilungen erlauben (optional)
3. Tipp: Beim ersten Mal an jeder Station unter **Einstellungen → „Standort = hier“** tippen. Dann ist der Punkt genau.

## Bauen

Jeder Push auf `main` baut über GitHub Actions automatisch eine neue APK und legt sie unter „Releases“ ab.
Lokal: `gradle assembleRelease` (Android SDK nötig).
