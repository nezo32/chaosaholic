package dev.chaosaholic.event.impl;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventContext;
import dev.chaosaholic.event.helper.Spots;
import dev.chaosaholic.event.helper.Warning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EntityBasedExplosionDamageCalculator;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Bad: after the warning (3 s, 4 s on Hardcore), lit TNT drops around the affected players for 15-20 s.
 *
 * <p>Every {@link #INTERVAL_TICKS} one TNT per affected player is planned on a safe spot {@link #MIN_DISTANCE} to
 * {@link #MAX_DISTANCE} blocks away (horizontally), marked with smoke for {@link #MARK_TICKS}, and spawned up to
 * {@link #DROP_HEIGHT} blocks above it (never through a roof). No spot is ever within {@link #MIN_DISTANCE} of any
 * player (bystanders who are not affected included, Spectators excepted); if one walked there meanwhile, that TNT is
 * skipped. At most {@link #MAX_TNT} per instance (extensions included); no new TNT
 * once it could no longer explode before the event ends.
 *
 * <p>The TNT is owned (never saved, discarded when the event ends for any reason, so nothing is ever left lit).
 * Vanilla TNT always breaks blocks; this event replaces the final detonation with its own explosion of power
 * {@link #POWER} using {@link Level.ExplosionInteraction#MOB}, which breaks blocks only when the {@code mobGriefing}
 * game rule is on, and nothing explodes when {@code tntExplodes} is off. Fuse {@link #FUSE_TICKS} (Hardcore
 * {@link #HARDCORE_FUSE_TICKS}), so every TNT gives several seconds to run. On Hardcore an explosion of this event
 * that would kill a player outright is cancelled, and the explosions never push players (no knockback off a ledge or
 * into lava).
 */
public final class TntRain extends ChaosEvent {
	/** Per instance, extensions included. */
	public static final int MAX_TNT = 12;
	/** Horizontal distance of a drop spot from every affected player (never onto a player). */
	public static final double MIN_DISTANCE = 4.0;
	public static final double MAX_DISTANCE = 10.0;
	/** Ticks between two drops (per affected player). */
	public static final int INTERVAL_TICKS = 20;
	/** Smoke marker on the spot before the TNT appears. */
	public static final int MARK_TICKS = 10;
	/** Spawn height above the spot (lower under a roof). */
	public static final int DROP_HEIGHT = 8;
	/** Fuse of each TNT (vanilla 80); Hardcore gets one more second. */
	public static final int FUSE_TICKS = 80;
	public static final int HARDCORE_FUSE_TICKS = 100;
	/** Explosion power (vanilla TNT 4.0). */
	public static final float POWER = 3.0F;
	/** Scoreboard tag on every TNT of this event (besides the framework's owned tag). */
	public static final String TNT_TAG = "chaosaholic_tnt_rain";

	public TntRain() {
		super("tnt_rain", Category.BAD, 15, 20);
	}

	/** Per-instance run state. */
	public static final class State {
		boolean raining;
		int rainStart;
		/** Planned + spawned TNT (the cap counts both). */
		int planned;
		int detonations;
		/** Every TNT this instance spawned (also after detonation: damage attribution). */
		final Set<UUID> tnt = new HashSet<>();
	}

	@Override
	public boolean canStart(EventContext ctx) {
		for (ServerPlayer p : ctx.players()) {
			if (Spots.near(ctx.level(), p.position(), MIN_DISTANCE, MAX_DISTANCE, ctx.random(), EntityTypes.TNT).isPresent()) return true;
		}
		return false;
	}

	@Override
	public void onStart(ActiveEvent ev) {
		State s = ev.state(State::new);
		Warning.thenRun(ev, () -> {
			s.raining = true;
			s.rainStart = ev.age();
		});
	}

	@Override
	public void onTick(ActiveEvent ev) {
		State s = ev.state(State::new);
		// Replace the vanilla detonation (it would happen during the TNT's next tick) by our own explosion.
		for (Entity e : ev.entities().list()) {
			if (e instanceof PrimedTnt tnt && tnt.getFuse() <= 1) {
				ev.entities().remove(tnt);
				detonate(ev.level(), tnt);
				s.detonations++;
			}
		}
		// No new TNT that could not explode before the end (it would just vanish with the event).
		if (s.raining && (ev.age() - s.rainStart) % INTERVAL_TICKS == 0 && ev.remainingTicks() > MARK_TICKS + fuse(ev)) {
			for (ServerPlayer p : ev.players()) plan(ev, p);
		}
	}

	/**
	 * Plans one TNT near {@code player}: smoke marker now, TNT {@link #MARK_TICKS} later. False when the cap is
	 * reached or no spot was found.
	 */
	public static boolean plan(ActiveEvent ev, ServerPlayer player) {
		State s = ev.state(State::new);
		if (s.planned >= MAX_TNT) return false;
		Optional<Vec3> found = Spots.near(ev.level(), player.position(), MIN_DISTANCE, MAX_DISTANCE, ev.random(), EntityTypes.TNT);
		if (found.isEmpty()) return false;
		Vec3 spot = found.get();
		if (!farFromPlayers(ev, spot)) return false;
		s.planned++;
		ev.level().sendParticles(ParticleTypes.SMOKE, spot.x, spot.y + 0.3, spot.z, 12, 0.3, 0.2, 0.3, 0.01);
		ev.schedule(MARK_TICKS, () -> {
			if (spawn(ev, spot) == null) s.planned--; // give the slot back
		});
		return true;
	}

	/** Spawns an owned lit TNT above {@code spot}; null if a player came too close, the column is blocked or capped. */
	public static @Nullable PrimedTnt spawn(ActiveEvent ev, Vec3 spot) {
		if (!farFromPlayers(ev, spot)) return null;
		ServerLevel level = ev.level();
		BlockPos base = BlockPos.containing(spot);
		if (!level.getBlockState(base).isAir()) return null;
		int height = 0;
		while (height < DROP_HEIGHT && level.getBlockState(base.above(height + 1)).isAir()) height++;
		PrimedTnt tnt = new PrimedTnt(level, spot.x, base.getY() + height, spot.z, null);
		tnt.setDeltaMovement(Vec3.ZERO);
		int fuse = fuse(ev);
		tnt.setFuse(fuse);
		tnt.setPortalCooldown(fuse + 20); // never carried to another dimension, where nobody would own it
		tnt.addTag(TNT_TAG);
		if (ev.entities().spawn(tnt) == null) return null;
		ev.state(State::new).tnt.add(tnt.getUUID());
		level.playSound(null, tnt.getX(), tnt.getY(), tnt.getZ(), SoundEvents.TNT_PRIMED, tnt.getSoundSource(), 1.0F, 1.0F);
		return tnt;
	}

	/**
	 * The event's explosion in place of the vanilla one: same position and damage source, power {@link #POWER},
	 * blocks broken only with mobGriefing, nothing at all with tntExplodes off. Discards the TNT.
	 */
	public static void detonate(ServerLevel level, PrimedTnt tnt) {
		tnt.discard();
		if (!level.getGameRules().get(GameRules.TNT_EXPLODES)) {
			level.sendParticles(ParticleTypes.POOF, tnt.getX(), tnt.getY() + 0.5, tnt.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
			return;
		}
		level.explode(tnt, Explosion.getDefaultDamageSource(level, tnt), damageCalculator(tnt, level.getServer().isHardcore()),
				tnt.getX(), tnt.getY(0.0625), tnt.getZ(), POWER, false, Level.ExplosionInteraction.MOB);
	}

	/**
	 * The vanilla calculator of a TNT explosion; on Hardcore without knockback for players (a cancelled lethal hit
	 * would otherwise still throw them, and knockback alone can throw a player off a ledge or into lava).
	 */
	public static ExplosionDamageCalculator damageCalculator(PrimedTnt tnt, boolean hardcore) {
		if (!hardcore) return new EntityBasedExplosionDamageCalculator(tnt);
		return new EntityBasedExplosionDamageCalculator(tnt) {
			@Override
			public float getKnockbackMultiplier(Entity entity) {
				return entity instanceof Player ? 0.0F : super.getKnockbackMultiplier(entity);
			}
		};
	}

	private static int fuse(ActiveEvent ev) {
		return ev.context().isHardcore() ? HARDCORE_FUSE_TICKS : FUSE_TICKS;
	}

	private static boolean farFromPlayers(ActiveEvent ev, Vec3 spot) {
		return farFromPlayers(ev.level(), spot, MIN_DISTANCE);
	}

	/**
	 * No player of {@code level} but Spectators (affected or not: a bystander never gets a hazard dropped on them)
	 * within {@code min} blocks of {@code spot}, horizontally.
	 */
	public static boolean farFromPlayers(ServerLevel level, Vec3 spot, double min) {
		for (Player p : level.players()) {
			if (p.isSpectator()) continue;
			double dx = p.getX() - spot.x;
			double dz = p.getZ() - spot.z;
			if (dx * dx + dz * dz < min * min) return false;
		}
		return true;
	}

	/** Planned + spawned TNT of the instance (for tests). */
	public static int planned(ActiveEvent ev) {
		return ev.state(State::new).planned;
	}

	/** Explosions done by the event itself (for tests). */
	public static int detonations(ActiveEvent ev) {
		return ev.state(State::new).detonations;
	}

	/** Hardcore: an explosion of this instance never kills a player outright (the lethal hit is cancelled). */
	@Override
	public boolean allowDamage(ActiveEvent ev, LivingEntity entity, DamageSource source, float amount) {
		return !ev.context().isHardcore() || !isLethalHit(ev, entity, source, amount);
	}

	/** A hit by an explosion of this instance that would kill a player (cancelled on Hardcore). */
	public static boolean isLethalHit(ActiveEvent ev, LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer)) return false;
		Entity direct = source.getDirectEntity();
		return direct != null && ev.state(State::new).tnt.contains(direct.getUUID()) && isLethal(entity, amount);
	}

	/** {@code amount} (before armor) would kill {@code entity}. */
	public static boolean isLethal(LivingEntity entity, float amount) {
		return amount >= entity.getHealth() + entity.getAbsorptionAmount();
	}

	@Override
	public boolean hasWarning() {
		return true; // Warning.thenRun(...) before every hazard
	}

	@Override
	public Holder<SoundEvent> startSound() {
		return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.TNT_PRIMED);
	}
}
