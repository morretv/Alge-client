package dev.cheatclient.mixin;

import dev.cheatclient.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** ESP ueber den eingebauten Glow-Outline-Effekt (sichtbar auch durch Waende). */
@Mixin(Entity.class)
public class EntityGlowMixin {
	@Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true, require = 0)
	private void cheat$esp(CallbackInfoReturnable<Boolean> cir) {
		if (!Modules.ESP.enabled) return;
		Entity self = (Entity) (Object) this;
		var player = Minecraft.getInstance().player;
		if (player == null || self == player || !(self instanceof LivingEntity)) return;
		if (player.distanceTo(self) <= Modules.ESP.value) cir.setReturnValue(true);
	}
}
