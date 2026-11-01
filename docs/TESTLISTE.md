# Testliste – was noch von Hand zu prüfen ist

Stand: 2026-11-01 · Version 1.2.0 · Abhaken mit `[x]`. Rückmeldungen (Screenshots, Dateien) einfach im Chat schicken.

Alles hier ist automatisch getestet (Unit-Tests, simulierte Maus, gerenderte Screenshots), aber **noch nie auf echter
Hardware bzw. mit einer echten KI gelaufen**. Die wichtigsten Punkte stehen oben.

## 1. KI-Generator (noch nie mit echter KI gelaufen) – wichtigster Test

### 1a. Über Claude.ai kopieren (ohne API-Schlüssel)
- [ ] Generator öffnen (KI → Szenario generieren …), „über Claude.ai kopieren“ ist vorgewählt
- [ ] Kurze Vorlage (2–3 Sätze), Länge „kurz“, eigener Katalog geladen
- [ ] „Anweisung kopieren“ → in neuen Chat auf claude.ai → Antwort zurückkopieren → Story-Entwurf erscheint
- [ ] Entwurf bearbeiten (Titel, Kapitel, Landschaft) → „Szenario erzeugen“ → wieder kopieren/einfügen
- [ ] Antwortet Claude vollständig oder wird die JSON-Antwort abgeschnitten?
- [ ] Wie viele Korrekturrunden waren nötig? Welche Fehler kamen? (Screenshot der Fehlerliste)
- [ ] Passen die gewählten Kacheln zur Landschaft der Kapitel (Kategorien)?
- [ ] „In den Editor übernehmen“ → Karte sieht sinnvoll aus, passt auf den Tisch
- [ ] Probespielen (F5): Ist das Szenario spielbar und stimmig?

### 1b. Direkt per API-Schlüssel (optional, kostet Geld)
- [ ] Schlüssel unter KI → Einstellungen speichern; Editor neu starten → Schlüssel ist noch da (DPAPI)
- [ ] Generierung mit Opus 5.5, Kosten oben rechts plausibel (erwartet 0,30–1,50 $)
- [ ] **Ausweich-Option „Bei Ablehnung …“**: Läuft die Anfrage damit ohne Fehlermeldung? (ungetestet gegen die echte API – falls Fehler: Option abschalten und mir den Text schicken)
- [ ] Abbrechen während der Erzeugung funktioniert
- [ ] Falscher Schlüssel → verständliche Meldung

## 2. Windows-Editor

- [ ] Installation der `.msi`; Update von einer älteren Version (überschreibt sauber?)
- [ ] Windows-Warnung „unbekannte App“ → „Trotzdem ausführen“ klappt
- [ ] Datei-Dialoge (Öffnen, Speichern unter, Katalog wählen, ZIP exportieren, Textdatei laden)
- [ ] Tastenkürzel: Strg+S/Z/Y/N/O/G, F5; im Karteneditor R, F, Entf, Esc
- [ ] Karteneditor mit der Maus: Kachel ziehen, drehen, Seite wechseln, Mausrad-Zoom, Ansicht verschieben
- [ ] Kachelbilder erscheinen gerade und passend auf den Feldern (auch gedreht)
- [ ] Tischgröße einstellen (Datei → Tischgröße …) → Anzeige über der Karte, rote Warnung bei zu großer Karte
- [ ] Katalog-Fenster: Kachel anlegen, Form anklicken, Kategorien setzen, als ZIP exportieren
- [ ] Ungespeicherte Änderungen: Nachfrage beim Schließen

## 3. Android-App (auf dem Handy)

- [ ] Installation der `.apk`, Start, Beispielkampagne laden, komplett durchspielen
- [ ] `mein-katalog.zip` importieren (⋮ → Material-Katalog importieren) → Kacheln mit Bildern im Spiel
- [ ] Im Editor gespeicherte Kampagne importieren (z. B. über Google Drive / OneDrive)
- [ ] KI-generiertes Szenario am Tisch spielen
- [ ] Spiel unterbrechen, App schließen, Spiel fortsetzen
- [ ] Lesbarkeit am Tisch: Schriftgrößen, Karte, Aufbau-Anweisungen

## 4. Am echten Tisch

- [ ] Aufbau nach App-Anweisung: stimmen Kachelnummer, Seite, Lage und Drehung mit der Karte überein?
- [ ] Passt eine generierte Karte wirklich auf deinen Tisch?
- [ ] Token-/Gegner-Positionen eindeutig erkennbar?

## Offene Punkte / später

- Restliche Kacheln scannen (dann ergänze ich den Katalog)
- Gegnertypen in den Katalog eintragen (fehlen noch)
- Installer verkleinern (große Icon-Bibliothek entfernen, ca. –35 MB)
- Pull Requests für Phase 1 und 2 zusammenführen (Branch `phase2-editor` → `main`)
