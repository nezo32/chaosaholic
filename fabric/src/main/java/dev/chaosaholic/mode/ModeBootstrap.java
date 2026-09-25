package dev.chaosaholic.mode;

import dev.chaosaholic.Chaosaholic;
import dev.chaosaholic.mixin.MinecraftServerAccessor;
import net.minecraft.server.MinecraftServer;

/** ServerLifecycleEvents.SERVER_STARTING: initialize the mode before levels load or anyone joins. */
public final class ModeBootstrap {
	private ModeBootstrap() {}

	public static void onServerStarting(MinecraftServer server) {
		// 1. new world from the Create World screen: the button value rides on this world's storage access
		PendingWorldMode access = (PendingWorldMode) ((MinecraftServerAccessor) server).chaosaholic$getStorageSource();
		Boolean pending = access.chaosaholic$takePendingMode();
		if (pending != null) {
			ChaosSettings.get(server).setEnabled(pending);
			server.getDataStorage().scheduleSave(); // persist now: a crash before the first autosave must not lose the choice
			Chaosaholic.LOGGER.info("Chaosaholic Mode {} for new world", pending ? "ON" : "OFF");
			return;
		}
		// 2. existing world with settings.dat: it is authoritative
		if (server.getDataStorage().get(ChaosSettings.TYPE) != null) {
			Chaosaholic.LOGGER.debug("Chaosaholic Mode loaded: {}", ChaosSettings.get(server).enabled());
			return;
		}
		// 3. no settings.dat yet (dedicated server, other launcher, world from before the mod): OFF, written once
		ChaosSettings.get(server).setEnabled(false);
		server.getDataStorage().scheduleSave();
	}
}
