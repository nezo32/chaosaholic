package dev.chaosaholic;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.chaosaholic.core.EventIds;
import dev.chaosaholic.event.ChaosEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Translatable components with the mod's own English text as fallback. Players on vanilla clients (server-only
 * install) have no Chaosaholic lang file and see the fallback; clients with the mod see their language. The fallback
 * is read from {@code assets/chaosaholic/lang/en_us.json} inside the jar, so there is no English text in the code.
 */
public final class Texts {
	private static final Map<String, String> ENGLISH = load();

	private Texts() {}

	private static Map<String, String> load() {
		Map<String, String> map = new HashMap<>();
		try (InputStream in = Texts.class.getResourceAsStream("/assets/chaosaholic/lang/en_us.json")) {
			if (in == null) return map;
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
				for (Map.Entry<String, JsonElement> e : obj.entrySet()) map.put(e.getKey(), e.getValue().getAsString());
			}
		} catch (Exception e) {
			Chaosaholic.LOGGER.warn("Could not read the English lang file for fallbacks", e);
		}
		return map;
	}

	/** {@code Component.translatableWithFallback(key, <en_us value>, args)}. */
	public static MutableComponent tr(String key, Object... args) {
		String fallback = ENGLISH.get(key);
		return fallback == null ? Component.translatable(key, args) : Component.translatableWithFallback(key, fallback, args);
	}

	/** English text of a key (null if missing); for fallbacks and tests. */
	public static String english(String key) {
		return ENGLISH.get(key);
	}

	/** Event display name, in the category colour. */
	public static MutableComponent name(ChaosEvent event) {
		return tr(EventIds.nameKey(event.id())).withStyle(event.category().color());
	}

	public static MutableComponent description(ChaosEvent event) {
		return tr(EventIds.descKey(event.id()));
	}

	public static MutableComponent announce(ChaosEvent event) {
		return tr(EventIds.announceKey(event.id()));
	}

	public static MutableComponent category(ChaosEvent event) {
		return tr(event.category().langKey()).withStyle(event.category().color());
	}

	/** Green "ON" / red "OFF". */
	public static MutableComponent onOff(boolean on) {
		return tr(on ? "chaosaholic.command.value.on" : "chaosaholic.command.value.off")
				.withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED);
	}
}
