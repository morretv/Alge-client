# Cheat Client (eigenstaendig) – Fabric 26.2

Ein eigenes, unabhaengiges Mod. Keine Abhaengigkeit von Meteor Client oder
Baritone, kein Addon - laeuft fuer sich alleine mit Fabric API.

Singleplayer: sofort aktiv. Server: erst nach `.server on` (pro Verbindung,
nur wo Cheats erlaubt sind).

## Bauen
1. JDK 25 installieren.
2. Im Projektordner einmal `gradle wrapper --gradle-version 9.5.1` ausfuehren.
3. `./gradlew build`  ->  `build/libs/cheatclient-1.0.0.jar` in den mods-Ordner
   (+ Fabric API). Testen: `./gradlew runClient`

## Oeffnen des Menues
`.menu` oder `.gui` im Chat oeffnet das Menue im Stil deines Videos
(Tabs Combat / Movement / Player / World / Settings). ESC schliesst.

## Befehle (im Chat, mit Punkt)
.help  .list  .menu  .server on|off
.target mobs|players|all   .aimspeed <grad/tick>
.<modul>  /  .<modul> on|off  /  .<modul> 0.8  (Zahl = Tempo/Reichweite)
Nur Singleplayer: .gm creative|survival|adventure|spectator  .heal

Combat:   autoaim, killaura, velocity (Rueckstoss), criticals
Movement: fly, speed, sprint, goto (einfacher Autowalk)
Player:   nofall, autocmd, scaffold
World:    fullbright, xray, esp, tracers, storageesp (Kisten/Oefen), basefinder (Chunks mit Spieleraktivitaet)

## X-Ray
.xray add diamond | .xray remove spawner | .xray list

## Autocmd (Rechtsklick-Makro)
Fuehrt beim Rechtsklick (Benutzen-Taste) eine Liste von Chat-Befehlen aus.
Platzhalter: {player} = dein Spielername, {balance} = letzte Zahl aus der
Scoreboard-Sidebar (bestes Bemuehen, API kann je nach Version abweichen).

.autocmd on|off
.autocmd <zahl>              Cooldown in ms (z.B. .autocmd 1000)
.autocmd add sell all        Befehl hinzufuegen (kein / noetig)
.autocmd add pay Bob {balance}
.autocmd remove 0            Befehl Nr. 0 entfernen
.autocmd list                 Zeigt alle Befehle + Cooldown

## Bekannte Unsicherheiten (ungetestet)
Ich konnte das hier nicht gegen echtes Minecraft 26.2 kompilieren (kein
Netzwerk/Build in dieser Umgebung). Falls `./gradlew build` meckert, schick
mir die Fehlermeldung - meistens reicht eine kleine Anpassung an einer
Mapping-/Klassennamen-Stelle.

## Neu: Esp, Tracers, Scaffold, Goto
Alles eigener Code, nichts aus Meteor Client oder Baritone kopiert.

- `.esp on|off|zahl` – Boxen um Spieler/Mobs, auch durch Waende (Wert = Reichweite).
  Zeichnet ueber `EspRenderer` via Fabric API `WorldRenderEvents`.
- `.tracers on|off|zahl` – Linien von der Kamera zu Spielern/Mobs (Wert = Reichweite).
- `.scaffold on|off` – platziert automatisch einen Block unter dir, wenn du laeufst
  und kein Block im Inventar fehlt (sucht Slot 0-8 nach einem `BlockItem`).
- `.goto <x> <y> <z>` / `.goto off` – einfacher Autowalk: laeuft geradewegs Richtung
  Ziel und springt, wenn er vorne blockiert ist. **Kein echter Pfadfinder** wie
  Baritone (kein A*, keine Umgehung von Wasser/Luecken/Umwegen).

**Groesste Unsicherheit:** `EspRenderer.java` – Minecraft 26.2 hat die
Rendering-Pipeline grossteils umgebaut. Ich konnte PoseStack/RenderType/
MultiBufferSource/WorldRenderEvents hier nicht gegen echten 26.2-Code pruefen.
Falls der Build genau hier Fehler wirft: das ist die Stelle, deren
Fehlermeldung am meisten hilft. Auch ohne "durch Waende sehen" (also mit
normalem Tiefentest) waere das Modul schon nuetzlich - dafuer muss nur
`RenderType.lines()` durch eine Variante ohne Tiefentest ersetzt werden,
sobald klar ist, wie die in 26.2 heisst.

## Neu: PvP- und Finder-Module
Wieder alles eigener Code.

- `.velocity on|off|zahl` – Rueckstoss verringern (0 = keiner, 1 = normal, Default 0.3).
  Braucht `mixin/VelocityMixin` (neu, in cheatclient.mixins.json eingetragen).
- `.criticals on|off|zahl` – springt automatisch kurz bevor ein Ziel in Reichweite ist,
  um eher kritische Treffer zu landen.
- `.storageesp on|off|zahl` – hebt Kisten, Fässer, Shulker, Oefen und Hopper in
  geladenen Chunks hervor, auch durch Waende (Wert = Reichweite in Bloecken).
- `.basefinder on|off|zahl` – markiert geladene Chunks, in denen sich laut
  `inhabitedTime` schon Spieler aufgehalten haben (Wert = Mindest-Ticks,
  20 Ticks = 1 Sekunde, Default 6000 = 5 Minuten). Grober Hinweis auf moegliche
  Basen in der Naehe, **kein** Ersatz fuer echte "NewChunks"-Mods, die dafuer
  Regiondateien direkt auswerten - das haette den Rahmen hier gesprengt.

**Unsicherste Stellen zum Testen, in dieser Reihenfolge:**
1. `VelocityMixin` – Methodenname/Signatur von `knockback(...)` in 26.2.
2. `EspRenderer.renderStorage` – `LevelChunk#getBlockEntities()` als Zugriffsweg
   auf Block-Entities in einem Chunk.
3. `EspRenderer.renderBaseFinder` – `getInhabitedTime()` auf dem Chunk-Objekt.
Alle drei sind mit try/catch bzw. `require = 0` abgesichert, damit ein
Mapping-Unterschied nicht gleich den ganzen Build/das ganze Spiel zerschiesst -
im schlimmsten Fall tut nur das eine Modul nichts.

## Automatischer Build über GitHub Actions
Im Repo liegt jetzt `.github/workflows/build.yml`. Sobald du pushst (oder
"Run workflow" im Actions-Tab klickst), baut GitHub automatisch:

1. GitHub-Repo -> Tab **Actions** -> den Lauf "Build" anklicken.
2. Unten auf der Lauf-Seite erscheint **Artifacts** -> `cheatclient-jar`
   zum Download (eine .zip mit der fertigen .jar drin).
3. Schlaegt der Build fehl, gibt es stattdessen ein Artefakt `build-log` mit
   den Fehlerberichten - und der rote "Build"-Schritt im Log zeigt die
   Java-Fehlermeldung direkt an. Schick mir einfach den Ausschnitt.

Das ersetzt nicht das lokale Bauen, ist aber der einfachste Weg, wenn du
kein JDK/Gradle lokal installieren willst - und es gibt dir zum ersten Mal
eine echte Compiler-Fehlermeldung, falls an den als "ungetestet" markierten
Stellen (EspRenderer, VelocityMixin, StorageEsp, BaseFinder, Xray-Mixin)
etwas an die 26.2-Mappings angepasst werden muss.
