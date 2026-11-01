# Phase 2 – Editor: Plan zur Freigabe

Stand: 2026-11-01 · Noch kein Code. Baut auf Phase 1 auf (Branch `phase2-editor` zweigt von `phase1-skeleton` ab).

## 1. Ziel

Kampagnen vollständig in der App anlegen und bearbeiten: Kampagne → Szenarien → Kapitel/Kacheln/Token → Events/Trigger/Ziele/Gegner/Auflösungen, mit Live-Validierung, Probespielen und Export. Das Kampagnenformat bleibt **unverändert**; der Editor erzeugt dieselben JSON-Dateien wie bisher.

## 2. Architektur

### Neues Modul `:editing` (Kotlin/JVM, ohne Android) – Vorschlag
Reine Bearbeitungslogik auf den unveränderlichen Datenklassen, voll testbar:

| Operation | Verhalten |
|---|---|
| `create…` | legt Objekt mit **eindeutiger, sprechender ID** an (aus dem Titel: „Alter Brunnen“ → `tok_alter_brunnen`, bei Kollision `_2`) |
| `rename(id, neueId)` | ändert die ID **und alle Verweise** darauf (Events, Trigger, Ziele, Übergänge …) |
| `delete(id)` | liefert vorher die Liste der Verweise („wird benutzt von …“); Löschen entfernt das Objekt, Verweise bleiben als Validierungsfehler sichtbar |
| `usages(id)` | Rückverweise für die Anzeige „Benutzt von“ |
| Kacheln | platzieren, verschieben, drehen (Token/Gegner wandern mit, da kachel-lokal), Seite wechseln (Felder, die auf der neuen Seite nicht existieren, werden als Konflikt gemeldet) |
| Undo/Redo | Verlauf aus Kampagnen-Schnappschüssen (Datenklassen sind unveränderlich → billig) |

Begründung: Umbenennen/Löschen mit Verweisen ist fehleranfällig und gehört in Unit-Tests, nicht in die UI. Später nutzt auch der KI-Generator diese Operationen.

### Entwürfe vs. Bibliothek – Vorschlag
- **Entwürfe** (`filesDir/drafts/<id>.json`) dürfen Fehler enthalten und werden bei jeder Änderung automatisch gespeichert.
- **„In Bibliothek übernehmen“** nur ohne Fehler (Warnungen erlaubt) – die Bibliothek bleibt damit immer spielbar.
- Importierte oder bestehende Kampagnen lassen sich „Als Entwurf bearbeiten“.
- **Export** jeder Kampagne/jedes Entwurfs als JSON-Datei über den Android-Dateidialog (Sicherung, Weitergabe, später KI).

### Live-Validierung
Nach jeder Änderung (entprellt ~300 ms, im Hintergrund) läuft der vorhandene Validator. Die Befunde werden über ihren **Pfad** zugeordnet:
- Zähler-Badges an Tabs und Listeneinträgen,
- Fehlertext direkt am betroffenen Feld,
- Tab „Prüfung“: komplette Liste, Antippen springt zum Objekt.

## 3. Bildschirme

1. **Entwürfe** (neuer Bereich auf dem Startbildschirm): Neu, Bearbeiten, Duplizieren, Exportieren, Löschen.
2. **Kampagne**: Titel, Beschreibung, Szenarienliste (anlegen/sortieren), Startszenario, **Übergänge** (je Szenario + Auflösung → Folgeszenario, per Auswahllisten).
3. **Szenario** mit Tabs:
   - **Allgemein**: Titel, Einleitung, Bedrohung (Max, pro Runde Basis/pro Held), Startkapitel, Startziel
   - **Karte**: Kartenbearbeitung je Kapitel (siehe 4.)
   - **Events**: Liste + Event-Editor (siehe 5.)
   - **Trigger**, **Ziele**, **Gegner**, **Auflösungen**: Formulare
   - **Prüfung**: Befundliste
4. **Probespielen**: startet den Player mit dem Entwurf (nur ohne Fehler), Spielstand wird nicht in die normale Liste übernommen.

## 4. Karteneditor

- Kapitelauswahl oben; Kacheln früherer Kapitel werden blass mit angezeigt (Kontext), Kacheln späterer ausgeblendet.
- Karte verschieb- und zoombar (Zwei-Finger-Geste).
- **Kachel platzieren** (Tippen statt Ziehen – zuverlässiger auf dem Handy): Kachel aus der Katalog-Leiste wählen (gefiltert auf vorhandene Erweiterungen) → Vorschau folgt dem angetippten Feld → Buttons ↺ ↻, A/B, ✓ Platzieren. Überlappungen werden rot angezeigt.
- **Kachel auswählen** → verschieben, drehen, Seite wechseln, löschen.
- **Token-Modus**: Feld antippen → neues Token (Typ, Bezeichnung, Untersuchungstext, Event, „anfangs aktiv“, „nach Interaktion entfernen“) oder vorhandenes Token bearbeiten/verschieben.
- **Gegner-Modus**: Feld antippen → Gegnergruppe hier platzieren.

## 5. Event-Editor

- Schritte als Karten in Reihenfolge; „+ Schritt“ mit Typmenü; Hoch/Runter, Löschen.
- Verweisfelder (Event, Token, Kapitel, Ziel, Trigger, Gegnergruppe, Auflösung) sind **Auswahllisten des passenden Typs** plus „Neu anlegen …“, das das Objekt direkt erzeugt und einträgt. Freie ID-Eingabe gibt es nicht → falsche Verweise sind im Editor kaum möglich.
- Regel „Auswahl/Probe/Szenario-Ende nur am Schluss“ wird durch die UI erzwungen (danach kein „+ Schritt“).
- „Benutzt von“: wer dieses Event startet (Token, Trigger, Optionen, Proben, Kapitel).

## 6. Umsetzungsschritte (nach jedem: baut + Tests grün)

1. `:editing`: ID-Erzeugung, Verweissuche (`usages`), Umbenennen, Löschen + Tests
2. `:editing`: Kachel-/Token-Operationen, Undo/Redo + Tests
3. App: Entwurfsspeicher, Export, Entwurfsliste, Kampagnen-Bildschirm mit Übergängen
4. App: Szenario-Tabs Allgemein/Ziele/Gegner/Auflösungen/Trigger + Live-Validierung
5. App: Event-Editor
6. App: Karteneditor (Kacheln), danach Token/Gegner
7. App: Prüfung-Tab mit Sprung zum Objekt, Probespielen, „In Bibliothek übernehmen“
8. Screenshots der Editor-Bildschirme (Robolectric) und ein Test, der das Beispielszenario komplett über `:editing`-Operationen nachbaut und auf null Befunde prüft

## 7. Nicht in Phase 2
Ablauf-Graph als Diagramm, eigene Kachelfotos, Katalog-Editor in der App (Katalog bleibt Datei), Mehrsprachigkeit.

## 8. Offene Fragen
1. Eigenes Modul `:editing` (empfohlen) oder Logik direkt in der App?
2. Entwürfe getrennt von der Bibliothek (empfohlen) oder direkt die Bibliotheksdateien bearbeiten?
3. Bearbeitest du hauptsächlich auf dem **Handy oder Tablet**? (Tablet → zweispaltiges Layout: Liste links, Formular rechts)
4. Soll ein **Katalog-Editor** (Kachelformen per Antippen von Feldern zeichnen) doch in Phase 2? Das würde das Erfassen deiner echten Kacheln stark erleichtern.
