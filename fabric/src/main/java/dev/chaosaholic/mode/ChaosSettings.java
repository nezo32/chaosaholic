package dev.chaosaholic.mode;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.chaosaholic.Chaosaholic;
import dev.chaosaholic.core.ChaosLimits;
import dev.chaosaholic.event.ChaosEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Per-world settings, saved as {@code <world>/data/chaosaholic/settings.dat} in the server-wide data storage:
 * the mode switch (absent file = off), the scope (default player) and per-event overrides. An event without an
 * override uses its own defaults (enabled, {@link ChaosEvent#defaultWeight()}); overrides of unknown ids are kept, so
 * a removed-then-restored event keeps its setting.
 */
public final class ChaosSettings extends SavedData {
	/** One event's override; empty fields mean "event default". */
	public record EventSetting(Optional<Boolean> enabled, Optional<Integer> weight) {
		public static final EventSetting NONE = new EventSetting(Optional.empty(), Optional.empty());
		public static final Codec<EventSetting> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.BOOL.optionalFieldOf("enabled").forGetter(EventSetting::enabled),
				Codec.INT.optionalFieldOf("weight").forGetter(EventSetting::weight)
		).apply(i, EventSetting::new));

		public boolean isEmpty() {
			return enabled.isEmpty() && weight.isEmpty();
		}
	}

	public static final Codec<ChaosSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", false).forGetter(ChaosSettings::enabled),
			Scope.CODEC.optionalFieldOf("scope", Scope.PLAYER).forGetter(ChaosSettings::scope),
			Codec.unboundedMap(Codec.STRING, EventSetting.CODEC).optionalFieldOf("events", Map.of()).forGetter(s -> s.events)
	).apply(i, ChaosSettings::new));

	/** null DataFixTypes: no vanilla fixer applies; Fabric's SavedDataStorageMixin skips datafixing for null. */
	public static final SavedDataType<ChaosSettings> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath(Chaosaholic.MOD_ID, "settings"), ChaosSettings::new, CODEC, null);

	private boolean enabled;
	private Scope scope;
	private final Map<String, EventSetting> events;

	public ChaosSettings() {
		this(false, Scope.PLAYER, Map.of());
	}

	private ChaosSettings(boolean enabled, Scope scope, Map<String, EventSetting> events) {
		this.enabled = enabled;
		this.scope = scope;
		this.events = new HashMap<>(events);
	}

	public static ChaosSettings get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public boolean enabled() {
		return enabled;
	}

	/** Always marks dirty, so the file exists even when the value did not change. */
	public void setEnabled(boolean value) {
		enabled = value;
		setDirty();
	}

	public Scope scope() {
		return scope;
	}

	public void setScope(Scope value) {
		scope = value;
		setDirty();
	}

	public boolean isEventEnabled(ChaosEvent event) {
		return events.getOrDefault(event.id(), EventSetting.NONE).enabled().orElse(true);
	}

	/** The per-world weight, clamped to [0, 1000]. */
	public int weight(ChaosEvent event) {
		return ChaosLimits.clampWeight(events.getOrDefault(event.id(), EventSetting.NONE).weight().orElse(event.defaultWeight()));
	}

	/** Weight used for random rolls: 0 when the event is switched off. */
	public int effectiveWeight(ChaosEvent event) {
		return isEventEnabled(event) ? weight(event) : 0;
	}

	public void setEventEnabled(ChaosEvent event, boolean value) {
		EventSetting old = events.getOrDefault(event.id(), EventSetting.NONE);
		put(event.id(), new EventSetting(value ? Optional.empty() : Optional.of(false), old.weight()));
	}

	public void setWeight(ChaosEvent event, int weight) {
		int w = ChaosLimits.clampWeight(weight);
		EventSetting old = events.getOrDefault(event.id(), EventSetting.NONE);
		put(event.id(), new EventSetting(old.enabled(), w == event.defaultWeight() ? Optional.empty() : Optional.of(w)));
	}

	private void put(String id, EventSetting setting) {
		if (setting.isEmpty()) {
			events.remove(id);
		} else {
			events.put(id, setting);
		}
		setDirty();
	}

	/** Drops every per-event override (gametests). */
	public void resetEvents() {
		events.clear();
		setDirty();
	}
}
