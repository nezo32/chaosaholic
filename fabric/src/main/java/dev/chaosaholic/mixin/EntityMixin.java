package dev.chaosaholic.mixin;

import dev.chaosaholic.event.helper.OwnedEntities;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entities spawned by a chaos event (OwnedEntities#spawn) are never written to disk: a chunk unload drops them and
 * a crash cannot leave them behind. Replacements (which carry an original to restore) are saved as usual.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
	@Inject(method = "shouldBeSaved", at = @At("HEAD"), cancellable = true)
	private void chaosaholic$skipOwned(CallbackInfoReturnable<Boolean> cir) {
		if (OwnedEntities.isUnsaved((Entity) (Object) this)) cir.setReturnValue(false);
	}
}
