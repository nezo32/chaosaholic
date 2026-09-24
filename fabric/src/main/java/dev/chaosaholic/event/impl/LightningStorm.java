package dev.chaosaholic.event.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.chaosaholic.core.ChaosLimits;
import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventContext;
import dev.chaosaholic.event.helper.Sounds;
import dev.chaosaholic.event.helper.Spots;
import dev.chaosaholic.event.helper.Warning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Bad: after the warning (3 s, 4 s on Hardcore, distant thunder on the last ping), lightning strikes around the
 * affected players for 15-20 s.
 *
 * <p>Every {@link #INTERVAL_TICKS} one strike per affected player is planned on a spot {@link #MIN_STRIKE_DISTANCE}
 * to {@link #MAX_STRIKE_DISTANCE} blocks away, sparks on it for {@link #MARK_TICKS}, then a bolt hits it, unless any
 * affected player is now within {@link #MIN_DISTANCE} blocks (horizontally) of it: that strike is skipped. At most
 * {@link #MAX_STRIKES} per instance.
 *
 * <p>Design decision: the bolts are <b>visual only</b> ({@code setVisualOnly(true)}): flash, thunder and impact
 * sound, but no damage, no fire, no charged creepers or witches. Spots on copper or lightning rods are never picked,
 * so the strikes change no blocks either. The bolts are owned entities (removed if the event ends mid-flash).
 */
public final class LightningStorm extends ChaosEvent {
	/** Per instance, extensions included. */
	public static final int MAX_STRIKES = 8;
	/** No strike lands within this horizontal distance of any player. */
	public static final double MIN_DISTANCE = 4.0;
	/** Planned strike spots are further away, so a walking player rarely cancels one. */
	public static final double MIN_STRIKE_DISTANCE = 5.0;
	public static final double MAX_STRIKE_DISTANCE = 14.0;
	/** Ticks between two strikes (per affected player). */
	public static final int INTERVAL_TICKS = 40;
	/** Spark marker on the spot before the bolt. */
	public static final int MARK_TICKS = 10;

	public LightningStorm() {
		super("lightning_storm", Category.BAD, 15, 20);
	}

	/** Per-instance run state. */
	public static final class State {
		boolean striking;
		int strikeStart;
		/** Planned + struck (the cap counts both). */
		int planned;
		final List<Vec3> strikes = new ArrayList<>();
	}

	@Override
	public boolean canStart(EventContext ctx) {
		for (ServerPlayer p : ctx.players()) {
			if (Spots.near(ctx.level(), p.position(), MIN_STRIKE_DISTANCE, MAX_STRIKE_DISTANCE, ctx.random(), EntityTypes.LIGHTNING_BOLT).isPresent()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void onStart(ActiveEvent ev) {
		State s = ev.state(State::new);
		Warning.thenRun(ev, () -> {
			s.striking = true;
			s.strikeStart = ev.age();
		});
		// Distant thunder with the last warning ping (design/presentation.md §4); scheduled after the pings, so it plays after.
		int lastPing = (Warning.delay(ev.context()) / ChaosLimits.TICKS_PER_SECOND - 1) * ChaosLimits.TICKS_PER_SECOND;
		ev.schedule(Math.max(1, lastPing), () -> {
			for (ServerPlayer p : ev.players()) Sounds.play(p, SoundEvents.TRIDENT_THUNDER, 0.3F, 1.6F);
		});
	}

	@Override
	public void onPlayerAdded(ActiveEvent ev, ServerPlayer player) {
		ev.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1, player.getZ(), 20, 0.6, 0.6, 0.6, 0.05);
	}

	@Override
	public void onTick(ActiveEvent ev) {
		State s = ev.state(State::new);
		if (s.striking && (ev.age() - s.strikeStart) % INTERVAL_TICKS == 0 && ev.remainingTicks() > MARK_TICKS) {
			for (ServerPlayer p : ev.players()) plan(ev, p);
		}
	}

	/** Plans one strike near {@code player}: sparks now, bolt {@link #MARK_TICKS} later. False when capped or no spot. */
	public static boolean plan(ActiveEvent ev, ServerPlayer player) {
		State s = ev.state(State::new);
		if (s.planned >= MAX_STRIKES) return false;
		Optional<Vec3> found = Spots.near(ev.level(), player.position(), MIN_STRIKE_DISTANCE, MAX_STRIKE_DISTANCE, ev.random(), EntityTypes.LIGHTNING_BOLT);
		if (found.isEmpty()) return false;
		Vec3 spot = found.get();
		if (!allowed(ev, spot)) return false;
		s.planned++;
		ev.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, spot.x, spot.y + 0.3, spot.z, 16, 0.3, 0.4, 0.3, 0.05);
		ev.schedule(MARK_TICKS, () -> {
			if (strike(ev, spot) == null) s.planned--; // give the slot back
		});
		return true;
	}

	/** A visual-only owned bolt at {@code spot}; null if a player is too close now, or capped. */
	public static @Nullable LightningBolt strike(ActiveEvent ev, Vec3 spot) {
		if (!allowed(ev, spot)) return null;
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(ev.level(), EntitySpawnReason.EVENT);
		if (bolt == null) return null;
		bolt.snapTo(spot.x, spot.y, spot.z);
		bolt.setVisualOnly(true); // no damage, no fire (see the class javadoc)
		if (ev.entities().spawn(bolt) == null) return null;
		ev.state(State::new).strikes.add(spot);
		return bolt;
	}

	/** Far enough from every affected player and no block a bolt would change. */
	private static boolean allowed(ActiveEvent ev, Vec3 spot) {
		for (ServerPlayer p : ev.players()) {
			double dx = p.getX() - spot.x;
			double dz = p.getZ() - spot.z;
			if (dx * dx + dz * dz < MIN_DISTANCE * MIN_DISTANCE) return false;
		}
		BlockState struck = ev.level().getBlockState(BlockPos.containing(spot.x, spot.y - 1.0E-6, spot.z));
		return !(struck.getBlock() instanceof WeatheringCopper)
				&& !HoneycombItem.WAX_OFF_BY_BLOCK.get().containsKey(struck.getBlock())
				&& !struck.is(BlockTags.LIGHTNING_RODS);
	}

	/** Positions struck so far (for tests). */
	public static List<Vec3> strikes(ActiveEvent ev) {
		return List.copyOf(ev.state(State::new).strikes);
	}

	/** Planned + struck (for tests). */
	public static int planned(ActiveEvent ev) {
		return ev.state(State::new).planned;
	}

	@Override
	public boolean hasWarning() {
		return true; // Warning.thenRun(...) before every hazard
	}

	@Override
	public Holder<SoundEvent> startSound() {
		return SoundEvents.TRIDENT_THUNDER;
	}

	@Override
	public float startSoundPitch() {
		return 1.2F;
	}

	@Override
	public float startSoundVolume() {
		return 0.6F;
	}
}
