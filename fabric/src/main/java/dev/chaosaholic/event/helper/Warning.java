package dev.chaosaholic.event.helper;

import dev.chaosaholic.Texts;
import dev.chaosaholic.core.ChaosLimits;
import dev.chaosaholic.core.EventIds;
import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * Danger warning for bad events (Hardcore rule: no hazard without at least {@link ChaosLimits#WARNING_TICKS} of
 * warning). Actionbar "⚠ &lt;chaosaholic.event.&lt;id&gt;.warning&gt;" in gold plus a rising NOTE_BLOCK_PLING, once per
 * second (design/presentation.md §4). Always sent as vanilla packets, so it shows and plays even when the player
 * turned event messages / sounds off: it is safety information. The event must override {@code hasWarning()}.
 */
public final class Warning {
	private Warning() {}

	/** Warning time for this context: 3 s, 4 s on Hardcore. */
	public static int delay(EventContext ctx) {
		return ctx.isHardcore() ? ChaosLimits.WARNING_TICKS + ChaosLimits.TICKS_PER_SECOND : ChaosLimits.WARNING_TICKS;
	}

	/**
	 * Warns every affected player on the next tick and each second after, then runs {@code hazard} exactly
	 * {@link #delay} ticks after the first ping (in the instance's tick; nothing runs if the instance ended first).
	 * Call from {@code onStart} (players are added right after, so the first ping goes out on the next tick) or from
	 * {@code onTick}.
	 */
	public static void thenRun(ActiveEvent ev, Runnable hazard) {
		int delay = delay(ev.context());
		int steps = delay / ChaosLimits.TICKS_PER_SECOND;
		int first = 1;
		for (int i = 0; i < steps; i++) {
			int step = i;
			ev.schedule(first + i * ChaosLimits.TICKS_PER_SECOND, () -> {
				for (ServerPlayer p : ev.players()) warn(p, ev.event(), step);
			});
		}
		ev.schedule(first + delay, hazard);
	}

	/** One warning ping for {@code player}; step 0, 1, 2 ... raises the pitch. */
	public static void warn(ServerPlayer player, ChaosEvent event, int step) {
		player.sendOverlayMessage(message(event));
		Sounds.play(player, SoundEvents.NOTE_BLOCK_PLING, 1.0F, Math.min(2.0F, 0.7F + 0.1F * step));
	}

	public static Component message(ChaosEvent event) {
		return Texts.tr("chaosaholic.warning.format", Texts.tr(EventIds.warningKey(event.id()))).withStyle(ChatFormatting.GOLD);
	}
}
