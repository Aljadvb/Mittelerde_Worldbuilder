# Phase 3 – KI-Generator im Windows-Editor: Plan zur Freigabe

Stand: 2026-11-01 · **Umgesetzt** (Schritte 1–5). Offen: Schritt 6, der erste echte Testlauf mit API-Schlüssel.

Abweichung: Der API-Schlüssel wird mit der Windows-Datenschutz-API (DPAPI) verschlüsselt und an das Windows-Konto gebunden gespeichert – derselbe Mechanismus, den die Anmeldeinformationsverwaltung nutzt. Die Kopier-Prüfung markiert Fundstellen; umformuliert wird im Editor (kein eigener KI-Knopf dafür).

## 1. Ziel

Im Editor aus einer Vorlage (eigene Notizen, Zusammenfassung oder gemeinfreier Text) ein spielbares Szenario erzeugen:

1. **Story-Entwurf**: Die KI schlägt Handlung, Kapitel, Ziele, Gegner und Enden vor. Du bearbeitest den Entwurf und gibst ihn frei.
2. **Szenario**: Die KI erzeugt daraus das Szenario im Kampagnenformat. Der vorhandene Validator prüft es, die Fehlerliste geht zurück an die KI, und sie korrigiert. Das Ergebnis landet zur Nachbearbeitung im Editor.

Generieren, Prüfen, Nachbearbeiten und Probespielen passieren alle im selben Programm. Der API-Schlüssel liegt nur auf dem PC.

## 2. Ablauf im Editor

```
Menü „KI“ → „Szenario generieren …“
 ┌─ Schritt 1: Eingaben ───────────────────────────────────────────┐
 │ Vorlage (Text einfügen oder .txt/.md laden) · Länge (kurz/mittel/│
 │ lang = 2/3/4 Kapitel) · Schwierigkeit · Heldenzahl-Fokus ·       │
 │ Material: welche Kacheln/Gegnertypen verwendet werden dürfen     │
 │ (vorbelegt: alles „im Besitz“) · Ziel: neue Kampagne oder als    │
 │ weiteres Szenario in die geöffnete Kampagne                      │
 └──────────────────────────────────────────────────────────────────┘
 ┌─ Schritt 2: Story-Entwurf (bearbeitbar) ────────────────────────┐
 │ Titel, Einleitung, Kapitel (Titel, Inhalt, Schauplätze),        │
 │ Ziele, Gegner, Enden. „Neu vorschlagen“ / „Freigeben“            │
 └──────────────────────────────────────────────────────────────────┘
 ┌─ Schritt 3: Szenario erzeugen ──────────────────────────────────┐
 │ Fortschritt: Erzeugen → Prüfen (n Fehler) → Korrigieren (Runde   │
 │ 1/3) → fertig. Ergebnis + verbleibende Befunde, Kosten der       │
 │ Anfrage. „In den Editor übernehmen“                              │
 └──────────────────────────────────────────────────────────────────┘
```

Nach der Übernahme ist das Szenario ein ganz normaler Teil des Dokuments. Du kannst es im Editor anpassen, mit Rückgängig verwerfen und probespielen.

## 3. Architektur

### Neues Modul `:generator` (Kotlin/JVM, ohne UI)

| Teil | Aufgabe |
|---|---|
| `LlmProvider` (Schnittstelle) | `generate(request) → result` mit System-Text, Nachrichten, optionalem JSON-Schema, Abbruch und Token-/Kostenangabe. Anbieter sind austauschbar. |
| `AnthropicProvider` | Umsetzung mit dem **offiziellen Anthropic-Java-SDK** (aus Kotlin nutzbar). Andere Anbieter lassen sich später als weitere Klasse ergänzen. |
| `FakeProvider` | Liefert aufgezeichnete Antworten, für Tests ohne Kosten. |
| `StoryDraft` | Datenklasse des Entwurfs (eigenes kleines JSON-Format). |
| `PromptBuilder` | Baut die Anweisungen aus Formatbeschreibung (aus dem JSON-Schema), Material-Auszug und Entwurf. |
| `ScenarioGenerator` | Pipeline: erzeugen → parsen → validieren → Fehler zurückgeben → höchstens 3 Korrekturrunden. |
| `AutoLayout` | Ordnet die Kacheln eines Kapitels überschneidungsfrei an (siehe 4.). |
| `Importer` | Fügt das Szenario ins Dokument ein und benennt kollidierende IDs über `:editing.rename` um. |

### Modellwahl und API-Nutzung (Vorschlag)

- **Standardmodell: Claude Opus 5.5** (`claude-opus-5-5`), umstellbar in den Einstellungen.
- **Strukturierte Ausgabe:** Die Antwort wird gegen ein JSON-Schema eingeschränkt (`output_config.format`). Ob unser vollständiges Schema mit allen Konstrukten (`oneOf`, `if/then`) dort unterstützt wird, prüfe ich beim Umsetzen. Falls nicht, nutze ich ein vereinfachtes Schema. Die eigentliche Absicherung ist ohnehin unser Validator mit Korrekturschleife.
- **Streaming**, weil die Antworten lang sind (ein Szenario hat leicht 10–20 000 Tokens). Die Oberfläche zeigt dabei den Fortschritt und lässt sich abbrechen.
- **Prompt-Caching:** Formatbeschreibung und Material-Auszug stehen als fester Block am Anfang. Korrekturrunden und weitere Generierungen lesen ihn dann günstig aus dem Cache.
- **Ablehnungen** (`refusal`) werden abgefangen und verständlich angezeigt. Bei Opus 5.5 schalte ich die serverseitige Ausweich-Option („fallbacks“) ein. Das kannst du abschalten.
- **Denktiefe (effort):** Ich schlage „high“ für die Szenarioerzeugung und „medium“ für den Story-Entwurf vor. Beides ist einstellbar.

### Kosten (grobe Schätzung, Opus 5.5: 4 $ / 20 $ pro Mio. Tokens Ein-/Ausgabe)

| Schritt | Eingabe | Ausgabe | ca. Kosten |
|---|---|---|---|
| Story-Entwurf | 5–20 k | 2–4 k | 0,05–0,15 $ |
| Szenario | 15–30 k | 10–20 k | 0,25–0,50 $ |
| je Korrekturrunde | ~30 k (größtenteils aus dem Cache) | 10–20 k | 0,20–0,45 $ |

Ein komplettes Szenario kostet also typischerweise **0,30–1,50 $**. Die tatsächlichen Kosten zeigt der Generator nach jeder Anfrage an.

### API-Schlüssel

Der Schlüssel wird in der **Windows-Anmeldeinformationsverwaltung** gespeichert, also verschlüsselt an dein Windows-Konto gebunden. Er landet nicht als Klartext in einer Datei und nie im Repo. Eingeben und löschen kannst du ihn über „KI → Einstellungen“.

## 4. Kacheln auf der Karte (Vorschlag)

Exakte Hex-Koordinaten ohne Überlappung sind für ein Sprachmodell fehleranfällig. Deshalb teile ich die Aufgabe:

- **Die KI wählt** pro Kapitel die Kacheln (nur aus dem freigegebenen Material) und setzt Token und Gegner auf **kachel-lokale Felder**. Diese Felder kennt sie aus dem Material-Auszug, und der Validator prüft sie.
- **Passende Landschaft:** Der Material-Auszug enthält zu jeder Kachelseite ihre **Kategorien** (z. B. dichter Wald, Fluss, Ruinen) und die **Beschreibung**. So wählt die KI für einen Schauplatz „Turmruine am Waldrand“ etwa 103B neben 100A statt einer beliebigen Kachel. Ein kleiner Vorfilter im Code (`tilesWith(…)`) schlägt pro Schauplatz geeignete Kachelseiten vor.
- **Unser Code ordnet die Kacheln an.** `AutoLayout` legt sie überschneidungsfrei aneinander, Kapitel für Kapitel. Spätere Kapitel schließen an die früheren an.

Die Positionen kannst du danach im Karteneditor wie gewohnt verschieben und drehen.

## 5. Eigene Texte, keine Übernahmen

- Die Anweisungen verlangen eigene Formulierungen. Wörtliche Zitate aus der Vorlage sind ausgeschlossen, nur Namen und Handlungselemente dürfen übernommen werden.
- **Kopier-Prüfung im Code:** Nach der Erzeugung vergleicht der Generator alle Texte des Szenarios mit der Vorlage. Lange wörtliche Übereinstimmungen (z. B. ab 12 Wörtern am Stück) werden als Hinweis mit Fundstelle angezeigt und können per Knopf von der KI umformuliert werden.
- Die Oberfläche weist darauf hin, als Vorlage eigene Notizen, Zusammenfassungen oder gemeinfreie Texte zu verwenden.

## 6. Umsetzungsschritte (nach jedem: baut + Tests grün)

1. `:generator`-Gerüst: `LlmProvider`, `FakeProvider`, `StoryDraft`, `PromptBuilder` + Tests (Prompts enthalten Schema, Material, Entwurf; Cache-Block bleibt bytegleich)
2. `ScenarioGenerator`-Pipeline mit Korrekturschleife + `Importer` (ID-Kollisionen) + Tests mit FakeProvider (Fehler → Korrektur → gültig; Abbruch nach 3 Runden)
3. `AutoLayout` + Kopier-Prüfung + Tests (keine Überlappungen, Anschluss an frühere Kapitel; Übereinstimmungen werden gefunden)
4. `AnthropicProvider` mit dem Java-SDK: Streaming, strukturierte Ausgabe, Caching, Ablehnung/Fehler, Kostenberechnung. Getestet gegen aufgezeichnete Antworten, **ohne echte API-Aufrufe**.
5. Editor-UI: Einstellungen (Anbieter, Modell, Denktiefe, Schlüssel in der Windows-Anmeldeinformationsverwaltung), Assistent mit den drei Schritten, Fortschritt, Abbruch, Übernahme. Dazu Screenshots.
6. **Ein echter Testlauf mit deinem API-Schlüssel**, nur nach deiner Zustimmung, weil er Geld kostet (geschätzt unter 2 $). Danach Feinschliff der Anweisungen anhand des Ergebnisses.

## 7. Nicht in Phase 3
Bilder generieren, mehrere Szenarien einer Kampagne in einem Rutsch, automatische Verbesserungsschleifen über mehrere Generierungen, andere Anbieter als Anthropic (die Schnittstelle ist aber vorbereitet).

## 8. Offene Fragen
1. **Generierungsweg:** gesamtes Szenario-JSON in einem Aufruf plus Korrekturschleife (empfohlen, einfach und günstig) – oder ein „Agent“, der das Szenario Schritt für Schritt über Werkzeuge (Kapitel anlegen, Event anlegen …) aufbaut (robuster bei sehr großen Szenarien, aber deutlich teurer und langsamer)?
2. **Kachel-Anordnung** automatisch durch unseren Code (empfohlen) oder Positionen von der KI?
3. **API-Schlüssel** in der Windows-Anmeldeinformationsverwaltung (empfohlen) oder einfache Datei im Benutzerordner?
4. **Anbieter:** zunächst nur Anthropic (Claude) mit offener Schnittstelle – passt das, und hast du bereits einen API-Schlüssel (console.anthropic.com)?
5. **Modell/Kosten:** Standard Claude Opus 5.5 – einverstanden, oder lieber standardmäßig das günstigere Claude Sonnet 5.5 (2 $ / 10 $ pro Mio. Tokens)?
