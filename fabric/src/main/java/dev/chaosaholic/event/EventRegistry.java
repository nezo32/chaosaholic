package dev.chaosaholic.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/** All chaos events, in registration order ({@link ChaosEvents}). Ids are unique and valid (checked by ChaosEvent). */
public final class EventRegistry {
	private static final Map<String, ChaosEvent> EVENTS = new LinkedHashMap<>();

	private EventRegistry() {}

	/** Registers {@code event}; a duplicate id is a programming error. Returns the event. */
	public static <T extends ChaosEvent> T register(T event) {
		if (EVENTS.containsKey(event.id())) throw new IllegalStateException("duplicate chaos event id: " + event.id());
		EVENTS.put(event.id(), event);
		return event;
	}

	public static @Nullable ChaosEvent get(String id) {
		return EVENTS.get(id);
	}

	public static List<ChaosEvent> all() {
		return Collections.unmodifiableList(new ArrayList<>(EVENTS.values()));
	}

	public static List<String> ids() {
		return List.copyOf(EVENTS.keySet());
	}
}
