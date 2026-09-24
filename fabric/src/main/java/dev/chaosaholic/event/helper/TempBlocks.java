package dev.chaosaholic.event.helper;

import dev.chaosaholic.core.ChaosLimits;
import dev.chaosaholic.event.ActiveEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Temporary blocks of one event instance. The original state is recorded (persistently, see {@link TempBlockStore})
 * before the change and put back when the instance ends, unless a player changed the block in the meantime.
 * Blocks with a block entity (chests, furnaces, ...) are never replaced, and one position is never temporary for
 * two instances at once. At most {@link ChaosLimits#MAX_TEMP_BLOCKS_PER_EVENT} per instance.
 */
public final class TempBlocks {
	private final ActiveEvent owner;

	public TempBlocks(ActiveEvent owner) {
		this.owner = owner;
	}

	/** Places {@code state} at {@code pos} until the event ends. False if refused (cap, block entity, already temporary, unloaded). */
	public boolean set(BlockPos pos, BlockState state) {
		ServerLevel level = owner.level();
		BlockState original = level.getBlockState(pos);
		if (!canChange(pos, original)) return false;
		if (!level.setBlock(pos, state, Block.UPDATE_ALL)) return false;
		store().add(new TempBlockStore.Entry(level.dimension(), pos.immutable(), original, level.getBlockState(pos), owner.uuid()));
		return true;
	}

	/**
	 * Takes over a block that something else already changed (e.g. a falling anvil landed): {@code original} is
	 * restored at the end if the block is still what it is now.
	 */
	public boolean adopt(BlockPos pos, BlockState original) {
		ServerLevel level = owner.level();
		if (!canChange(pos, original)) return false;
		store().add(new TempBlockStore.Entry(level.dimension(), pos.immutable(), original, level.getBlockState(pos), owner.uuid()));
		return true;
	}

	public int count() {
		return store().count(owner.uuid());
	}

	/** Framework: end of the instance. */
	public void revertAll() {
		store().restore(owner.level().getServer(), owner.uuid());
	}

	private boolean canChange(BlockPos pos, BlockState original) {
		ServerLevel level = owner.level();
		return level.isLoaded(pos) && !original.hasBlockEntity() && count() < ChaosLimits.MAX_TEMP_BLOCKS_PER_EVENT
				&& !store().contains(level.dimension(), pos);
	}

	private TempBlockStore store() {
		return TempBlockStore.get(owner.level().getServer());
	}
}
