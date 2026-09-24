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
 * Cleared on disconnect. Only filled on servers that run Chaosaholic. The countdown stops while the game is paused
 * (singleplayer menu), like the server's boss bar.
 */
public final class ClientEventState {
	/** {@code elapsed}: ticks counted here since the id appeared; unlike total - remaining it never jumps on extension. */
	private record Timer(int remaining, int total, int elapsed) {}

	private static final Map<String, Timer> ACTIVE = new LinkedHashMap<>();

	private ClientEventState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(ActiveEventsPayload.TYPE, (payload, ctx) -> set(payload.events()));
		// the integrated server stops ticking while singleplayer is paused: so does the countdown
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!client.isPaused()) tick();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ACTIVE.clear());
	}

	/**
	 * Replaces the whole state (a payload always carries the complete list). An id that stays active keeps its local
	 * elapsed counter (an extension raises remaining and total, the effect runs on smoothly); a new id starts at
	 * total - remaining (joined mid-run, e.g. after a relog).
	 */
	public static void set(List<ActiveEventsPayload.Entry> entries) {
		Map<String, Timer> before = new LinkedHashMap<>(ACTIVE);
		ACTIVE.clear();
		for (ActiveEventsPayload.Entry e : entries) {
			Timer old = before.get(e.id());
			int elapsed = old != null ? old.elapsed() : Math.max(0, e.total() - e.remaining());
			ACTIVE.put(e.id(), new Timer(e.remaining(), e.total(), elapsed));
		}
	}

	private static void tick() {
		if (ACTIVE.isEmpty()) return;
		ACTIVE.replaceAll((id, t) -> new Timer(Math.max(0, t.remaining() - 1), t.total(), t.elapsed() + 1));
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

	/**
	 * Ticks the event has been running for the local player, counted locally (paused with the game) and continued
	 * across extensions (0 if not active).
	 */
	public static int elapsedTicks(String id) {
		Timer t = ACTIVE.get(id);
		return t == null ? 0 : t.elapsed();
	}

	public static int totalTicks(String id) {
		Timer t = ACTIVE.get(id);
		return t == null ? 0 : t.total();
	}

	public static List<String> activeIds() {
		return List.copyOf(ACTIVE.keySet());
	}
}
