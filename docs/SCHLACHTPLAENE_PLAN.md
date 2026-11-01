# Schlachtspielpläne – Plan und Formatentwurf zur Freigabe

Stand: 2026-11-01 · Noch kein Code. Details werden nach den Scans (Pläne und Geländeteile) angepasst.

## 1. Ziel

Neben **Reiseabenteuern** auf Sechseck-Kacheln gibt es **Schlachten** auf viereckigen Schlachtspielplänen mit
**Geländeteilen**, die darauf platziert werden. Ein Szenario kann beides enthalten, z. B. erst eine Reise und dann eine
Schlacht als letztes Kapitel. Editor, App, Validator, Probespiel und KI-Generator sollen damit umgehen können.

## 2. Grundidee: Jedes Kapitel hat eine Kartenart

| Kartenart | Raster | Was darauf liegt |
|---|---|---|
| `travel` (wie bisher) | Sechsecke, axiale Koordinaten (q, r) | Kacheln, Token, Gegner |
| `battle` (neu) | Quadrate, Koordinaten (x, y) | ein oder mehrere Schlachtpläne, Geländeteile, Token, Gegner |

Bestehende Kampagnen bleiben unverändert gültig, denn fehlt die Angabe, ist ein Kapitel ein Reise-Kapitel. Ein
Schlacht-Kapitel ersetzt beim Aufbau die Reisekarte. Die App zeigt dann die Anweisung „Schlachtplan auslegen“ statt
„Kacheln anlegen“.

## 3. Material-Katalog (Erweiterung)

```json
"battleBoards": [
  {
    "id": "s01", "label": "S1", "expansion": "...",
    "sides": {
      "A": {
        "width": 6, "height": 4,
        "image": {"file": "bilder/S1A.jpg", "originX": 40, "originY": 38, "cellSize": 212.5},
        "terrain": ["wald_licht", "fluss"],
        "description": "…"
      },
      "B": { "...": "…" }
    }
  }
],
"terrainPieces": [
  {
    "id": "barrikade", "name": "Barrikade", "expansion": "...",
    "footprint": [{"x": 0, "y": 0}, {"x": 1, "y": 0}],
    "image": {"file": "bilder/barrikade.png", "originX": 10, "originY": 10, "cellSize": 212.5},
    "count": 2
  }
],
"squareSizeMm": 38.0
```

- **Plan:** Größe in Feldern (Breite × Höhe) je Seite, optional Bild mit Lage des Rasters (linke obere Feldecke und
  Feldgröße in Pixeln), dazu Kategorien und Beschreibung wie bei den Kacheln. Die Kategorien helfen der KI bei der Wahl.
- **Geländeteil:** belegte Felder relativ zum eigenen Ankerfeld (0,0), Bild und **Anzahl im Besitz**. So weiß die App,
  wie viele Teile dieser Art es gibt.
- Bilder und Werte kommen wie bisher nur in dein privates Katalog-ZIP, nicht ins Repo. Die Scans werte ich wieder
  automatisch aus: Raster erkennen, gerade ausrichten, Felder zählen.

## 4. Kampagnenformat (Erweiterung)

```json
{
  "id": "ch_furt", "title": "Kampf an der Furt", "map": "battle",
  "battlefield": {
    "boards": [ {"id": "bp_furt", "board": "s01", "side": "A", "position": {"x": 0, "y": 0}, "rotation": 0} ],
    "terrain": [ {"id": "gt_barrikade_1", "piece": "barrikade", "position": {"x": 3, "y": 1}, "rotation": 1} ]
  },
  "tokens": [ {"id": "tok_fahne", "type": "search", "label": "Zerrissene Fahne", "location": {"square": {"x": 2, "y": 3}}} ]
}
```

- **Pläne** liegen auf einem gemeinsamen Quadratraster; mehrere Pläne können aneinander liegen. Drehung in 90°-Schritten
  (0–3).
- **Geländeteile** bekommen Position und Drehung (0–3) auf demselben Raster.
- **Token und Gegner** stehen in Schlacht-Kapiteln auf einem Feld `{"square": {"x", "y"}}`, statt auf einer
  Kachel-Platzierung. Die bisherige Angabe `placement + cell` bleibt für Reise-Kapitel.

### Neue Prüfregeln (Validator)
- Plan und Seite existieren im Katalog; Pläne überlappen sich nicht.
- Geländeteile liegen vollständig auf einem Plan und überlappen sich nicht.
- Es werden nicht mehr Geländeteile einer Art verwendet, als im Katalog als Anzahl angegeben sind.
- Token und Gegner stehen auf einem Feld eines Plans.
- Reise-Kapitel enthalten keine Schlachtpläne und Schlacht-Kapitel keine Kacheln.
- Die Gesamtgröße der Schlachtpläne passt auf den Tisch.

## 5. Editor

- Im Kapitel-Formular die **Kartenart** umschalten (Reise / Schlacht).
- **Schlacht-Karteneditor** analog zum Karteneditor:
  - Plan aus der Palette ziehen; R dreht um 90°, F wechselt die Seite.
  - Geländeteile per Drag & Drop setzen, drehen und löschen. Die Palette zeigt, wie viele noch frei sind.
  - Token und Gegner auf Felder setzen.
- **Katalog-Fenster:** Pläne (Größe, Bild, Kategorien) und Geländeteile (Grundfläche, Bild, Anzahl) pflegen.
- **Probespiel:** zeigt Schlacht-Kapitel mit Plan und Geländeteilen.

## 6. App (Player)

- Beim Aufbau eines Schlacht-Kapitels zeigt die App den Plan mit Bild, alle Geländeteile mit Position und Drehung sowie
  die Token und Gegner auf ihren Feldern.
- Die Anweisungen sind ausdrücklich formuliert, z. B. „Lege Plan S1, Seite A“ und „Stelle 2× Barrikade wie markiert“.
- Die Spielregeln einer Schlacht selbst, also Zugfolge und Kampf, bildet die App nicht nach, wie bisher bei Kämpfen
  auch.

## 7. KI-Generator

- Im Story-Entwurf kann ein Kapitel als **Schlacht** markiert werden.
- Die KI wählt Plan und Seite passend zur Landschaft und platziert Geländeteile und Gegner auf Felder.
- Prüfung und Korrekturschleife funktionieren wie bisher.

## 8. Umsetzungsschritte (jeweils mit Tests)

1. Format: Katalog (`battleBoards`, `terrainPieces`), Kampagne (`map`, `battlefield`, `square`-Ort), Schema, Validator
2. Scans auswerten → Katalog-ZIP mit Plänen und Geländeteilen
3. Gemeinsame Kartenzeichnung für Quadrate (App und Editor), Aufbau-Anzeige in der App
4. Editor: Kartenart, Schlacht-Karteneditor, Katalog-Fenster
5. Probespiel, KI-Generator, Doku, Release

## 9. Offene Fragen (am besten mit den Scans beantworten)

1. Haben die Schlachtpläne ein **aufgedrucktes Quadratraster**? Falls nicht: Wie werden Positionen am Tisch bestimmt?
2. Gibt es **mehrere Pläne pro Schlacht**, die aneinandergelegt werden, oder immer genau einen?
3. Sind die Pläne **zweiseitig** (A/B) wie die Kacheln?
4. Haben die Geländeteile eine **Ausrichtung**, die eine Rolle spielt (z. B. eine Mauer mit Vorder- und Rückseite)?
5. Wechselt ein Szenario **innerhalb** zwischen Reise und Schlacht, oder ist ein Szenario entweder das eine oder das andere?
