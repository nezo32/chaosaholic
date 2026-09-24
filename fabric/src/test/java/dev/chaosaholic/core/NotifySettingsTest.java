package dev.chaosaholic.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NotifySettingsTest {
	@TempDir
	Path dir;

	@Test
	void missingFileIsDefaultAndNotCreated() {
		Path file = dir.resolve("chaosaholic.json");
		assertEquals(NotifySettings.DEFAULT, NotifySettings.load(file));
		assertFalse(Files.exists(file));
		assertEquals(new NotifySettings(true, true, true), NotifySettings.DEFAULT);
	}

	@Test
	void saveLoadRoundTripsAllCombinations() throws IOException {
		Path file = dir.resolve("chaosaholic.json");
		for (boolean sound : new boolean[] {true, false}) {
			for (boolean message : new boolean[] {true, false}) {
				for (boolean effects : new boolean[] {true, false}) {
					NotifySettings s = new NotifySettings(sound, message, effects);
					s.save(file);
					assertEquals(s, NotifySettings.load(file), s.toString());
				}
			}
		}
		new NotifySettings(false, true, false).save(file);
		String text = Files.readString(file, StandardCharsets.UTF_8);
		assertTrue(text.contains("\"notifySound\": false"), text);
		assertTrue(text.contains("\"notifyMessage\": true"), text);
		assertTrue(text.contains("\"screenEffects\": false"), text);
	}

	@Test
	void toJsonFormat() {
		assertEquals("{\n  \"notifySound\": true,\n  \"notifyMessage\": false,\n  \"screenEffects\": true\n}\n",
				new NotifySettings(true, false, true).toJson());
	}

	@Test
	void saveCreatesParentsOverwritesAndLeavesNoTmp() throws IOException {
		Path file = dir.resolve("a/b/config/chaosaholic.json");
		new NotifySettings(false, false, false).save(file);
		assertEquals(new NotifySettings(false, false, false), NotifySettings.load(file));
		new NotifySettings(true, false, true).save(file);
		assertEquals(new NotifySettings(true, false, true), NotifySettings.load(file));
		try (Stream<Path> files = Files.list(file.getParent())) {
			List<String> names = files.map(p -> p.getFileName().toString()).toList();
			assertEquals(List.of("chaosaholic.json"), names);
		}
	}

	@Test
	void corruptInputIsDefaultAndNotRewritten() throws IOException {
		Path file = dir.resolve("chaosaholic.json");
		List<byte[]> inputs = List.of(
				"not json".getBytes(StandardCharsets.UTF_8),
				new byte[0],
				"[]".getBytes(StandardCharsets.UTF_8),
				"null".getBytes(StandardCharsets.UTF_8),
				"{\"notifySound\":".getBytes(StandardCharsets.UTF_8),
				new byte[] {(byte) 0xFF, (byte) 0xFE, 0x00, (byte) 0xC3, 0x28, (byte) 0x80, 0x7B, 0x01, (byte) 0xFA});
		for (byte[] bytes : inputs) {
			Files.write(file, bytes);
			assertEquals(NotifySettings.DEFAULT, NotifySettings.load(file), "input " + new String(bytes, StandardCharsets.ISO_8859_1));
			assertArrayEquals(bytes, Files.readAllBytes(file), "load must not rewrite the file");
		}
		assertEquals(NotifySettings.DEFAULT, NotifySettings.parse(null));
		assertEquals(NotifySettings.DEFAULT, NotifySettings.parse("\"x\""));
		assertEquals(NotifySettings.DEFAULT, NotifySettings.parse("true"));
	}

	@Test
	void directoryInsteadOfFileIsDefault() throws IOException {
		Path file = dir.resolve("chaosaholic.json");
		Files.createDirectories(file);
		assertEquals(NotifySettings.DEFAULT, NotifySettings.load(file));
	}

	@Test
	void partialAndWrongTypes() {
		assertEquals(new NotifySettings(false, true, true), NotifySettings.parse("{\"notifySound\":false}"));
		assertEquals(new NotifySettings(true, false, true), NotifySettings.parse("{\"notifyMessage\":false}"));
		assertEquals(new NotifySettings(true, true, false), NotifySettings.parse("{\"screenEffects\":false}"));
		assertEquals(NotifySettings.DEFAULT, NotifySettings.parse("{\"notifySound\":\"no\",\"notifyMessage\":0,\"screenEffects\":\"false\"}"));
		assertEquals(NotifySettings.DEFAULT, NotifySettings.parse("{\"notifySound\":\"false\",\"notifyMessage\":null}"));
		assertEquals(NotifySettings.DEFAULT, NotifySettings.parse("{\"notifySound\":{},\"notifyMessage\":[false]}"));
		assertEquals(new NotifySettings(false, false, false), NotifySettings.parse(
				"{\"notifySound\":false,\"notifyMessage\":false,\"screenEffects\":false,\"extra\":1,\"other\":{\"a\":false}}"));
		assertEquals(NotifySettings.DEFAULT, NotifySettings.parse("{}"));
		// an Enchantaholic-style file without the effects key keeps effects on
		assertEquals(new NotifySettings(false, false, true), NotifySettings.parse("{\"notifySound\":false,\"notifyMessage\":false}"));
	}

	@Test
	void withersAreImmutableAndJsonRoundTrips() {
		NotifySettings base = NotifySettings.DEFAULT;
		NotifySettings noSound = base.withSound(false);
		NotifySettings noMessage = base.withMessage(false);
		NotifySettings noEffects = base.withEffects(false);
		assertEquals(new NotifySettings(true, true, true), base);
		assertEquals(new NotifySettings(false, true, true), noSound);
		assertEquals(new NotifySettings(true, false, true), noMessage);
		assertEquals(new NotifySettings(true, true, false), noEffects);
		assertEquals(new NotifySettings(false, false, false), noSound.withMessage(false).withEffects(false));
		assertNotSame(base, base.withSound(true));
		for (NotifySettings s : List.of(base, noSound, noMessage, noEffects, new NotifySettings(false, false, false))) {
			assertEquals(s, NotifySettings.parse(s.toJson()));
		}
	}
}
