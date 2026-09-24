package dev.chaosaholic.event.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.helper.Sounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Bad: for 30-45 s, every {@link #CHECK_TICKS} ticks (first check after {@link #GRACE_TICKS}) each affected player
 * has a {@link #CHANCE_PERCENT} % chance to drop the whole main-hand stack, at most {@link #MAX_DROPS} times per
 * player and run. The stack is tossed like Q (a normal item entity, thrower = the player, pickup delay
 * {@link #PICKUP_DELAY_TICKS}): about a block forward, or straight down at the feet if the spot ahead has lava,
 * fire, a campfire or cactus, or no floor within 3 blocks. Never while the player is airborne or in lava (the item
 * could be lost). Nothing is destroyed and nothing needs cleanup: the dropped items are the player's own and stay in
 * the world like any drop. No hazard, so no warning.
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
	/** Horizontal toss speed (blocks per tick); lands about a block ahead. */
	public static final double TOSS_SPEED = 0.2;

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
	 * stands on the ground, is not in lava, and has drops left. Returns true if the stack was dropped.
	 */
	public static boolean attempt(ActiveEvent ev, ServerPlayer player, boolean lucky) {
		if (!lucky || !ev.isAffected(player)) return false;
		State s = ev.state(State::new);
		int done = s.drops.getOrDefault(player.getUUID(), 0);
		if (done >= MAX_DROPS) return false;
		if (player.getMainHandItem().isEmpty() || !player.onGround() || player.isInLava()) return false;
		drop(ev.level(), player);
		s.drops.put(player.getUUID(), done + 1);
		return true;
	}

	/** Drops left for {@code player} in this run. */
	public static int dropsLeft(ActiveEvent ev, ServerPlayer player) {
		return MAX_DROPS - ev.state(State::new).drops.getOrDefault(player.getUUID(), 0);
	}

	private static void drop(ServerLevel level, ServerPlayer player) {
		ItemStack held = player.getMainHandItem();
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
		boolean forward = flat != Vec3.ZERO && safeLanding(level, BlockPos.containing(player.position().add(flat.scale(1.2))));
		Vec3 velocity = forward ? flat.scale(TOSS_SPEED).add(0.0, 0.15, 0.0) : new Vec3(0.0, 0.1, 0.0);
		// not Player#drop: its signature differs between 26.2 and 26.3
		ItemEntity item = new ItemEntity(level, player.getX(), player.getEyeY() - 0.3, player.getZ(), held);
		item.setPickUpDelay(PICKUP_DELAY_TICKS);
		item.setThrower(player);
		item.setDeltaMovement(velocity);
		level.addFreshEntity(item);
		player.containerMenu.broadcastChanges();
		Sounds.play(player, SoundEvents.ITEM_PICKUP, 0.6F, 0.6F);
	}

	/** Landing spot ahead: passable, a floor within 3 blocks below and no lava / fire / campfire / cactus down to that floor. */
	private static boolean safeLanding(ServerLevel level, BlockPos feet) {
		boolean floor = false;
		for (int i = 0; i <= 3; i++) {
			BlockPos pos = feet.below(i);
			BlockState state = level.getBlockState(pos);
			if (destroysItems(state)) return false;
			if (i > 0 && !state.getCollisionShape(level, pos).isEmpty()) {
				floor = true;
				break;
			}
		}
		// the block at the feet itself must be passable, otherwise the item bounces off a wall
		return floor && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty();
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
