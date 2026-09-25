package dev.chaosaholic.test.events;

import static dev.chaosaholic.test.TestSupport.cleanup;
import static dev.chaosaholic.test.TestSupport.defaults;
import static dev.chaosaholic.test.TestSupport.leave;
import static dev.chaosaholic.test.TestSupport.manager;
import static dev.chaosaholic.test.TestSupport.start;
import static dev.chaosaholic.test.TestSupport.survivalPlayer;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.StopReason;
import dev.chaosaholic.event.impl.DoubleXp;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.GameType;

/** double_xp: orbs touched by affected players give twice their points (ExperienceOrbMixin). */
public class DoubleXpGameTests {
	/** Points {@code p} gains from touching a fresh orb worth {@code value}. */
	private static int pickUp(GameTestHelper h, ServerPlayer p, int value) {
		ExperienceOrb orb = new ExperienceOrb(h.getLevel(), p.getX(), p.getY(), p.getZ(), value);
		h.getLevel().addFreshEntity(orb);
		int before = p.totalExperience;
		p.takeXpDelay = 0;
		orb.playerTouch(p);
		orb.discard();
		return p.totalExperience - before;
	}

	@GameTest
	public void doublesOrbsOfAffectedPlayersOnly(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ServerPlayer other = survivalPlayer(h);
		h.assertValueEqual(pickUp(h, p, 3), 3, "normal before");
		ActiveEvent ev = start(h, DoubleXp.ID, p);
		h.assertTrue(ev.hasBossBar(), "timed");
		h.assertValueEqual(pickUp(h, p, 3), 6, "doubled");
		h.assertValueEqual(pickUp(h, other, 3), 3, "other player normal");
		manager(h).stop(ev, StopReason.FORCED);
		h.assertValueEqual(pickUp(h, p, 3), 3, "normal after the end");
		cleanup(h, p, other);
		h.succeed();
	}

	@GameTest
	public void doubledPointsCanLevelUp(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		start(h, DoubleXp.ID, p);
		h.assertValueEqual(pickUp(h, p, 7), 14, "doubled");
		h.assertTrue(p.experienceLevel >= 1, "levelled up from doubled points: " + p.experienceLevel);
		h.assertValueEqual(DoubleXp.points(p, Integer.MAX_VALUE), Integer.MAX_VALUE, "no overflow");
		cleanup(h, p);
		h.succeed();
	}

	@GameTest(maxTicks = 40)
	public void creativeAndLogoutStopDoubling(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ServerPlayer q = survivalPlayer(h);
		ActiveEvent ev = start(h, DoubleXp.ID, p);
		ActiveEvent ev2 = start(h, DoubleXp.ID, q);
		p.setGameMode(GameType.CREATIVE);
		h.assertValueEqual(DoubleXp.points(p, 5), 5, "creative: never doubled");
		leave(h, q);
		h.assertValueEqual(DoubleXp.points(q, 5), 5, "logged out: never doubled");
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(ev.isStopped() && ev2.isStopped(), "both ended"))
				.thenExecute(() -> cleanup(h, p))
				.thenSucceed();
	}
}
