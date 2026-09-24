package dev.chaosaholic.client;

import java.io.IOException;
import java.nio.file.Path;

import dev.chaosaholic.Chaosaholic;
import dev.chaosaholic.core.NotifySettings;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The client's notification settings, in {@code config/chaosaholic.json}. A corrupt file is not overwritten at
 * load (the defaults are used); it is replaced on the next change.
 */
public final class NotifyConfig {
	public static final String FILE_NAME = "chaosaholic.json";
	private static volatile NotifySettings current = NotifySettings.DEFAULT;

	private NotifyConfig() {}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	public static void load() {
		current = NotifySettings.load(path());
	}

	public static NotifySettings get() {
		return current;
	}

	/** Updates memory first, then saves; an IOException is logged and swallowed. */
	public static void set(NotifySettings s) {
		current = s;
		try {
			s.save(path());
		} catch (IOException e) {
			Chaosaholic.LOGGER.warn("Could not save {}", path(), e);
		}
	}
}
