package dev.cheatclient.mixin;

import dev.cheatclient.Modules;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public class XrayMixin {
	// require = 0: Falls sich die Methode in 26.2 geaendert hat, stuerzt das Spiel nicht ab,
	// X-Ray tut dann nur nichts (siehe README).
	@Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true, require = 0)
	private static void cheat$xray(BlockState state, BlockState neighbor, Direction dir,
	                               CallbackInfoReturnable<Boolean> cir) {
		if (Modules.XRAY.enabled) cir.setReturnValue(Modules.XRAY.matches(state));
	}
}
