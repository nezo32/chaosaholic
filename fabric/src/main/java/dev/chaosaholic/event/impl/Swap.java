package dev.chaosaholic.event.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventContext;
import dev.chaosaholic.event.helper.Area;
import dev.chaosaholic.event.helper.Spots;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Weird (instant): each affected player swaps places with a random mob within {@link #RADIUS} blocks. Only "fair
 * game" mobs are picked (a {@link Mob}, so no armor stands, and Area#isFairGame: no bosses, pets, named mobs, riders
 * or entities of other events), and only when both destinations are safe ({@link #fits}): the player's current box
 * fits at the mob's spot and the mob's box at the player's, both loaded, inside the world border, standing on a
 * block, free of blocks, liquids and hazards (fire, lava, magma, cactus, berry bush, powder snow, campfire, wither
 * rose). The player must also see the mob (line of sight, eyes to eyes) or the mob must stand in open space
 * ({@link Spots#isOpen}), so a player is never swapped into a sealed pocket inside rock. No such mob:
 * {@link #canStart} is false.
 *
 * <p>Both keep their own rotation; velocity and fall distance of both are reset, so the swap can neither hurt nor
 * save anyone from a fall. A player riding something is not swapped. Nothing to clean up: the positions are the effect.
 */
public final class Swap extends ChaosEvent {
	/** Search radius around the player. */
	public static final double RADIUS = 16.0;
	/** How far below a box a block must be to count as ground under it. */
	private static final double GROUND_PROBE = 0.2;

	/** Mobs already swapped by this instance (world scope: one mob per player). */
	private static final class State {
		final Set<Entity> used = new HashSet<>();
	}

	public Swap() {
		super("swap", Category.WEIRD, INSTANT, INSTANT);
	}

	@Override
	public boolean canStart(EventContext ctx) {
		return !candidates(ctx.level(), ctx.trigger()).isEmpty();
	}

	@Override
	public void onPlayerAdded(ActiveEvent ev, ServerPlayer player) {
		State s = ev.state(State::new);
		List<LivingEntity> mobs = candidates(ev.level(), player);
		mobs.removeIf(s.used::contains);
		if (mobs.isEmpty()) return;
		LivingEntity mob = mobs.get(ev.random().nextInt(mobs.size()));
		if (swap(ev.level(), player, mob)) s.used.add(mob);
	}

	/** Mobs {@code player} can swap with right now, nearest first (empty while riding). */
	public static List<LivingEntity> candidates(ServerLevel level, ServerPlayer player) {
		if (player.isPassenger() || player.level() != level) return new ArrayList<>();
		return Area.entities(level, player.position(), RADIUS, LivingEntity.class,
				mob -> mob instanceof Mob && Area.isFairGame(mob) && canSwap(level, player, mob));
	}

	/** Both boxes fit at the other's position, and the player sees the mob or the mob stands in open space. */
	public static boolean canSwap(ServerLevel level, ServerPlayer player, LivingEntity mob) {
		Vec3 p = player.position();
		Vec3 m = mob.position();
		return fits(level, player, player.getBoundingBox().move(m.subtract(p)))
				&& fits(level, mob, mob.getBoundingBox().move(p.subtract(m)))
				&& (player.hasLineOfSight(mob) || Spots.isOpen(level, mob.blockPosition()));
	}

	/** Swaps the two positions (rotations kept). False (and nothing moved) if it is not safe any more. */
	public static boolean swap(ServerLevel level, ServerPlayer player, LivingEntity mob) {
		if (mob.isRemoved() || mob.level() != level || !canSwap(level, player, mob)) return false;
		Vec3 p = player.position();
		Vec3 m = mob.position();
		effects(level, player, p);
		effects(level, player, m);
		player.teleportTo(level, m.x, m.y, m.z, Set.of(), player.getYRot(), player.getXRot(), false);
		player.resetFallDistance();
		mob.teleportTo(p.x, p.y, p.z);
		mob.setDeltaMovement(Vec3.ZERO);
		mob.resetFallDistance();
		if (mob instanceof Mob pathing) pathing.getNavigation().stop();
		return true;
	}

	/**
	 * {@code box} (the entity's box moved to its destination) is loaded, inside the world border, free of blocks and
	 * liquids, has ground right below and touches no hazard block.
	 */
	static boolean fits(ServerLevel level, Entity entity, AABB box) {
		if (!level.isLoaded(BlockPos.containing(box.minX, box.minY, box.minZ)) || !level.isLoaded(BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
			return false;
		}
		if (box.minY <= level.getMinY() || box.maxY >= level.getMaxY()) return false;
		if (!level.getWorldBorder().isWithinBounds(box)) return false;
		if (!level.noCollision(entity, box) || level.containsAnyLiquid(box)) return false;
		AABB below = new AABB(box.minX, box.minY - GROUND_PROBE, box.minZ, box.maxX, box.minY, box.maxZ);
		if (!level.getBlockCollisions(entity, below).iterator().hasNext()) return false;
		return level.getBlockStatesIfLoaded(box.expandTowards(0, -0.5, 0)).noneMatch(Swap::hazard);
	}

	private static boolean hazard(BlockState state) {
		return state.is(BlockTags.FIRE) || state.is(Blocks.LAVA)
				|| state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
				|| state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW)
				|| state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)
				|| state.is(Blocks.WITHER_ROSE);
	}

	/** PORTAL particles and the teleport sound for bystanders (the player hears the start sound). */
	private static void effects(ServerLevel level, ServerPlayer player, Vec3 at) {
		level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 1.0, at.z, 24, 0.4, 0.6, 0.4, 0.3);
		level.playSound(player, at.x, at.y, at.z, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.0F);
	}

	@Override
	public Holder<SoundEvent> startSound() {
		return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.CHORUS_FRUIT_TELEPORT);
	}
}
