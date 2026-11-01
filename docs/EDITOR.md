# Windows-Editor – Kurzanleitung

## Installieren

1. Auf GitHub rechts unter **Releases** die neueste Version öffnen
   (https://github.com/Aljadvb/Mittelerde_Worldbuilder/releases).
2. `Mittelerde-Worldbuilder-Editor-….msi` herunterladen und ausführen. Java ist enthalten.
   Neue Versionen werden einfach darüber installiert.
3. `Mittelerde-Worldbuilder-App-….apk` ist die App für das Handy.

Ein neues Release entsteht automatisch, wenn ein Versions-Tag wie `v1.1.0` gepusht wird.
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
