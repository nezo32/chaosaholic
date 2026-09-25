package dev.chaosaholic.event;

import java.util.List;

import dev.chaosaholic.mode.Scope;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;

/**
 * What an event sees when it is about to start ({@link ChaosEvent#canStart}) and, frozen at that moment, for its
 * whole run ({@link ActiveEvent#context()}).
 *
 * @param trigger the player whose level-up (or /chaosaholic trigger) started it
 * @param players the eligible players it would affect: just {@code trigger} for scope player, every eligible player
 *                in {@code level} for scope world. Never empty.
 */
public record EventContext(MinecraftServer server, ServerLevel level, ServerPlayer trigger, List<ServerPlayer> players,
		RandomSource random, Scope scope) {
	public Difficulty difficulty() {
		return level.getDifficulty();
	}

	public boolean isPeaceful() {
		return difficulty() == Difficulty.PEACEFUL;
	}

	/**
	 * Hardcore world. Bad events must stay survivable here too: no instant unavoidable death, a warning at least
	 * {@link dev.chaosaholic.core.ChaosLimits#WARNING_TICKS} before any hazard (see helper.Warning).
	 */
	public boolean isHardcore() {
		return server.isHardcore();
	}

	/** Difficulty as 0 (peaceful) .. 3 (hard); Hardcore counts as hard. */
	public int difficultyLevel() {
		return difficulty().getId();
	}

	public boolean isWorldScope() {
		return scope == Scope.WORLD;
	}
}
