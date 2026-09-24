package dev.chaosaholic.test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import dev.chaosaholic.event.Announcer;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.helper.Warning;
import dev.chaosaholic.net.ActiveEventsPayload;
import dev.chaosaholic.net.EventStartPayload;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;

/**
 * Announcements: vanilla clients get title / actionbar + sound packets from the server; clients with Chaosaholic get
 * only the {@code chaosaholic:event_start} payload. Warnings always go out as vanilla packets. Payload codecs.
 */
public class ChaosNotifyGameTests {
	/** A mock player whose outbound packets can be inspected. */
	private record Mock(ServerPlayer player, EmbeddedChannel channel) {
		List<Object> drain() {
			channel.runPendingTasks();
			List<Object> out = new ArrayList<>(channel.outboundMessages());
			channel.outboundMessages().clear();
			return out;
		}
	}

	private static Mock mockPlayer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "notify-mock"), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		EmbeddedChannel channel = new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		Mock mock = new Mock(player, channel);
		mock.drain(); // join packets
		return mock;
	}

	private static long count(List<Object> out, Class<?> type) {
		return out.stream().filter(type::isInstance).count();
	}

	private static boolean isStartPayload(Object msg) {
		return msg instanceof ClientboundCustomPayloadPacket p && p.payload() instanceof EventStartPayload;
	}

	@GameTest
	public void vanillaClientGetsActionbarForGoodEvents(GameTestHelper h) {
		Mock mock = mockPlayer(h);
		h.assertFalse(ServerPlayNetworking.canSend(mock.player(), EventStartPayload.TYPE), "mock has no chaosaholic channel");
		ChaosEvent speed = TestSupport.event("speed_demon");
		Announcer.announce(mock.player(), speed);
		List<Object> out = mock.drain();
		long overlays = out.stream().filter(m -> m instanceof ClientboundSystemChatPacket p && p.overlay()
				&& p.content().equals(Announcer.actionbar(speed))).count();
		h.assertValueEqual(overlays, 1L, "actionbar; outbound=" + out);
		h.assertValueEqual(count(out, ClientboundSoundPacket.class), 2L, "sting + event sound; outbound=" + out);
		h.assertValueEqual(count(out, ClientboundSetTitleTextPacket.class), 0L, "no title for good events");
		h.assertTrue(out.stream().noneMatch(ChaosNotifyGameTests::isStartPayload), "no payload");
		TestSupport.leave(h, mock.player());
		h.succeed();
	}

	@GameTest
	public void vanillaClientGetsTitleForBadEvents(GameTestHelper h) {
		Mock mock = mockPlayer(h);
		ChaosEvent bad = TestSupport.event("tnt_rain");
		Announcer.announce(mock.player(), bad, false);
		List<Object> out = mock.drain();
		h.assertValueEqual(count(out, ClientboundSetTitleTextPacket.class), 1L, "title; outbound=" + out);
		h.assertValueEqual(count(out, ClientboundSetSubtitleTextPacket.class), 1L, "subtitle");
		h.assertValueEqual(count(out, ClientboundSoundPacket.class), 1L, "category sting (no event sound yet)");
		TestSupport.leave(h, mock.player());
		h.succeed();
	}

	@GameTest
	public void moddedClientGetsPayloadOnly(GameTestHelper h) {
		Mock mock = mockPlayer(h);
		Announcer.announce(mock.player(), TestSupport.event("speed_demon"), true);
		List<Object> out = mock.drain();
		List<EventStartPayload> payloads = out.stream().filter(ChaosNotifyGameTests::isStartPayload)
				.map(m -> (EventStartPayload) ((ClientboundCustomPayloadPacket) m).payload()).toList();
		h.assertValueEqual(payloads.size(), 1, "payloads; outbound=" + out);
		h.assertValueEqual(payloads.getFirst().id(), "speed_demon", "payload id");
		h.assertValueEqual(count(out, ClientboundSoundPacket.class), 0L, "no sound packet");
		h.assertTrue(out.stream().noneMatch(m -> m instanceof ClientboundSystemChatPacket p && p.overlay()), "no actionbar");
		TestSupport.leave(h, mock.player());
		h.succeed();
	}

	/** Warnings are safety information: always vanilla actionbar + sound, whatever the client. */
	@GameTest
	public void warningAlwaysSent(GameTestHelper h) {
		Mock mock = mockPlayer(h);
		ChaosEvent tnt = TestSupport.event("tnt_rain");
		Warning.warn(mock.player(), tnt, 0);
		List<Object> out = mock.drain();
		h.assertTrue(out.stream().anyMatch(m -> m instanceof ClientboundSystemChatPacket p && p.overlay()
				&& p.content().getString().contains("TNT")), "warning actionbar (English fallback); outbound=" + out);
		h.assertValueEqual(count(out, ClientboundSoundPacket.class), 1L, "warning ping");
		TestSupport.leave(h, mock.player());
		h.succeed();
	}

	/** A running timed event sends boss bar packets to its players. */
	@GameTest
	public void bossBarPacketsSent(GameTestHelper h) {
		TestSupport.defaults(h);
		Mock mock = mockPlayer(h);
		TestSupport.manager(h).observe(mock.player(), true);
		TestSupport.start(h, "feather_fall", mock.player());
		List<Object> out = mock.drain();
		h.assertTrue(count(out, ClientboundBossEventPacket.class) >= 1, "boss bar add packet; outbound=" + out);
		TestSupport.cleanup(h, mock.player());
		h.succeed();
	}

	@GameTest
	public void payloadCodecsRoundTrip(GameTestHelper h) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
		try {
			EventStartPayload.CODEC.encode(buf, new EventStartPayload("tiny_world"));
			h.assertValueEqual(EventStartPayload.CODEC.decode(buf).id(), "tiny_world", "start id");
			ActiveEventsPayload sent = new ActiveEventsPayload(List.of(
					new ActiveEventsPayload.Entry("slippery", 400, 600), new ActiveEventsPayload.Entry("screen_shake", 20, 200)));
			ActiveEventsPayload.CODEC.encode(buf, sent);
			h.assertValueEqual(ActiveEventsPayload.CODEC.decode(buf), sent, "active list");
			h.assertValueEqual(buf.readableBytes(), 0, "bytes left unread");
		} finally {
			buf.release();
		}
		h.assertValueEqual(EventStartPayload.TYPE.id().toString(), "chaosaholic:event_start", "channel id");
		h.assertValueEqual(ActiveEventsPayload.TYPE.id().toString(), "chaosaholic:active_events", "channel id");
		h.succeed();
	}

	/** What a modded client would be synced: timed events affecting the player, with their timers. */
	@GameTest
	public void syncEntriesListTimedEvents(GameTestHelper h) {
		TestSupport.defaults(h);
		ServerPlayer p = TestSupport.survivalPlayer(h);
		TestSupport.start(h, "speed_demon", p);
		var entries = TestSupport.manager(h).entries(p);
		h.assertValueEqual(entries.size(), 1, "one entry");
		h.assertValueEqual(entries.getFirst().id(), "speed_demon", "id");
		h.assertTrue(entries.getFirst().remaining() > 0 && entries.getFirst().remaining() <= entries.getFirst().total(), "timer");
		TestSupport.cleanup(h, p);
		h.assertTrue(TestSupport.manager(h).entries(p).isEmpty(), "empty after cleanup");
		h.succeed();
	}
}
