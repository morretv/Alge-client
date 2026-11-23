package dev.cheatclient;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Zeichnet ESP-Boxen und Tracer-Linien, auch durch Waende.
 *
 * ACHTUNG - das ist der unsicherste Teil im ganzen Projekt. Minecraft 26.2
 * hat die Rendering-Pipeline grossteils neu gebaut (siehe GuiGraphicsExtractor
 * fuer 2D-GUI). Fuer die 3D-Welt-Rendering-Seite (PoseStack, RenderType,
 * MultiBufferSource, "durch Waende sehen" per deaktiviertem Tiefentest)
 * konnte ich die genauen 26.2-Signaturen hier nicht gegen echten Code
 * pruefen. Die Grundidee (RenderType ohne Tiefentest + Fabric API
 * WorldRenderEvents) ist in aelteren Versionen Standard, kann sich in 26.2
 * aber in Details unterscheiden. Wenn der Build hier Fehler wirft: das ist
 * die Stelle, deren Fehlermeldung mir am meisten hilft.
 */
public final class EspRenderer {
	private EspRenderer() {}

	public static void init() {
		WorldRenderEvents.AFTER_TRANSLUCENT.register(EspRenderer::render);
	}

	private static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		if (!CheatClient.allowed(mc)) return;
		boolean any = Modules.ESP.enabled || Modules.TRACERS.enabled
			|| Modules.STORAGE.enabled || Modules.BASEFINDER.enabled;
		if (!any) return;

		PoseStack pose = ctx.matrixStack();
		if (pose == null) return;
		MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
		Vec3 cam = ctx.camera().getPosition();

		pose.pushPose();
		pose.translate(-cam.x, -cam.y, -cam.z);

		if (Modules.ESP.enabled) {
			var through = RenderType.lines(); // Platzhalter: siehe Klassenkommentar - idealerweise ohne Tiefentest
			var vc = buffers.getBuffer(through);
			for (LivingEntity e : Modules.entities(mc, mc.player, Modules.ESP.value)) {
				if (e == mc.player) continue;
				AABB box = e.getBoundingBox().inflate(0.05);
				drawBox(pose, vc, box, 1f, 0.25f, 0.25f, 0.9f);
			}
		}

		if (Modules.TRACERS.enabled) {
			var through = RenderType.lines();
			var vc = buffers.getBuffer(through);
			Vec3 from = mc.player.getEyePosition(ctx.tickDelta());
			for (LivingEntity e : Modules.entities(mc, mc.player, Modules.TRACERS.value)) {
				if (e == mc.player) continue;
				Vec3 to = e.getBoundingBox().getCenter();
				drawLine(pose, vc, from, to, 0.3f, 1f, 0.4f, 0.8f);
			}
		}

		if (Modules.STORAGE.enabled) renderStorage(mc, pose, buffers);
		if (Modules.BASEFINDER.enabled) renderBaseFinder(mc, pose, buffers);

		buffers.endBatch();
		pose.popPose();
	}

	/**
	 * Scannt geladene Chunks um den Spieler nach Behaelter-Block-Entities
	 * (Kiste, Fass, Shulker, Ofen, Hopper) und zeichnet Boxen darum.
	 * Untested: {@code LevelChunk#getBlockEntities()} ist die ueblichen
	 * Mojang-Mappings-Methode dafuer, kann sich aber geaendert haben.
	 */
	private static void renderStorage(Minecraft mc, PoseStack pose, MultiBufferSource.BufferSource buffers) {
		var vc = buffers.getBuffer(RenderType.lines());
		var p = mc.player;
		int range = (int) (Modules.STORAGE.value / 16) + 1;
		int cx = p.chunkPosition().x, cz = p.chunkPosition().z;
		for (int dx = -range; dx <= range; dx++) for (int dz = -range; dz <= range; dz++) {
			var chunk = mc.level.getChunkSource().getChunk(cx + dx, cz + dz, false);
			if (chunk == null) continue;
			try {
				for (var entry : chunk.getBlockEntities().entrySet()) {
					var be = entry.getValue();
					if (!isContainer(be)) continue;
					var pos = entry.getKey();
					if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
						> Modules.STORAGE.value * Modules.STORAGE.value) continue;
					AABB box = new AABB(pos).inflate(0.03);
					drawBox(pose, vc, box, 1f, 0.85f, 0.2f, 0.9f);
				}
			} catch (Throwable ignored) {
				// getBlockEntities()-Signatur kann abweichen - siehe Klassenkommentar
			}
		}
	}

	private static boolean isContainer(net.minecraft.world.level.block.entity.BlockEntity be) {
		return be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity
			|| be instanceof net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity
			|| be instanceof net.minecraft.world.level.block.entity.HopperBlockEntity
			|| be instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity
			|| be instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity;
	}

	/**
	 * Markiert geladene Chunks, in denen laut {@code inhabitedTime} schon
	 * Spieler unterwegs waren - grober Hinweis auf Basen/Spielerbauten.
	 */
	private static void renderBaseFinder(Minecraft mc, PoseStack pose, MultiBufferSource.BufferSource buffers) {
		var vc = buffers.getBuffer(RenderType.lines());
		var p = mc.player;
		int range = 6;
		int cx = p.chunkPosition().x, cz = p.chunkPosition().z;
		int groundY = (int) p.getY();
		for (int dx = -range; dx <= range; dx++) for (int dz = -range; dz <= range; dz++) {
			var chunk = mc.level.getChunkSource().getChunk(cx + dx, cz + dz, false);
			if (chunk == null) continue;
			try {
				if (chunk.getInhabitedTime() < (long) Modules.BASEFINDER.value) continue;
				int bx = (cx + dx) * 16, bz = (cz + dz) * 16;
				AABB box = new AABB(bx, groundY - 4, bz, bx + 16, groundY + 20, bz + 16);
				drawBox(pose, vc, box, 0.3f, 0.6f, 1f, 0.6f);
			} catch (Throwable ignored) {
				// getInhabitedTime() kann je nach Version abweichen
			}
		}
	}

	private static void drawLine(PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer vc,
	                              Vec3 a, Vec3 b, float r, float g, float bl, float alpha) {
		var mat = pose.last().pose();
		vc.addVertex(mat, (float) a.x, (float) a.y, (float) a.z).setColor(r, g, bl, alpha);
		vc.addVertex(mat, (float) b.x, (float) b.y, (float) b.z).setColor(r, g, bl, alpha);
	}

	private static void drawBox(PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer vc,
	                             AABB bb, float r, float g, float b, float alpha) {
		double[] xs = {bb.minX, bb.maxX}, ys = {bb.minY, bb.maxY}, zs = {bb.minZ, bb.maxZ};
		// 12 Kanten einer Box: je zwei Punkte, die sich in genau einer Koordinate unterscheiden
		for (int i = 0; i < 2; i++) for (int j = 0; j < 2; j++) {
			line(pose, vc, xs[0], ys[i], zs[j], xs[1], ys[i], zs[j], r, g, b, alpha);
			line(pose, vc, xs[i], ys[0], zs[j], xs[i], ys[1], zs[j], r, g, b, alpha);
			line(pose, vc, xs[i], ys[j], zs[0], xs[i], ys[j], zs[1], r, g, b, alpha);
		}
	}

	private static void line(PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer vc,
	                          double x1, double y1, double z1, double x2, double y2, double z2,
	                          float r, float g, float b, float alpha) {
		var mat = pose.last().pose();
		vc.addVertex(mat, (float) x1, (float) y1, (float) z1).setColor(r, g, b, alpha);
		vc.addVertex(mat, (float) x2, (float) y2, (float) z2).setColor(r, g, b, alpha);
	}
}
