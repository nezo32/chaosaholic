package dev.chaosaholic.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Payload registration; called once from Chaosaholic#onInitialize (runs on both sides). */
public final class ChaosNetworking {
	private ChaosNetworking() {}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(EventStartPayload.TYPE, EventStartPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ActiveEventsPayload.TYPE, ActiveEventsPayload.CODEC);
	}
}
