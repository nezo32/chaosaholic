package dev.chaosaholic.net;

import dev.chaosaholic.Chaosaholic;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server to client: "event {@code id} started for you". Sent instead of the vanilla title / actionbar + sound
 * packets when the client has Chaosaholic, so the client announces it according to its own notification settings
 * (it knows the event's name, category and sound from its own registry and lang files).
 */
public record EventStartPayload(String id) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<EventStartPayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Chaosaholic.MOD_ID, "event_start"));
	public static final StreamCodec<RegistryFriendlyByteBuf, EventStartPayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.stringUtf8(64), EventStartPayload::id, EventStartPayload::new);

	@Override
	public Type<EventStartPayload> type() {
		return TYPE;
	}
}
