package dev.chaosaholic.event.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.helper.Sounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Bad: for 30-45 s, every {@link #CHECK_TICKS} ticks (first check after {@link #GRACE_TICKS}) each affected player
 * has a {@link #CHANCE_PERCENT} % chance to drop the whole main-hand stack, at most {@link #MAX_DROPS} times per
 * player and run. The stack is tossed like Q (a normal item entity, thrower = the player, pickup delay
 * {@link #PICKUP_DELAY_TICKS}) about 1.7 blocks forward, but only if every block it can fly over or slide onto
 * within {@link #FLIGHT_REACH} blocks (the item's width included, up to a wall) has a floor at most
 * {@link #MAX_FALL} blocks down and no lava, fire, campfire or cactus. Otherwise it is set down without speed on top
 * of the block the player stands on (the solid part under the player, never the air of an overhang); if that is not
 * safe either, nothing is dropped this time. Never while the player is airborne or in lava. So the item is never
 * destroyed (lava, fire, the void) and nothing needs cleanup: the dropped items are the player's own and stay in the
 * world like any drop. No hazard, so no warning.
 */
public final class Butterfingers extends ChaosEvent {
	public static final int CHECK_TICKS = 100;
	/** No drop in the first seconds: the player reads the announcement first. */
	public static final int GRACE_TICKS = 60;
	public static final int CHANCE_PERCENT = 35;
	/** Drops per player and run (extensions included). */
	public static final int MAX_DROPS = 4;
	/** Shorter than a Q toss (40): the player can grab it back quickly. */
	public static final int PICKUP_DELAY_TICKS = 20;
	/** Horizontal toss speed (blocks per tick); on level ground the item lands about 1.7 blocks ahead. */
	public static final double TOSS_SPEED = 0.15;
	/**
	 * How far ahead the flight is checked: the landing point on level ground plus a drop of {@link #MAX_FALL} blocks
	 * (about 2.4) and the slide after landing, with margin.
	 */
	public static final double FLIGHT_REACH = 3.0;
	/** Sampling step along the flight. */
	private static final double FLIGHT_STEP = 0.25;
	/** Half the width of an item entity, plus margin. */
	private static final double ITEM_HALF_WIDTH = 0.15;
	/** Deepest drop an item may fall to its landing (blocks below the player's feet). */
	public static final int MAX_FALL = 3;

	private static final class State {
		final Map<UUID, Integer> drops = new HashMap<>();
	}

	public Butterfingers() {
		super("butterfingers", Category.BAD, 30, 45);
	}

	@Override
	public void onPlayerAdded(ActiveEvent ev, ServerPlayer player) {
		ev.level().sendParticles(ParticleTypes.DRIPPING_HONEY, player.getX(), player.getY() + 1.2, player.getZ(), 6, 0.3, 0.3, 0.3, 0.0);
	}

	@Override
	public void onTick(ActiveEvent ev) {
		if (ev.age() < GRACE_TICKS || (ev.age() - GRACE_TICKS) % CHECK_TICKS != 0) return;
		for (ServerPlayer p : ev.players()) {
			attempt(ev, p, ev.random().nextInt(100) < CHANCE_PERCENT);
		}
	}

	/**
	 * One drop check for an affected player: drops the main-hand stack if {@code lucky}, the player holds something,
	 * stands on the ground, is not in lava, has drops left and there is a safe place for the item ({@link #plan}).
	 * Returns true if the stack was dropped.
	 */
	public static boolean attempt(ActiveEvent ev, ServerPlayer player, boolean lucky) {
		if (!lucky || !ev.isAffected(player)) return false;
		State s = ev.state(State::new);
		int done = s.drops.getOrDefault(player.getUUID(), 0);
		if (done >= MAX_DROPS) return false;
		if (player.getMainHandItem().isEmpty() || !player.onGround() || player.isInLava()) return false;
		Toss toss = plan(ev.level(), player);
		if (toss == null) return false;
		drop(ev.level(), player, toss);
		s.drops.put(player.getUUID(), done + 1);
		return true;
	}

	/** Drops left for {@code player} in this run. */
	public static int dropsLeft(ActiveEvent ev, ServerPlayer player) {
		return MAX_DROPS - ev.state(State::new).drops.getOrDefault(player.getUUID(), 0);
	}

	/** Where the item starts and how it moves. */
	public record Toss(Vec3 from, Vec3 velocity) {}

	/**
	 * How the stack would leave {@code player}'s hand: tossed forward when every block along the flight
	 * ({@link #FLIGHT_REACH} blocks ahead, the item's width included) is safe ({@link #safeColumn}), else set down
	 * without speed right above the block the player stands on (the part under the player, not the air of an
	 * overhang); null when neither is safe (then nothing is dropped).
	 */
	public static @Nullable Toss plan(ServerLevel level, ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		if (flat.lengthSqr() > 1.0E-4 && safeFlight(level, player, flat.normalize())) {
			Vec3 from = new Vec3(player.getX(), player.getEyeY() - 0.3, player.getZ());
			return new Toss(from, flat.normalize().scale(TOSS_SPEED).add(0.0, 0.15, 0.0));
		}
		Vec3 feet = setDown(level, player);
		return feet == null ? null : new Toss(feet, Vec3.ZERO);
	}

	private static void drop(ServerLevel level, ServerPlayer player, Toss toss) {
		ItemStack held = player.getMainHandItem();
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		// not Player#drop: its signature differs between 26.2 and 26.3
		ItemEntity item = new ItemEntity(level, toss.from().x, toss.from().y, toss.from().z, held);
		item.setPickUpDelay(PICKUP_DELAY_TICKS);
		item.setThrower(player);
		item.setDeltaMovement(toss.velocity());
		level.addFreshEntity(item);
		player.containerMenu.broadcastChanges();
		Sounds.play(player, SoundEvents.ITEM_PICKUP, 0.6F, 0.6F);
	}

	/**
	 * Every column the tossed item can fly over or slide onto is safe: sampled every {@link #FLIGHT_STEP} blocks up to
	 * {@link #FLIGHT_REACH} ahead, and {@link #ITEM_HALF_WIDTH} to each side. The flight ends early (safe so far) at a
	 * block in the way at flight height (0.6-1.9 above the feet): the item bounces off and falls in front of it.
	 */
	private static boolean safeFlight(ServerLevel level, ServerPlayer player, Vec3 dir) {
		Vec3 side = new Vec3(-dir.z, 0.0, dir.x).scale(ITEM_HALF_WIDTH);
		double y = player.getY();
		if (!level.noCollision(flightBox(player.position(), y))) return false; // no room to throw (low ceiling)
		for (double d = FLIGHT_STEP; d <= FLIGHT_REACH + 1.0E-6; d += FLIGHT_STEP) {
			Vec3 at = player.position().add(dir.scale(d));
			boolean wall = false;
			for (int k = -1; k <= 1; k++) {
				Vec3 p = at.add(side.scale(k));
				if (!level.noCollision(flightBox(p, y))) {
					wall = true;
				} else if (!safeColumn(level, BlockPos.containing(p.x, y + 0.5, p.z))) {
					return false;
				}
			}
			if (wall) return true;
		}
		return true;
	}

	/**
	 * An item falling down column {@code top} lands safely: a floor (any collision) at most {@link #MAX_FALL} blocks
	 * below and nothing that destroys items (lava, fire, campfire, cactus) from the flight height down to that floor.
	 */
	private static boolean safeColumn(ServerLevel level, BlockPos top) {
		if (destroysItems(level.getBlockState(top.above()))) return false;
		for (int i = 0; i <= MAX_FALL; i++) {
			BlockPos pos = top.below(i);
			if (pos.getY() < level.getMinY()) return false; // the void
			BlockState state = level.getBlockState(pos);
			if (destroysItems(state)) return false;
			if (!state.getCollisionShape(level, pos).isEmpty()) return true; // the floor
		}
		return false;
	}

	/** The space the item flies through above point {@code p} (0.6-1.9 above the feet height {@code y}). */
	private static AABB flightBox(Vec3 p, double y) {
		return new AABB(p.x - ITEM_HALF_WIDTH, y + 0.6, p.z - ITEM_HALF_WIDTH, p.x + ITEM_HALF_WIDTH, y + 1.9, p.z + ITEM_HALF_WIDTH);
	}

	private static boolean blocks(ServerLevel level, BlockPos pos) {
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	/**
	 * Just above the block the player stands on, inside that block's top face (so the item cannot roll over an edge),
	 * or null if that block is not a sturdy, harmless top or the spot is not free.
	 */
	private static @Nullable Vec3 setDown(ServerLevel level, ServerPlayer player) {
		BlockPos support = supportingBlock(level, player);
		if (support == null) return null;
		double x = Mth.clamp(player.getX(), support.getX() + ITEM_HALF_WIDTH, support.getX() + 1.0 - ITEM_HALF_WIDTH);
		double z = Mth.clamp(player.getZ(), support.getZ() + ITEM_HALF_WIDTH, support.getZ() + 1.0 - ITEM_HALF_WIDTH);
		BlockPos above = support.above();
		if (blocks(level, above) || destroysItems(level.getBlockState(above))) return null;
		return new Vec3(x, above.getY() + 0.05, z);
	}

	/**
	 * The block right under the player's feet that carries them: a sturdy top face, not harmful to items, nearest to
	 * the player's centre among the blocks under their box (the edge block when the player leans over an overhang).
	 */
	private static @Nullable BlockPos supportingBlock(ServerLevel level, ServerPlayer player) {
		AABB box = player.getBoundingBox();
		int y = Mth.floor(player.getY() - 1.0E-3);
		if (Math.abs(player.getY() - (y + 1)) > 1.0E-3) return null; // not standing on a full block top (slab, path, ...)
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX - 1.0E-7); x++) {
			for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ - 1.0E-7); z++) {
				BlockPos pos = new BlockPos(x, y, z);
				BlockState state = level.getBlockState(pos);
				if (!state.isFaceSturdy(level, pos, Direction.UP) || destroysItems(state)) continue;
				double dx = x + 0.5 - player.getX();
				double dz = z + 0.5 - player.getZ();
				double dist = dx * dx + dz * dz;
				if (dist < bestDist) {
					bestDist = dist;
					best = pos;
				}
			}
		}
		return best;
	}

	/** Blocks that burn or break an item entity touching them. */
	private static boolean destroysItems(BlockState state) {
		return state.getFluidState().is(FluidTags.LAVA) || state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES)
				|| state.is(Blocks.CACTUS);
	}

	@Override
	public Holder<SoundEvent> startSound() {
		return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.HONEY_BLOCK_SLIDE);
	}

	@Override
	public float startSoundPitch() {
		return 1.2F;
	}
}
