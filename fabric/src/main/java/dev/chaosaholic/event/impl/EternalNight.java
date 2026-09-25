package dev.chaosaholic.event.impl;

import java.util.Optional;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventContext;
import dev.chaosaholic.event.EventManager;
import dev.chaosaholic.event.StopReason;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.saveddata.WeatherData;
import org.jspecify.annotations.Nullable;

/**
 * Bad: midnight at once, held for 60-120 s, then the clock resumes as if nothing happened. 26.x has world clocks
 * instead of a day time: the dimension's default clock (the overworld clock; the Nether and the End have none, so the
 * event refuses there) is set to midnight of the current day, kept inside [{@link #NIGHT_START}, {@link #NIGHT_END})
 * every tick, and at the end set to {@code original + ticks that passed naturally} (daylight cycle on: the day moved
 * on normally; daylight cycle off: exactly the original time).
 *
 * <p>World-level side effect with a single owner: while an instance holds a clock, another instance on that clock
 * cannot start ({@link #canStart} false) unless every one of its players is already in the running instance, in
 * which case the framework extends it (stacking extends). Restored on every stop reason: expiry, forced stop,
 * /chaosaholic off, the last player leaving (NO_PLAYERS), errors and server stop (before the final save).
 * After a crash mid-event the clock simply continues from the night.
 *
 * <p>Sleeping cannot skip it: when enough players sleep, vanilla wakes them and jumps to the morning; the jump is
 * undone on the same tick (as is any other external jump, e.g. /time set) and does not count as passed time. The rain
 * or thunder that the skipped night would have cleared comes back too (vanilla clears the weather with the jump). Night
 * mob spawning is plain vanilla (no extra wave).
 */
public final class EternalNight extends ChaosEvent {
	public static final long DAY_LENGTH = 24000L;
	/** Clock time of day the night is set to. */
	public static final long MIDNIGHT = 18000L;
	/** Night range kept while active (13000 = dusk is over; back to midnight at 22500, before dawn begins). */
	public static final long NIGHT_START = 13000L;
	public static final long NIGHT_END = 22500L;
	/** A clock step up to this many ticks between two of our ticks counts as natural (clock rate changes included). */
	private static final long NATURAL_STEP_MAX = 20L;

	/**
	 * Per instance: the clock held, its time before the event, time that passed naturally, the last value seen, and
	 * the weather at the end of the previous tick (restored when a sleep skip cleared it).
	 */
	private static final class State {
		private @Nullable Holder<WorldClock> clock;
		private long before;
		private long natural;
		private long last;
		private @Nullable WeatherData weather;
	}

	public EternalNight() {
		super("eternal_night", Category.BAD, 60, 120);
	}

	/** The default clock of {@code level} (empty in the Nether / End). */
	public static Optional<Holder<WorldClock>> clock(ServerLevel level) {
		return level.dimensionType().defaultClock();
	}

	@Override
	public boolean canStart(EventContext ctx) {
		Optional<Holder<WorldClock>> clock = clock(ctx.level());
		if (clock.isEmpty()) return false;
		for (ActiveEvent other : EventManager.get(ctx.server()).activeEvents()) {
			if (other.event() != this || other.isStopped() || !clock(other.level()).equals(clock)) continue;
			// the clock is already held: only a pure extension of that instance may go through
			for (ServerPlayer p : ctx.players()) if (!other.isAffected(p)) return false;
		}
		return true;
	}

	@Override
	public void onStart(ActiveEvent ev) {
		Optional<Holder<WorldClock>> clock = clock(ev.level());
		if (clock.isEmpty()) return;
		State s = ev.state(State::new);
		long now = ev.level().getDefaultClockTime();
		s.clock = clock.get();
		s.before = now;
		s.last = set(ev, s, now - Math.floorMod(now, DAY_LENGTH) + MIDNIGHT);
	}

	@Override
	public void onPlayerAdded(ActiveEvent ev, ServerPlayer player) {
		ev.level().sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.0, player.getZ(), 10, 0.6, 0.6, 0.6, 0.05);
	}

	@Override
	public void onTick(ActiveEvent ev) {
		State s = ev.state(State::new);
		if (s.clock == null) return;
		long now = ev.level().getDefaultClockTime();
		long delta = now - s.last;
		if (delta >= 0 && delta <= NATURAL_STEP_MAX) {
			s.natural += delta;
		} else {
			now = set(ev, s, s.last); // sleeping through the night, /time set, ...: undone
			restoreWeatherAfterSleep(ev.level(), s.weather);
		}
		long day = Math.floorMod(now, DAY_LENGTH);
		if (day < NIGHT_START || day >= NIGHT_END) now = set(ev, s, now - day + MIDNIGHT); // dawn is near: back to midnight
		s.last = now;
		s.weather = copy(ev.level().getWeatherData());
	}

	/**
	 * Vanilla clears rain and thunder when the players sleep through the night (ServerLevel#resetWeatherCycle, with
	 * advanceWeather on). If the weather now looks exactly like that reset and it rained a tick ago, the previous
	 * weather comes back.
	 */
	private static void restoreWeatherAfterSleep(ServerLevel level, @Nullable WeatherData before) {
		WeatherData now = level.getWeatherData();
		if (before == null || !before.isRaining()) return;
		boolean reset = !now.isRaining() && !now.isThundering() && now.getRainTime() == 0 && now.getThunderTime() == 0;
		if (!reset) return;
		now.setClearWeatherTime(before.getClearWeatherTime());
		now.setRainTime(before.getRainTime());
		now.setThunderTime(before.getThunderTime());
		now.setRaining(true);
		now.setThundering(before.isThundering());
	}

	private static WeatherData copy(WeatherData w) {
		return new WeatherData(w.getClearWeatherTime(), w.getRainTime(), w.getThunderTime(), w.isRaining(), w.isThundering());
	}

	@Override
	public void onStop(ActiveEvent ev, StopReason reason) {
		State s = ev.state(State::new);
		if (s.clock == null) return;
		set(ev, s, s.before + s.natural);
		s.clock = null;
	}

	private static long set(ActiveEvent ev, State s, long total) {
		if (s.clock != null) ev.level().clockManager().setTotalTicks(s.clock, total);
		return total;
	}

	/** Clock time the instance will restore at its end (for tests). */
	public static long restoreTime(ActiveEvent ev) {
		State s = ev.state(State::new);
		return s.before + s.natural;
	}

	@Override
	public Holder<SoundEvent> startSound() {
		return SoundEvents.AMBIENT_CAVE;
	}

	@Override
	public float startSoundVolume() {
		return 0.6F;
	}
}
