package dev.chaosaholic.event.impl;

import java.util.Optional;
import java.util.Set;

import dev.chaosaholic.core.ChaosLimits;
import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventContext;
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
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Weird (instant): each affected player is teleported to a random safe spot {@link #MIN_DISTANCE}-{@link #MAX_DISTANCE}
 * blocks away (horizontally) in the same dimension, like an ender pearl without the damage. A spot comes from
 * {@link Spots#column} (sturdy floor, room for a player, no fluid or hazard; scanned around the player's height, so
 * caves and the Nether work) and must also be in a loaded chunk, inside the world border, free of liquid over the
 * whole player box and open space ({@link Spots#isOpen}: at least {@value Spots#MIN_OPEN_CELLS} connected free
 * blocks, never a sealed pocket inside rock). A player standing under the open sky lands on the surface whenever a
 * tried column offers a surface spot (not in a cave below it). At most {@link ChaosLimits#SPOT_ATTEMPTS} columns are
 * tried; none safe: {@link #canStart} is false.
 *
 * <p>The player keeps their rotation, is dismounted if riding, and lands with zero velocity and zero fall distance on
 * solid ground: the teleport never causes fall damage (a player who was falling is caught; they cannot aim it, so it
 * is not an exploit). Nothing to clean up: the new position is the effect.
 */
public final class RandomTeleport extends ChaosEvent {
	/** Shortest horizontal jump. */
	public static final double MIN_DISTANCE = 8.0;
	/** Longest horizontal jump (≤ ChaosLimits.MAX_RADIUS). */
	public static final double MAX_DISTANCE = 24.0;

	public RandomTeleport() {
		super("random_teleport", Category.WEIRD, INSTANT, INSTANT);
	}

	@Override
	public boolean canStart(EventContext ctx) {
		return findSpot(ctx.level(), ctx.trigger().position(), ctx.random()).isPresent();
	}

	@Override
	public void onPlayerAdded(ActiveEvent ev, ServerPlayer player) {
		// canStart found a spot for the trigger player; the search is random, so try once more before giving up
		Optional<Vec3> spot = findSpot(ev.level(), player.position(), ev.random());
		if (spot.isEmpty()) spot = findSpot(ev.level(), player.position(), ev.random());
		spot.ifPresent(to -> teleport(ev.level(), player, to));
	}

	/**
	 * A random safe standing position for a player {@link #MIN_DISTANCE}-{@link #MAX_DISTANCE} blocks from {@code from}.
	 * A player under the open sky lands on the surface when any tried column offers it (not in a cave below); other
	 * spots must be open space ({@link Spots#isOpen}: never a sealed pocket inside rock).
	 */
	public static Optional<Vec3> findSpot(ServerLevel level, Vec3 from, RandomSource random) {
		double max = Math.min(MAX_DISTANCE, ChaosLimits.MAX_RADIUS);
		boolean sky = Spots.underSky(level, BlockPos.containing(from));
		Optional<Vec3> fallback = Optional.empty();
		for (int i = 0; i < ChaosLimits.SPOT_ATTEMPTS; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double dist = MIN_DISTANCE + random.nextDouble() * (max - MIN_DISTANCE);
			int x = Mth.floor(from.x + Math.cos(angle) * dist);
			int z = Mth.floor(from.z + Math.sin(angle) * dist);
			Optional<Vec3> spot = spotIn(level, from, x, z, sky);
			if (spot.isEmpty()) continue;
			if (!sky || Spots.underSky(level, BlockPos.containing(spot.get()))) return spot;
			if (fallback.isEmpty()) fallback = spot; // under the sky, but this column only had a spot below the surface
		}
		return fallback;
	}

	/**
	 * The destination in column (x, z), if any ({@link #accepts}): with {@code sky} (the player stands under the open
	 * sky) the surface spot first, then the spot nearest to the player's height ({@link Spots#column}).
	 */
	public static Optional<Vec3> spotIn(ServerLevel level, Vec3 from, int x, int z, boolean sky) {
		int y = Mth.floor(from.y);
		if (sky) {
			Optional<Vec3> top = Spots.surface(level, x, y, z, EntityTypes.PLAYER);
			if (top.isPresent() && accepts(level, from, top.get())) return top;
		}
		return Spots.column(level, x, y, z, EntityTypes.PLAYER).filter(spot -> accepts(level, from, spot));
	}

	/**
	 * {@code to} is a valid destination from {@code from}: in range, inside the world border, no liquid in the player
	 * box, and open space (not a sealed pocket).
	 */
	public static boolean accepts(ServerLevel level, Vec3 from, Vec3 to) {
		double dx = to.x - from.x;
		double dz = to.z - from.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		if (horizontal < MIN_DISTANCE || horizontal > Math.min(MAX_DISTANCE, ChaosLimits.MAX_RADIUS)) return false;
		AABB box = EntityTypes.PLAYER.getSpawnAABB(to.x, to.y, to.z);
		return level.getWorldBorder().isWithinBounds(box) && !level.containsAnyLiquid(box)
				&& Spots.isOpen(level, BlockPos.containing(to));
	}

	/** Teleports {@code player} to {@code to} (same level, rotation kept) with ender-pearl-like particles and sound. */
	public static void teleport(ServerLevel level, ServerPlayer player, Vec3 to) {
		Vec3 from = player.position();
		if (player.isPassenger()) player.stopRiding();
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1.0, from.z, 32, 0.4, 0.7, 0.4, 0.05);
		level.playSound(player, from.x, from.y, from.z, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.2F);
		player.teleportTo(level, to.x, to.y, to.z, Set.of(), player.getYRot(), player.getXRot(), false);
		player.resetFallDistance();
		level.sendParticles(ParticleTypes.PORTAL, to.x, to.y + 1.0, to.z, 32, 0.4, 0.7, 0.4, 0.3);
		level.playSound(player, to.x, to.y, to.z, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.2F);
	}

	@Override
	public Holder<SoundEvent> startSound() {
		return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.CHORUS_FRUIT_TELEPORT);
	}

	@Override
	public float startSoundPitch() {
		return 1.2F;
	}
}
