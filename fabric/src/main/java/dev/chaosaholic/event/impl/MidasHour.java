package dev.chaosaholic.event.impl;

import java.util.ArrayList;
import java.util.List;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventManager;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Good: for 60-120 s, every ore ({@code c:ores}) an affected player mines drops its loot a second time. The bonus is
 * one extra, independent roll of the block's real drop computation with the player's tool (Fortune is rolled again),
 * popped where the block was. Nothing to clean up: the bonus items are normal drops.
 *
 * <p>No bonus when vanilla would drop nothing: wrong tool ({@code hasCorrectToolForDrops}), game rule
 * {@code block_drops} off, or a Creative/Spectator player. Items equal to the broken block itself are never doubled
 * ({@link #bonusDrops}): with Silk Touch (or an ore that always drops itself, such as ancient debris) doubling would
 * let a player place the copy and mine it again, duplicating ore blocks without limit during the event. So Silk
 * Touch mining gets no bonus; the doubling applies to the ore's resources.
 */
public final class MidasHour extends ChaosEvent {
	public static final String ID = "midas_hour";

	public MidasHour() {
		super("midas_hour", Category.GOOD, 60, 120);
	}

	@Override
	public void onPlayerAdded(ActiveEvent ev, ServerPlayer player) {
		ev.level().sendParticles(ParticleTypes.WAX_ON, player.getX(), player.getY() + 1.0, player.getZ(), 16, 0.6, 0.6, 0.6, 0.05);
	}

	@Override
	public void afterBlockBreak(ActiveEvent ev, ServerPlayer player, BlockPos pos, BlockState state) {
		if (!ev.isAffected(player) || !EventManager.isEligible(player) || !state.is(ConventionalBlockTags.ORES)) return;
		if (!(player.level() instanceof ServerLevel level) || !level.getGameRules().get(GameRules.BLOCK_DROPS)) return;
		if (!player.hasCorrectToolForDrops(state)) return;
		// PlayerBlockBreakEvents.AFTER runs before the tool is damaged or broken: the stack is the one that mined it.
		List<ItemStack> bonus = bonusDrops(state, Block.getDrops(state, level, pos, null, player, player.getMainHandItem()));
		if (bonus.isEmpty()) return;
		for (ItemStack stack : bonus) Block.popResource(level, pos, stack);
		level.sendParticles(ParticleTypes.WAX_ON, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
	}

	/** The extra drops: {@code drops} without empty stacks and without the broken block's own item (Silk Touch). */
	public static List<ItemStack> bonusDrops(BlockState state, List<ItemStack> drops) {
		Item self = state.getBlock().asItem();
		List<ItemStack> out = new ArrayList<>(drops.size());
		for (ItemStack stack : drops) {
			if (!stack.isEmpty() && !stack.is(self)) out.add(stack);
		}
		return out;
	}

	@Override
	public Holder<SoundEvent> startSound() {
		return SoundEvents.NOTE_BLOCK_BELL;
	}

	@Override
	public float startSoundPitch() {
		return 1.4F;
	}
}
