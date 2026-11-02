# Windows-Editor – Kurzanleitung

## Installieren

1. Auf GitHub rechts unter **Releases** die neueste Version öffnen
   (https://github.com/Aljadvb/Mittelerde_Worldbuilder/releases).
2. `Mittelerde-Worldbuilder-Editor-….msi` herunterladen und ausführen. Java ist enthalten.
   Neue Versionen werden einfach darüber installiert.
3. `Mittelerde-Worldbuilder-App-….apk` ist die App für das Handy.

Ein neues Release entsteht automatisch, sobald in `version.txt` eine neue Versionsnummer steht (z. B. `1.1.0`) und gepusht wird.
Zwischenstände ohne Release liegen unter **Actions → Build → Lauf → Artifacts** (90 Tage).

> Windows warnt beim ersten Start evtl. vor einer „unbekannten App“, weil das Paket nicht signiert ist:
> „Weitere Informationen“ → „Trotzdem ausführen“.

## Aufbau des Fensters

| Bereich | Inhalt |
|---|---|
| links **Struktur** | Kampagne → Szenarien → Kapitel (Kacheln, Token), Events, Trigger, Ziele, Gegnergruppen, Auflösungen. Rote/gelbe Zahlen = Fehler/Hinweise darunter. |
| Mitte | Formular des gewählten Objekts, bei Kapiteln/Kacheln/Token/Gegnern zusätzlich der **Karteneditor**. Unten „Benutzt von“ (anklickbar). |
| rechts **Prüfung** | Alle Befunde des Validators – Klick springt zum Objekt. Ohne Fehler kann die App die Datei importieren. |

## Typischer Ablauf

1. **Datei → Material-Katalog bearbeiten …** (bzw. *Neuer Material-Katalog*): eigene Kacheln anlegen, Form durch
   Anklicken der Felder zeichnen, „Seite B = Spiegelung von A“, Gegnertypen und Erweiterungen eintragen, speichern.
2. **Datei → Neue Kampagne**: erzeugt ein Gerüst (ein Kapitel, Startziel, Sieg/Niederlage, Niederlage bei maximaler Bedrohung).
3. Kapitel wählen → im Karteneditor **Kachel hinzufügen**, Token und Gegner setzen.
4. Szenario wählen → Events, Trigger, Ziele über die Knöpfe unter „Inhalt anlegen“ erstellen und verknüpfen.
   Verweise wählst du aus Listen; „＋ Neu anlegen …“ erstellt das Ziel-Objekt direkt.
5. **Probespiel → Probespielen (F5)** durchklicken, mit „Schritt zurück“ Varianten ausprobieren.
6. **Speichern** (Strg+S) – z. B. in einen Google-Drive-/OneDrive-Ordner – und in der App importieren.

## Eigene Kachelbilder

Ein Material-Katalog kann zu jeder Kachelseite ein Bild enthalten (z. B. deine Scans). Editor und App
zeichnen die Kacheln dann mit diesem Bild statt einfarbig. Bilder und Katalog werden als **ZIP** weitergegeben:
`material.json` plus Ordner `bilder/`.

- **Editor:** Datei → *Material-Katalog wählen (.json/.zip) …* – ein ZIP wird nach
  `%USERPROFILE%\.mittelerde-worldbuilder\kataloge\` entpackt und von dort verwendet.
- **App:** ⋮ → *Material-Katalog importieren* – `.json` oder `.zip`.
- Im Katalog-Fenster erzeugt *Datei → Als ZIP für die App exportieren …* ein solches Paket.

Die Bilder gehören dir und kommen nicht ins (öffentliche) Repository.

### Kategorien und Beschreibung

Im Katalog-Fenster hat jede Kachelseite **Kategorien** aus einer festen Liste (z. B. *Lichter Wald*,
*Dichter Wald*, *Fluss / Bach*, *Ruinen*, *Höhle*, *Düster*) und eine kurze **Beschreibung**. Die Kachelauswahl
im Karteneditor zeigt sie an, und der spätere KI-Generator wählt damit Kacheln passend zur Geschichte.

## KI-Generator

**KI → Einstellungen …**: Modell wählen (Claude Opus 5.5 oder das günstigere Sonnet 5.5) und den API-Schlüssel von
console.anthropic.com eintragen. Er wird verschlüsselt an dein Windows-Konto gebunden gespeichert.

**Ohne API-Schlüssel:** Im Generator unter „Weg“ *über Claude.ai kopieren* wählen (Voreinstellung, solange kein
Schlüssel gespeichert ist). Der Editor erstellt dann für jeden Schritt eine Anweisung: kopieren, in einen neuen Chat auf
claude.ai einfügen, Claudes Antwort komplett zurückkopieren und „Antwort übernehmen“. Korrekturen gehen im selben Chat
weiter. Kosten entstehen dabei keine zusätzlichen; für Szenarien am besten die Länge „kurz“ wählen.

**KI → Szenario generieren … (Strg+G)** führt in drei Schritten zum Szenario:

1. **Vorlage**: eigene Notizen, eine Zusammenfassung oder gemeinfreier Text, dazu Länge, Schwierigkeit, Wünsche,
   **Kartenart** (Automatisch / Reise / Schlacht) und das erlaubte Material (Kacheln mit ihren Kategorien, Gegnertypen).
   Bei *Automatisch* entscheidet die KI: Spielt die Geschichte an einem Ort, nimmt sie Schlachtpläne, bei Erkundung,
   Reise oder Flucht die Reisekarte. Die Begründung steht im Entwurf, die Kartenart ist dort pro Kapitel umstellbar.
2. **Story-Entwurf**: Titel, Einleitung, Kapitel mit Schauplätzen und Landschaft, Ziele, Gegner, Enden – alles bearbeitbar,
   oder „Neu vorschlagen“.
3. **Szenario**: Die KI erzeugt das Szenario, die App ordnet die Kacheln an und prüft alles; Fehler gehen bis zu
   dreimal zur Korrektur an die KI zurück. „In den Editor übernehmen“ öffnet es als neue Kampagne oder fügt es in die
   geöffnete ein – danach wie gewohnt bearbeiten, probespielen, speichern.

Oben rechts stehen die bisherigen Kosten. Lange wörtliche Übernahmen aus der Vorlage werden markiert.

## Tischgröße

**Datei → Tischgröße …** (Standard 120 × 80 cm). Alle Kapitel eines Szenarios liegen am Ende gleichzeitig auf dem Tisch:
Der Karteneditor zeigt über der Karte die Größe der gesamten Karte und warnt, wenn sie nicht passt; der KI-Generator
ordnet die Kacheln nur so an, dass alles darauf passt. Die Feldgröße der echten Kacheln (22,2 mm Mitte–Ecke) steht im
Material-Katalog (`hexSizeMm`).

## Karteneditor

| Aktion | Bedienung |
|---|---|
| Kachel platzieren | „Kachel hinzufügen ▾“ → Kachel/Seite wählen → Vorschau folgt der Maus → Klick |
| Kachel/Token/Gegner verschieben | mit der Maus ziehen |
| Drehen / Seite wechseln | **R** / **F** (für die Vorschau oder die gewählte Kachel) |
| Löschen | **Entf** |
| Abbrechen | **Esc** |
| Ansicht | Mausrad = Zoom, leere Fläche oder mittlere Maustaste ziehen = verschieben, „Einpassen“ |

Frühere Kapitel werden blass angezeigt, spätere ausgeblendet. Überlappungen werden rot markiert und nicht übernommen.

## Unerforschte Kacheln und Inspiration

Im Kachel-Formular (Kachel auf der Karte anklicken) macht das Häkchen **„Unerforscht“** die Kachel zu einer Kachel mit
Erkundungsmarker: In der App liegt sie ausgegraut, Token darauf erscheinen erst beim Erkunden, und der erkundende oder ein
naher Held erhält 1 Inspiration. Optional läuft ein **Erkundungs-Event**; Trigger vom Typ **„Kachel erkundet“** reagieren
darauf (z. B. für Ziele). Die Startkachel sollte erforscht bleiben.

Der Event-Schritt **„Inspiration erhalten“** belohnt die Helden (Anzahl, Empfänger: handelnder Held / jeder Held / Held
nach Wahl, optionaler Text). Die App zeigt nur den Hinweis – die Marker liegen am Tisch.

Der Event-Schritt **„Schaden/Furcht erleiden“** ist die übliche Folge einer fehlgeschlagenen Probe (Schaden, Furcht,
Empfänger, „verdeckt“, optionaler Text). Die Bedrohung steigt normalerweise nur am Rundenende; „Bedrohung ändern“ in
Events bleibt die Ausnahme.

## Schlachtpläne

Im Kapitel-Formular unter **Kartenart** auf *Schlacht* umschalten (nur bei einem Kapitel ohne Kacheln). Dann zeigt der
Editor statt der Sechseck-Karte den **Schlachtplan-Editor**:

| Aktion | Bedienung |
|---|---|
| Plan legen | „Schlachtplan ▾“ → Plan und Seite wählen → Klick; Pläne rasten bündig Kante an Kante ein |
| Geländeteil legen | „Geländeteil ▾“ (zeigt, wie viele noch frei sind) → Klick in ein Feld bzw. nahe an eine Feldgrenze; mehrere nacheinander, Esc beendet |
| Token / Gegner | „Token setzen ▾“ / „Gegner setzen“ → Klick auf ein Feld |
| Verschieben | ziehen (Pläne, Teile, Token, Gegner) |
| Drehen / Wenden | **R** dreht den Plan um 90°, **F** wendet Plan oder Geländeteil |

Ein gewähltes Geländeteil bekommt im Formular optional einen **eigenen Namen** und eine **eigene Regel** für dieses
Szenario – entweder *ersetzt* sie die Geländekarte oder sie *gilt zusätzlich*. In der App erscheint das Teil mit goldenem
Stern; beim Antippen sieht man Name, Bild, die eigene Regel und den Kartentext.

Im **Katalog-Fenster** werden Schlachtpläne (Anzahl, Seiten, Beschreibung, Kategorien) und Geländeteile (Name und Lage je
Seite, Anzahl der Pappteile, Wirkung in eigenen Worten, Kartentext, „versperrt“) gepflegt. Kartentexte und Bilder
bleiben in deinem privaten Katalog.

## Tastenkürzel

Strg+N neu · Strg+O öffnen · Strg+S speichern · Strg+Umschalt+S speichern unter · Strg+Z / Strg+Y rückgängig/wiederholen · F5 Probespiel
