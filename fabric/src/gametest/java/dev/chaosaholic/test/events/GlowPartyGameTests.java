package dev.chaosaholic.test.events;

import static dev.chaosaholic.test.TestSupport.cleanup;
import static dev.chaosaholic.test.TestSupport.defaults;
import static dev.chaosaholic.test.TestSupport.leave;
import static dev.chaosaholic.test.TestSupport.manager;
import static dev.chaosaholic.test.TestSupport.mob;
import static dev.chaosaholic.test.TestSupport.player;
import static dev.chaosaholic.test.TestSupport.start;
import static dev.chaosaholic.test.TestSupport.survivalPlayer;

import java.util.ArrayList;
import java.util.List;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.StopReason;
import dev.chaosaholic.event.impl.GlowParty;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * glow_party: the player and living entities within 16 blocks glow (capped at 48 per instance), newcomers light up on
 * the next scan, Creative players and foreign glows are left alone; everything is removed at the end.
 */
public class GlowPartyGameTests {
	private static boolean glows(LivingEntity e) {
		return e.hasEffect(MobEffects.GLOWING);
	}

	@GameTest
	public void playerAndNearbyMobsGlow(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h, new Vec3(1.5, 2, 1.5));
		Pig near = mob(h, EntityTypes.PIG, new Vec3(4.5, 2, 1.5));
		Pig far = mob(h, EntityTypes.PIG, new Vec3(1.5, 2, 1.5 + GlowParty.RADIUS + 3));
		ActiveEvent ev = start(h, "glow_party", p);
		h.assertTrue(glows(p), "player glows");
		h.assertTrue(glows(near), "near pig glows");
		h.assertFalse(glows(far), "far pig untouched");
		h.assertTrue(ev.totalTicks() >= 30 * 20 && ev.totalTicks() <= 60 * 20, "30-60 s, got " + ev.totalTicks());
		manager(h).stop(ev, StopReason.FORCED);
		h.assertFalse(glows(p), "player reverted");
		h.assertFalse(glows(near), "pig reverted");
		near.discard();
		far.discard();
		cleanup(h, p);
		h.succeed();
	}

	@GameTest
	public void skipsCreativePlayersAndForeignGlow(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h, new Vec3(1.5, 2, 1.5));
		ServerPlayer creative = player(h, GameType.CREATIVE);
		ServerPlayer friend = survivalPlayer(h, new Vec3(3.5, 2, 3.5));
		Pig marked = mob(h, EntityTypes.PIG, new Vec3(4.5, 2, 1.5));
		marked.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 600, 0)); // e.g. a spectral arrow
		ActiveEvent ev = start(h, "glow_party", p);
		h.assertFalse(glows(creative), "Creative player never affected");
		h.assertTrue(glows(friend), "nearby survival player glows");
		h.assertFalse(ev.effects().tracks(marked), "already glowing: not ours");
		manager(h).stop(ev, StopReason.FORCED);
		h.assertFalse(glows(friend), "friend reverted");
		MobEffectInstance kept = marked.getEffect(MobEffects.GLOWING);
		h.assertTrue(kept != null && kept.getDuration() > 20 * 500, "foreign glow kept");
		marked.discard();
		cleanup(h, p, creative, friend);
		h.succeed();
	}

	@GameTest(maxTicks = 80)
	public void newcomersGlowAndIneligibleOthersRevert(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h, new Vec3(1.5, 2, 1.5));
		ServerPlayer friend = survivalPlayer(h, new Vec3(3.5, 2, 3.5));
		ActiveEvent ev = start(h, "glow_party", p);
		h.assertTrue(glows(friend), "friend glows");
		friend.setGameMode(GameType.CREATIVE);
		Pig late = mob(h, EntityTypes.PIG, new Vec3(4.5, 2, 1.5));
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(glows(late), "newcomer lit up by the next scan"))
				.thenExecute(() -> {
					h.assertFalse(glows(friend), "friend reverted after switching to Creative");
					h.assertFalse(ev.isStopped(), "event still running");
					ev.setRemainingTicks(3);
				})
				.thenWaitUntil(() -> h.assertTrue(ev.isStopped(), "expired"))
				.thenExecute(() -> {
					h.assertFalse(glows(p), "player reverted");
					h.assertFalse(glows(late), "pig reverted");
					late.discard();
					cleanup(h, p, friend);
				})
				.thenSucceed();
	}

	@GameTest
	public void cappedPerInstance(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h, new Vec3(1.5, 2, 1.5));
		List<Pig> pigs = new ArrayList<>();
		for (int i = 0; i < GlowParty.MAX_GLOWING + 6; i++) {
			pigs.add(mob(h, EntityTypes.PIG, new Vec3(2.5 + (i % 3), 2, 1.5 + (i / 3) % 3)));
		}
		ActiveEvent ev = start(h, "glow_party", p);
		long glowing = pigs.stream().filter(GlowPartyGameTests::glows).count();
		h.assertTrue(glowing <= GlowParty.MAX_GLOWING, "cap respected, got " + glowing);
		h.assertTrue(glowing >= GlowParty.MAX_GLOWING - 4, "nearest pigs first, got " + glowing);
		manager(h).stop(ev, StopReason.FORCED);
		h.assertTrue(pigs.stream().noneMatch(GlowPartyGameTests::glows), "all reverted");
		pigs.forEach(Pig::discard);
		cleanup(h, p);
		h.succeed();
	}

	@GameTest(maxTicks = 40)
	public void logoutAndUnload(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h, new Vec3(1.5, 2, 1.5));
		Pig near = mob(h, EntityTypes.PIG, new Vec3(4.5, 2, 1.5));
		Pig unloaded = mob(h, EntityTypes.PIG, new Vec3(1.5, 2, 4.5));
		ActiveEvent ev = start(h, "glow_party", p);
		ServerEntityEvents.ENTITY_UNLOAD.invoker().onUnload(unloaded, h.getLevel());
		h.assertFalse(ev.effects().tracks(unloaded), "forgotten on unload");
		MobEffectInstance left = unloaded.getEffect(MobEffects.GLOWING);
		h.assertTrue(left != null && left.getDuration() <= ev.remainingTicks() + 20, "runs out with the event");
		leave(h, p);
		h.assertFalse(glows(p), "player reverted on logout");
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(ev.isStopped(), "no players left: ended"))
				.thenExecute(() -> {
					h.assertFalse(glows(near), "pig reverted at the end");
					near.discard();
					unloaded.discard();
				})
				.thenSucceed();
	}
}
