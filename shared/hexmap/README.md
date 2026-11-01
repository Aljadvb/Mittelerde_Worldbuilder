# Gemeinsamer Kartencode

Hex-Geometrie und Kartenzeichnung (Compose), die **App und Windows-Editor gemeinsam** nutzen.
Kein eigenes Gradle-Modul: Beide Projekte binden diesen Ordner als zusätzliches Quellverzeichnis ein,
weil die verwendeten Compose-APIs auf Android und Desktop identisch sind.
