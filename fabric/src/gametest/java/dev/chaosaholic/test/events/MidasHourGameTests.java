package dev.chaosaholic.test.events;

import static dev.chaosaholic.test.TestSupport.cleanup;
import static dev.chaosaholic.test.TestSupport.defaults;
import static dev.chaosaholic.test.TestSupport.leave;
import static dev.chaosaholic.test.TestSupport.manager;
import static dev.chaosaholic.test.TestSupport.start;
import static dev.chaosaholic.test.TestSupport.survivalPlayer;

import java.util.List;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.StopReason;
import dev.chaosaholic.event.impl.MidasHour;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * midas_hour: the routed PlayerBlockBreakEvents.AFTER hook pops one extra roll of an ore's real drops for affected
 * players with the right tool. The tests fire the Fabric event directly (what ServerPlayerGameMode does after a
 * break), so only the bonus lands on the ground.
 */
public class MidasHourGameTests {
	private static final BlockPos ORE = new BlockPos(4, 2, 4);

	/** Fires the break hook for {@code block} at {@link #ORE} and returns (and removes) the items that popped. */
	private static List<ItemStack> mine(GameTestHelper h, ServerPlayer p, Block block) {
		BlockPos pos = h.absolutePos(ORE);
		PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(h.getLevel(), p, pos, block.defaultBlockState(), null);
		List<ItemEntity> items = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.5));
		List<ItemStack> stacks = items.stream().map(e -> e.getItem().copy()).toList();
		items.forEach(ItemEntity::discard);
		return stacks;
	}

	private static int count(List<ItemStack> stacks, Item item) {
		return stacks.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
	}

	private static ServerPlayer miner(GameTestHelper h, ItemStack tool) {
		ServerPlayer p = survivalPlayer(h, new Vec3(2.5, 2, 2.5));
		p.setItemInHand(InteractionHand.MAIN_HAND, tool);
		return p;
	}

	@GameTest
	public void doublesOresForAffectedPlayersOnly(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = miner(h, new ItemStack(Items.IRON_PICKAXE));
		ServerPlayer other = miner(h, new ItemStack(Items.IRON_PICKAXE));
		h.assertTrue(mine(h, p, Blocks.IRON_ORE).isEmpty(), "no bonus before the event");
		ActiveEvent ev = start(h, MidasHour.ID, p);
		h.assertTrue(ev.hasBossBar(), "timed");
		List<ItemStack> iron = mine(h, p, Blocks.IRON_ORE);
		h.assertValueEqual(count(iron, Items.RAW_IRON), 1, "one extra raw iron: " + iron);
		h.assertTrue(count(mine(h, p, Blocks.DEEPSLATE_DIAMOND_ORE), Items.DIAMOND) >= 1, "diamond ore doubled");
		h.assertTrue(count(mine(h, p, Blocks.COAL_ORE), Items.COAL) >= 1, "coal ore doubled");
		h.assertTrue(mine(h, p, Blocks.STONE).isEmpty(), "not an ore: nothing extra");
		h.assertTrue(mine(h, other, Blocks.IRON_ORE).isEmpty(), "players outside the event get nothing extra");
		manager(h).stop(ev, StopReason.FORCED);
		h.assertTrue(mine(h, p, Blocks.IRON_ORE).isEmpty(), "no bonus after the end");
		cleanup(h, p, other);
		h.succeed();
	}

	@GameTest
	public void wrongToolAndSilkTouchGetNothing(GameTestHelper h) {
		defaults(h);
		ServerPlayer hand = miner(h, ItemStack.EMPTY);
		ServerPlayer wood = miner(h, new ItemStack(Items.WOODEN_PICKAXE));
		ItemStack silk = new ItemStack(Items.DIAMOND_PICKAXE);
		silk.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
		ServerPlayer silky = miner(h, silk);
		start(h, MidasHour.ID, hand);
		start(h, MidasHour.ID, wood);
		start(h, MidasHour.ID, silky);
		h.assertTrue(mine(h, hand, Blocks.IRON_ORE).isEmpty(), "bare hand: vanilla drops nothing, so no bonus");
		h.assertTrue(mine(h, wood, Blocks.DIAMOND_ORE).isEmpty(), "wooden pickaxe on diamond ore: no bonus");
		h.assertTrue(count(mine(h, wood, Blocks.COAL_ORE), Items.COAL) >= 1, "wooden pickaxe on coal ore: bonus");
		h.assertTrue(mine(h, silky, Blocks.DIAMOND_ORE).isEmpty(), "silk touch: the ore block itself is never doubled");
		List<ItemStack> filtered = MidasHour.bonusDrops(Blocks.IRON_ORE.defaultBlockState(),
				List.of(new ItemStack(Items.IRON_ORE), new ItemStack(Items.RAW_IRON), ItemStack.EMPTY));
		h.assertTrue(filtered.size() == 1 && filtered.getFirst().is(Items.RAW_IRON), "self drop filtered: " + filtered);
		cleanup(h, hand, wood, silky);
		h.succeed();
	}

	@GameTest(maxTicks = 40)
	public void creativeAndLogoutEndIt(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = miner(h, new ItemStack(Items.IRON_PICKAXE));
		ActiveEvent ev = start(h, MidasHour.ID, p);
		p.setGameMode(GameType.CREATIVE);
		h.assertTrue(mine(h, p, Blocks.IRON_ORE).isEmpty(), "creative: no bonus, even before the framework removes the player");
		ServerPlayer q = miner(h, new ItemStack(Items.IRON_PICKAXE));
		ActiveEvent ev2 = start(h, MidasHour.ID, q);
		leave(h, q);
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(ev.isStopped() && ev2.isStopped(), "creative / logout ended both"))
				.thenExecute(() -> cleanup(h, p))
				.thenSucceed();
	}
}
