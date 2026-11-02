# Schlachtspielpläne – Plan und Formatentwurf zur Freigabe

Stand: 2026-11-02 · **Schritte 1–3 umgesetzt**: Format, Schema, Validator; privater Katalog; Darstellung in App und Probespiel, Antippen eines Geländeteils zeigt Name, Kartentext und abweichende Regel. Beispiel: `examples/beispiel-schlacht.json`.

**Abweichungen bei der Umsetzung von Schritt 1:**
- Statt `"map": "battle"` gilt: Ein Kapitel mit `battlefield` ist ein Schlacht-Kapitel. So gibt es keinen widersprüchlichen Zustand.
- Geländeteil-Seiten haben zusätzlich `effect` (kurze Wirkung in eigenen Worten für Editor und KI) und `blocksMovement`
  (Mauer, Barrikade: für die Hinweise zur Erreichbarkeit).
- Die Feld-Liste eines Plans heißt `areas`. Fehlt sie, ist jedes Quadrat ein eigenes Feld.
- Gegnergruppen stehen in Schlachten ebenfalls mit `{"square": {…}}`.
- Die Tischprüfung nimmt bei gemischten Szenarien die größere der beiden Karten (Reise oder Schlacht), weil die Schlacht die
  Reisekarte ersetzt.

**Nachtrag nach dem Beispielfoto:** Die Pläne sind nicht fein gerastert, sondern durch Linien in **wenige große Felder
(Bereiche)** geteilt. Figuren, Standfahnen und runde Geländeteile stehen *in* einem Bereich, **Mauern liegen *auf den
Kanten*** zwischen zwei Bereichen oder am Rand. Mehrere Pläne werden aneinandergelegt. Der Entwurf unten ist daran
angepasst (Abschnitte 3 und 4).

**Nachtrag nach dem ersten Scan (Plan 1, Seite A, 28,5 × 28,5 cm):** Der Plan ist ein **3 × 3-Raster aus Quadraten
von 9,5 cm**; einige Rasterlinien fehlen, dadurch entstehen **5 Bereiche** (vier Rechtecke aus je 2 Quadraten im Windrad
um ein Mittelfeld). Im Katalog wird ein Plan deshalb als Raster (`width`/`height` in Quadraten, `cellSizeMm`) plus Liste
der **Bereiche** (welche Quadrate zusammengehören) beschrieben:

```json
"A": {
  "width": 3, "height": 3, "cellSizeMm": 95,
  "areas": [
    [{"x":0,"y":0},{"x":0,"y":1}], [{"x":1,"y":0},{"x":2,"y":0}], [{"x":2,"y":1},{"x":2,"y":2}],
    [{"x":0,"y":2},{"x":1,"y":2}], [{"x":1,"y":1}]
  ],
  "image": {"file": "bilder/schlacht1A.jpg", "originX": 0, "originY": 0, "cellSize": 561}
}
```

Figuren/Token/Gegner stehen in einem Bereich (angegeben über ein Quadrat des Bereichs), Mauern liegen auf Quadratkanten.

**Geklärt (02.11.):** Es gibt **einen** Plantyp mit Seite A (Wiese) und B (Steinboden), **zweimal** vorhanden
(`count: 2` im Katalog). Beide Seiten haben dieselbe Windrad-Aufteilung. Mehrere Pläne liegen **immer bündig Kante an
Kante, ohne Versatz** – Planpositionen werden deshalb in ganzen Plänen angegeben (`position: {"x": 1, "y": 0}` = rechts
neben dem ersten Plan), dazu die Drehung in 90°-Schritten. Die Scans beider Seiten sind zusammengesetzt und exakt
vermessen (28,5 × 28,5 cm, Raster liegt auf den Linien).

**Geländeteile (02.11.):** Die Teile sind **zweiseitig**; die Anzahl gilt pro Pappteil. Im Katalog deshalb wie Kacheln:
ein Teil mit Seite A/B (je Name, Lage `edge`/`area`, Bild, Kategorien) und `count`. Der Validator zählt beide Seiten
zusammen (z. B. Fluss/Steinmauer: 10 Teile → 7 Flüsse + 3 Mauern erlaubt, 7 + 4 nicht).

| Teil | Seite A | Seite B | Anzahl | Lage |
|---|---|---|---|---|
| Streifen 9,6 × 2 cm | Fluss | Steinmauer | 10 | Kante |
| Streifen 9,6 × 2 cm | Graben | Dornenhecke | 9 | Kante |
| Rechteck 7,2 × 3,5 cm | Truhe | Barrikade | 4 | Bereich |
| Rechteck 7,2 × 3,5 cm | Tisch | Fässer | 4 | Bereich |
| Rechteck 7,2 × 3,5 cm | Baumstamm | Anhöhe | 4 | Bereich |
| rund Ø 3,8 cm | Spinnenweben | Geröll | 9 | Bereich |
| rund Ø 3,8 cm | Gebüsch | Felsen | 9 | Bereich |
| rund Ø 3,8 cm | Lagerfeuer | Statue | 4 | Bereich |
| groß ca. 8 × 8 cm | Nebel | Grube | 3 | Bereich |
| groß ca. 8 × 8 cm | Brunnen | Teich | 1 | Bereich |

**Geländekarten (02.11.):** Zu jeder Seite eines Geländeteils kommt der **Kartentext** (`cardText`) in den Katalog –
ausschließlich in dein privates Katalog-ZIP, nie ins Repo. Die Namen folgen den Karten (Mauer, Zaun, Spinnennetz,
Feuerstelle, Springbrunnen). Symbole stehen im Text als Platzhalter in eckigen Klammern (z. B. `[Erfolg]`) und werden in
der App als Wort angezeigt. Ein Szenario kann einem gelegten Teil eine **eigene Bedeutung** geben:

```json
{"id": "gt_altar", "piece": "feuer_statue", "side": "B", "area": {"x": 1, "y": 1},
 "customName": "Altar des Nekromanten",
 "customRule": {"mode": "replace", "text": "Wer hier steht, ..."}}
```

`mode`: `replace` (ersetzt die Kartenregel) oder `add` (gilt zusätzlich). Tippt man in der App beim Spielen auf ein
Geländeteil, erscheint ein Fenster mit Name, Bild, Kartentext und – deutlich hervorgehoben – der abweichenden Regel im
Szenario. Die KI darf `customName`/`customRule` setzen; ihre Texte formuliert sie selbst.

**Abgleich mit den Regelbüchern (02.11.)** (nur Regelwissen in eigenen Worten, kein Regeltext im Repo):
- **Lage geklärt:** Fluss, Graben, Mauer und Zaun liegen auf **Feldgrenzen**, alle anderen Teile immer **in einem Feld**.
  Ein Feld ist ein von Linien umschlossener Bereich (bei unserem Plan: die 5 Windrad-Bereiche).
- **Nachbarschaft für die Prüfung der Erreichbarkeit:** Mauer trennt (auch diagonal über Ecken), Barrikade-Felder sind nicht
  betretbar. Fluss, Graben und Zaun erschweren nur die Bewegung, die Felder bleiben benachbart.
- **Abenteuerspezifisches Gelände:** In der offiziellen App ersetzt eine abenteuerspezifische Regel die Geländekarte, und
  das Teil wird hervorgehoben. Deshalb ist `replace` der Standard. `add` ist unsere Erweiterung für „gilt zusätzlich“.
- **Erweiterungen der Teile:** Grundspiel: Fluss/Mauer, Gebüsch/Felsen, Fässer/Tisch, Feuerstelle/Statue, Grube/Nebel.
  Dunkle Pfade: Anhöhe/Baumstamm, Spinnennetz/Geröll. Schatten des Krieges: Graben/Zaun, Truhe/Barrikade,
  Teich/Springbrunnen. Daraus wird `expansion` im Katalog belegt, sodass nur Teile aus Erweiterungen „im Besitz“ benutzt
  werden.
- Die Spielregeln selbst bildet die App weiterhin nicht nach. Die KI bekommt pro Teil nur eine kurze eigene Zusammenfassung
  der Wirkung, damit Platzierung und eigene Bedeutungen sinnvoll sind.

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
    "id": "fluss_mauer", "expansion": "...", "count": 10,
    "sides": {
      "A": { "name": "Fluss",      "placement": "edge", "image": {"file": "bilder/fluss.png"},      "terrain": ["fluss"] },
      "B": { "name": "Steinmauer", "placement": "edge", "image": {"file": "bilder/steinmauer.png"}, "terrain": ["bauwerk"] }
    }
  }
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
      {"id": "gt_feuer_1", "piece": "statue_feuer", "side": "B", "area": {"x": 2, "y": 0}},
      {"id": "gt_mauer_1", "piece": "fluss_mauer",  "side": "B", "edge": {"x": 1, "y": 0, "side": "east"}}
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

- **Kartenart bei den Eingaben (02.11.):** Auswahl **Automatisch / Reise / Schlacht**.
  - *Reise* oder *Schlacht*: vorab fest vorgegeben, die KI hält sich daran.
  - *Automatisch*: Die KI entscheidet anhand der Vorlage. Spielt die Geschichte an **einem Ort** (Verteidigung,
    Überfall, Kampf in einer Halle) → Schlachtplan. Geht es um **Erkundung, Reise, Flucht, Suche** über mehrere Orte →
    Reisekarte. Die Entscheidung steht mit kurzer Begründung im Story-Entwurf und lässt sich dort umstellen.
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
2. ~~Mehrere Pläne?~~ → ja, bis zu zwei, immer bündig ohne Versatz.
3. ~~Zweiseitig?~~ → ja, A (Wiese) und B (Steinboden).
4. Gibt es außer „in einem Bereich“ und „auf einer Kante“ noch andere Arten, Geländeteile zu legen (z. B. auf einer Ecke oder über zwei Bereiche)?
5. Wechselt ein Szenario **innerhalb** zwischen Reise und Schlacht, oder ist ein Szenario entweder das eine oder das andere?
