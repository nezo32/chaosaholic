package dev.chaosaholic.event.impl;

import java.util.ArrayList;
import java.util.List;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventManager;
import dev.chaosaholic.event.helper.Area;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Weird: for 30-60 s the affected players and the living entities around them (radius {@link #RADIUS}) glow, i.e.
 * are outlined through walls. The area is scanned again every {@link #SCAN_PERIOD} ticks so newcomers light up too,
 * up to {@link #MAX_GLOWING} entities per instance (affected players not counted).
 *
 * <p>Everything goes through the effect tracker: removed at the end, per affected player on removal, never longer
 * than the run on unloaded entities. Entities that already glow from something else (spectral arrow) are skipped so
 * their own glow is never taken away. Other nearby players glow only while eligible: Creative / Spectator players
 * are never picked, and a picked one who switches loses the glow at the next scan.
 */
public final class GlowParty extends ChaosEvent {
	public static final double RADIUS = 16.0;
	/** Entities (besides the affected players) one instance may make glow over its whole run. */
	public static final int MAX_GLOWING = 48;
	/** Area re-scan period (1 s). */
	public static final int SCAN_PERIOD = 20;

	/** Per-instance run data. */
	private static final class State {
		/** Entities given Glowing so far (the cap counts them even after they died or unloaded). */
		private int given;
		/** Non-affected players we made glow: re-checked for eligibility on every scan. */
		private final List<ServerPlayer> others = new ArrayList<>();
	}

	public GlowParty() {
		super("glow_party", Category.WEIRD, 30, 60);
	}

	@Override
	public void onPlayerAdded(ActiveEvent ev, ServerPlayer player) {
		ev.effects().give(player, MobEffects.GLOWING, 0);
		glowAround(ev, player);
		ev.level().sendParticles(ParticleTypes.GLOW, player.getX(), player.getY() + 1, player.getZ(), 12, 0.6, 0.6, 0.6, 0.05);
	}

	@Override
	public void onTick(ActiveEvent ev) {
		if (ev.age() == 0 || !ev.every(SCAN_PERIOD)) return;
		State s = ev.state(State::new);
		s.others.removeIf(o -> {
			if (ev.isAffected(o)) return true; // joined in the same start (world scope): the framework owns it now
			if (EventManager.isEligible(o) && o.level() == ev.level()) return false;
			ev.effects().revert(o);
			return true;
		});
		for (ServerPlayer p : ev.players()) glowAround(ev, p);
	}

	/** Gives Glowing to new living entities around {@code player}, nearest first, within the instance cap. */
	private static void glowAround(ActiveEvent ev, ServerPlayer player) {
		State s = ev.state(State::new);
		int room = MAX_GLOWING - s.given;
		if (room <= 0) return;
		List<LivingEntity> found = Area.entities(ev.level(), player.position(), RADIUS, LivingEntity.class,
				e -> isCandidate(ev, e), room);
		for (LivingEntity e : found) {
			if (!ev.effects().give(e, MobEffects.GLOWING, 0)) continue;
			s.given++;
			if (e instanceof ServerPlayer other) s.others.add(other);
		}
	}

	private static boolean isCandidate(ActiveEvent ev, LivingEntity e) {
		if (ev.isAffected(e) || ev.effects().tracks(e) || e.hasEffect(MobEffects.GLOWING)) return false;
		return !(e instanceof ServerPlayer p) || EventManager.isEligible(p);
	}

	@Override
	public Holder<SoundEvent> startSound() {
		return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.GLOW_SQUID_AMBIENT);
	}

	@Override
	public float startSoundPitch() {
		return 1.2F;
	}
}
