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

import dev.chaosaholic.core.ChaosLimits;
import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.StopReason;
import dev.chaosaholic.event.helper.OwnedEntities;
import dev.chaosaholic.event.impl.SheepDisco;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * sheep_disco: 3-5 owned jeb_ sheep near the player, damage-proof, not shearable, not breedable, never saved;
 * removed at the end (expiry, forced stop, logout) without dropping anything. The structure is air: the tests lay a
 * stone floor at relative y = 1 and stand the player in its middle, so the sheep spawn inside the test area.
 */
public class SheepDiscoGameTests {
	private static void floor(GameTestHelper h) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
		}
	}

	private static ServerPlayer player(GameTestHelper h) {
		floor(h);
		return survivalPlayer(h, new Vec3(4.0, 2, 4.0));
	}

	private static List<Sheep> sheep(ActiveEvent ev) {
		List<Sheep> out = new ArrayList<>();
		for (Entity e : ev.entities().list()) if (e instanceof Sheep s) out.add(s);
		return out;
	}

	private static void assertNoFarmDrops(GameTestHelper h) {
		Vec3 origin = Vec3.atLowerCornerOf(h.absolutePos(BlockPos.ZERO));
		AABB area = new AABB(origin, origin.add(8, 8, 8)).inflate(2);
		List<ItemEntity> items = h.getLevel().getEntitiesOfClass(ItemEntity.class, area,
				i -> i.getItem().is(ItemTags.WOOL) || i.getItem().is(Items.MUTTON));
		h.assertTrue(items.isEmpty(), "no wool or mutton dropped: " + items);
	}

	@GameTest
	public void spawnsRainbowSheepAndRemovesThemAtTheEnd(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = player(h);
		ActiveEvent ev = start(h, "sheep_disco", p);
		List<Sheep> sheep = sheep(ev);
		h.assertTrue(sheep.size() >= 1 && sheep.size() <= SheepDisco.MAX_SHEEP, "1-5 sheep: " + sheep.size());
		for (Sheep s : sheep) {
			h.assertValueEqual(s.getCustomName().getString(), SheepDisco.JEB, "jeb_");
			h.assertFalse(s.isCustomNameVisible(), "name hidden");
			h.assertTrue(s.getAge() > 0 && !s.isBaby(), "adult on a breeding cooldown");
			h.assertTrue(SheepDisco.isDiscoSheep(s), "disco sheep");
			h.assertTrue(OwnedEntities.isUnsaved(s), "never saved");
			h.assertFalse(s.readyForShearing(), "not shearable");
			double d = s.position().subtract(p.position()).horizontalDistance();
			h.assertTrue(d <= SheepDisco.MAX_DISTANCE + 1.0, "near the player: " + d);
		}
		manager(h).stop(ev, StopReason.FORCED);
		for (Sheep s : sheep) h.assertTrue(s.isRemoved(), "sheep removed");
		assertNoFarmDrops(h);
		cleanup(h, p);
		h.succeed();
	}

	@GameTest(maxTicks = 100)
	public void partiesUntilExpiryThenCleansUp(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = player(h);
		ActiveEvent ev = start(h, "sheep_disco", p);
		List<Sheep> sheep = sheep(ev);
		h.assertFalse(sheep.isEmpty(), "sheep");
		ev.setRemainingTicks(30);
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(ev.isStopped(), "ended"))
				.thenExecute(() -> {
					for (Sheep s : sheep) h.assertTrue(s.isRemoved(), "sheep removed at expiry");
					assertNoFarmDrops(h);
					cleanup(h, p);
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 40)
	public void logoutEndsTheParty(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = player(h);
		ActiveEvent ev = start(h, "sheep_disco", p);
		List<Sheep> sheep = sheep(ev);
		leave(h, p);
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(ev.isStopped(), "no players left: ended"))
				.thenExecute(() -> {
					for (Sheep s : sheep) h.assertTrue(s.isRemoved(), "sheep removed after logout");
				})
				.thenSucceed();
	}

	@GameTest
	public void noWoolMuttonOrLambs(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = player(h);
		ActiveEvent ev = start(h, "sheep_disco", p);
		Sheep s = sheep(ev).getFirst();
		// shears (the player path; dispensers use the same readyForShearing check)
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
		s.mobInteract(p, InteractionHand.MAIN_HAND);
		h.assertFalse(s.isSheared(), "not sheared");
		// killing
		h.assertFalse(s.hurtServer(h.getLevel(), h.getLevel().damageSources().playerAttack(p), 1000.0F), "no damage");
		h.assertTrue(s.isAlive(), "still alive");
		// breeding
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT, 4));
		s.mobInteract(p, InteractionHand.MAIN_HAND);
		h.assertFalse(s.isInLove(), "cannot breed");
		// a normal sheep is still shearable: the mixin only targets disco sheep
		Sheep normal = mob(h, EntityTypes.SHEEP, new Vec3(1.5, 2, 1.5));
		h.assertTrue(normal.readyForShearing(), "normal sheep shearable");
		normal.discard();
		manager(h).stop(ev, StopReason.FORCED);
		assertNoFarmDrops(h);
		cleanup(h, p);
		h.succeed();
	}

	@GameTest
	public void cappedByOwnedEntityLimit(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = player(h);
		ActiveEvent ev = start(h, "sheep_disco", p);
		for (int i = 0; i < 12; i++) SheepDisco.spawnSheep(ev, p);
		h.assertTrue(ev.entities().count() <= ChaosLimits.MAX_OWNED_PER_EVENT, "capped: " + ev.entities().count());
		manager(h).stop(ev, StopReason.FORCED);
		h.assertValueEqual(ev.entities().count(), 0, "all removed");
		cleanup(h, p);
		h.succeed();
	}

	@GameTest
	public void refusedWithoutRoomAndForCreative(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		p.snapTo(p.getX(), h.getLevel().getMaxY() + 20, p.getZ(), 0.0F, 0.0F); // nowhere for a sheep to stand
		h.assertTrue(manager(h).trigger(event("sheep_disco"), p).isEmpty(), "refused without a spot");
		ServerPlayer c = player(h);
		c.setGameMode(GameType.CREATIVE);
		h.assertTrue(manager(h).trigger(event("sheep_disco"), c).isEmpty(), "creative refused");
		cleanup(h, p, c);
		h.succeed();
	}
}
