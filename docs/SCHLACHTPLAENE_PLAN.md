# Schlachtspielpläne – Plan und Formatentwurf zur Freigabe

Stand: 2026-11-01 · Noch kein Code. Details werden nach den Scans (Pläne und Geländeteile) angepasst.

**Nachtrag nach dem Beispielfoto:** Die Pläne sind nicht fein gerastert, sondern durch Linien in **wenige große Felder
(Bereiche)** geteilt. Figuren, Standfahnen und runde Geländeteile stehen *in* einem Bereich, **Mauern liegen *auf den
Kanten*** zwischen zwei Bereichen oder am Rand. Mehrere Pläne werden aneinandergelegt. Der Entwurf unten ist daran
angepasst (Abschnitte 3 und 4).

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
  { "id": "felsen",  "name": "Felsen",     "expansion": "...", "placement": "area", "image": {"file": "bilder/felsen.png"},  "count": 2 },
  { "id": "mauer",   "name": "Steinmauer", "expansion": "...", "placement": "edge", "image": {"file": "bilder/mauer.png"},   "count": 4 }
],
"squareSizeMm": 38.0
```

- **Plan:** Größe in Feldern (Breite × Höhe) je Seite, optional Bild mit Lage des Rasters (linke obere Feldecke und
  Feldgröße in Pixeln), dazu Kategorien und Beschreibung wie bei den Kacheln. Die Kategorien helfen der KI bei der Wahl.
- **Feld = Bereich:** `width`/`height` zählen die großen Bereiche eines Plans (z. B. 3 × 2), nicht kleine Quadrate.
- **Geländeteil:** Art der Platzierung – `area` (liegt *in* einem Bereich, z. B. Felsen, Tümpel) oder `edge` (liegt *auf
  einer Kante* zwischen zwei Bereichen bzw. am Rand, z. B. Mauer) –, Bild und **Anzahl im Besitz**.
- Bilder und Werte kommen wie bisher nur in dein privates Katalog-ZIP, nicht ins Repo. Die Scans werte ich wieder
  automatisch aus: Raster erkennen, gerade ausrichten, Felder zählen.

## 4. Kampagnenformat (Erweiterung)

```json
{
  "id": "ch_furt", "title": "Kampf an der Furt", "map": "battle",
  "battlefield": {
    "boards": [ {"id": "bp_furt", "board": "s01", "side": "A", "position": {"x": 0, "y": 0}, "rotation": 0} ],
    "terrain": [
      {"id": "gt_felsen_1", "piece": "felsen", "area": {"x": 2, "y": 0}},
      {"id": "gt_mauer_1",  "piece": "mauer",  "edge": {"x": 1, "y": 0, "side": "east"}}
    ]
  },
  "tokens": [ {"id": "tok_fahne", "type": "search", "label": "Zerrissene Fahne", "location": {"square": {"x": 2, "y": 3}}} ]
}
```

- **Pläne** liegen auf einem gemeinsamen Quadratraster; mehrere Pläne können aneinander liegen. Drehung in 90°-Schritten
  (0–3).
- **Geländeteile** in einem Bereich: `area {x, y}`. Auf einer Kante: `edge {x, y, side}` mit `side` = north/east/south/west
  des Bereichs (x, y) – die Ausrichtung ergibt sich daraus.
- **Token und Gegner** stehen in Schlacht-Kapiteln in einem Bereich `{"square": {"x", "y"}}`, statt auf einer
  Kachel-Platzierung. (Mehrere Figuren und Teile dürfen sich einen Bereich teilen.) Die bisherige Angabe `placement + cell` bleibt für Reise-Kapitel.

### Neue Prüfregeln (Validator)
- Plan und Seite existieren im Katalog; Pläne überlappen sich nicht.
- Geländeteile liegen in einem Bereich bzw. auf einer Kante eines Plans; auf einer Kante höchstens ein Teil.
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

1. ~~Aufgedrucktes Raster?~~ → große Bereiche mit Linien (laut Foto). Wie viele Bereiche hat ein Plan (z. B. 3 × 2)?
2. ~~Mehrere Pläne?~~ → ja, aneinandergelegt (laut Foto). Immer nur an ganzen Kanten, oder auch versetzt?
3. Sind die Pläne **zweiseitig** (A/B) wie die Kacheln?
4. Gibt es außer „in einem Bereich“ und „auf einer Kante“ noch andere Arten, Geländeteile zu legen (z. B. auf einer Ecke oder über zwei Bereiche)?
5. Wechselt ein Szenario **innerhalb** zwischen Reise und Schlacht, oder ist ein Szenario entweder das eine oder das andere?
