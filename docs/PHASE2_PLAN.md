# Phase 2 – Editor für Windows: Plan zur Freigabe

Stand: 2026-11-01 · **Umgesetzt** (Branch `phase2-editor`).

Abweichung bei der Umsetzung: Statt eines eigenen Compose-Multiplatform-Moduls `:hexmap` liegt der gemeinsame Kartencode in `shared/hexmap/` und wird von App und Editor als zusätzlicher Quellordner eingebunden – die verwendeten Compose-APIs sind auf beiden Plattformen identisch, und `:model`/`:engine` mussten so nicht auf Multiplatform umgestellt werden.

**Änderung gegenüber dem ersten Entwurf:** Der Editor läuft als **Desktop-Programm unter Windows**. Die Android-App bleibt der **Player** am Spieltisch. Das Kampagnenformat ändert sich nicht: Der Editor erzeugt dieselben JSON-Dateien, die die App importiert.

---

## 1. Technik: Compose Multiplatform Desktop (Vorschlag)

- Kotlin auf der JVM mit Compose for Desktop (JetBrains, Version 1.9). Das ist dasselbe UI-Toolkit wie in der App.
- **Die Module `:model`, `:validation` und `:engine` laufen unverändert mit.** Validator und Engine sind reines Kotlin ohne Android. Der Editor prüft also exakt so wie die App, und es gibt keine zweite Implementierung, die auseinanderlaufen kann.
- Alternativen und warum nicht:
  - **Web-App:** Validator und Engine müssten nach Kotlin/JS portiert oder neu geschrieben werden.
  - **WPF/.NET:** komplette Neuimplementierung in C#.

### Modulstruktur danach

| Modul | Plattform | Inhalt |
|---|---|---|
| `:model`, `:validation`, `:engine` | JVM | wie bisher |
| `:editing` (neu) | JVM | Bearbeitungslogik (siehe 2.), mit Tests |
| `:hexmap` (neu) | Compose Multiplatform (Android + Desktop) | Hex-Geometrie und Kartenzeichnung, gemeinsam für App und Editor |
| `:app` | Android | Player (wie bisher, nutzt dann `:hexmap`) |
| `:editor` (neu) | Desktop (Windows) | Editor |

### Auslieferung auf Windows
- **Vorschlag:** Ein GitHub-Actions-Workflow baut bei jedem Push auf einem Windows-Runner ein **Installationspaket (.msi)** mit eingebauter Java-Laufzeit. Du lädst es unter „Actions“ bzw. als Release herunter und installierst es; ein separates Java brauchst du nicht.
- Alternativ kannst du lokal auf Windows mit `gradlew :editor:run` (braucht JDK 17+) bauen.
- Hier in der Cloud-Umgebung (Linux) baue und teste ich den Editor normal. Screenshots rendere ich headless, so wie bei der App. Das .msi selbst lässt sich nur unter Windows erzeugen, daher der Workflow.

### Vom PC aufs Handy
- Der Editor speichert ganz normale `.json`-Dateien, wo du willst.
- Die App importiert sie wie bisher über den Android-Dateidialog. Der kann direkt auf Google Drive oder OneDrive zugreifen, sofern die jeweilige App installiert ist. Am bequemsten speicherst du also im Cloud-Ordner und importierst am Handy.
- Eine automatische Synchronisierung gehört nicht zu Phase 2.

---

## 2. Modul `:editing` (reines Kotlin, mit Tests)

| Operation | Verhalten |
|---|---|
| `create…` | legt das Objekt mit eindeutiger, sprechender ID an („Alter Brunnen“ → `tok_alter_brunnen`, bei Kollision `_2`) |
| `rename(id, neueId)` | ändert die ID **und alle Verweise** |
| `usages(id)` | Rückverweise („Benutzt von …“) |
| `delete(id)` | entfernt das Objekt; noch vorhandene Verweise werden vorher angezeigt und bleiben danach als Validierungsfehler sichtbar |
| Kacheln | platzieren, verschieben, drehen, Seite wechseln; Token und Gegner wandern mit, Feldkonflikte werden gemeldet |
| Undo/Redo | Verlauf aus unveränderlichen Kampagnen-Schnappschüssen |

---

## 3. Editor-Oberfläche (Desktop, Maus und Tastatur)

Ein Fenster, drei Spalten:

```
┌──────────────┬───────────────────────────┬──────────────────┐
│ Struktur     │ Bearbeitung               │ Prüfung          │
│ ▸ Kampagne   │ Formular des gewählten    │ Fehler/Hinweise, │
│ ▸ Szenario   │ Objekts – oder die Karte  │ Klick springt    │
│   ▸ Kapitel  │                           │ zum Objekt       │
│   ▸ Events   │                           │                  │
│   ▸ Trigger  │                           │ „Benutzt von“    │
│   ▸ Ziele …  │                           │                  │
└──────────────┴───────────────────────────┴──────────────────┘
```

- **Datei:** Neu, Öffnen, Speichern, Speichern unter, zuletzt geöffnet. Material-Katalog wählen. Dateien dürfen mit Fehlern gespeichert werden; die App nimmt nur fehlerfreie an.
- **Live-Validierung** nach jeder Änderung (entprellt): Fehlermarken in der Struktur, Fehlertext am Feld, Liste rechts.
- **Tastenkürzel:** Strg+S, Strg+Z/Strg+Y, Entf, Strg+F (Objekt suchen).
- **Kampagne:** Titel, Beschreibung, Szenarien, Startszenario, **Übergänge** als Tabelle (Szenario + Auflösung → Folgeszenario).
- **Szenario:** Titel, Einleitung, Bedrohung, Startkapitel, Startziel; Listen und Formulare für Kapitel, Events, Trigger, Ziele, Gegnergruppen, Auflösungen.

### Karteneditor
- Kachelpalette aus dem Katalog (gefiltert auf vorhandene Erweiterungen), **per Drag & Drop** auf die Karte. Mit der Maus ist das zuverlässiger als auf dem Handy, deshalb Ziehen statt Antippen.
- Gewählte Kachel: **R** drehen, **F** Seite wechseln, **Entf** löschen, ziehen zum Verschieben. Überlappungen erscheinen rot.
- Mausrad zoomt, mittlere Maustaste oder Leertaste+Ziehen verschiebt die Ansicht.
- Kapitel-Umschalter: Frühere Kapitel werden blass angezeigt, spätere ausgeblendet.
- Token und Gegner: Werkzeug wählen, Feld anklicken, dann Eigenschaften im Formular. Vorhandene Token lassen sich auf andere Felder ziehen.

### Event-Editor
- Schritte als Liste: hinzufügen über ein Typmenü, umsortieren per Ziehen oder Alt+↑/↓.
- Verweisfelder sind Auswahllisten des passenden Typs mit „Neu anlegen …“. Freie ID-Eingabe gibt es nicht.
- Die Regel „Auswahl/Probe/Ende nur am Schluss“ setzt die Oberfläche durch.

### Probespielen im Editor
Ein einfacher Spieldialog nutzt dieselbe Engine: Token anklicken, Meldungen bestätigen, Erfolge eingeben, Runde beenden. So lässt sich ein Szenario am PC durchklicken, ohne es aufs Handy zu kopieren.

---

## 4. Katalog-Editor (Vorschlag: in Phase 2, weil er am PC gut funktioniert)
- Kacheln anlegen (ID, aufgedruckte Nummer, Erweiterung), **Form je Seite durch Anklicken von Feldern auf einem Hex-Raster zeichnen**, Seite B per Knopf als Spiegelung von A vorbelegen.
- Gegnertypen und Erweiterungen (mit „im Besitz“) pflegen.
- Speichert die `material.json`, die du dann auch in die App importierst.

---

## 5. Auswirkungen auf die App
- Der Hex-Zeichencode wandert nach `:hexmap` und wird von App und Editor gemeinsam genutzt. Für die App ändert sich sichtbar nichts.
- Ansonsten nichts: Die App bleibt reiner Player mit Import.

## 6. Umsetzungsschritte (nach jedem: baut + Tests grün)
1. `:editing`: IDs, Verweise, Umbenennen, Löschen + Tests
2. `:editing`: Kachel-/Token-Operationen, Undo/Redo + Tests
3. `:hexmap` aus der App herauslösen (Compose Multiplatform), App nutzt es; Screenshot-Tests der App bleiben grün
4. `:editor`-Gerüst: Fenster, Datei öffnen/speichern, Katalog laden, Struktur-Baum, Prüfungsliste; GitHub-Actions-Workflow für das Windows-.msi
5. Formulare: Kampagne, Übergänge, Szenario, Ziele, Gegner, Auflösungen, Trigger
6. Event-Editor
7. Karteneditor (Kacheln, Drag & Drop, Drehen, Seiten), dann Token/Gegner
8. Katalog-Editor
9. Probespielen im Editor
10. Abschluss: headless Screenshots der Editor-Ansichten; ein Test baut das Beispielszenario komplett über `:editing` nach und erwartet null Befunde

## 7. Nicht in Phase 2
Automatische Synchronisierung PC ↔ Handy, Ablauf-Graph als Diagramm, eigene Kachelfotos.

## 8. Offene Fragen
1. **Technik:** Compose Multiplatform Desktop wie oben – einverstanden?
2. **Windows-Installer** über GitHub Actions bauen lassen (empfohlen) oder lokal selbst bauen?
3. **Katalog-Editor** in Phase 2 mit aufnehmen (empfohlen)?
4. **Phase 3 (KI-Generator):** Soll er dann ebenfalls in den Windows-Editor statt in die App? Dafür spricht, dass Generieren, Prüfen und Nachbearbeiten am selben Ort stattfinden und der API-Schlüssel nur auf dem PC liegt.
