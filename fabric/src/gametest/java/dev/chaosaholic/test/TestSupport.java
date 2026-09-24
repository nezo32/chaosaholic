package dev.chaosaholic.test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventManager;
import dev.chaosaholic.event.EventRegistry;
import dev.chaosaholic.event.StopReason;
import dev.chaosaholic.mode.ChaosSettings;
import dev.chaosaholic.mode.Scope;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Shared helpers for the server gametests (framework and event tests).
 *
 * <p>Shared-state rule: the world settings (mode, scope, per-event switches and weights) are server-global and
 * gametests run in parallel. Every test calls {@link #defaults} first. The defaults are: mode ON, scope player, and
 * only {@link #SAFE_EVENTS} enabled for random rolls (so level-up tests never roll TNT next to another test's
 * structure). A test that needs other settings changes them, does synchronous work only, and calls
 * {@link #defaults} again before returning. {@code EventManager#trigger} ignores switches and weights, so event
 * tests do not depend on these settings at all.
 */
public final class TestSupport {
	/** The only events random rolls may pick in gametests: harmless, player-only, no world changes. */
	public static final Set<String> SAFE_EVENTS = Set.of("speed_demon", "feather_fall");

	private TestSupport() {}

	public static MinecraftServer server(GameTestHelper h) {
		return h.getLevel().getServer();
	}

	public static EventManager manager(GameTestHelper h) {
		return EventManager.get(server(h));
	}

	public static ChaosSettings settings(GameTestHelper h) {
		return ChaosSettings.get(server(h));
	}

	/** Mode ON, scope player, all per-event overrides dropped, then everything but SAFE_EVENTS switched off. */
	public static void defaults(GameTestHelper h) {
		ChaosSettings s = settings(h);
		s.setEnabled(true);
		s.setScope(Scope.PLAYER);
		s.resetEvents();
		for (ChaosEvent e : EventRegistry.all()) {
			if (!SAFE_EVENTS.contains(e.id())) s.setEventEnabled(e, false);
		}
	}

	public static ChaosEvent event(String id) {
		ChaosEvent e = EventRegistry.get(id);
		if (e == null) throw new IllegalStateException("no event " + id);
		return e;
	}

	/**
	 * A mock server player in the test level, in survival, at the test's relative position (1, 2, 1).
	 *
	 * <p>Same as {@link GameTestHelper#makeMockServerPlayerInLevel()}, except that the vanilla helper's player
	 * overrides {@code gameMode()} to always return CREATIVE. Mock players never tick and are invulnerable (their
	 * client never "loads"): test damage through the ALLOW_DAMAGE event, not hurtServer.
	 */
	public static ServerPlayer survivalPlayer(GameTestHelper h) {
		return survivalPlayer(h, new Vec3(1.5, 2, 1.5));
	}

	public static ServerPlayer survivalPlayer(GameTestHelper h, Vec3 relative) {
		ServerLevel level = h.getLevel();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "cm" + UUID.randomUUID().toString().substring(0, 8)), false);
		ServerPlayer p = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, p, cookie);
		p.setGameMode(GameType.SURVIVAL);
		p.getInventory().clearContent();
		Vec3 pos = h.absoluteVec(relative);
		p.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
		manager(h).observe(p, settings(h).enabled()); // first observation: the anti-farming mark = current level
		return p;
	}

	public static ServerPlayer player(GameTestHelper h, GameType mode) {
		ServerPlayer p = survivalPlayer(h);
		p.setGameMode(mode);
		return p;
	}

	/** Logs the mock player out the normal way (PlayerList#remove fires ServerPlayerEvents.LEAVE). */
	public static void leave(GameTestHelper h, ServerPlayer p) {
		if (!p.isRemoved()) server(h).getPlayerList().remove(p);
	}

	/** Starts {@code id} on {@code p} with the forced path (/chaosaholic trigger) and fails the test if it did not start. */
	public static ActiveEvent start(GameTestHelper h, String id, ServerPlayer p) {
		return manager(h).trigger(event(id), p).orElseThrow(() -> new AssertionError(id + " did not start for " + p));
	}

	/** Ends every event of {@code p} and logs it out. Call at the end of every test that created a player. */
	public static void cleanup(GameTestHelper h, ServerPlayer... players) {
		for (ServerPlayer p : players) {
			for (ActiveEvent ev : manager(h).activeFor(p)) manager(h).stop(ev, StopReason.FORCED);
			leave(h, p);
		}
	}

	/** A still mob (no AI) at a relative position. */
	public static <T extends Mob> T mob(GameTestHelper h, EntityType<T> type, Vec3 relative) {
		T mob = h.spawnWithNoFreeWill(type, BlockPos.containing(relative));
		Vec3 pos = h.absoluteVec(relative);
		mob.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
		mob.setPersistenceRequired();
		return mob;
	}

	public static double scale(net.minecraft.world.entity.LivingEntity e) {
		return e.getAttributeValue(Attributes.SCALE);
	}

	public static List<ActiveEvent> active(GameTestHelper h, ServerPlayer p) {
		return manager(h).activeFor(p);
	}
}
