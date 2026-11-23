package dev.cheatclient.mixin;

import dev.cheatclient.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Verringert/entfernt den Rueckstoss, den der lokale Spieler erleidet.
 * Untested: Die Methode heisst in neueren Mojang-Mappings ueblicherweise
 * {@code LivingEntity.knockback(double, double, double)} - require = 0
 * sorgt dafuer, dass das Spiel trotzdem startet, falls sich das in 26.2
 * geaendert hat; velocity bleibt dann einfach ohne Wirkung.
 */
@Mixin(LivingEntity.class)
public class VelocityMixin {
	private static boolean cheat$reentry = false;

	@Inject(method = "knockback", at = @At("HEAD"), cancellable = true, require = 0)
	private void cheat$velocity(double strength, double x, double z, CallbackInfo ci) {
		if (cheat$reentry) return;
		if (!Modules.VELOCITY.enabled) return;
		if ((Object) this != Minecraft.getInstance().player) return;

		ci.cancel();
		double mult = Math.max(0, Math.min(1, Modules.VELOCITY.value));
		if (mult <= 0) return; // komplett abgefangen

		cheat$reentry = true;
		try {
			((LivingEntity) (Object) this).knockback(strength * mult, x, z);
		} finally {
			cheat$reentry = false;
		}
	}
}
