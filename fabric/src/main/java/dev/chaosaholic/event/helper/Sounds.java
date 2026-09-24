package dev.chaosaholic.event.helper;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Sounds played to one player only (not broadcast), at the player's position. */
public final class Sounds {
	private Sounds() {}

	public static void play(ServerPlayer player, Holder<SoundEvent> sound, float volume, float pitch) {
		player.connection.send(new ClientboundSoundPacket(sound, SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(),
				volume, pitch, player.getRandom().nextLong()));
	}

	public static void play(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
		play(player, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), volume, pitch);
	}
}
