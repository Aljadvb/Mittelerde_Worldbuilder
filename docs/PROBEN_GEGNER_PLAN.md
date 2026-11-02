# Proben, Stimmungs-Marker und Gegner – Plan zur Freigabe

Stand: 2026-11-02 · **Umgesetzt in 1.12.0.** Entscheidungen: Banner als Nummern (keine Katalogliste); ein Versuch einer Sammelprobe kostet nur die Aktion, die App hält nichts vor (optional `onAttempt` im Szenario). Anlass: Rückmeldung nach dem ersten Spiel mit einem KI-Szenario.

## 1. Proben

### 1.1 Arten von Proben

Der Event-Schritt `test` bekommt zwei neue, optionale Felder:

```json
{ "type": "test", "attribute": "might", "successes": 6,
  "hidden": false, "cumulative": true, "onSuccess": "evt_stein_weg", "onAttempt": "evt_stein_knirscht" }
```

| Art | Felder | Anzeige in der App |
|---|---|---|
| **Offen** (wie bisher) | – | „Probe auf Körperkraft – 2 Erfolge nötig“ |
| **Verdeckt** | `hidden: true` | „Probe auf Weisheit“ ohne Zahl. Ihr gebt eure Erfolge ein, die App sagt nur „geschafft“ oder „nicht geschafft“. |
| **Sammelprobe** | `cumulative: true` | „Gemeinsame Probe auf Körperkraft – 6 Erfolge nötig, bisher 2“. Jeder Versuch ist eine Aktion eines Helden (der Marker bleibt liegen). Die App zählt die Erfolge über alle Versuche und Runden. Erst bei Erreichen läuft `onSuccess`. |
| **Verdeckte Sammelprobe** | beide | Ohne Zahl; die App beschreibt nur den Fortschritt („Der Stein bewegt sich ein Stück …“). |

- Bei Sammelproben läuft nach jedem Versuch, der das Ziel noch nicht erreicht, optional das Event `onAttempt` (z. B. Lärm: Bedrohung +1 oder Text). `onFailure` gibt es dort nicht.
- Der Marker einer Sammelprobe darf nicht nach dem Interagieren verschwinden; die Prüfung warnt sonst.
- Die Engine merkt sich den Stand je Probe (`testProgress`); alte Spielstände laden weiter.

### 1.2 KI-Generator

- **Erfolge nach Schwierigkeit der einzelnen Probe** (Vorgabe des Nutzers): **1 Erfolg = leicht**, **2 Erfolge =
  normal**, **3 oder mehr = schwer**. Die KI wählt die Zahl nach dem, was die Probe in der Geschichte bedeutet (einen Zaun
  übersteigen = 1, eine Spur im Regen lesen = 2, einen reißenden Fluss durchqueren = 3–4). Die Schwierigkeit des Szenarios
  verschiebt nur den Anteil: „Leicht“ mehr 1er, „Schwer“ mehr 3er und 4er; jede Stufe enthält alle drei Arten.
- Pro Szenario 1–2 verdeckte Proben (Unbekanntes, Rätsel, Gespür).
- Höchstens 1 Sammelprobe pro Szenario, für besondere Momente (Stein wegrollen, Tor aufstemmen, Ritual). Benötigte
  Erfolge etwa 2 × Heldenzahl bzw. 4–8.

## 2. Stimmungs-Marker (optional, mit Belohnung)

Nur KI-Anleitung und Prüfung, kein neues Format:

- Pro Kapitel **2–3 optionale Marker**, die für die Geschichte nicht nötig sind: Lagerfeuer, Aussichtspunkt, alter Schrein,
  Gespräch mit einem Wanderer, Quelle, Inschrift. Jeder hat einen stimmungsvollen Text, oft eine kleine Probe oder Auswahl,
  und eine Belohnung: Inspiration, Wissen, ein Schmuckstück, Schaden oder Furcht ablegen (neuer Hinweis: „Ein Held darf
  1 Schaden ablegen“), selten ein Hinweis auf spätere Gefahren.
- Die Prüfung zählt sie nicht als Fehler, auch wenn sie keine Ziele erfüllen.
- Kleiner neuer Event-Schritt **„Erholung“** (`recover`: Schaden und/oder Furcht ablegen, wer), damit Belohnungen nicht
  nur Inspiration sind. Spiegelbild von „Schaden/Furcht erleiden“.

## 3. Gegner

### 3.1 KI-Generator: Gegner müssen auch erscheinen

- Jede Gegnergruppe muss in einem Event mit `spawnEnemies` erscheinen. Die Prüfung warnt schon heute bei nie erscheinenden
  Gruppen; der Generator bekommt die Regel ausdrücklich: **pro Reise-Szenario 2–4 Gegnergruppen**, Erscheinen beim
  Erkunden einer Kachel, bei Bedrohungsschwellen, beim Betreten eines Kapitels oder als Folge einer gescheiterten Probe.
- Schlacht-Kapitel: Gegner erscheinen mit dem Aufbau und in Wellen.

### 3.2 Gegnermenü

- Gegner stehen schon heute als rote Raute mit Anzahl auf der Karte. **Antippen der Raute** (oder des Eintrags in der
  Gegnerliste) öffnet das **Gegnermenü** der Gruppe:
  - Name, Banner, Gesundheit jedes Feindes, Rüstung, Schutzzauber, Bewegung, Fernkampf, erschöpft ja/nein
  - Knöpfe: Angreifen, Provozieren, Besiegt
- Die Gegnerliste unter der Karte bleibt als Übersicht.

### 3.3 Markierung gleicher Gegner (Feindbanner)

Laut Regeln (34.2) bekommt eine Gruppe ein Feindbanner, sobald eine weitere Gruppe desselben Typs auf dem Spielplan steht.
Die App weist die Banner automatisch zu:

- Beim Erscheinen: „2 Ork-Jäger erscheinen auf Kachel 205. Stellt **Banner ▲ (Dreieck)** dazu.“
- Karte und Gegnermenü zeigen das Banner-Symbol an der Raute bzw. im Namen („Ork-Jäger ▲“).
- Ist die Gruppe besiegt, wird ihr Banner wieder frei.
- Welche Banner es gibt, steht im Katalog (Liste `enemyBanners` mit Name und Symbol), damit es zu deinem Material passt.
  Ohne Eintrag nummeriert die App: „Banner 1, 2, 3 …“.

## 4. Umsetzung in Schritten

1. Proben: Format, Schema, Engine, Prüfung, Tests.
2. Proben: App, Editor-Formular, Probespiel.
3. Erholung-Schritt; Generator-Regeln für Proben, Stimmungs-Marker und Gegner.
4. Gegnermenü in der App (Antippen auf der Karte).
5. Feindbanner: Katalog, Engine-Zuweisung, Anzeige.
6. Doku, Testliste, Version.

## 5. Offene Fragen

- **A. Banner:** Welche Markierungen hast du für gleiche Gegner? Zum Beispiel die Standfahnen aus dem Spiel mit Symbolen
  oder Farben, nummerierte Marker oder etwas Eigenes. Dann trage ich sie so in den Katalog ein.
- **B. Sammelprobe:** Soll ein gescheiterter Versuch einer Sammelprobe immer etwas kosten (z. B. Bedrohung +1), oder
  entscheidet das das Szenario (Vorschlag: das Szenario, über `onAttempt`)?
