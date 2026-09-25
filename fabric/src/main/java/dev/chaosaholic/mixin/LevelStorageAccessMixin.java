package dev.chaosaholic.mixin;

import dev.chaosaholic.mode.PendingWorldMode;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Implements {@link PendingWorldMode} on the storage access of one world directory. Volatile: the
 * client thread writes the value (Create World), the integrated server thread reads it (SERVER_STARTING).
 */
@Mixin(LevelStorageSource.LevelStorageAccess.class)
public abstract class LevelStorageAccessMixin implements PendingWorldMode {
	@Unique
	private volatile Boolean chaosaholic$pendingMode;

	@Override
	public void chaosaholic$setPendingMode(boolean enabled) {
		chaosaholic$pendingMode = enabled;
	}

	@Override
	public Boolean chaosaholic$takePendingMode() {
		Boolean value = chaosaholic$pendingMode;
		chaosaholic$pendingMode = null;
		return value;
	}
}
