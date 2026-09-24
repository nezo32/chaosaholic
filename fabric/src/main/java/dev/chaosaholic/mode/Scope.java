package dev.chaosaholic.mode;

import java.util.Locale;

import com.mojang.serialization.Codec;

/** Who an event affects: only the player who levelled up, or every eligible player in that dimension. */
public enum Scope {
	PLAYER,
	WORLD;

	public static final Codec<Scope> CODEC = Codec.STRING.xmap(Scope::parse, Scope::id);

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Unknown / null -> PLAYER (the default). */
	public static Scope parse(String id) {
		return "world".equals(id) ? WORLD : PLAYER;
	}
}
