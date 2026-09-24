package dev.chaosaholic.test.events;

import static dev.chaosaholic.test.TestSupport.cleanup;
import static dev.chaosaholic.test.TestSupport.defaults;
import static dev.chaosaholic.test.TestSupport.event;
import static dev.chaosaholic.test.TestSupport.manager;
import static dev.chaosaholic.test.TestSupport.player;
import static dev.chaosaholic.test.TestSupport.start;
import static dev.chaosaholic.test.TestSupport.survivalPlayer;

import java.util.List;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.StopReason;
import dev.chaosaholic.event.impl.Butterfingers;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * butterfingers: the drop itself (tossed, not destroyed, short pickup delay), its conditions and the per-run cap.
 * The random roll is bypassed through {@link Butterfingers#attempt} with {@code lucky = true}.
 */
public class ButterfingersGameTests {
	private static List<ItemEntity> dropped(GameTestHelper h, ServerPlayer p) {
		return h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(4.0),
				e -> e.getItem().is(Items.NAME_TAG));
	}

	private static void hold(ServerPlayer p, int count) {
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NAME_TAG, count));
	}

	@GameTest
	public void dropsTheWholeStackAsAnItem(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		p.setOnGround(true);
		hold(p, 3);
		ActiveEvent ev = start(h, "butterfingers", p);
		h.assertTrue(p.getMainHandItem().getCount() == 3, "no drop right at the start");
		h.assertTrue(Butterfingers.attempt(ev, p, true), "dropped");
		h.assertTrue(p.getMainHandItem().isEmpty(), "hand empty");
		List<ItemEntity> items = dropped(h, p);
		h.assertValueEqual(items.size(), 1, "one item entity");
		ItemEntity item = items.getFirst();
		h.assertValueEqual(item.getItem().getCount(), 3, "whole stack, nothing destroyed");
		h.assertTrue(item.hasPickUpDelay(), "short pickup delay");
		h.assertTrue(item.getOwner() == p, "thrown by the player");
		item.discard();
		manager(h).stop(ev, StopReason.FORCED);
		cleanup(h, p);
		h.succeed();
	}

	@GameTest
	public void conditionsAndCap(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ServerPlayer other = survivalPlayer(h);
		ActiveEvent ev = start(h, "butterfingers", p);
		hold(p, 1);
		h.assertFalse(Butterfingers.attempt(ev, p, false), "unlucky roll: kept");
		p.setOnGround(false);
		h.assertFalse(Butterfingers.attempt(ev, p, true), "airborne: kept");
		p.setOnGround(true);
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		h.assertFalse(Butterfingers.attempt(ev, p, true), "empty hand: nothing");
		other.setOnGround(true);
		hold(other, 1);
		h.assertFalse(Butterfingers.attempt(ev, other, true), "not affected: kept");
		int drops = 0;
		for (int i = 0; i < Butterfingers.MAX_DROPS + 2; i++) {
			hold(p, 1);
			if (Butterfingers.attempt(ev, p, true)) drops++;
		}
		h.assertValueEqual(drops, Butterfingers.MAX_DROPS, "capped drops per run");
		h.assertValueEqual(Butterfingers.dropsLeft(ev, p), 0, "none left");
		h.assertFalse(p.getMainHandItem().isEmpty(), "kept after the cap");
		dropped(h, p).forEach(ItemEntity::discard);
		manager(h).stop(ev, StopReason.FORCED);
		cleanup(h, p, other);
		h.succeed();
	}

	@GameTest
	public void creativeIsNeverAffected(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = player(h, GameType.CREATIVE);
		h.assertTrue(manager(h).trigger(event("butterfingers"), p).isEmpty(), "refused for Creative");
		cleanup(h, p);
		h.succeed();
	}

	@GameTest(maxTicks = 40)
	public void endsCleanly(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "butterfingers", p);
		h.assertTrue(ev.hasBossBar(), "timed");
		ev.setRemainingTicks(3);
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(ev.isStopped(), "ended"))
				.thenExecute(() -> {
					h.assertTrue(ev.bossBarPlayers().isEmpty(), "boss bar gone");
					h.assertTrue(manager(h).activeFor(p).isEmpty(), "nothing active");
					cleanup(h, p);
				})
				.thenSucceed();
	}
}
