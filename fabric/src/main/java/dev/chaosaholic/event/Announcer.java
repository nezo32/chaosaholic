package dev.chaosaholic.event;

import dev.chaosaholic.Texts;
import dev.chaosaholic.core.ChaosLimits;
import dev.chaosaholic.event.helper.Sounds;
import dev.chaosaholic.net.EventStartPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

/**
 * Start announcement of an event, for one affected player. Bad events: title (name, bold, category colour) +
 * subtitle (announce line, gray); good and weird: actionbar "✦ name · announce" (design/presentation.md §2). Plus the
 * category sting and the event's own start sound.
 *
 * <p>Clients with Chaosaholic (they accept {@code chaosaholic:event_start}) get only an {@link EventStartPayload} and
 * announce it themselves according to their notification settings; vanilla clients get the packets from the server.
 */
public final class Announcer {
	private Announcer() {}

	public static void announce(ServerPlayer player, ChaosEvent event) {
		announce(player, event, ServerPlayNetworking.canSend(player, EventStartPayload.TYPE));
	}

	/** modded = the client has the chaosaholic:event_start channel. Public for gametests. */
	public static void announce(ServerPlayer player, ChaosEvent event, boolean modded) {
		if (modded) {
			player.connection.send(ServerPlayNetworking.createClientboundPacket(new EventStartPayload(event.id())));
			return;
		}
		if (event.category().usesTitle()) {
			player.connection.send(new ClientboundSetTitlesAnimationPacket(ChaosLimits.TITLE_FADE_IN, ChaosLimits.TITLE_STAY, ChaosLimits.TITLE_FADE_OUT));
			player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle(event)));
			player.connection.send(new ClientboundSetTitleTextPacket(title(event)));
		} else {
			player.sendOverlayMessage(actionbar(event));
		}
		Sounds.play(player, event.category().sting(), event.category().volume(), event.category().pitch());
		if (event.startSound() != null) Sounds.play(player, event.startSound(), event.startSoundVolume(), event.startSoundPitch());
	}

	/** Title line: the event name, bold, in the category colour. */
	public static Component title(ChaosEvent event) {
		return Texts.tr("chaosaholic.announce.title", Texts.name(event).withStyle(ChatFormatting.BOLD));
	}

	/** Subtitle line: the announce text, gray. */
	public static Component subtitle(ChaosEvent event) {
		return Texts.announce(event).withStyle(ChatFormatting.GRAY);
	}

	/** Actionbar line: "✦ name · announce". */
	public static Component actionbar(ChaosEvent event) {
		return Texts.tr("chaosaholic.announce.actionbar", Texts.name(event), Texts.announce(event).withStyle(ChatFormatting.GRAY));
	}
}
