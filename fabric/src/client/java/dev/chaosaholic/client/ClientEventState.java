package dev.chaosaholic.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.chaosaholic.net.ActiveEventsPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client copy of the timed events affecting the local player ({@link ActiveEventsPayload}), counted down locally
 * between updates. Client-side event effects read it:
 * <pre>
 * if (ClientEventState.isActive("slippery")) ...                   // gameplay (movement prediction): always
 * if (ClientEventState.isActiveWithScreenEffects("screen_shake")) // cosmetic: respects the Screen effects setting
 * </pre>
 * Cleared on disconnect. Only filled on servers that run Chaosaholic.
 */
public final class ClientEventState {
	private record Timer(int remaining, int total) {}

	private static final Map<String, Timer> ACTIVE = new LinkedHashMap<>();

	private ClientEventState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(ActiveEventsPayload.TYPE, (payload, ctx) -> set(payload.events()));
		ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ACTIVE.clear());
	}

	/** Replaces the whole state (a payload always carries the complete list). */
	public static void set(List<ActiveEventsPayload.Entry> entries) {
		ACTIVE.clear();
		for (ActiveEventsPayload.Entry e : entries) ACTIVE.put(e.id(), new Timer(e.remaining(), e.total()));
	}

	private static void tick() {
		if (ACTIVE.isEmpty()) return;
		ACTIVE.replaceAll((id, t) -> new Timer(Math.max(0, t.remaining() - 1), t.total()));
		ACTIVE.values().removeIf(t -> t.remaining() <= 0);
	}

	public static boolean isActive(String id) {
		return ACTIVE.containsKey(id);
	}

	/** Active and the player allows screen effects (camera shake, overlays, ...). */
	public static boolean isActiveWithScreenEffects(String id) {
		return isActive(id) && screenEffects();
	}

	public static boolean screenEffects() {
		return NotifyConfig.get().effects();
	}

	/** Remaining ticks as last synced and counted down (0 if not active). */
	public static int remainingTicks(String id) {
		Timer t = ACTIVE.get(id);
		return t == null ? 0 : t.remaining();
	}

	public static int totalTicks(String id) {
		Timer t = ACTIVE.get(id);
		return t == null ? 0 : t.total();
	}

	public static List<String> activeIds() {
		return List.copyOf(ACTIVE.keySet());
	}
}
