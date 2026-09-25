package dev.chaosaholic.core;

import java.util.regex.Pattern;

/** Event id rules: lower snake case, 2-32 chars, used in commands, lang keys and saved settings. */
public final class EventIds {
	private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_]{1,31}");

	private EventIds() {}

	public static boolean isValid(String id) {
		return id != null && ID.matcher(id).matches();
	}

	public static String nameKey(String id) {
		return "chaosaholic.event." + id;
	}

	public static String descKey(String id) {
		return nameKey(id) + ".desc";
	}

	public static String announceKey(String id) {
		return nameKey(id) + ".announce";
	}

	public static String warningKey(String id) {
		return nameKey(id) + ".warning";
	}
}
