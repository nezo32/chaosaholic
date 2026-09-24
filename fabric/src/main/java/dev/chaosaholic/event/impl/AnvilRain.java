package dev.chaosaholic.event.impl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventContext;
import dev.chaosaholic.event.helper.Spots;
import dev.chaosaholic.event.helper.Warning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Bad: after the warning (3 s, 4 s on Hardcore), anvils crash down on marked spots near the affected players for
 * 15-20 s.
 *
 * <p>Every {@link #INTERVAL_TICKS} one target per affected player is picked on a safe spot {@link #MIN_DISTANCE} to
 * {@link #MAX_DISTANCE} blocks away (never where a player stands) and marked with a red dust column. The anvil is
 * released {@link #LEAD_TICKS} later from up to {@link #DROP_HEIGHT} blocks above the spot (lower under a roof) and
 * the mark stays until it lands. Marking starts during the warning, so the first marks are visible before anything
 * falls, and the first anvil is released exactly when the warning ends. At most {@link #MAX_ANVILS} per instance.
 *
 * <p>Anvils never become blocks ({@code disableDrop}, no item drop): on impact they break with the vanilla "anvil
 * destroyed" sound. Damage is {@link #DAMAGE_PER_BLOCK} per block fallen, capped at {@link #MAX_DAMAGE} (vanilla 2 /
 * 40), so one anvil cannot kill a player at full health; on Hardcore an anvil hit that would kill is cancelled. The
 * falling anvils are owned entities: discarded when the event ends for any reason, never saved.
 */
public final class AnvilRain extends ChaosEvent {
	/** Per instance, extensions included. */
	public static final int MAX_ANVILS = 12;
	/** Horizontal distance of a target from every affected player when it is picked. */
	public static final double MIN_DISTANCE = 2.0;
	public static final double MAX_DISTANCE = 8.0;
	/** Ticks between two targets (per affected player). */
	public static final int INTERVAL_TICKS = 20;
	/** Mark time before the anvil is released (it then falls for about another second). */
	public static final int LEAD_TICKS = 30;
	/** Release height above the target (lower under a roof). */
	public static final int DROP_HEIGHT = 10;
	/** Damage per block fallen and its cap (vanilla anvils: 2.0 / 40). */
	public static final float DAMAGE_PER_BLOCK = 1.0F;
	public static final int MAX_DAMAGE = 6;
	/** Time an anvil may need to land; no new target closer than LEAD_TICKS + this to the end. */
	public static final int FALL_TICKS = 30;
	/** Marker refresh period. */
	public static final int MARKER_PERIOD = 4;
	/** Red dust of the target marks. */
	public static final DustParticleOptions MARKER = new DustParticleOptions(0xE01010, 1.0F);

	public AnvilRain() {
		super("anvil_rain", Category.BAD, 15, 20);
	}

	/** One marked spot: its anvil is released at {@code dropAt}. */
	public static final class Target {
		public final BlockPos pos;
		public final int dropAt;
		@Nullable FallingBlockEntity anvil;
		boolean done;

		Target(BlockPos pos, int dropAt) {
			this.pos = pos;
			this.dropAt = dropAt;
		}

		public @Nullable FallingBlockEntity anvil() {
			return anvil;
		}
	}

	/** Per-instance run state. */
	public static final class State {
		boolean marking;
		int markStart;
		boolean dropping;
		int planned;
		final List<Target> targets = new ArrayList<>();
	}

	@Override
	public boolean canStart(EventContext ctx) {
		for (ServerPlayer p : ctx.players()) {
			if (Spots.near(ctx.level(), p.position(), MIN_DISTANCE, MAX_DISTANCE, ctx.random(), EntityTypes.FALLING_BLOCK).isPresent()) return true;
		}
		return false;
	}

	@Override
	public void onStart(ActiveEvent ev) {
		State s = ev.state(State::new);
		// The first marks appear during the warning; their anvils are released when it ends.
		ev.schedule(Warning.delay(ev.context()) - LEAD_TICKS, () -> {
			s.marking = true;
			s.markStart = ev.age();
		});
		Warning.thenRun(ev, () -> s.dropping = true);
	}

	@Override
	public void onTick(ActiveEvent ev) {
		State s = ev.state(State::new);
		if (s.marking && (ev.age() - s.markStart) % INTERVAL_TICKS == 0 && ev.remainingTicks() > LEAD_TICKS + FALL_TICKS) {
			for (ServerPlayer p : ev.players()) plan(ev, p);
		}
		for (Iterator<Target> it = s.targets.iterator(); it.hasNext(); ) {
			Target t = it.next();
			if (t.anvil == null && s.dropping && ev.age() >= t.dropAt) {
				t.anvil = drop(ev, t.pos);
				if (t.anvil == null) t.done = true;
			} else if (t.anvil != null && t.anvil.isRemoved()) {
				t.done = true; // landed (and broke), timed out or unloaded
			}
			if (t.done) it.remove();
		}
		if (ev.every(MARKER_PERIOD)) {
			for (Target t : s.targets) mark(ev.level(), t.pos);
		}
	}

	/** Picks and marks one target near {@code player}. False when the cap is reached or no spot was found. */
	public static boolean plan(ActiveEvent ev, ServerPlayer player) {
		State s = ev.state(State::new);
		if (s.planned >= MAX_ANVILS) return false;
		Optional<Vec3> found = Spots.near(ev.level(), player.position(), MIN_DISTANCE, MAX_DISTANCE, ev.random(), EntityTypes.FALLING_BLOCK);
		if (found.isEmpty()) return false;
		BlockPos pos = BlockPos.containing(found.get());
		for (ServerPlayer p : ev.players()) {
			double dx = p.getX() - (pos.getX() + 0.5);
			double dz = p.getZ() - (pos.getZ() + 0.5);
			if (dx * dx + dz * dz < MIN_DISTANCE * MIN_DISTANCE) return false;
		}
		for (Target t : s.targets) if (t.pos.equals(pos)) return false;
		s.planned++;
		s.targets.add(new Target(pos, ev.age() + LEAD_TICKS));
		mark(ev.level(), pos);
		return true;
	}

	/**
	 * Releases an owned anvil above {@code target} (which must still be air). Null when blocked or capped. The
	 * falling anvil never places a block and hurts at most {@link #MAX_DAMAGE}.
	 */
	public static @Nullable FallingBlockEntity drop(ActiveEvent ev, BlockPos target) {
		ServerLevel level = ev.level();
		if (!ev.entities().canSpawn() || !level.getBlockState(target).isAir()) return null;
		int height = 0;
		while (height < DROP_HEIGHT && level.getBlockState(target.above(height + 1)).isAir()) height++;
		// fall() replaces the block at its position with air: only ever called on an air block
		FallingBlockEntity anvil = FallingBlockEntity.fall(level, target.above(height), Blocks.ANVIL.defaultBlockState());
		anvil.setHurtsEntities(DAMAGE_PER_BLOCK, MAX_DAMAGE);
		anvil.dropItem = false;
		anvil.disableDrop();
		if (!ev.entities().adopt(anvil)) {
			anvil.discard();
			return null;
		}
		return anvil;
	}

	private static void mark(ServerLevel level, BlockPos pos) {
		for (int i = 0; i < 4; i++) {
			level.sendParticles(MARKER, pos.getX() + 0.5, pos.getY() + 0.1 + i * 0.5, pos.getZ() + 0.5, 2, 0.12, 0.05, 0.12, 0.0);
		}
	}

	/** Targets still marked or falling (for tests). */
	public static List<Target> targets(ActiveEvent ev) {
		return List.copyOf(ev.state(State::new).targets);
	}

	/** Targets picked so far, the cap counts them (for tests). */
	public static int planned(ActiveEvent ev) {
		return ev.state(State::new).planned;
	}

	/** Hardcore: an anvil of this instance never kills a player outright (the lethal hit is cancelled). */
	@Override
	public boolean allowDamage(ActiveEvent ev, LivingEntity entity, DamageSource source, float amount) {
		if (!ev.context().isHardcore() || !(entity instanceof ServerPlayer)) return true;
		Entity direct = source.getDirectEntity();
		if (!(direct instanceof FallingBlockEntity) || !ev.entities().owns(direct)) return true;
		return !TntRain.isLethal(entity, amount);
	}

	@Override
	public boolean hasWarning() {
		return true; // Warning.thenRun(...) before every hazard
	}

	@Override
	public Holder<SoundEvent> startSound() {
		return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.ANVIL_LAND);
	}

	@Override
	public float startSoundPitch() {
		return 1.4F;
	}

	@Override
	public float startSoundVolume() {
		return 0.5F;
	}
}
