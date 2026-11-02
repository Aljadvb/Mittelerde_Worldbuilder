# Entwurf: Plan & Kampagnenformat (v1) – zur Freigabe

Stand: 2026-11-01 · Noch kein Code geschrieben.

---

## 1. Architektur

### Gradle-Module (Vorschlag)

| Modul         | Typ               | Inhalt                                                            | Abhängigkeiten        |
|---------------|-------------------|-------------------------------------------------------------------|-----------------------|
| `:model`      | Kotlin/JVM        | Datenklassen des Formats, Material-Katalog, Hex-Mathematik, JSON  | kotlinx.serialization |
| `:validation` | Kotlin/JVM        | Validator → strukturierte Fehlerliste                             | `:model`              |
| `:engine`     | Kotlin/JVM        | Spielzustand, Befehle, Event-Ausführung, Spielstand-Format        | `:model`              |
| `:app`        | Android (Compose) | UI, ViewModels, Dateizugriff, Speichern                           | alle oben             |

Echte Gradle-Module statt nur Pakete: Damit ist technisch erzwungen, dass `model`/`validation`/`engine` keine Android-Abhängigkeit haben. Die Tests laufen dort als schnelle JVM-Tests, und die Module lassen sich später auch für den KI-Generator weiterverwenden (z. B. als Kommandozeilen-Validator).

Umgebung (geprüft): JDK 21, Gradle 8.14, Android SDK 35 sind vorhanden → `compileSdk 35`, `minSdk 26`.

### Spielstände
**Vorschlag: JSON-Dateien** im app-internen Speicher (`filesDir/saves/<kampagne>/<slot>.json`), serialisiert mit demselben kotlinx.serialization wie das Format. Der Spielstand ist ein strukturiertes, versioniertes Objekt. Als Datei lässt er sich leicht testen, exportieren und debuggen. **DataStore** nur für Einstellungen (z. B. zuletzt gespielt). Den API-Schlüssel (Phase 3) legen wir verschlüsselt über den Android Keystore ab, nicht im Klartext.

### Engine-Prinzip
Unveränderlicher `GameState` + Befehle → neuer Zustand:

```
Engine.apply(state, command) -> state'
Befehle: StartScenario, InteractToken(id), ChooseOption(index), ReportTestResult(successes),
         DefeatEnemyGroup(id), EndRound, AcknowledgeText
```

Läuft ein Event, steht im Zustand ein `pending`-Eintrag (z. B. „wartet auf Auswahl“ oder „wartet auf Anzahl Erfolge“). Damit lässt sich **jederzeit** speichern, auch mitten in einem Event. Proben würfelt die App nicht: Ihr macht die Probe am Tisch mit den echten Karten, die App fragt nur „Wie viele Erfolge?“.

---

## 2. Grundregeln des Formats

- **Ein Weg pro Sache.** Damit eine KI das Format zuverlässig befüllt, gibt es für jede Aufgabe genau eine Darstellung.
- **IDs**: `^[a-z][a-z0-9_]*$`, sprechend, mit Typ-Präfix. Sie sind **innerhalb einer Kampagne eindeutig** (über alle Objekttypen hinweg). Präfixe (vom Validator als Warnung geprüft): `cmp_`, `scn_`, `ch_`, `pl_` (Platzierung), `tok_`, `evt_`, `trg_`, `obj_`, `eg_` (Gegnergruppe), `res_`.
- Verweise laufen **nur** über IDs. Verschachtelte Objekte gibt es nur, wo sie nirgends sonst referenziert werden (z. B. Schritte innerhalb eines Events).
- Polymorphe Objekte haben ein Feld `"type"` (kotlinx-Klassendiskriminator, im Schema als `oneOf` + `const`).
- `"formatVersion": 1` auf oberster Ebene, für spätere Migrationen.
- Unbekannte Felder sind **Fehler** (`additionalProperties: false`, Parser mit `ignoreUnknownKeys = false`). So fallen Tippfehler einer KI sofort auf.

---

## 3. Hex-Geometrie

- **Axiale Koordinaten** `{"q": int, "r": int}`.
- Eine Kachelform ist eine Liste von Feldern in **kachel-lokalen** Koordinaten. Das Feld `(0,0)` ist der Ankerpunkt der Kachel.
- **Platzierung**: `position` = Kartenkoordinate des Ankers, `rotation` = 0–5 (Schritte zu 60°, im Uhrzeigersinn um den Anker).
  Karte = `rotate(lokal, rotation) + position`. Rotation in Würfelkoordinaten: `(x,y,z) → (−z,−x,−y)` pro Schritt.
- **Token und Gegner** werden in **kachel-lokalen** Koordinaten angegeben (`placement` + `cell`). Dreht man eine Kachel im Editor, wandern sie mit. Der Validator prüft, ob das Feld zur Kachelform gehört.
- Seite A und B einer echten Kachel sind beim Umdrehen gespiegelt. Deshalb hat **jede Seite ihre eigene Feldliste**, jeweils von oben gesehen.
- Die Darstellungsausrichtung ist eine Einstellung im Katalog (`hexOrientation`, Standard `flat` = flache Seite oben wie bei den Original-Kacheln) und betrifft nur das Zeichnen.

---

## 4. Material-Katalog (`material.json`, von dir gepflegt)

```json
{
  "formatVersion": 1,
  "hexOrientation": "flat",
  "expansions": [
    { "id": "basis", "name": "Grundspiel", "owned": true }
  ],
  "tiles": [
    {
      "id": "x01",
      "label": "X01",
      "expansion": "basis",
      "sides": {
        "A": { "cells": [ {"q":0,"r":0}, {"q":1,"r":0}, {"q":0,"r":1} ] },
        "B": { "cells": [ {"q":0,"r":0}, {"q":1,"r":-1}, {"q":1,"r":0} ] }
      }
    }
  ],
  "enemyTypes": [
    { "id": "nebelwolf", "name": "Nebelwolf", "expansion": "basis" }
  ]
}
```

- `label` ist die aufgedruckte Kachelnummer, die die App anzeigt. `id` ist der technische Schlüssel.
- `owned: false` bei einer Erweiterung → der Validator warnt, wenn ein Szenario deren Material nutzt.
- Im Repo liegt nur ein **Beispielkatalog mit erfundenen Kacheln/Gegnern**. Deine echte Datei befüllst du selbst; sie kommt nicht ins Repo (bzw. landet in `.gitignore`).
- Später ergänzbar: `"image": "pfad.jpg"` pro Kachelseite für eigene Fotos.

---

## 5. Kampagnendatei

### 5.1 Kampagne

```json
{
  "formatVersion": 1,
  "id": "cmp_nebelhain",
  "title": "Die Nebel von Hainbrück",
  "description": "…",
  "startScenario": "scn_muehle",
  "scenarios": [ /* Szenario-Objekte */ ],
  "transitions": [
    { "from": "scn_muehle", "resolution": "res_muehle_sieg",       "to": "scn_moor" },
    { "from": "scn_muehle", "resolution": "res_muehle_niederlage", "to": "scn_flucht" }
  ]
}
```

Eine Auflösung ohne Übergang beendet die Kampagne.

### 5.2 Szenario

```json
{
  "id": "scn_muehle",
  "title": "Die stille Mühle",
  "intro": "Einleitungstext …",
  "threat": {
    "max": 20,
    "perRound": { "base": 1, "perHero": 1 }
  },
  "startChapter": "ch_dorf",
  "startObjective": "obj_muehle_finden",
  "chapters":    [ … ],
  "enemyGroups": [ … ],
  "events":      [ … ],
  "triggers":    [ … ],
  "objectives":  [ … ],
  "resolutions": [ … ]
}
```

- **Bedrohungsschwellen sind Trigger** vom Typ `threatReached` (siehe 5.7). Die Leiste in der UI zeigt sie als Markierungen an. So gibt es keine zweite Schreibweise für dasselbe.
- Erreicht die Bedrohung `max`, löst das den Trigger-Typ `threatMax` aus. Der Validator verlangt dafür eine Reaktion, typischerweise eine Niederlage-Auflösung.
- Am Rundenende steigt die Bedrohung um `base + perHero × Heldenzahl`. Weitere Steigerungen laufen über `changeThreat` in Events.

### 5.3 Kapitel

```json
{
  "id": "ch_dorf",
  "title": "Das Dorf",
  "setupText": "Die Gruppe erreicht den Dorfrand …",
  "onUnlock": "evt_dorf_ankunft",
  "tiles": [
    { "id": "pl_dorfplatz", "tile": "x01", "side": "A", "position": {"q":0,"r":0}, "rotation": 0 },
    { "id": "pl_muehlweg",  "tile": "x02", "side": "B", "position": {"q":2,"r":-1}, "rotation": 3 }
  ],
  "tokens": [ … ]
}
```

Wird ein Kapitel freigeschaltet, zeigt der Player die Aufbau-Anweisungen (Kacheln mit Nummer, Seite, Lage auf der Karte; Token). Danach läuft `onUnlock`, falls angegeben.

### 5.4 Token

```json
{
  "id": "tok_brunnen",
  "type": "search",
  "label": "Alter Brunnen",
  "location": { "placement": "pl_dorfplatz", "cell": {"q":1,"r":0} },
  "initiallyActive": true,
  "inspectText": "Moos bedeckt die Steine des Brunnens …",
  "onInteract": "evt_brunnen",
  "removeAfterInteract": true
}
```

- `type`: `search` | `person` | `threat` | `explore` | `custom`. Bei `custom` ist zusätzlich `"customType": "Altar"` Pflicht.
- Jedes Token ist in einem Kapitel definiert, also mit festem Ort. Mit `initiallyActive: false` erscheint es erst durch den Schritt `addToken`. Dadurch kann der Validator **jedes** Token-Feld prüfen.
- `onInteract` sagt, *was passiert*. Ein Trigger `tokenInteracted` dient für *zusätzliche Reaktionen*, z. B. um ein Ziel zu erfüllen.

### 5.5 Gegnergruppen

```json
{
  "id": "eg_woelfe_muehle",
  "enemyType": "nebelwolf",
  "count": { "1": 1, "2": 1, "3": 2, "4": 2, "5": 3 },
  "location": { "placement": "pl_muehlweg", "cell": {"q":0,"r":1} }
}
```

Die Gruppe erscheint über den Schritt `spawnEnemies`. Im Player gibt es pro aktiver Gruppe einen Button „Besiegt“, der den Trigger `enemyGroupDefeated` auslöst. Echter Kampf kommt erst später.

### 5.6 Events

Ein Event ist eine **Folge von Schritten**:

```json
{
  "id": "evt_brunnen",
  "steps": [
    { "type": "text", "text": "Im Brunnen schimmert etwas." },
    { "type": "test", "text": "Du beugst dich hinab …",
      "attribute": "agility", "successes": 2,
      "onSuccess": "evt_brunnen_erfolg", "onFailure": "evt_brunnen_fehlschlag" }
  ]
}
```

| `type`          | Felder                                                         | Wirkung                                      |
|-----------------|----------------------------------------------------------------|----------------------------------------------|
| `text`          | `text`                                                         | Text anzeigen                                |
| `choice`        | `text`, `options: [{label, event}]` (≥ 2)                      | Auswahl → Event der Option                   |
| `test`          | `text?`, `attribute`, `successes`, `onSuccess`, `onFailure?`   | Erfolge abfragen → Folge-Event               |
| `spawnEnemies`  | `group`                                                        | Gegnergruppe aufstellen                      |
| `changeThreat`  | `amount` (± Zahl)                                              | Bedrohung ändern                             |
| `addToken`      | `token`                                                        | Token aktivieren                             |
| `removeToken`   | `token`                                                        | Token entfernen                              |
| `unlockChapter` | `chapter`                                                      | Kapitel freischalten (Aufbau anzeigen)       |
| `setObjective`  | `objective`                                                    | Ziel aktivieren                              |
| `fireTrigger`   | `trigger`                                                      | benannten Trigger vom Typ `manual` auslösen  |
| `endScenario`   | `resolution`                                                   | Szenario mit dieser Auflösung beenden        |

- `attribute`: feste Liste `might | agility | wisdom | spirit | wit`, in der UI wie im Regelbuch als Körperkraft / Beweglichkeit / Weisheit / Willen / Scharfsinn.
- **Strenge Regel:** `choice`, `test` und `endScenario` dürfen nur als **letzter** Schritt stehen. Verzweigungen gehen immer in ein neues Event. Es gibt keine Sprünge mitten in eine Liste, und der Ablauf bleibt für Mensch und KI eindeutig.
- `test` ohne `onFailure` bedeutet: Bei Misserfolg passiert nichts weiter.

### 5.7 Trigger

```json
{ "id": "trg_brunnen_untersucht",
  "on": { "type": "tokenInteracted", "token": "tok_brunnen" },
  "events": ["evt_hinweis_muehle"],
  "repeatable": false }
```

| `on.type`             | Felder        | Feuert, wenn …                              |
|-----------------------|---------------|---------------------------------------------|
| `tokenInteracted`     | `token`       | das Token angetippt/untersucht wurde        |
| `threatReached`       | `value`       | die Bedrohung den Wert erreicht/übersteigt  |
| `threatMax`           | –             | die Bedrohung `max` erreicht                |
| `enemyGroupDefeated`  | `group`       | die Gruppe als besiegt gemeldet wurde       |
| `objectiveCompleted`  | `objective`   | das Ziel erfüllt wurde                      |
| `roundEnded`          | `round`       | Runde Nr. X beendet wurde                   |
| `manual`              | –             | ein Schritt `fireTrigger` ihn auslöst       |

`events` darf leer sein, wenn der Trigger nur ein Ziel erfüllt. `repeatable` ist standardmäßig `false`.

### 5.8 Ziele

```json
{ "id": "obj_muehle_finden",
  "title": "Finde die Mühle",
  "reminder": "Irgendwo östlich des Dorfes soll die alte Mühle stehen.",
  "completedBy": "trg_muehle_betreten" }
```

Mehrere Ziele können gleichzeitig aktiv sein. Ein Ziel gilt nur als erfüllt, wenn es **aktiv** ist, während sein Trigger feuert. Danach feuern `objectiveCompleted`-Trigger.

### 5.9 Auflösungen

```json
{ "id": "res_muehle_sieg", "title": "Das Licht kehrt zurück", "text": "…", "outcome": "victory" }
```

`outcome`: `victory` | `defeat` | `neutral`. Das ist nur eine Anzeige; die Verzweigung läuft über `transitions` und die ID.

---

### 5.9a Inspiration und unerforschte Kacheln

- Event-Schritt `{"type": "gainInspiration", "amount": 1, "who": "active" | "each" | "chosen", "text": "…"}`: Die App
  zeigt einen Hinweis („Jeder Held erhält 1 Inspiration“); die Marker liegen am Tisch, die App zählt nicht mit.
- Event-Schritt `{"type": "sufferHarm", "damage": 2, "fear": 0, "who": "active", "hidden": false, "text": "…"}`: Helden
  erleiden Schaden und/oder Furcht (Karten am Tisch, optional verdeckt). Typische Folge fehlgeschlagener Proben; die
  Bedrohung steigt dagegen normalerweise nur am Rundenende.
- Kachel-Platzierung `"unexplored": true` (optional `"onExplore": "evt_…"`): Die Kachel liegt zunächst ausgegraut mit
  Erkundungsmarker, ihre Token bleiben verborgen. Antippen des Markers erkundet sie: Token erscheinen, der erkundende oder
  ein naher Held erhält 1 Inspiration, dann laufen `onExplore` und Trigger `{"type": "tileExplored", "placement": …}`.

### 5.9b Kampagnenkarte, Merker, Wissen und Erfahrung

Orte (`places`), Kampagnen-Merker (`flags`), Belohnungen der Auflösungen (`lore`, `xp`, `setFlags`), Übergänge mit
`requiresFlag` und die Schritte `setFlag`, `checkFlag`, `gainLore`: siehe [KAMPAGNE_PLAN.md](KAMPAGNE_PLAN.md).

### 5.9c Schattenphase (Gegnerphase)

Nach „Runde beenden“: 1. jede bereite Gegnergruppe wird aktiviert (die App wählt Ziel-Held und einen Angriff des
Gegnertyps: Bewegung, Schaden, Furcht, Text; erschöpfte Gruppen erholen sich stattdessen), 2. Dunkelheit (Event-Schritt
`{"type": "setDarkness", "fear": 1}`, 0 = aus), 3. Bedrohung. Zusätzlich zieht die App jede Runde ein Event aus
`scenario.shadowEvents` – auch harmlose. Gegnertypen im Katalog: `health`, `armor`, `sorcery`, `movement`, `ranged`,
`attacks` [{damage, fear, text}]. Die App führt die Gesundheit jedes Gegners; Angriffe mit Treffern und den Modifikatoren
Durchbohren, Zerschlagen, Zerfallen, Spalten, Betäuben, Tödlich.

### 5.10 Schlacht-Kapitel

Ein Kapitel mit `battlefield` spielt auf Schlachtplänen statt auf Kacheln (keine `tiles`). Token und Gegner stehen dort in
einem Feld: `"location": {"square": {"x": 4, "y": 1}}`. Details, Prüfregeln und Katalogeinträge (`battleBoards`,
`terrainPieces`): siehe [SCHLACHTPLAENE_PLAN.md](SCHLACHTPLAENE_PLAN.md), Beispiel: `examples/beispiel-schlacht.json`.

## 6. JSON-Schema

- Wird **von Hand** geschrieben (Draft 2020-12) und liegt unter `schema/campaign.schema.json` und `schema/material.schema.json`. Die Beschreibungen (`description`) sind ausführlich, damit sie später direkt als KI-Anleitung dienen.
- Ein **Test** prüft das Beispielszenario sowohl gegen das Schema (JVM-Schema-Validator, nur als Test-Abhängigkeit) als auch gegen die Kotlin-Klassen. Dadurch können Schema und Code nicht auseinanderlaufen.
- Das Schema prüft die Struktur. Alles, was Verweise oder Logik betrifft, prüft der Validator.

---

## 7. Validator

```kotlin
data class ValidationIssue(
    val severity: Severity,      // ERROR | WARNING
    val code: IssueCode,         // stabiler Code, z. B. UNKNOWN_REFERENCE
    val path: String,            // z. B. "scenarios[0].chapters[1].tokens[2].location.cell"
    val objectId: String?,       // z. B. "tok_brunnen"
    val message: String          // deutscher Klartext
)
```

Geprüft wird unter anderem:

**Verweise & Katalog**
- Doppelte IDs, ungültiges ID-Format, falsches Präfix (Warnung)
- Jeder Verweis existiert und zeigt auf den **richtigen Typ** (z. B. `onSuccess` muss ein Event sein)
- Kachel/Seite und Gegnertyp existieren im Katalog; Erweiterung nicht im Besitz (Warnung)
- Token-/Gegner-Feld gehört zur Form der Kachel
- Überlappende Kacheln innerhalb eines Kapitels/Szenarios (Fehler); Kachel mehrfach verwendet (Warnung, denn sie liegt physisch nur einmal vor)
- `count` deckt die Spielerzahlen 1–5 ab
- Schrittregeln: `choice`/`test`/`endScenario` nur am Ende; `choice` mit ≥ 2 Optionen; `fireTrigger` nur auf `manual`-Trigger

**Erreichbarkeit** (Graphanalyse; Bedingungen werden optimistisch angenommen, es zählt also „irgendein Weg“)
- Start: `startChapter` (+ dessen Token und `onUnlock`), `startObjective`, Rundenende, Bedrohung
- Kanten: Event → von seinen Schritten referenzierte Events/Kapitel/Token/Ziele/Trigger/Auflösungen; Token aktiv → `tokenInteracted`; Gruppe erscheint → `enemyGroupDefeated`; Ziel aktiv **und** sein Trigger erreichbar → `objectiveCompleted`
- Fehler: Ziel nicht erfüllbar; **keine** Auflösung erreichbar; kein Trigger für `threatMax`
- Warnungen: unerreichbare Events/Token/Kapitel/Auflösungen, Szenarien ohne eingehenden Übergang, Events, die zu nichts führen

---

## 8. Beispielszenario (Phase 1)

„Die stille Mühle“, komplett erfunden, mit erfundenem Beispielkatalog:
2 Kapitel (Dorf → Mühle), 4–5 Token (Suche, Person, Erkundung), 1 Probe, 1 Auswahl, 1 Gegnergruppe, 2 Bedrohungsschwellen, 2 Ziele, 2 Enden (Sieg und Niederlage durch Bedrohung).

---

## 9. Player (Phase 1) – Bildschirme

1. **Start**: Kampagne importieren (Dateiauswahl → Kopie in den App-Speicher, mit Validierung und Fehleranzeige), Spielstände fortsetzen
2. **Neues Spiel**: Heldenzahl 1–5
3. **Einleitung**
4. **Aufbau**: Kartenansicht mit schematischen Sechsecken (Kachelnummer + Seite, neue Kacheln hervorgehoben) und eine Liste der Anweisungen
5. **Spiel**: Karte mit antippbaren Token, Bedrohungsleiste mit Schwellen, aktive Ziele, Gegnergruppen („Besiegt“), „Runde beenden“, Menü (Speichern, Ereignisprotokoll)
6. **Event-Dialog**: Text / Auswahl / Probe (Eingabe der Erfolge)
7. **Auflösung** → nächstes Szenario laut `transitions`

---

## 10. Umsetzungsschritte (nach jedem: baut + Tests grün)

1. Projektgerüst: 4 Module, Version Catalog, leere Compose-App mit `strings.xml` (DE)
2. `:model`: Datenklassen, Serialisierung, Hex-Mathematik + Tests (Round-Trip, Rotation)
3. JSON-Schema, Beispielkatalog, Beispielszenario + Schema-Konformitätstest
4. `:validation`: Verweise, Katalog, Felder, Schrittregeln + Tests
5. `:validation`: Erreichbarkeitsanalyse + Tests
6. `:engine`: Zustand, Befehle, Events, Trigger, Bedrohung, Ziele, Auflösung + Tests (komplettes Durchspielen des Beispiels)
7. `:engine`: Spielstand-Format (versioniert) + Tests; `:app`: Speicher-Repository
8. `:app`: Import, Einleitung, Aufbau mit Hex-Karte
9. `:app`: Spielbildschirm, Event-Dialoge, Rundenende, Auflösung, Speichern/Laden
