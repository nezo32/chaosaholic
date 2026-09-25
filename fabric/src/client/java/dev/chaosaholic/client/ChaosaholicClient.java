package dev.chaosaholic.client;

import net.fabricmc.api.ClientModInitializer;

public final class ChaosaholicClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		NotifyConfig.load();
		NotifyClient.register();
		NotifyCommand.register();
		ClientEventState.register();
	}
}
