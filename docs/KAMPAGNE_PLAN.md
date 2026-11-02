# Kampagnen über mehrere Spielabende – Plan und Umsetzung

Stand: 2026-11-02 · **Umgesetzt in Version 1.6.0.** Entscheidungen: eigene schematische Karte (1a), die App zeigt nur das
Gesamtwissen (2a), alle Helden erhalten gleich viele EP (3).

## Regeln (laut Regelbuch, in eigenen Worten)
- **Wissen** ist ein gemeinsamer Wert der Gruppe, bleibt dauerhaft und wird nie ausgegeben. Es schaltet Aufwertungen von
  Gegenständen frei, deren Wissenswert erreicht ist.
- **Erfahrung (EP)** sammelt jeder Held für seine aktuelle Rolle; sie wird für Fertigkeiten ausgegeben, beim Verkauf
  einer Fertigkeit gibt es sie zurück. Nach einem Rollenwechsel bleibt die EP der alten Rolle erhalten.

**Eigenes Kartenbild (02.11.):** Der Material-Katalog kann ein Kartenbild enthalten (`"campaignMap": {"file":
"bilder/kampagnenkarte.jpg"}`) – nur im privaten Katalog, nie im Repo. App und Editor zeigen es dann statt der
schematischen Karte; die Orte liegen relativ (0–1) darauf und werden im Editor auf dem Bild gesetzt.

## Format
```json
"places": [{"id": "pt_bree", "name": "Bree", "x": 0.27, "y": 0.38}],
"flags":  [{"id": "fl_mueller_gerettet", "label": "Müller gerettet"}],
"transitions": [{"from": "scn_a", "resolution": "res_sieg", "to": "scn_b2", "requiresFlag": "fl_mueller_gerettet"},
                {"from": "scn_a", "resolution": "res_sieg", "to": "scn_b"}]
```
- Szenario: `"place": "pt_bree"`.
- Auflösung: `"lore": 3` (Wissen der Gruppe), `"xp": 2` (EP für jeden Helden), `"setFlags": ["fl_…"]`.
- Übergänge mit `requiresFlag` haben Vorrang vor Übergängen ohne Bedingung.
- Event-Schritte: `setFlag`, `checkFlag` (Verzweigung `ifSet`/`ifNotSet`, nur als letzter Schritt), `gainLore`.

## App
- Neues Spiel: Anzahl, Namen und Rollen der Helden.
- Nach jedem Szenario: **Lager** mit Belohnung, Kampagnenkarte (gespielte Orte mit Ergebnis, Weg, nächster Ort),
  Gesamtwissen und je Held EP pro Rolle; Fertigkeit kaufen (Kosten, optional Name), verkaufen, Rolle wechseln.
- Die Übersicht ist im Spiel über ⋮ → Kampagne erreichbar und erscheint am Ende der Kampagne.

## Editor
- Kampagnen-Formular: Kampagnenkarte (Orte setzen und verschieben), Kampagnen-Merker, Übergänge mit Bedingung.
- Szenario: Ort; Auflösung: Wissen, EP, gesetzte Merker; Event-Schritte für Merker und Wissen.

## KI
- Auflösungen bekommen Wissen und EP, 1–2 `gainLore` pro Szenario; neue Merker werden bei Bedarf angelegt, bestehende
  Merker der Kampagne kann das Szenario mit `checkFlag` abfragen. Der Ort wird im Editor gewählt.
