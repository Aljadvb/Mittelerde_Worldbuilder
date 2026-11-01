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

## Tastenkürzel

Strg+N neu · Strg+O öffnen · Strg+S speichern · Strg+Umschalt+S speichern unter · Strg+Z / Strg+Y rückgängig/wiederholen · F5 Probespiel
