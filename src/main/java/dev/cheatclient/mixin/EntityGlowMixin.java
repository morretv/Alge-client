package dev.cheatclient.mixin;

import dev.cheatclient.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** ESP / Item-ESP ueber den eingebauten Glow-Outline-Effekt (sichtbar auch durch Waende). */
@Mixin(Entity.class)
public class EntityGlowMixin {
	@Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true, require = 0)
	private void cheat$esp(CallbackInfoReturnable<Boolean> cir) {
		boolean esp = Modules.ESP.enabled, items = Modules.ITEMESP.enabled;
		if (!esp && !items) return;
		Entity self = (Entity) (Object) this;
		var player = Minecraft.getInstance().player;
		if (player == null || self == player) return;
		if (esp && Modules.matchesTarget(self) && player.distanceTo(self) <= Modules.ESP.value) {
			cir.setReturnValue(true);
		} else if (items && self instanceof ItemEntity && player.distanceTo(self) <= Modules.ITEMESP.value) {
			cir.setReturnValue(true);
		}
	}
}
