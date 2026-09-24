package dev.chaosaholic.test.events;

import static dev.chaosaholic.test.TestSupport.cleanup;
import static dev.chaosaholic.test.TestSupport.defaults;
import static dev.chaosaholic.test.TestSupport.event;
import static dev.chaosaholic.test.TestSupport.leave;
import static dev.chaosaholic.test.TestSupport.manager;
import static dev.chaosaholic.test.TestSupport.server;
import static dev.chaosaholic.test.TestSupport.start;
import static dev.chaosaholic.test.TestSupport.survivalPlayer;

import java.util.List;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.EventContext;
import dev.chaosaholic.event.StopReason;
import dev.chaosaholic.event.impl.EternalNight;
import dev.chaosaholic.mode.Scope;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.WeatherData;

/**
 * eternal_night. The world clock is server-global and only one instance may hold it, so everything runs in ONE test
 * (parallel tests of this class would refuse each other): midnight at start, held against a sleep-like jump (and its weather reset),
 * extension by the same player, refusal for another player and in the Nether, restore on a forced stop and on
 * NO_PLAYERS (logout). It runs in its own batch (environment {@code chaosaholic-gametest:eternal_night}, an empty
 * all_of definition) so the night never reaches other tests.
 */
public class EternalNightGameTests {
	public static final String ISOLATED = "chaosaholic-gametest:eternal_night";

	private static long now(GameTestHelper h) {
		return h.getLevel().getDefaultClockTime();
	}

	private static long day(long total) {
		return Math.floorMod(total, EternalNight.DAY_LENGTH);
	}

	@GameTest(environment = ISOLATED, maxTicks = 300)
	public void holdsNightAndRestoresTheClock(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ServerPlayer other = survivalPlayer(h);
		long morning = now(h) - day(now(h)) + 1000;
		h.setTime(morning);
		ActiveEvent ev = start(h, "eternal_night", p);
		h.assertValueEqual(day(now(h)), EternalNight.MIDNIGHT, "midnight at once");
		h.assertTrue(start(h, "eternal_night", p) == ev, "same player: extended");
		h.assertTrue(manager(h).trigger(event("eternal_night"), other).isEmpty(), "clock already held: refused for another player");
		ServerLevel nether = server(h).getLevel(Level.NETHER);
		if (nether != null) {
			EventContext inNether = new EventContext(server(h), nether, p, List.of(p), p.getRandom(), Scope.PLAYER);
			h.assertFalse(event("eternal_night").canStart(inNether), "no clock in the Nether");
		}
		long[] held = new long[1];
		ActiveEvent[] second = new ActiveEvent[1];
		WeatherData weather = h.getLevel().getWeatherData();
		WeatherData before = new WeatherData(weather.getClearWeatherTime(), weather.getRainTime(), weather.getThunderTime(),
				weather.isRaining(), weather.isThundering());
		h.startSequence()
				.thenIdle(4)
				.thenExecute(() -> {
					weather.setRainTime(6000);
					weather.setRaining(true);
				})
				.thenIdle(1)
				.thenExecute(() -> {
					held[0] = now(h);
					h.setTime(held[0] + 6000); // what sleeping through the night does: jump to the morning, clear the weather
					h.getLevel().resetWeatherCycle();
				})
				.thenIdle(2)
				.thenExecute(() -> {
					long t = now(h);
					h.assertTrue(t >= held[0] - 2 && t <= held[0] + 5, "sleep skip undone: " + held[0] + " -> " + t);
					long d = day(t);
					h.assertTrue(d >= EternalNight.NIGHT_START && d < EternalNight.NIGHT_END, "still night: " + d);
					h.assertTrue(weather.isRaining() && weather.getRainTime() > 5900, "the rain the skip cleared is back: " + weather.getRainTime());
					weather.setClearWeatherTime(before.getClearWeatherTime());
					weather.setRainTime(before.getRainTime());
					weather.setThunderTime(before.getThunderTime());
					weather.setRaining(before.isRaining());
					weather.setThundering(before.isThundering());
				})
				.thenIdle(20)
				.thenExecute(() -> {
					long expected = EternalNight.restoreTime(ev);
					h.assertTrue(expected >= morning && expected <= morning + 40, "only natural time counts: " + (expected - morning));
					manager(h).stop(ev, StopReason.FORCED);
					h.assertValueEqual(now(h), expected, "clock restored on stop");
					h.assertTrue(manager(h).trigger(event("eternal_night"), other).isPresent(), "free again after the end");
				})
				.thenExecute(() -> {
					second[0] = manager(h).activeFor(other).stream().filter(e -> e.id().equals("eternal_night")).findFirst().orElseThrow();
					h.assertValueEqual(day(now(h)), EternalNight.MIDNIGHT, "second night");
					held[0] = EternalNight.restoreTime(second[0]);
					leave(h, other);
					h.assertFalse(second[0].isStopped(), "ends on the next tick (NO_PLAYERS)");
				})
				.thenWaitUntil(() -> h.assertTrue(second[0].isStopped(), "ended"))
				.thenExecute(() -> {
					h.assertTrue(now(h) >= held[0] && now(h) <= held[0] + 3, "restored on NO_PLAYERS");
					h.assertTrue(day(now(h)) < EternalNight.NIGHT_START, "morning again: " + day(now(h)));
					cleanup(h, p, other);
				})
				.thenSucceed();
	}
}
