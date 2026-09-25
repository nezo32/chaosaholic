package dev.chaosaholic.event.helper;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

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
 * powder snow) at the feet or below, and room for the entity's box. Searched in a ring around {@code center} near the
 * same height (+4/-6 blocks, nearest height first, so it works in caves and indoors a spot in the same room wins over
 * one on the roof), falling back to the surface heightmap when that is close. {@link #isOpen} tells a sealed pocket
 * (a few air blocks inside solid rock) from open space.
 */
public final class Spots {
	private static final int SCAN_UP = 4;
	private static final int SCAN_DOWN = 6;
	/** {@link #isOpen}: connected free blocks needed to count as open space (a sealed 2-block pocket has 2). */
	public static final int MIN_OPEN_CELLS = 8;

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

	/**
	 * Safe standing position in column (x, z) nearest to height {@code y} (y, y-1, y+1, y-2, ... within +4/-6); then
	 * the surface if that is close.
	 */
	public static Optional<Vec3> column(ServerLevel level, int x, int y, int z, EntityType<?> type) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i <= 2 * Math.max(SCAN_UP, SCAN_DOWN); i++) {
			int dy = (i & 1) == 0 ? i / 2 : -(i + 1) / 2; // 0, -1, +1, -2, +2, ...
			if (dy > SCAN_UP || dy < -SCAN_DOWN) continue;
			pos.set(x, y + dy, z);
			if (isSafe(level, pos, type)) return Optional.of(Vec3.atBottomCenterOf(pos));
		}
		return surface(level, x, y, z, type);
	}

	/** Safe standing position on top of column (x, z) (surface heightmap, leaves ignored), if within 10 blocks of {@code y}. */
	public static Optional<Vec3> surface(ServerLevel level, int x, int y, int z, EntityType<?> type) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
		if (!level.isLoaded(pos)) return Optional.empty();
		int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		if (Math.abs(surface - y) <= SCAN_UP + SCAN_DOWN && isSafe(level, pos.set(x, surface, z), type)) {
			return Optional.of(Vec3.atBottomCenterOf(pos));
		}
		return Optional.empty();
	}

	/** True if nothing but sky is above {@code pos} (at or above the surface heightmap, leaves ignored). */
	public static boolean underSky(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) return false;
		return pos.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
	}

	/**
	 * True if {@code feet} is open space: at least {@link #MIN_OPEN_CELLS} free blocks (no collision, no fluid, loaded)
	 * are connected to it through their faces. Bounded: the flood fill stops as soon as that many were reached, so it
	 * visits at most a few dozen blocks.
	 */
	public static boolean isOpen(ServerLevel level, BlockPos feet) {
		if (!free(level, feet)) return false;
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		Set<BlockPos> seen = new HashSet<>();
		queue.add(feet.immutable());
		seen.add(feet.immutable());
		while (!queue.isEmpty()) {
			BlockPos at = queue.poll();
			for (Direction d : Direction.values()) {
				BlockPos next = at.relative(d);
				if (seen.contains(next) || !free(level, next)) continue;
				seen.add(next);
				if (seen.size() >= MIN_OPEN_CELLS) return true;
				queue.add(next);
			}
		}
		return seen.size() >= MIN_OPEN_CELLS;
	}

	private static boolean free(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos) || pos.getY() < level.getMinY() || pos.getY() >= level.getMaxY()) return false;
		BlockState state = level.getBlockState(pos);
		return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
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
