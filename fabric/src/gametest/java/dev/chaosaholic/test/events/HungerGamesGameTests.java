package dev.chaosaholic.test.events;

import static dev.chaosaholic.test.TestSupport.cleanup;
import static dev.chaosaholic.test.TestSupport.defaults;
import static dev.chaosaholic.test.TestSupport.event;
import static dev.chaosaholic.test.TestSupport.manager;
import static dev.chaosaholic.test.TestSupport.server;
import static dev.chaosaholic.test.TestSupport.start;
import static dev.chaosaholic.test.TestSupport.survivalPlayer;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.StopReason;
import dev.chaosaholic.event.impl.HungerGames;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * hunger_games: Hunger III for 30-45 s; the food level never stays below 1 while it runs (vanilla starvation needs 0,
 * and kills on Hard); refused on Peaceful. Mock players never tick, so the drain itself is vanilla's and not tested.
 */
public class HungerGamesGameTests {
	@GameTest
	public void startGivesHungerThree(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "hunger_games", p);
		MobEffectInstance hunger = p.getEffect(MobEffects.HUNGER);
		h.assertTrue(hunger != null && hunger.getAmplifier() == 2, "Hunger III");
		h.assertTrue(hunger.getDuration() >= ev.remainingTicks(), "lasts the whole event");
		h.assertTrue(ev.totalTicks() >= 30 * 20 && ev.totalTicks() <= 45 * 20, "30-45 s, got " + ev.totalTicks());
		manager(h).stop(ev, StopReason.FORCED);
		h.assertFalse(p.hasEffect(MobEffects.HUNGER), "removed on stop");
		cleanup(h, p);
		h.succeed();
	}

	@GameTest(maxTicks = 60)
	public void neverStarves(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		p.getFoodData().setFoodLevel(0); // already starving
		ActiveEvent ev = start(h, "hunger_games", p);
		h.assertValueEqual(p.getFoodData().getFoodLevel(), HungerGames.MIN_FOOD, "raised on start");
		p.getFoodData().setFoodLevel(0); // drained during the run
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(p.getFoodData().getFoodLevel() >= HungerGames.MIN_FOOD, "kept at 1"))
				.thenExecute(() -> {
					h.assertValueEqual(p.getFoodData().getFoodLevel(), HungerGames.MIN_FOOD, "only up to the floor");
					ev.setRemainingTicks(3);
				})
				.thenWaitUntil(() -> h.assertTrue(ev.isStopped(), "expired"))
				.thenExecute(() -> {
					h.assertFalse(p.hasEffect(MobEffects.HUNGER), "removed at the end");
					cleanup(h, p);
				})
				.thenSucceed();
	}

	@GameTest
	public void doesNotFeedAboveTheFloor(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		p.getFoodData().setFoodLevel(7);
		start(h, "hunger_games", p);
		h.assertValueEqual(p.getFoodData().getFoodLevel(), 7, "food untouched");
		cleanup(h, p);
		h.succeed();
	}

	/** Synchronous difficulty switch, restored before returning (world settings are shared by parallel tests). */
	@GameTest
	public void refusedOnPeaceful(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		MinecraftServer server = server(h);
		Difficulty before = h.getLevel().getDifficulty();
		boolean peaceful;
		boolean normal;
		try {
			server.setDifficulty(Difficulty.PEACEFUL, true);
			peaceful = event("hunger_games").canStart(manager(h).context(p));
			server.setDifficulty(Difficulty.NORMAL, true);
			normal = event("hunger_games").canStart(manager(h).context(p));
		} finally {
			server.setDifficulty(before, true);
		}
		h.assertFalse(peaceful, "refused on Peaceful");
		h.assertTrue(normal, "allowed on Normal");
		cleanup(h, p);
		h.succeed();
	}
}
