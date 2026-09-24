package dev.chaosaholic;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/**
 * No hardcoded player-facing text in the mod sources (src/main, src/client): every text is a lang key.
 * <ul>
 * <li>{@code Component.literal(...)} only with a string literal without letters (numbers, punctuation),
 *     {@code String.valueOf(...)} or a CONSTANT (fixed game names like the "Dinnerbone" Easter egg).</li>
 * <li>{@code translatableWithFallback} only in Texts (which takes the fallback from en_us.json).</li>
 * <li>Every literal key passed to {@code translatable(...)} / {@code Texts.tr(...)} exists in en_us.json.</li>
 * </ul>
 */
class SourceTextTest {
	private static final List<Path> ROOTS = List.of(Path.of("src/main/java"), Path.of("src/client/java"));
	private static final Pattern LITERAL = Pattern.compile("Component\\.literal\\(\\s*([^)]*)\\)?");
	private static final Pattern KEY = Pattern.compile("(?:translatable|Texts\\.tr|\\btr)\\(\\s*\"([a-zA-Z0-9_.]+)\"\\s*[,)]");

	private static List<Path> sources() throws IOException {
		List<Path> out = new ArrayList<>();
		for (Path root : ROOTS) {
			assertTrue(Files.isDirectory(root), "missing " + root.toAbsolutePath());
			try (Stream<Path> walk = Files.walk(root)) {
				walk.filter(p -> p.toString().endsWith(".java")).forEach(out::add);
			}
		}
		return out;
	}

	private static JsonObject english() throws IOException {
		try (InputStream in = SourceTextTest.class.getResourceAsStream("/assets/chaosaholic/lang/en_us.json");
				Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		}
	}

	static boolean allowedLiteral(String argument) {
		String arg = argument.strip();
		if (arg.startsWith("String.valueOf(")) return true;
		// a CONSTANT: fixed game names such as the "Dinnerbone" / "jeb_" Easter eggs (see ARCHITECTURE.md)
		if (arg.matches("([A-Z][A-Za-z0-9]*\\.)?[A-Z][A-Z0-9_]*")) return true;
		if (arg.startsWith("\"")) {
			int end = arg.indexOf('"', 1);
			String text = end > 0 ? arg.substring(1, end) : arg.substring(1);
			return text.codePoints().noneMatch(Character::isLetter);
		}
		return false;
	}

	@Test
	void literalHelperSanity() {
		assertTrue(allowedLiteral("\" · \""));
		assertTrue(allowedLiteral("\"42\""));
		assertTrue(allowedLiteral("String.valueOf(n)"));
		assertTrue(allowedLiteral("DINNERBONE"));
		assertTrue(allowedLiteral("UpsideDown.DINNERBONE"));
		assertTrue(!allowedLiteral("\"Hello\""));
		assertTrue(!allowedLiteral("\"Привет\""));
		assertTrue(!allowedLiteral("name"));
	}

	@Test
	void noHardcodedPlayerText() throws IOException {
		List<String> violations = new ArrayList<>();
		for (Path file : sources()) {
			List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
			boolean isTexts = file.getFileName().toString().equals("Texts.java");
			for (int i = 0; i < lines.size(); i++) {
				String line = lines.get(i);
				if (line.strip().startsWith("*") || line.strip().startsWith("//")) continue; // comments / javadoc
				Matcher m = LITERAL.matcher(line);
				while (m.find()) {
					if (!allowedLiteral(m.group(1))) violations.add(file + ":" + (i + 1) + ": " + line.strip());
				}
				if (!isTexts && line.contains("translatableWithFallback(")) {
					violations.add(file + ":" + (i + 1) + " (use Texts.tr): " + line.strip());
				}
			}
		}
		assertTrue(violations.isEmpty(), "hardcoded player-facing text:\n" + String.join("\n", violations));
	}

	@Test
	void everyLiteralKeyExists() throws IOException {
		JsonObject lang = english();
		List<String> missing = new ArrayList<>();
		for (Path file : sources()) {
			String src = Files.readString(file, StandardCharsets.UTF_8);
			Matcher m = KEY.matcher(src);
			while (m.find()) {
				String key = m.group(1);
				if (!key.contains(".")) continue;
				if (!lang.has(key)) missing.add(file.getFileName() + ": " + key);
			}
		}
		assertTrue(missing.isEmpty(), "keys missing from en_us.json:\n" + String.join("\n", missing));
	}
}
