package dev.chaosaholic.event.impl.mob;

import dev.chaosaholic.Texts;
import dev.chaosaholic.core.ChaosLimits;
import dev.chaosaholic.core.EventIds;
import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.helper.Sounds;
import dev.chaosaholic.event.helper.Warning;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * The Hardcore warning for the mob events (mob_surprise, bee_swarm): the same timing, actionbar style and rising
 * NOTE_BLOCK_PLING as {@link Warning#thenRun}, but the actionbar shows "⚠ &lt;event name&gt;" because these events have
 * no {@code .warning} lang key (their hasWarning() stays false so the lang parity test holds). Always sent as vanilla
 * packets, like the framework warning: it is safety information.
 */
public final class MobWarning {
	private MobWarning() {}

	/**
	 * Warns every affected player now and each second, then runs {@code hazard} after {@link Warning#delay} (3 s, 4 s
	 * on Hardcore). Nothing runs if the instance ended first (the schedule is dropped).
	 */
	public static void thenRun(ActiveEvent ev, Runnable hazard) {
		int delay = Warning.delay(ev.context());
		int steps = delay / ChaosLimits.TICKS_PER_SECOND;
		for (int i = 0; i < steps; i++) {
			int step = i;
			ev.schedule(i * ChaosLimits.TICKS_PER_SECOND + (i == 0 ? 1 : 0), () -> {
				for (ServerPlayer p : ev.players()) warn(p, ev.event(), step);
			});
		}
		ev.schedule(delay, hazard);
	}

	/** One ping: actionbar "⚠ name" in gold and a pling whose pitch rises with {@code step}. */
	public static void warn(ServerPlayer player, ChaosEvent event, int step) {
		player.sendOverlayMessage(message(event));
		Sounds.play(player, SoundEvents.NOTE_BLOCK_PLING, 1.0F, Math.min(2.0F, 0.7F + 0.1F * step));
	}

	public static Component message(ChaosEvent event) {
		return Texts.tr("chaosaholic.warning.format", Texts.tr(EventIds.nameKey(event.id()))).withStyle(ChatFormatting.GOLD);
	}
}
