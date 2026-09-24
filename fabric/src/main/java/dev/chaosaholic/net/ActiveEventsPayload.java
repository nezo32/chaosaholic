package dev.chaosaholic.net;

import java.util.List;

import dev.chaosaholic.Chaosaholic;
import dev.chaosaholic.core.ChaosLimits;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server to client: the complete list of timed events currently affecting this player, re-sent whenever it changes
 * (start, extension, end, removal). The client counts {@code remaining} down by itself between updates. Client-side
 * event effects (screen shake, slippery, bouncy, ...) read it through ClientEventState.
 */
public record ActiveEventsPayload(List<Entry> events) implements CustomPacketPayload {
	public record Entry(String id, int remaining, int total) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.composite(
				ByteBufCodecs.stringUtf8(64), Entry::id,
				ByteBufCodecs.VAR_INT, Entry::remaining,
				ByteBufCodecs.VAR_INT, Entry::total,
				Entry::new);
	}

	public static final CustomPacketPayload.Type<ActiveEventsPayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Chaosaholic.MOD_ID, "active_events"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ActiveEventsPayload> CODEC = StreamCodec.composite(
			Entry.CODEC.apply(ByteBufCodecs.list(4 * ChaosLimits.MAX_ACTIVE_PER_PLAYER)), ActiveEventsPayload::events,
			ActiveEventsPayload::new);

	@Override
	public Type<ActiveEventsPayload> type() {
		return TYPE;
	}
}
