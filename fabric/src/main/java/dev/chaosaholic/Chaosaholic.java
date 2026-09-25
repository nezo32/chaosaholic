package dev.chaosaholic;

import dev.chaosaholic.event.ChaosEvents;
import dev.chaosaholic.event.EventManager;
import dev.chaosaholic.event.PlayerLevels;
import dev.chaosaholic.event.helper.Marks;
import dev.chaosaholic.mode.ChaosCommand;
import dev.chaosaholic.mode.ModeBootstrap;
import dev.chaosaholic.net.ChaosNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Chaosaholic implements ModInitializer {
	public static final String MOD_ID = "chaosaholic";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ChaosNetworking.register();
		PlayerLevels.register();
		Marks.register();
		ChaosEvents.register();
		ServerLifecycleEvents.SERVER_STARTING.register(ModeBootstrap::onServerStarting);
		EventManager.register(); // after the bootstrap: SERVER_STARTING listeners run in registration order
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ChaosCommand.register(dispatcher));
	}
}
