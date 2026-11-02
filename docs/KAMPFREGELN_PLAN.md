# Kampfregeln Schritt 1 – Plan zur Freigabe

Stand: 2026-11-02 · **Entwurf, noch nicht umgesetzt.**

Ziel: Die Gegnerphase und der Kampf folgen den Regeln aus dem Referenzhandbuch. Dazu kommen vier Funktionen:
Gegenangriff, Provozieren, „Kein Ziel“ und Letztes Aufbäumen mit „Held besiegt“.

Für alle vier gilt: Schaden, Furcht, Karten und Figuren bleiben auf dem Tisch. Die App sagt nur, **wer angreift und
mit wie viel**, oder **welche Probe** abzulegen ist. Die Angriffswerte kommen wie bisher aus den Gegnerwerten im
Katalog (`attacks`); die App wählt per Zufall einen Angriff.

## 1. Regeln, auf die sich der Plan stützt (Referenzhandbuch, Abschnitte in Klammern)

- **Erschöpft (30):** Ein erschöpfter Feind wird in der Schattenphase nicht aktiviert und kann nicht provoziert werden.
  Gegenangriffe kann er nur führen, wenn er Elite ist. Am Ende der Schattenphase sind alle Feinde wieder bereit.
- **Feind-Aktivierung, „Kein Ziel“ (33, 35.3):** Erreicht eine Gruppe weder ihr Ziel noch den nächsten Helden, führt
  sie ihre Anweisung gar nicht aus, auch die Bewegung nicht. In der App wählt man „Kein Ziel“ und bekommt eine neue
  Anweisung. Danach endet die Aktivierung.
- **Provozieren (35):** Will ein Held ein Feld mit Feinden verlassen oder dort interagieren, muss er vorher jede bereite
  Gruppe auf seinem Feld provozieren. In der App wählt man die Gruppe aus, die App gibt den Angriff vor.
  Bei mehrfachem Interagieren muss jedes Mal neu provoziert werden.
- **Gegenangriff (45):** Nach dem Angriff eines Helden fragt die App: „Kann der Feind angreifen?“ Ja, wenn die Gruppe in
  Reichweite des Angreifers ist. Keine Gegenangriffe bei erschöpften Nicht-Elite-Gruppen. Gibt es auch nicht, wenn die
  Treffer nicht aus einem Angriff stammen.
- **Letztes Aufbäumen (61):** Erreicht ein Held sein Schaden- oder Furchtlimit, legt er eine Schadens- bzw. Furchtprobe ab.
  Danach gibt die App weitere Anweisungen.
- **Held besiegt (19):** Die Figur kommt vom Plan. Das Abenteuer endet zu Beginn der nächsten Schattenphase mit einer
  Niederlage, außer die Helden gewinnen vorher noch.

## 2. Engine

Neue Befehle (`Command`):

| Befehl | erlaubt | Wirkung |
|---|---|---|
| `NoTarget` | während einer `EnemyActivation`-Meldung | ersetzt die Meldung durch `EnemyRegroup` |
| `ProvokeEnemy(group)` | im Leerlauf, Gruppe aktiv und **nicht** erschöpft | Meldung `EnemyAttack(reason = PROVOKED)` |
| `CounterAttack(yes)` | während einer `CounterQuestion` | bei Ja: `EnemyAttack(reason = COUNTER)` |
| `LastStand(hero, kind)` | im Leerlauf, Held nicht besiegt | Probe `LastStandTest` (siehe 2.4) |
| `ReportLastStand(successes)` | während `LastStandTest` | Ergebnis auswerten |

Neue Meldungen (`Prompt`):

- `EnemyRegroup(group, movement)`: „Bewegt sich bis zu X Felder auf den nächsten Helden zu und greift nicht an.“
- `EnemyAttack(group, reason, damage, fear, text, ranged)`: Angriff durch Provozieren oder Gegenangriff. Ziel ist
  der Held, der provoziert bzw. angegriffen hat; deshalb ist kein Heldenindex nötig.
- `CounterQuestion(group)`: „Kann der Feind angreifen?“ mit Ja/Nein.
- `LastStandTest(hero, kind, successes)`: Probe auf Körperkraft (Schaden) bzw. Willen (Furcht).
- `HeroSurvives(hero, kind)` / `HeroDefeated(hero)`: Ergebnis mit Anweisung.

Neuer Zustand in `GameState` (mit Standardwerten, alte Spielstände laden weiter):

- `defeatedHeroes: Set<Int>`
- `lastStands: Map<Int, Int>`: Anzahl der Letzten Aufbäumen je Held in diesem Abenteuer.

### 2.1 Gegenangriff

Nach `AttackResult` folgt `CounterQuestion`, wenn noch Feinde übrig sind und die Gruppe nicht erschöpft ist. Der
Angriff mit „Betäuben“ erschöpft die Gruppe, deshalb gibt es danach keine Frage. Elite-Gruppen
kommen in Schritt 2 dazu; dann gibt es die Frage auch bei erschöpften Elite-Gruppen.

### 2.2 Provozieren

Neben „Angreifen“ und „Besiegt“ bekommt jede Gruppe einen Knopf **„Provozieren“**. Er ist ausgegraut, solange die
Gruppe erschöpft ist. Ein kurzer Hinweis erklärt, wann man provozieren muss (Feld verlassen, auf dem Feld interagieren).

### 2.3 „Kein Ziel“

Die Aktivierungsmeldung bekommt zwei Knöpfe: **„Erledigt“** und **„Kein Ziel“**. Der bisherige Hinweis („greift den
nächstgelegenen Helden an“) bleibt.

### 2.4 Letztes Aufbäumen und Held besiegt

- Im Menü „Helden“ (oder per Knopf in der Heldenleiste) wählt man **„Letztes Aufbäumen“** → Held → Schaden oder Furcht.
- Probe: Körperkraft bei Schaden, Willen bei Furcht. **Benötigte Erfolge: 2**, für jedes weitere Letzte Aufbäumen
  desselben Helden im Abenteuer **+1** (Vorschlag, siehe Entscheidung A).
- **Bestanden:** Der Held bleibt im Spiel und legt Karten ab (siehe Entscheidung A).
- **Nicht bestanden:** Der Held ist besiegt. Meldung: Figur vom Plan nehmen; wenn ihr nicht vor der nächsten
  Schattenphase gewinnt, ist das Abenteuer verloren.
- Beim nächsten „Runde beenden“ endet das Szenario vor der Schattenphase mit der **ersten Niederlage-Auflösung** des
  Szenarios.
- Besiegte Helden sind nie mehr Ziel einer Feind-Aktivierung, und die Dunkelheit trifft sie nicht.
- Die Helden können natürlich trotzdem noch vorher gewinnen.

## 3. Prüfung, Editor, Generator

- **Prüfung:** Ein Szenario ohne Niederlage-Auflösung bekommt einen Hinweis („Wird ein Held besiegt, braucht das Szenario
  eine Niederlage-Auflösung“).
- **Editor-Probespiel:** Die neuen Meldungen werden beschrieben, Provozieren und Letztes Aufbäumen sind als Knöpfe da.
- **Generator:** Die Anleitung für die KI verlangt eine Niederlage-Auflösung pro Szenario und erklärt in einem Satz,
  dass sie auch bei einem besiegten Helden gilt.
- Kein neues Feld im Datenformat, also keine Schema-Änderung.

## 4. Umsetzung in kleinen Schritten

1. Engine: Gegenangriff und Provozieren, mit Tests.
2. Engine: „Kein Ziel“, mit Tests.
3. Engine: Letztes Aufbäumen und Held besiegt, mit Tests; Prüfungshinweis.
4. App: Knöpfe, Meldungen, Heldenmenü, Texte; Screenshot-Tests.
5. Editor-Probespiel und Generator-Anleitung.
6. Doku (ENTWURF, TESTLISTE), Version 1.9.0.

## 5. Offene Entscheidungen

**A. Was passiert beim bestandenen Letzten Aufbäumen?** Die Regeln überlassen das der App.

- **A1 (Vorschlag):** Der Held legt **2 Schadenkarten** (bzw. Furchtkarten) seiner Wahl ab. Die Probe braucht
  2 Erfolge, für jedes weitere Letzte Aufbäumen +1.
- A2: Der Held legt **alle** Karten dieser Art ab. Die Probe ist schwerer (3 Erfolge, +1 je weiterem).
- A3: Der Held legt **nichts** ab, bleibt aber im Spiel, bis er erneut Schaden bzw. Furcht erleidet.

**B. Flussüberquerung gleich mitnehmen?** Die Aktivierungsmeldung bekäme einen dritten Knopf „Bewegung beendet“
(Fluss, Zaun, Graben). Die Gruppe bleibt an der Grenze stehen. Ist dann ein Held in Reichweite, greift sie diesen
Helden mit denselben Werten an, sonst passiert nichts mehr. Das ist wenig Aufwand, weil es dieselbe Stelle betrifft.
Vorschlag: ja.
