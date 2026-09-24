package dev.chaosaholic.mixin;

import dev.chaosaholic.event.impl.SheepDisco;
import net.minecraft.world.entity.animal.sheep.Sheep;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The rainbow sheep of sheep_disco can never be sheared (no free wool): {@code readyForShearing} is the one check
 * behind players' shears, dispensers and golems alike.
 */
@Mixin(Sheep.class)
public abstract class SheepMixin {
	@Inject(method = "readyForShearing", at = @At("HEAD"), cancellable = true)
	private void chaosaholic$noDiscoShearing(CallbackInfoReturnable<Boolean> cir) {
		if (SheepDisco.isDiscoSheep((Sheep) (Object) this)) cir.setReturnValue(false);
	}
}
