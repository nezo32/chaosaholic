package dev.chaosaholic.client;

import dev.chaosaholic.core.ChaosLimits;
import dev.chaosaholic.core.NotifySettings;
import dev.chaosaholic.event.Announcer;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventRegistry;
import dev.chaosaholic.net.EventStartPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;

/**
 * Receives {@link EventStartPayload} and announces the event (title / actionbar, category sting, event sound)
 * according to {@link NotifyConfig}. Same look as the server's vanilla fallback ({@link Announcer}).
 */
public final class NotifyClient {
	private NotifyClient() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(EventStartPayload.TYPE, (payload, ctx) -> handle(payload.id(), ctx.player()));
	}

	/** Shows / plays according to NotifyConfig.get(). Public so the client gametest can call it directly. */
	public static void handle(String id, LocalPlayer player) {
		ChaosEvent event = EventRegistry.get(id);
		if (player == null || event == null) return;
		NotifySettings settings = NotifyConfig.get();
		if (settings.message()) {
			if (event.category().usesTitle()) {
				var hud = Minecraft.getInstance().gui.hud;
				hud.setTimes(ChaosLimits.TITLE_FADE_IN, ChaosLimits.TITLE_STAY, ChaosLimits.TITLE_FADE_OUT);
				hud.setSubtitle(Announcer.subtitle(event));
				hud.setTitle(Announcer.title(event));
			} else {
				player.sendOverlayMessage(Announcer.actionbar(event));
			}
		}
		if (settings.sound()) {
			player.level().playLocalSound(player.getX(), player.getY(), player.getZ(), event.category().sting().value(),
					SoundSource.PLAYERS, event.category().volume(), event.category().pitch(), false);
			if (event.startSound() != null) {
				player.level().playLocalSound(player.getX(), player.getY(), player.getZ(), event.startSound().value(),
						SoundSource.PLAYERS, event.startSoundVolume(), event.startSoundPitch(), false);
			}
		}
	}
}
