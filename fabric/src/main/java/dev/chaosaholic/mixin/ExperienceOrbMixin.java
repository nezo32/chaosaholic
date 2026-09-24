package dev.chaosaholic.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.chaosaholic.event.impl.DoubleXp;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * double_xp: the points an orb gives after Mending repairs ({@code playerTouch} → {@code Player#giveExperiencePoints})
 * go through {@link DoubleXp#points}, which doubles them while the event affects that player.
 */
@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbMixin {
	@WrapOperation(method = "playerTouch", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Player;giveExperiencePoints(I)V"))
	private void chaosaholic$doubleXp(Player player, int points, Operation<Void> original) {
		original.call(player, DoubleXp.points(player, points));
	}
}
