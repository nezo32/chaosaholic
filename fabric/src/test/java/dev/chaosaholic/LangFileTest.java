package dev.chaosaholic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LangFileTest {
	private static JsonObject lang;
	private static JsonObject ru;
	/** Event id -> category, read from the event classes (JUnit cannot bootstrap Minecraft registries). */
	private static Map<String, String> events;
	private static List<String> warningEvents;

	static final Path IMPL = Path.of("src/main/java/dev/chaosaholic/event/impl");

	@BeforeAll
	static void load() throws IOException {
		lang = read("en_us");
		ru = read("ru_ru");
		events = new LinkedHashMap<>();
		warningEvents = new ArrayList<>();
		Pattern ctor = Pattern.compile("super\\(\"([a-z0-9_]+)\",\\s*Category\\.([A-Z]+)");
		try (Stream<Path> files = Files.list(IMPL)) {
			for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
				String src = Files.readString(file, StandardCharsets.UTF_8);
				Matcher m = ctor.matcher(src);
				assertTrue(m.find(), "no super(\"id\", Category.X ...) in " + file);
				events.put(m.group(1), m.group(2));
				if (src.contains("boolean hasWarning()")) warningEvents.add(m.group(1));
			}
		}
	}

	private static JsonObject read(String code) throws IOException {
		try (InputStream in = LangFileTest.class.getResourceAsStream("/assets/chaosaholic/lang/" + code + ".json")) {
			assertNotNull(in, code + ".json not on the test classpath");
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				return JsonParser.parseReader(reader).getAsJsonObject();
			}
		}
	}

	/** Format placeholders (%s, %1$s, %d, …) of a lang value, sorted; %% is a literal percent sign. */
	static List<String> placeholders(String value) {
		Matcher m = Pattern.compile("%(?:(\\d+)\\$)?([a-zA-Z%])").matcher(value);
		List<String> out = new ArrayList<>();
		int next = 1;
		while (m.find()) {
			if (m.group(2).equals("%")) continue;
			// unnumbered %s count as positional, so "%s %s" and "%2$s %1$s" compare equal
			out.add((m.group(1) != null ? m.group(1) : String.valueOf(next++)) + "$" + m.group(2));
		}
		out.sort(null);
		return out;
	}

	@Test
	void placeholderHelperSanity() {
		assertEquals(List.of("1$s", "2$s"), placeholders("%1$s — %2$s"));
		assertEquals(List.of("1$s", "2$s"), placeholders("%2$s a %1$s"));
		assertEquals(List.of("1$s"), placeholders("Sound: %s (100%%)"));
		assertEquals(List.of(), placeholders("none"));
	}

	@Test
	void russianHasExactlyTheEnglishKeys() {
		assertEquals(new TreeSet<>(lang.keySet()), new TreeSet<>(ru.keySet()), "ru_ru.json key set differs from en_us.json");
	}

	@Test
	void russianPlaceholdersMatchEnglish() {
		for (String key : lang.keySet()) {
			if (!ru.has(key)) continue; // reported by russianHasExactlyTheEnglishKeys
			assertEquals(placeholders(lang.get(key).getAsString()), placeholders(ru.get(key).getAsString()), "placeholders of " + key);
		}
	}

	@Test
	void noEmptyValues() {
		for (JsonObject file : new JsonObject[] {lang, ru}) {
			for (String key : file.keySet()) {
				assertFalse(file.get(key).getAsString().isBlank(), "blank " + key + (file == ru ? " (ru_ru)" : " (en_us)"));
			}
		}
	}

	@Test
	void thirtyEventsByCategory() {
		assertEquals(30, events.size(), "events: " + events.keySet());
		assertEquals(9, events.values().stream().filter("GOOD"::equals).count());
		assertEquals(10, events.values().stream().filter("BAD"::equals).count());
		assertEquals(11, events.values().stream().filter("WEIRD"::equals).count());
	}

	@Test
	void everyEventHasNameDescAnnounce() {
		for (String id : events.keySet()) {
			for (String suffix : new String[] {"", ".desc", ".announce"}) {
				String key = "chaosaholic.event." + id + suffix;
				assertTrue(lang.has(key), "missing " + key);
				assertTrue(ru.has(key), "ru_ru missing " + key);
			}
		}
	}

	@Test
	void warningKeysExactlyForWarningEvents() {
		assertEquals(new TreeSet<>(List.of("anvil_rain", "lightning_storm", "tnt_rain")), new TreeSet<>(warningEvents));
		for (String key : lang.keySet()) {
			if (!key.endsWith(".warning")) continue;
			String id = key.substring("chaosaholic.event.".length(), key.length() - ".warning".length());
			assertTrue(warningEvents.contains(id), key + " but " + id + " does not override hasWarning()");
		}
		for (String id : warningEvents) assertTrue(lang.has("chaosaholic.event." + id + ".warning"), "missing warning of " + id);
	}

	@Test
	void noStrayEventKeys() {
		for (String key : lang.keySet()) {
			if (!key.startsWith("chaosaholic.event.")) continue;
			String rest = key.substring("chaosaholic.event.".length());
			String id = rest.contains(".") ? rest.substring(0, rest.indexOf('.')) : rest;
			assertTrue(events.containsKey(id), "lang key " + key + " for unknown event " + id);
		}
	}

	@Test
	void russianEventNamesTranslated() {
		for (String id : events.keySet()) {
			String key = "chaosaholic.event." + id;
			assertFalse(lang.get(key).getAsString().equals(ru.get(key).getAsString()), "untranslated " + key);
		}
		assertEquals("Режим Chaosaholic", ru.get("chaosaholic.createWorld.toggle").getAsString());
	}

	@Test
	void requiredSystemKeysPresent() {
		for (String key : new String[] {
				"chaosaholic.category.good", "chaosaholic.category.bad", "chaosaholic.category.weird",
				"chaosaholic.bossbar.format", "chaosaholic.time.seconds", "chaosaholic.time.minutes",
				"chaosaholic.announce.title", "chaosaholic.announce.actionbar", "chaosaholic.warning.format",
				"chaosaholic.createWorld.toggle", "chaosaholic.createWorld.toggle.tooltip",
				"chaosaholic.command.on", "chaosaholic.command.off", "chaosaholic.command.status.on", "chaosaholic.command.status.off",
				"chaosaholic.settings.title", "chaosaholic.settings.notifySound", "chaosaholic.settings.notifySound.tooltip",
				"chaosaholic.settings.notifyMessage", "chaosaholic.settings.notifyMessage.tooltip",
				"chaosaholic.settings.screenEffects", "chaosaholic.settings.screenEffects.tooltip",
				"chaosaholic.command.notify.sound", "chaosaholic.command.notify.message", "chaosaholic.command.notify.effects"}) {
			assertTrue(lang.has(key), "missing " + key);
		}
		String bar = lang.get("chaosaholic.bossbar.format").getAsString();
		assertTrue(bar.contains("%1$s") && bar.contains("%2$s"), "boss bar placeholders: " + bar);
		assertTrue(lang.get("chaosaholic.createWorld.toggle.tooltip").getAsString().contains("/chaosaholic"), "tooltip names the command");
	}

	@Test
	void noGameruleKeys() {
		for (String key : lang.keySet()) assertFalse(key.startsWith("gamerule."), "stale game rule key " + key);
	}
}
