package dev.cheatclient.mixin;

import dev.cheatclient.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Skaliert den Rueckstoss des lokalen Spielers. Statt knockback() erneut aufzurufen
 * (die Signatur hat sich geaendert), wird nur das erste double-Argument (die Staerke)
 * veraendert - das funktioniert fuer alle knockback-Ueberladungen gleichzeitig.
 * Wert 0 = kein Rueckstoss, 1 = normal.
 */
@Mixin(LivingEntity.class)
public class VelocityMixin {
	@ModifyVariable(method = "knockback", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
	private double cheat$velocity(double strength) {
		if (!Modules.VELOCITY.enabled) return strength;
		if ((Object) this != Minecraft.getInstance().player) return strength;
		double mult = Math.max(0, Math.min(1, Modules.VELOCITY.value));
		return strength * mult;
	}
}
