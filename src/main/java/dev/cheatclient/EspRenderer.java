package dev.cheatclient;

/**
 * Platzhalter fuer das 3D-Zeichnen (Tracer-Linien, Storage-Boxen, Basefinder).
 *
 * In Minecraft 26.2 / Fabric API 0.161 gibt es WorldRenderEvents, RenderType und
 * MultiBufferSource in der alten Form nicht mehr (Build-Fehler). Bis die neuen
 * Signaturen bekannt sind, ist dieser Teil bewusst leer, damit das Projekt baut.
 *
 * Der normale ESP laeuft ohne eigenes Rendering ueber den Glow-Outline-Mixin
 * (siehe mixin/EntityGlowMixin) und sieht Gegner auch durch Waende.
 */
public final class EspRenderer {
	private EspRenderer() {}

	public static void init() {
		// absichtlich leer - siehe Klassenkommentar
	}
}
