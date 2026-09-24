package dev.chaosaholic.client.mixin;

import dev.chaosaholic.client.CreateWorldModeHolder;
import dev.chaosaholic.mode.PendingWorldMode;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Holds the Create World button value per screen instance and, when this screen creates its world,
 * hands the value to that world's LevelStorageAccess, the object the integrated server is built with.
 */
@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin implements CreateWorldModeHolder {
	/** New worlds start with the mode ON; the player can switch it off on the Game tab. */
	@Unique
	private boolean chaosaholic$mode = true;

	@Override
	public boolean chaosaholic$isModeEnabled() {
		return chaosaholic$mode;
	}

	@Override
	public void chaosaholic$setModeEnabled(boolean enabled) {
		chaosaholic$mode = enabled;
	}

	@ModifyArg(method = "createNewWorld", index = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/worldselection/WorldOpenFlows;createLevelFromExistingSettings(Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;Lnet/minecraft/server/ReloadableServerResources;Lnet/minecraft/core/LayeredRegistryAccess;Lnet/minecraft/world/level/storage/LevelDataAndDimensions$WorldDataAndGenSettings;Ljava/util/Optional;)V"))
	private LevelStorageSource.LevelStorageAccess chaosaholic$handOffMode(LevelStorageSource.LevelStorageAccess access) {
		((PendingWorldMode) access).chaosaholic$setPendingMode(chaosaholic$mode);
		return access;
	}
}
