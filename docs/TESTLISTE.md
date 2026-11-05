# Testliste – was noch von Hand zu prüfen ist

Stand: 2026-11-02 · Version 1.22.0 · Abhaken mit `[x]`. Rückmeldungen (Screenshots, Dateien) einfach im Chat schicken.

Alles hier ist automatisch getestet (Unit-Tests, simulierte Maus, gerenderte Screenshots), aber **noch nie auf echter
Hardware bzw. mit einer echten KI gelaufen**. Die wichtigsten Punkte stehen oben.

## 1. KI-Generator (noch nie mit echter KI gelaufen) – wichtigster Test

### 1a. Über Claude.ai kopieren (ohne API-Schlüssel)
- [ ] Generator öffnen (KI → Szenario generieren …), „über Claude.ai kopieren“ ist vorgewählt
- [ ] Kurze Vorlage (2–3 Sätze), Länge „kurz“, eigener Katalog geladen
- [ ] „Anweisung kopieren“ → in neuen Chat auf claude.ai → Antwort zurückkopieren → Story-Entwurf erscheint
- [ ] Entwurf bearbeiten (Titel, Kapitel, Landschaft) → „Szenario erzeugen“ → wieder kopieren/einfügen
- [ ] Antwortet Claude vollständig oder wird die JSON-Antwort abgeschnitten?
- [ ] Wie viele Korrekturrunden waren nötig? Welche Fehler kamen? (Screenshot der Fehlerliste)
- [ ] Passen die gewählten Kacheln zur Landschaft der Kapitel (Kategorien)?
- [ ] „In den Editor übernehmen“ → Karte sieht sinnvoll aus, passt auf den Tisch
- [ ] Probespielen (F5): Ist das Szenario spielbar und stimmig?

### 1b. Direkt per API-Schlüssel (optional, kostet Geld)
- [ ] Schlüssel unter KI → Einstellungen speichern; Editor neu starten → Schlüssel ist noch da (DPAPI)
- [ ] Generierung mit Opus 5.5, Kosten oben rechts plausibel (erwartet 0,30–1,50 $)
- [ ] **Ausweich-Option „Bei Ablehnung …“**: Läuft die Anfrage damit ohne Fehlermeldung? (ungetestet gegen die echte API – falls Fehler: Option abschalten und mir den Text schicken)
- [ ] Abbrechen während der Erzeugung funktioniert
- [ ] Falscher Schlüssel → verständliche Meldung

## 1c. Schlachtpläne (neu in 1.3.0)

- [ ] Neuen Katalog `mein-katalog.zip` laden (Editor: Datei → Katalog wählen; App: ⋮ → Material-Katalog importieren)
- [ ] Kapitel auf „Schlacht“ umschalten, beide Pläne legen (bündig, gedreht, Seite gewechselt)
- [ ] Geländeteile legen: Fluss/Mauer/Graben/Zaun nur auf Feldgrenzen, andere in Feldern; Zähler „frei“ stimmt
- [ ] Eigenen Namen und eigene Regel vergeben (ersetzen / zusätzlich)
- [ ] Probespiel (F5): Schlachtplan erscheint, Antippen eines Teils zeigt Kartentext und eigene Regel
- [ ] App: Aufbau-Anweisung (Pläne, Teile, Token) stimmt mit dem echten Tisch überein; Teile in richtiger Größe
- [ ] App: Antippen eines Geländeteils am Handy – Text gut lesbar?
- [ ] KI-Generator mit „Automatisch“: Wählt die KI bei einer Geschichte an einem Ort einen Schlachtplan? Begründung sinnvoll?
- [ ] KI-Generator mit „Schlacht“: gültiges Szenario, sinnvoll platzierte Geländeteile?
- [ ] Neue Kacheln 200–227: Bilder gerade, Raster passt, Nummern stimmen
- [ ] „Vorher abräumen“ im Editor → Prüfung warnt (Karte soll nicht mitten im Szenario neu aufgebaut werden); KI nutzt es nicht mehr
- [ ] Nur ein Schlachtplan → Hinweis in der Prüfung; KI plant zwei Pläne

## 1d. Erkunden und Inspiration (neu in 1.4.0)

- [ ] Im Editor eine Kachel auf „Unerforscht“ setzen, Token darauf legen, Erkundungs-Event wählen
- [ ] App/Probespiel: Kachel ausgegraut mit grünem Kompass, Token darauf unsichtbar
- [ ] Kompass antippen: Hinweis „erkundet“ mit Inspiration und neuen Token, danach Erkundungs-Event
- [ ] Trigger „Kachel erkundet“ erfüllt ein Ziel
- [ ] Event-Schritt „Inspiration erhalten“ (jeder Held / handelnder Held / Wahl) erscheint als Hinweis
- [ ] KI-Generator: Nutzt er unerforschte Kacheln und Inspiration sinnvoll?
- [ ] Proben zeigen die Attribute wie im Regelbuch (Körperkraft, Beweglichkeit, Weisheit, Willen, Scharfsinn)

## 1e. Schaden und Furcht (neu in 1.5.0)

- [ ] Event-Schritt „Schaden/Furcht erleiden“ anlegen (z. B. im onFailure-Event einer Probe), auch „verdeckt“
- [ ] App/Probespiel: Hinweis „Der handelnde Held erleidet 2 Schaden (verdeckt).“ erscheint
- [ ] KI-Generator: Haben fehlgeschlagene Proben passende Folgen (Schaden/Furcht) und steigt die Bedrohung nur selten durch Events?

## 1f. Kampagnen, Lager, Wissen und EP (neu in 1.6.0)

- [ ] Editor: Orte auf der Kampagnenkarte setzen und verschieben, Szenarien zuordnen
- [ ] Editor: Merker anlegen, in einer Auflösung setzen, im nächsten Szenario mit „Merker prüfen“ abfragen
- [ ] Editor: zwei Übergänge für dieselbe Auflösung – einer mit Merker-Bedingung
- [ ] Auflösungen mit Wissen und EP versehen; „Wissen erhalten“ in einem Event
- [ ] App: neues Spiel mit Heldennamen und Rollen
- [ ] App: nach einem Szenario erscheint das Lager mit Belohnung, Karte, Gesamtwissen und EP
- [ ] App: Fertigkeit kaufen (EP werden abgezogen), verkaufen, Rolle wechseln (alte EP bleiben)
- [ ] App: Spiel beenden und an einem anderen Abend fortsetzen – Wissen, EP und Merker sind noch da
- [ ] App: ⋮ → Kampagne zeigt die Übersicht während des Spiels
- [ ] KI: Vergibt sie Wissen/EP sinnvoll und reagiert ein Folgeszenario auf Merker?
- [ ] Editor: „Szenarien aus Datei hinzufügen …“ – zwei einzeln gespeicherte Szenarien zu einer Kampagne zusammenfügen, Übergänge setzen, in der App durchspielen
- [ ] Eigenes Kartenbild aus dem Katalog erscheint in Editor und App; Orte darauf neu platzieren

## 1g. Schattenphase und Gegner (neu in 1.8.0)

- [ ] „Runde beenden“: jede Gegnergruppe greift einen benannten Helden an (Bewegung, Schaden, Furcht, Text)
- [ ] Dunkelheit (Event-Schritt „Dunkelheit festlegen“) bringt jede Runde Furcht
- [ ] Jede Runde ein Schattenereignis – mal harmlos, mal mit Folgen
- [ ] „Angreifen“: Treffer eingeben, Rüstung zieht ab, Modifikatoren wirken; Gesundheitsanzeige stimmt; Gruppe wird besiegt
- [ ] Betäuben: Gruppe setzt die nächste Aktivierung aus
- [ ] Gegnerwerte im Katalog-Fenster anpassen (Gesundheit, Rüstung, Angriffe) – sind meine Vorschläge zu stark/schwach?
- [ ] KI: Schattenereignisse sinnvoll gemischt?

## 1h. Kampfregeln (neu in 1.9.0)

- [ ] Gegner angreifen → Frage „Kann der Feind angreifen?“ → Ja: Gegenangriff mit Schaden/Furcht; nach „Betäuben“ keine Frage
- [ ] „Provozieren“ neben einer Gegnergruppe → Angriff auf den provozierenden Helden; bei erschöpfter Gruppe ausgegraut
- [ ] Schattenphase: „Kein Ziel“ → neue Anweisung (Bewegung zum nächsten Helden, kein Angriff)
- [ ] Schattenphase: „Bewegung beendet“ → Gruppe bleibt an Fluss/Zaun/Graben stehen, greift nur einen Helden in Reichweite an
- [ ] „Limit erreicht“ → Held und Schaden/Furcht wählen → Probe (Körperkraft bzw. Willen, 1 Erfolg) → Bestanden: alle Karten der Art ablegen
- [ ] Derselbe Held erreicht erneut ein Limit → sofort besiegt
- [ ] Nicht bestanden → Held besiegt; „Runde beenden“ → Abenteuer endet mit der Niederlage-Auflösung
- [ ] Besiegter Held wird in der Schattenphase nicht mehr als Ziel genannt
- [ ] Gegnerangriffe zeigen „Abwehr: Probe auf …“ (jeder Erfolg verhindert 1 Schaden oder 1 Furcht); einzelne Angriffe sind nicht abwehrbar (neu in 1.10.0)
- [ ] Katalog-Fenster → Gegner: pro Angriff „Abwehr“ einstellbar
- [ ] Gegnergruppe besiegt → der handelnde Held erhält 1 Inspiration (neu in 1.13.2)
- [ ] Event-Schritt „Gegner schwächen“: Treffer, Betäuben, Zerfallen usw. ohne Gegenangriff (neu in 1.14.0)
- [ ] KI-Schlacht: 3–5 taktische Marker, mit denen man sich Vorteile verschafft
- [ ] „Limit erreicht“ steckt jetzt im ⋮-Menü neben „Runde beenden“ (App) bzw. eingeklappt im Probespiel (Editor)
- [ ] Editor-Probespiel: „Angreifen“ mit Treffern und Modifikatoren, Gesundheit und Banner der Gegner sichtbar

## 1j. Schmuckstücke und Gruppeninventar (neu in 1.11.0)

- [ ] Katalog-Fenster → Schmuckstücke: anlegen, Name, Erweiterung, Wirkung, Kartentext
- [ ] Event-Schritt „Schmuckstück finden“: bestimmtes oder zufälliges Schmuckstück, wer die Karte nimmt
- [ ] App: Meldung „Schmuckstück gefunden“ mit Name, Wirkung bzw. Kartentext (und Kartenbild, sobald gescannt)
- [ ] Nach dem Szenario: Lager und ⋮ → Kampagne zeigen das Gruppeninventar; im nächsten Szenario kommt dasselbe Schmuckstück nicht noch einmal
- [ ] KI-Generator: 1–2 Funde pro Szenario an passenden Stellen

## 1l. Schmuckstücke mit Stufen (neu in 1.13.0)

- [ ] Katalog-Fenster → Schmuckstücke: 49 Karten mit Bild, Stufe, Linie, Wissenswert und Verbrauchsmarkern; Kartentexte stimmen
- [ ] Funde sind selten (höchstens 1 pro Szenario, oft keiner) und immer Stufe I
- [ ] Lager: Inventar zeigt Stufe; „Aufwerten zu …“ erscheint erst ab dem Wissenswert der Karte; bei Extrarationen gibt es zwei Wege (Breeländische Rationen oder Hobbitrationen)

## 1m. Kampagnenablauf und Karten (neu in 1.15.0)

- [ ] Kampagnenkarte zeigt am Anfang nur das erste Abenteuer, später nur erreichte Orte und das nächste Ziel
- [ ] Entweder-oder: Führen mehrere Wege weiter, wählt ihr im Lager das nächste Abenteuer (Test-Kampagne: Szenario 1 verlieren)
- [ ] Ein Szenario ist entweder Reise oder Schlacht (Prüfung meldet Mischung als Fehler; KI-Entwurf wählt die Kartenart für das ganze Szenario)
- [ ] KI-Reiseszenario: deutlich mehr Kacheln (Tisch gut gefüllt) und auf jeder Kachel etwas zu tun, mit optionalen Nebenaktionen
- [ ] Prüfung warnt bei Kacheln ohne Token oder Gegner
- [ ] Editor-Probespiel: nach einem Szenario erscheint ein einfaches Lager
- [ ] Startfeld der Helden: in Runde 1 grün markiert, Aufbau-Anweisung nennt es; im Editor über „Startfeld der Helden“ setzbar (Reise und Schlacht) (neu in 1.16.0)
- [ ] Token antippen (Karte oder Liste) → Fenster mit Name, Art und Beschreibung; erst „Interagieren“ löst die Aktion/Probe aus, „Zurück“ schließt nur (neu in 1.17.0)
- [ ] Angriffsfenster zeigt Gesundheit jedes Feindes sowie Rüstung (nach Zerfallen) und Schutzzauber (neu in 1.17.1)
- [ ] Karte mit zwei Fingern zoomen und mit einem Finger verschieben (Reise und Schlacht); Antippen trifft danach weiter die richtigen Marker; „Einpassen“ setzt zurück (neu in 1.18.0)
- [ ] Spielbildschirm: Titel, Runde und Bedrohung in einer Kopfzeile; Kopf, Zielzeile und untere Leiste durchscheinend, Karte darunter sichtbar; Ziel in einer Zeile, Antippen zeigt den ganzen Text (neu in 1.19.0)
- [ ] Lager als Szene: Lagerbild als Hintergrund, Helden als Karten oben (antippen: EP, Fertigkeiten, Rolle), Kartensymbol oben links, Wissen und Inventar unten links, Weiter unten rechts (neu in 1.20.0)
- [ ] Neues Spiel: Helden und Rollen aus dem Katalog per Auswahlliste (jeder Held nur einmal); Rollenwechsel im Lager ebenfalls per Liste (neu in 1.21.0)
- [ ] Pergament-Design: Titel und Knöpfe in Uncial Antiqua (frei, OFL; eine eigene Titelschrift im Katalog geht weiter vor), Fließtext in EB Garamond; Lesbarkeit am Tisch prüfen (neu in 1.22.0, Uncial ab 1.23.0)
- [ ] Elbische Inschrift (neu in 1.23.0): Test-Kampagne, Höhle → „Alte Inschrift“ zeigt Tengwar-Zeile, darunter die Umschrift kursiv und die Übersetzung
- [ ] Editor: Event-Schritt „Elbische Inschrift“ mit Vorschau in Tengwar; Prüfung meldet leere Inschrift oder fehlende Übersetzung
- [ ] Heldenausrüstung (neu in 1.33.0, Daten im privaten Katalog): neue Kampagne – jeder Held startet mit seiner Ausrüstung (z. B. Aragorn: Schwert, Banner, Reisekleidung); Lager → Held zeigt „Ausrüstung“ mit Stufe
- [ ] Ausrüstung aufwerten: Knopf „Aufwerten zu …“ erst ab dem Wissenswert der Karte; mehrere Wege zur Auswahl; eine Karte, die schon ein anderer Held hat, wird nicht angeboten; nur ein Stufe-IV-Gegenstand pro Held
- [ ] Reittiere: Event-Schritt „Schmuckstück finden“ mit einem Reittier (Liste zeigt „(Reittier)“); Inventar zeigt „– Reittier“; Heldenausrüstung lässt sich nicht finden (Prüfung meldet Fehler)
- [ ] Gegnerbilder (neu in 1.32.0, Bilder im privaten Katalog): Gegner erscheinen auf Reise- und Schlachtkarte als rundes Bild mit Anzahl unten links und Banner unten rechts; Elite mit gezacktem Rahmen
- [ ] Gegnermenü, Angriffsfenster und Meldung „Gegner erscheinen“ zeigen das Bild
- [ ] Titel (neu in 1.31.0): Test-Kampagne, „Frische Spuren“ – App fragt, welcher Held „Spurenfinder“ erhält; Lager zeigt den Titel; im nächsten Szenario wird derselbe Titel nicht noch einmal vergeben
- [ ] Schwäche: Umgestürzte Truhe misslungen → Meldung „mischt 1 Schwäche in seinen Fertigkeitenstapel“
- [ ] Lager → Fertigkeit kaufen: Auswahlliste mit den Fertigkeiten der aktuellen Rolle (Name, EP, Merkmale/Schlüsselwörter), schon gekaufte Karten fehlen; freie Eingabe geht weiter
- [ ] Editor: Schritte „Titel erhalten“ (Auswahl aus 33 Titeln) und „Schwäche erhalten“
- [ ] Heldenwerte (neu in 1.30.0, Daten im privaten Katalog): Neues Spiel – Auswahlliste zeigt Eigenschaften und Limits; beim Wählen wird die empfohlene Rolle eingetragen
- [ ] Lager → Held antippen: Werte, Fähigkeit und Startausrüstung
- [ ] Rollen heißen jetzt wie in den Regelheften (Hauptmann, Reisender, Wissenshüter, Wildnisführer, Proviantmeister, Krieger, Spitzbube); neu: Schatzgräber, Tierfreund
- [ ] Zustände zeigen ihre Wirkung (z. B. Ermutigt: beim Angriff +2 Treffer)
- [ ] Verderbnis für mächtige Gegenstände (neu in 1.29.0): Test-Kampagne, Höhle – nach dem Ring „Den Ring überstreifen“ → App fragt nach dem Helden, +1 Verderbnis und Gunst Versteckt
- [ ] Erleichterung durch Verderbnis: Altar zerstören (Körperkraft 3) zeigt den Knopf „Mit Verderbnis erleichtern“; danach Heldenwahl, +1 Verderbnis, Probe braucht nur noch 2 Erfolge; der Knopf erscheint nur einmal
- [ ] Editor: Schritt „Verderbnis erhalten“, bei Proben Feld „Erleichterung durch Verderbnis“; Prüfung warnt bei Sammelproben oder Proben mit nur 1 Erfolg
- [ ] Dunkelheit als Marker (neu in 1.28.0): Test-Kampagne, Bedrohung 8 – Hügelkuppe bekommt ein violettes „D“; danach meldet jede Schattenphase „Dunkelheit: 1 Furcht“ für Helden auf solchen Feldern
- [ ] Verderbnis (neu in 1.27.0): Test-Kampagne, Höhle nach dem Rätsel – Knopf „Den Ring an euch nehmen · +1 Verderbnis“; App fragt, welcher Held, und meldet den neuen Stand
- [ ] Verderbnis bleibt: Lager zeigt „Verderbnis 1“ unter dem Helden; nächstes Szenario beginnt mit „Verderbnis auslegen“
- [ ] Bruchwald: Dorfbrunnen – jeder verliert 1 Verderbnis; Kräuterfrau – App fragt, welcher Held spricht, verdorbene Helden bekommen einen anderen Text
- [ ] Altar (Steinkreis): „Das Opfergold einstecken · +1 Verderbnis“; ein Held mit schon 3 Verderbnis muss stattdessen ein Letztes Aufbäumen auf Wille ablegen (zählt nicht als das Aufbäumen gegen Schaden/Furcht)
- [ ] Gelände-Marker: Regen in Runde 2 legt „Unwegsamer Untergrund“ (braunes U auf der Karte, Regel in der Meldung); Welle im Steinkreis macht ein Feld „Befestigt“ (graues B)
- [ ] Editor: Auswahl-Option mit Feld „Verderbnis“; Schritte „Verderbnis verlieren“, „Verdorben?“, „Gelände-Marker legen/entfernen“ (Kachel + Feld q/r bzw. Spalte/Zeile); Probespiel fragt nach dem Helden
- [ ] Elite-Gegner (neu in 1.26.0): Test-Kampagne, Steinkreis – Hügeltroll erscheint mit „Elite-Gegner! Stellt Elite-Banner 1 dazu“, Erklärung und Boni; auf der Karte rote gezackte Raute mit rotem Banner
- [ ] Elite-Werte: Gegnermenü und Angriffsfenster zeigen die erhöhte Rüstung (Gepanzert); Gegenangriff des Trolls hat +1 Schaden (Wütend)
- [ ] Erschöpfung: Nach einem Gegenangriff ist eine normale Gruppe erschöpft (kein weiterer Gegenangriff, kein Provozieren, keine Aktivierung in dieser Schattenphase); eine Elite-Gruppe greift trotzdem zurück an, außer sie wurde betäubt
- [ ] Editor: Gegnergruppe → Haken „Elite-Gruppe“, Boni, Erklärung; Prüfung warnt ohne Bonus oder Erklärung; KI setzt Elite sparsam und begründet die Boni
- [ ] Zustände (neu in 1.25.0): Test-Kampagne zeigt alle sechs – Ermutigt (Lagerfeuer), Versteckt (Hügelkuppe), Verzagt (Inschrift misslungen), Entschlossen (Signalhorn), Gefangen (Zeltseile misslungen, Hinweis „Rückseite nicht ansehen“), Verängstigt (Altar misslungen)
- [ ] Editor: Event-Schritt „Zustand erhalten (Gunst oder Fluch)“ mit Zustand und Wer; Katalog → Gegnertyp → Angriff: „Zustand“ (z. B. Verängstigt), erscheint in App und Probespiel beim Angriff
- [ ] KI-Generator: 1–3 Zustände pro Szenario, Gunst als Belohnung, Flüche sparsam nach misslungenen Proben
- [ ] Token, die ein Event auf eine noch nicht erkundete Kachel legt, erscheinen erst beim Erkunden dieser Kachel (Test-Kampagne: „Frische Spuren“) (neu in 1.24.1)
- [ ] Elbisches Rätsel (neu in 1.24.0): Test-Kampagne, „Alte Inschrift“ zeigt zuerst nur Tengwar und Umschrift; Probe Scharfsinn 3 → erst bei Erfolg erscheint die Übersetzung, bei Misserfolg bleibt sie verborgen
- [ ] Editor: bei „Elbische Inschrift“ Haken „Rätsel“ setzen, Wert, Erfolge sowie Bei Erfolg/Misserfolg wählen; die Inschrift muss dann der letzte Schritt im Event sein
- [ ] KI-Generator: höchstens 0–2 Inschriften pro Szenario, nur an elbischen Orten, immer mit Übersetzung
- [ ] Verzierungen aus der Katalogschrift `schrift/ornament.ttf`: Band unter der Einleitung, Ring bei „Schmuckstück gefunden“, gekreuzte Schwerter bei Sieg/Niederlage; ohne Katalogschrift fehlen sie einfach

## 1k. Proben, Stimmungs-Marker, Gegnermenü und Banner (neu in 1.12.0)

- [ ] Neues KI-Szenario: Proben mit 1, 2 und 3+ Erfolgen gemischt (leicht/normal/schwer)
- [ ] Verdeckte Probe: App nennt die Zahl nicht, meldet nur „geschafft“ / „nicht geschafft“
- [ ] Sammelprobe: mehrere Versuche (auch verschiedene Helden, über Runden), App zeigt „bisher X von Y“; der Marker bleibt liegen
- [ ] Editor: Probe auf „Verdeckt“ und „Sammelprobe“ stellen; Schritt „Erholung“ anlegen
- [ ] KI-Szenario: 2–3 optionale Stimmungs-Marker pro Kapitel mit kleinen Belohnungen
- [ ] KI-Szenario: Gegner erscheinen wirklich (2–4 Gruppen)
- [ ] Gegner-Raute oder Gegnername antippen → Gegnermenü mit Werten, Gesundheit je Feind, Angreifen/Provozieren/Besiegt
- [ ] Zwei Gruppen desselben Gegnertyps: die zweite bekommt „Banner 1“ (Anzeige an der Raute und im Namen); nach dem Besiegen wird die Nummer wieder frei

## 1i. Alle Kacheln, Symbole und Höhlen-Durchgänge (Katalog vom 2.11.)

- [ ] Katalog-Fenster: Kacheln 300–320, 400–404, 500 vorhanden, Bilder gerade, Raster passt, Nummer und Seite stimmen
- [ ] Symbole auf den Kacheln (Dunkelheit, Befestigt, schwieriges Gelände) stichprobenartig mit der echten Kachel vergleichen; besonders 207B (0,0), 211B (2,-2), 224B (4,-2) und 227B (3,-1, jetzt „Befestigt“)
- [ ] Höhlenkachel ohne Durchgang zu einer Nachbarkachel legen → Hinweis in der Prüfung; mit passendem Pfeil → Hinweis verschwindet
- [ ] KI-Generator: Plant er Höhlenkacheln so, dass die Helden hinein- und wieder herauskommen?
- [ ] Erweiterung der Kacheln 2xx–5xx stimmt? (derzeit alle „Grundspiel“)

## 2. Windows-Editor

- [ ] Installation der `.msi`; Update von einer älteren Version (überschreibt sauber?)
- [ ] Windows-Warnung „unbekannte App“ → „Trotzdem ausführen“ klappt
- [ ] Datei-Dialoge (Öffnen, Speichern unter, Katalog wählen, ZIP exportieren, Textdatei laden)
- [ ] Tastenkürzel: Strg+S/Z/Y/N/O/G, F5; im Karteneditor R, F, Entf, Esc
- [ ] Karteneditor mit der Maus: Kachel ziehen, drehen, Seite wechseln, Mausrad-Zoom, Ansicht verschieben
- [ ] Kachelbilder erscheinen gerade und passend auf den Feldern (auch gedreht)
- [ ] Tischgröße einstellen (Datei → Tischgröße …) → Anzeige über der Karte, rote Warnung bei zu großer Karte
- [ ] Katalog-Fenster: Kachel anlegen, Form anklicken, Kategorien setzen, als ZIP exportieren
- [ ] Ungespeicherte Änderungen: Nachfrage beim Schließen

## 3. Android-App (auf dem Handy)

- [ ] Installation der `.apk`, Start, Beispielkampagne laden, komplett durchspielen
- [ ] `mein-katalog.zip` importieren (⋮ → Material-Katalog importieren) → Kacheln mit Bildern im Spiel
- [ ] Im Editor gespeicherte Kampagne importieren (z. B. über Google Drive / OneDrive)
- [ ] KI-generiertes Szenario am Tisch spielen
- [ ] Spiel unterbrechen, App schließen, Spiel fortsetzen
- [ ] Lesbarkeit am Tisch: Schriftgrößen, Karte, Aufbau-Anweisungen

## 4. Am echten Tisch

- [ ] Aufbau nach App-Anweisung: stimmen Kachelnummer, Seite, Lage und Drehung mit der Karte überein?
- [ ] Passt eine generierte Karte wirklich auf deinen Tisch?
- [ ] Token-/Gegner-Positionen eindeutig erkennbar?

## Offene Punkte / später

- Installer verkleinern (große Icon-Bibliothek entfernen, ca. –35 MB)
- Pull Requests für Phase 1 und 2 zusammenführen (Branch `phase2-editor` → `main`)
