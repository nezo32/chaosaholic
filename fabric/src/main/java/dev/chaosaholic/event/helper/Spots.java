package dev.chaosaholic.event.helper;

import java.util.Optional;

import dev.chaosaholic.core.ChaosLimits;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Safe positions near a player: solid top face below, no fluid or hazard (lava, fire, magma, cactus, berry bush,
 * powder snow) at the feet or below, and room for the entity's box. Searched in a ring around {@code center} at the
 * same height (±6 blocks, so it works in caves), falling back to the surface heightmap when that is close.
 */
public final class Spots {
	private static final int SCAN_UP = 4;
	private static final int SCAN_DOWN = 6;

	private Spots() {}

	/**
	 * A random safe spot for {@code type} between {@code minDist} and {@code maxDist} blocks (horizontal) from
	 * {@code center}. Distances are clamped to {@link ChaosLimits#MAX_RADIUS}; at most {@link ChaosLimits#SPOT_ATTEMPTS} tries.
	 */
	public static Optional<Vec3> near(ServerLevel level, Vec3 center, double minDist, double maxDist, RandomSource random, EntityType<?> type) {
		double max = ChaosLimits.clampRadius(maxDist);
		double min = Math.max(0, Math.min(minDist, max));
		for (int i = 0; i < ChaosLimits.SPOT_ATTEMPTS; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double dist = min + random.nextDouble() * (max - min);
			int x = Mth.floor(center.x + Math.cos(angle) * dist);
			int z = Mth.floor(center.z + Math.sin(angle) * dist);
			Optional<Vec3> spot = column(level, x, Mth.floor(center.y), z, type);
			if (spot.isPresent()) return spot;
		}
		return Optional.empty();
	}

	/** First safe standing position in column (x, z) near height {@code y}, scanning downwards; then the surface. */
	public static Optional<Vec3> column(ServerLevel level, int x, int y, int z, EntityType<?> type) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dy = SCAN_UP; dy >= -SCAN_DOWN; dy--) {
			pos.set(x, y + dy, z);
			if (isSafe(level, pos, type)) return Optional.of(Vec3.atBottomCenterOf(pos));
		}
		if (!level.isLoaded(pos.set(x, y, z))) return Optional.empty();
		int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		if (Math.abs(surface - y) <= SCAN_UP + SCAN_DOWN && isSafe(level, pos.set(x, surface, z), type)) {
			return Optional.of(Vec3.atBottomCenterOf(pos));
		}
		return Optional.empty();
	}

	/** {@code feet} is free for {@code type}, stands on a sturdy top face and nothing there hurts. */
	public static boolean isSafe(ServerLevel level, BlockPos feet, EntityType<?> type) {
		if (!level.isLoaded(feet) || feet.getY() <= level.getMinY() || feet.getY() >= level.getMaxY()) return false;
		BlockPos below = feet.below();
		BlockState floor = level.getBlockState(below);
		if (!floor.isFaceSturdy(level, below, Direction.UP) || hazard(floor)) return false;
		BlockState at = level.getBlockState(feet);
		if (hazard(at) || !level.getFluidState(feet).isEmpty() || !level.getFluidState(below).isEmpty()) return false;
		return level.noCollision(type.getSpawnAABB(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5));
	}

	private static boolean hazard(BlockState state) {
		return state.is(BlockTags.FIRE) || state.is(Blocks.LAVA)
				|| state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
				|| state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW)
				|| state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)
				|| state.is(Blocks.WITHER_ROSE);
	}
}
