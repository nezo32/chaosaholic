package dev.chaosaholic.test.events;

import static dev.chaosaholic.test.TestSupport.cleanup;
import static dev.chaosaholic.test.TestSupport.defaults;
import static dev.chaosaholic.test.TestSupport.event;
import static dev.chaosaholic.test.TestSupport.leave;
import static dev.chaosaholic.test.TestSupport.manager;
import static dev.chaosaholic.test.TestSupport.mob;
import static dev.chaosaholic.test.TestSupport.start;
import static dev.chaosaholic.test.TestSupport.survivalPlayer;

import java.util.ArrayList;
import java.util.List;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.StopReason;
import dev.chaosaholic.event.helper.Area;
import dev.chaosaholic.event.helper.Marks;
import dev.chaosaholic.event.helper.TrackedNames;
import dev.chaosaholic.event.impl.UpsideDown;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * upside_down: nearby mobs are renamed Dinnerbone (hidden unless their name was visible), restored with their
 * original name and visibility at the end / on logout; bosses, owned and already flipped mobs are left alone; the
 * per-instance cap holds; a name given during the event is kept. All checks that could see a neighbour test's mobs
 * are synchronous (start and stop in the same call), so neighbours never observe a renamed mob.
 */
public class UpsideDownGameTests {
	private static boolean flipped(Entity e) {
		return e.getCustomName() != null && UpsideDown.DINNERBONE.equals(e.getCustomName().getString());
	}

	@GameTest
	public void flipsNearbyMobsAndRestoresNames(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h, new Vec3(1.5, 2, 1.5));
		Zombie plain = mob(h, EntityTypes.ZOMBIE, new Vec3(3.5, 2, 1.5));
		Pig named = mob(h, EntityTypes.PIG, new Vec3(4.5, 2, 4.5));
		named.setCustomName(Component.literal("Bob"));
		named.setCustomNameVisible(true);
		Zombie far = mob(h, EntityTypes.ZOMBIE, new Vec3(1.5, 2, 1.5 + UpsideDown.RADIUS + 3));
		ActiveEvent ev = start(h, "upside_down", p);
		h.assertTrue(flipped(plain), "plain zombie flipped");
		h.assertFalse(plain.isCustomNameVisible(), "flip name hidden");
		h.assertTrue(plain.hasAttached(Marks.NAME), "original kept in a persistent mark");
		h.assertTrue(flipped(named), "named pig flipped");
		h.assertTrue(named.isCustomNameVisible(), "a visible name stays visible");
		h.assertFalse(flipped(far), "far zombie untouched");
		h.assertFalse(p.getCustomName() != null, "player never renamed");
		manager(h).stop(ev, StopReason.FORCED);
		h.assertTrue(plain.getCustomName() == null, "plain zombie has no name again");
		h.assertFalse(plain.isCustomNameVisible(), "plain visibility restored");
		h.assertFalse(plain.hasAttached(Marks.NAME), "mark removed");
		h.assertValueEqual(named.getCustomName().getString(), "Bob", "named pig got its name back");
		h.assertTrue(named.isCustomNameVisible(), "named visibility restored");
		plain.discard();
		named.discard();
		far.discard();
		cleanup(h, p);
		h.succeed();
	}

	@GameTest
	public void leavesBossesOwnedAndFlippedMobsAlone(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h, new Vec3(1.5, 2, 1.5));
		Warden warden = mob(h, EntityTypes.WARDEN, new Vec3(4.5, 2, 4.5));
		Zombie grumm = mob(h, EntityTypes.ZOMBIE, new Vec3(3.5, 2, 1.5));
		grumm.setCustomName(Component.literal("Grumm"));
		Zombie owned = mob(h, EntityTypes.ZOMBIE, new Vec3(1.5, 2, 3.5));
		ActiveEvent other = start(h, "tiny_world", p); // any running instance to own the zombie
		h.assertTrue(other.entities().adopt(owned), "adopted");
		ActiveEvent ev = start(h, "upside_down", p);
		h.assertFalse(flipped(warden), "boss not renamed");
		h.assertValueEqual(grumm.getCustomName().getString(), "Grumm", "already flipped mob untouched");
		h.assertFalse(flipped(owned), "entity owned by another event untouched");
		h.assertFalse(UpsideDown.canFlip(p), "players never");
		manager(h).stop(ev, StopReason.FORCED);
		h.assertValueEqual(grumm.getCustomName().getString(), "Grumm", "Grumm keeps its name");
		manager(h).stop(other, StopReason.FORCED);
		h.assertTrue(owned.isRemoved(), "owned zombie removed by its owner");
		warden.discard();
		grumm.discard();
		cleanup(h, p);
		h.succeed();
	}

	@GameTest
	public void rescanFlipsNewcomersAndNameTagIsKept(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h, new Vec3(1.5, 2, 1.5));
		Zombie first = mob(h, EntityTypes.ZOMBIE, new Vec3(3.5, 2, 1.5));
		ActiveEvent ev = start(h, "upside_down", p);
		h.assertTrue(flipped(first), "first flipped");
		Zombie newcomer = mob(h, EntityTypes.ZOMBIE, new Vec3(4.5, 2, 4.5));
		h.assertTrue(UpsideDown.flipAround(ev, p) >= 1, "rescan flips the newcomer");
		h.assertTrue(flipped(newcomer), "newcomer flipped");
		h.assertValueEqual(UpsideDown.flipAround(ev, p), 0, "nothing flipped twice");
		first.setCustomName(Component.literal("Rex")); // a name tag used during the event
		manager(h).stop(ev, StopReason.FORCED);
		h.assertValueEqual(first.getCustomName().getString(), "Rex", "name tag kept");
		h.assertFalse(first.hasAttached(Marks.NAME), "no stale mark on the renamed mob");
		h.assertTrue(newcomer.getCustomName() == null, "newcomer restored");
		first.discard();
		newcomer.discard();
		cleanup(h, p);
		h.succeed();
	}

	@GameTest
	public void capsFlippedMobsPerInstance(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h, new Vec3(0.5, 2, 0.5));
		List<Zombie> mine = new ArrayList<>();
		for (int i = 0; i < UpsideDown.MAX_FLIPPED + 4; i++) {
			mine.add(mob(h, EntityTypes.ZOMBIE, new Vec3(1.5 + i % 6, 2 + (i / 36) * 3, 1.5 + (i / 6) % 6)));
		}
		ActiveEvent ev = start(h, "upside_down", p);
		List<LivingEntity> tracked = Area.entities(h.getLevel(), p.position(), UpsideDown.RADIUS, LivingEntity.class, ev.names()::tracks);
		h.assertValueEqual(tracked.size(), UpsideDown.MAX_FLIPPED, "flipped mobs capped");
		h.assertValueEqual(UpsideDown.flipAround(ev, p), 0, "cap also stops rescans");
		manager(h).stop(ev, StopReason.FORCED);
		for (Zombie z : mine) {
			h.assertTrue(z.getCustomName() == null && !z.hasAttached(Marks.NAME), "all restored");
			z.discard();
		}
		cleanup(h, p);
		h.succeed();
	}

	@GameTest(maxTicks = 40)
	public void logoutEndsAndRestores(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h, new Vec3(1.5, 2, 1.5));
		Zombie z = mob(h, EntityTypes.ZOMBIE, new Vec3(2.5, 2, 1.5));
		ActiveEvent ev = start(h, "upside_down", p);
		h.assertTrue(flipped(z), "flipped");
		leave(h, p);
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(ev.isStopped(), "no players left: ended"))
				.thenExecute(() -> {
					h.assertTrue(z.getCustomName() == null && !z.hasAttached(Marks.NAME), "restored after logout");
					z.discard();
				})
				.thenSucceed();
	}

	/** Crash safety: a flipped mob loaded while its instance is gone gets its original name back. */
	@GameTest(maxTicks = 20)
	public void orphanNameRestoredOnLoad(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h, new Vec3(1.5, 2, 1.5));
		Zombie z = mob(h, EntityTypes.ZOMBIE, new Vec3(2.5, 2, 1.5));
		ActiveEvent ev = start(h, "upside_down", p);
		Marks.NameMark mark = z.getAttached(Marks.NAME);
		h.assertTrue(mark != null, "marked");
		ev.names().forget(z); // as if the instance vanished in a crash with the mob still flipped
		manager(h).stop(ev, StopReason.FORCED);
		h.assertTrue(flipped(z), "still flipped (orphan)");
		TrackedNames.onEntityLoad(z, h.getLevel());
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(z.getCustomName() == null, "restored on load"))
				.thenExecute(() -> {
					h.assertFalse(z.hasAttached(Marks.NAME), "mark removed");
					z.discard();
					cleanup(h, p);
				})
				.thenSucceed();
	}

	@GameTest
	public void creativePlayersNeverStart(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h, new Vec3(1.5, 2, 1.5));
		p.setGameMode(GameType.CREATIVE);
		h.assertTrue(manager(h).trigger(event("upside_down"), p).isEmpty(), "creative refused");
		cleanup(h, p);
		h.succeed();
	}
}
