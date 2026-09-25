package dev.chaosaholic.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import dev.chaosaholic.Chaosaholic;
import dev.chaosaholic.Texts;
import dev.chaosaholic.core.ChaosLimits;
import dev.chaosaholic.core.LevelMark;
import dev.chaosaholic.core.Stacking;
import dev.chaosaholic.core.TimeFormat;
import dev.chaosaholic.core.TriggerQueue;
import dev.chaosaholic.core.WeightedPicker;
import dev.chaosaholic.event.helper.Marks;
import dev.chaosaholic.event.helper.OwnedEntities;
import dev.chaosaholic.event.helper.TempBlockStore;
import dev.chaosaholic.event.helper.TrackedNames;
import dev.chaosaholic.mode.ChaosSettings;
import dev.chaosaholic.mode.Scope;
import dev.chaosaholic.net.ActiveEventsPayload;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/**
 * The event engine of one running server: watches experience levels, queues triggers per player, rolls and starts
 * events, ticks them, keeps their boss bars and client sync up to date and runs every cleanup path (end, logout,
 * death, dimension change, Creative/Spectator, chunk unload, mode off, server stop). Server thread only.
 *
 * <p>Created on SERVER_STARTING and dropped on SERVER_STOPPED; {@link #get(MinecraftServer)} returns it.
 */
public final class EventManager {
	private static @Nullable EventManager current;
	private static final List<Runnable> DEFERRED = new ArrayList<>();
	/** First exception per event id is logged at error level, later ones at debug. */
	private static final Set<String> LOGGED = new LinkedHashSet<>();

	private final MinecraftServer server;
	private final Map<UUID, PlayerState> states = new HashMap<>();
	private final List<ActiveEvent> active = new ArrayList<>();
	private final Map<UUID, ActiveEvent> byUuid = new HashMap<>();

	private static final class PlayerState {
		private int lastLevel = -1;
		private final TriggerQueue queue = new TriggerQueue(ChaosLimits.QUEUE_CAP, ChaosLimits.START_INTERVAL_TICKS);
		private int started;
	}

	private EventManager(MinecraftServer server) {
		this.server = server;
	}

	/** Registers every Fabric listener of the framework. Called once from Chaosaholic#onInitialize. */
	public static void register() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> current = new EventManager(server));
		ServerLifecycleEvents.SERVER_STARTED.register(TempBlockStore::restoreAll);
		// before the final save: nothing temporary may be written to disk
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> with(server, m -> m.stopAll(StopReason.SERVER_STOPPING)));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			current = null;
			DEFERRED.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> with(server, EventManager::tick));
		ServerPlayerEvents.LEAVE.register(player -> with(player.level().getServer(), m -> m.onLeave(player)));
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, from, to) ->
				with(to.getServer(), m -> m.removeEverywhere(player, RemoveReason.DIMENSION_CHANGE)));
		ServerLivingEntityEvents.AFTER_DEATH.register(EventManager::afterDeath);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(EventManager::allowDamage);
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (player instanceof ServerPlayer sp && current != null) {
				for (ActiveEvent ev : List.copyOf(current.active)) {
					current.guarded(ev, () -> ev.event().afterBlockBreak(ev, sp, pos, state));
				}
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			OwnedEntities.onEntityLoad(entity, level);
			TrackedNames.onEntityLoad(entity, level);
		});
		ServerLivingEntityEvents.MOB_CONVERSION.register((previous, converted, params) -> {
			OwnedEntities.onConversion(previous, converted);
			TrackedNames.onConversion(previous, converted);
		});
		ServerLivingEntityEvents.MOB_CONVERSION.register((previous, converted, params) -> ownedConverted(previous, converted));
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> with(level.getServer(), m -> m.onUnload(entity)));
	}

	private static void with(MinecraftServer server, java.util.function.Consumer<EventManager> action) {
		EventManager m = current;
		if (m != null && m.server == server) action.accept(m);
	}

	/** The manager of {@code server}. Throws if the server is not running (not started or already stopped). */
	public static EventManager get(MinecraftServer server) {
		EventManager m = current;
		if (m == null || m.server != server) throw new IllegalStateException("Chaosaholic event manager is not running");
		return m;
	}

	/** A running instance by uuid, or null (ended, or from before a restart). */
	public static @Nullable ActiveEvent findLive(UUID uuid) {
		EventManager m = current;
		return m == null ? null : m.byUuid.get(uuid);
	}

	/** Entities owned by all running instances together (for the global cap). */
	public static int ownedTotal() {
		EventManager m = current;
		if (m == null) return 0;
		int n = 0;
		for (ActiveEvent ev : m.active) n += ev.entities().count();
		return n;
	}

	/** Runs {@code task} at the start of the next server tick (e.g. reverting an entity while its chunk loads). */
	public static void defer(Runnable task) {
		DEFERRED.add(task);
	}

	/** Survival or Adventure, alive, online, not a fake player: can trigger and be affected. */
	public static boolean isEligible(ServerPlayer player) {
		return !player.isRemoved() && player.isAlive() && !player.isCreative() && !player.isSpectator() && !(player instanceof FakePlayer);
	}

	// ---- tick ----

	private void tick() {
		runDeferred();
		ChaosSettings settings = ChaosSettings.get(server);
		long now = server.getTickCount();
		List<ServerPlayer> online = server.getPlayerList().getPlayers();
		for (ServerPlayer p : online) observe(p, settings.enabled());
		if (settings.enabled()) {
			for (ServerPlayer p : List.copyOf(online)) {
				PlayerState st = states.get(p.getUUID());
				if (st != null && st.queue.ready(now)) startQueued(p, st, now);
			}
		}
		tickActive();
		if (now % ChaosLimits.TICKS_PER_SECOND == 0) TempBlockStore.flush(server);
	}

	private static void runDeferred() {
		if (DEFERRED.isEmpty()) return;
		List<Runnable> tasks = new ArrayList<>(DEFERRED);
		DEFERRED.clear();
		for (Runnable task : tasks) {
			try {
				task.run();
			} catch (RuntimeException e) {
				Chaosaholic.LOGGER.warn("Deferred Chaosaholic cleanup failed", e);
			}
		}
	}

	/**
	 * Level watch for one player (also called by gametests): updates the anti-farming mark and queues one trigger per
	 * new level above it, if the mode is on and the player is eligible. Returns the number of triggers queued.
	 */
	public int observe(ServerPlayer player, boolean enabled) {
		PlayerState st = states.computeIfAbsent(player.getUUID(), k -> new PlayerState());
		int level = player.experienceLevel;
		Integer mark = player.getAttached(PlayerLevels.MARK);
		if (mark == null) {
			player.setAttached(PlayerLevels.MARK, LevelMark.initial(level));
			st.lastLevel = level;
			return 0;
		}
		if (st.lastLevel < 0) {
			st.lastLevel = level;
			return 0;
		}
		if (level == st.lastLevel) return 0;
		LevelMark.Result r = LevelMark.observe(st.lastLevel, level, mark);
		st.lastLevel = level;
		if (r.mark() != mark) player.setAttached(PlayerLevels.MARK, r.mark());
		if (r.triggers() <= 0 || !enabled || !isEligible(player)) return 0;
		return st.queue.add(r.triggers());
	}

	private void startQueued(ServerPlayer player, PlayerState st, long now) {
		if (!isEligible(player)) {
			st.queue.clear();
			return;
		}
		if (!Stacking.hasRoom(activeCount(player), ChaosLimits.MAX_ACTIVE_PER_PLAYER)) {
			st.queue.delay(now); // wait for a slot
			return;
		}
		if (roll(player).isPresent()) {
			st.queue.take(now);
			st.started++;
		} else {
			st.queue.drop(now);
		}
	}

	private void tickActive() {
		for (ActiveEvent ev : List.copyOf(active)) {
			if (ev.isStopped()) continue;
			for (ServerPlayer p : ev.players()) {
				RemoveReason reason = ineligibility(p, ev.level());
				if (reason != null) removePlayer(ev, p, reason);
			}
			if (ev.playerMap().isEmpty()) {
				stop(ev, StopReason.NO_PLAYERS);
				continue;
			}
			if (!guarded(ev, ev::advance) || ev.isStopped()) continue;
			if (!guarded(ev, () -> ev.event().onTick(ev)) || ev.isStopped()) continue;
			if (ev.age() % ChaosLimits.EFFECT_REFRESH_TICKS == 0) {
				guarded(ev, () -> {
					ev.effects().refresh();
					ev.modifiers().prune();
				});
			}
			if (ev.age() % ChaosLimits.BOSS_BAR_UPDATE_TICKS == 0) updateBar(ev);
			if (ev.remainingTicks() <= 0) stop(ev, StopReason.EXPIRED);
		}
	}

	private static @Nullable RemoveReason ineligibility(ServerPlayer p, ServerLevel level) {
		if (p.isDeadOrDying()) return RemoveReason.DEATH;
		if (p.isRemoved()) return RemoveReason.LOGOUT;
		if (p.level() != level) return RemoveReason.DIMENSION_CHANGE;
		if (!isEligible(p)) return RemoveReason.INELIGIBLE;
		return null;
	}

	// ---- rolling and starting ----

	/** The context an event would start with if {@code trigger} rolled it now (scope from the world settings). */
	public EventContext context(ServerPlayer trigger) {
		Scope scope = ChaosSettings.get(server).scope();
		ServerLevel level = trigger.level();
		List<ServerPlayer> players = new ArrayList<>();
		if (scope == Scope.WORLD) {
			for (ServerPlayer p : level.players()) if (isEligible(p)) players.add(p);
			if (!players.contains(trigger)) players.addFirst(trigger);
		} else {
			players.add(trigger);
		}
		return new EventContext(server, level, trigger, List.copyOf(players), trigger.getRandom(), scope);
	}

	/**
	 * Rolls a random event for {@code trigger} (weighted by the world's weights, disabled events excluded, rerolled
	 * while canStart fails) and starts it. Empty if the player is not eligible or nothing can start.
	 */
	public Optional<ActiveEvent> roll(ServerPlayer trigger) {
		if (!isEligible(trigger)) return Optional.empty();
		ChaosSettings settings = ChaosSettings.get(server);
		EventContext ctx = context(trigger);
		Optional<ChaosEvent> pick = WeightedPicker.pick(EventRegistry.all(), settings::effectiveWeight,
				e -> canStart(e, ctx), ctx.random()::nextInt);
		return pick.flatMap(e -> start(e, ctx));
	}

	/**
	 * Starts {@code event} for {@code trigger} regardless of the mode switch, the event's enabled flag and weight
	 * (/chaosaholic trigger, gametests). Still refuses ineligible players (Creative/Spectator), a failing canStart
	 * and players without room. Returns the new or extended instance.
	 */
	public Optional<ActiveEvent> trigger(ChaosEvent event, ServerPlayer trigger) {
		if (!isEligible(trigger)) return Optional.empty();
		EventContext ctx = context(trigger);
		if (!canStart(event, ctx)) return Optional.empty();
		return start(event, ctx);
	}

	/** Why {@link #roll} or {@link #trigger} started nothing for a player (command feedback). */
	public enum Refusal {
		/** Creative / Spectator, dead, offline or a fake player. */
		INELIGIBLE,
		/** Already {@link ChaosLimits#MAX_ACTIVE_PER_PLAYER} timed events. */
		NO_ROOM,
		/** Every event is switched off or has weight 0 (roll only). */
		ALL_OFF,
		/** No event (roll) / not this event (trigger) can start here now: canStart refused. */
		CANNOT_START
	}

	/**
	 * The reason {@link #roll} (event null) or {@link #trigger} (that event) would start nothing for {@code player}
	 * right now. Call it after the attempt returned empty; never returns null.
	 */
	public Refusal refusal(ServerPlayer player, @Nullable ChaosEvent event) {
		if (!isEligible(player)) return Refusal.INELIGIBLE;
		boolean extendable = event != null && !event.isInstant() && find(event, player) != null;
		boolean instant = event != null && event.isInstant();
		if (!extendable && !instant && !Stacking.hasRoom(activeCount(player), ChaosLimits.MAX_ACTIVE_PER_PLAYER)) return Refusal.NO_ROOM;
		if (event == null) {
			ChaosSettings settings = ChaosSettings.get(server);
			boolean any = false;
			for (ChaosEvent e : EventRegistry.all()) if (settings.effectiveWeight(e) > 0) any = true;
			if (!any) return Refusal.ALL_OFF;
		}
		return Refusal.CANNOT_START;
	}

	private boolean canStart(ChaosEvent event, EventContext ctx) {
		try {
			return event.canStart(ctx);
		} catch (RuntimeException e) {
			logOnce(event.id(), "canStart", e);
			return false;
		}
	}

	/**
	 * Starts {@code event} in {@code ctx}: players on whom the same event already runs get that instance extended
	 * (stacking), players without room are skipped, the rest share one new instance.
	 */
	private Optional<ActiveEvent> start(ChaosEvent event, EventContext ctx) {
		List<ServerPlayer> fresh = new ArrayList<>();
		Set<ActiveEvent> extend = new LinkedHashSet<>();
		for (ServerPlayer p : ctx.players()) {
			if (!isEligible(p)) continue;
			ActiveEvent existing = event.isInstant() ? null : find(event, p);
			if (existing != null) {
				extend.add(existing);
			} else if (event.isInstant() || Stacking.hasRoom(activeCount(p), ChaosLimits.MAX_ACTIVE_PER_PLAYER)) {
				fresh.add(p);
			}
		}
		int duration = event.rollDuration(ctx);
		ActiveEvent result = null;
		for (ActiveEvent ev : extend) {
			ev.extend(duration);
			guarded(ev, () -> event.onExtended(ev, duration));
			if (ev.isStopped()) continue;
			updateBar(ev);
			for (ServerPlayer p : ev.players()) {
				Announcer.announce(p, event);
				sync(p);
			}
			result = ev;
		}
		if (fresh.isEmpty()) return Optional.ofNullable(result);

		EventContext own = new EventContext(ctx.server(), ctx.level(), ctx.trigger(), List.copyOf(fresh), ctx.random(), ctx.scope());
		ActiveEvent ev = new ActiveEvent(event, own, duration);
		active.add(ev);
		byUuid.put(ev.uuid(), ev);
		if (!event.isInstant()) {
			ServerBossEvent bar = new ServerBossEvent(ev.uuid(), barName(ev), event.category().barColor(), BossEvent.BossBarOverlay.NOTCHED_10);
			bar.setDarkenScreen(false);
			bar.setPlayBossMusic(false);
			bar.setCreateWorldFog(false);
			ev.setBossBar(bar);
		}
		if (!guarded(ev, () -> event.onStart(ev))) return Optional.empty();
		for (ServerPlayer p : fresh) {
			if (ev.isStopped()) break;
			addPlayer(ev, p);
		}
		if (ev.isStopped()) return Optional.empty();
		if (event.isInstant()) stop(ev, StopReason.INSTANT);
		return Optional.of(ev);
	}

	private void addPlayer(ActiveEvent ev, ServerPlayer p) {
		ev.playerMap().put(p.getUUID(), p);
		ServerBossEvent bar = ev.bossBar();
		if (bar != null) bar.addPlayer(p);
		if (!guarded(ev, () -> ev.event().onPlayerAdded(ev, p))) return;
		Announcer.announce(p, ev.event());
		sync(p);
	}

	// ---- removing and stopping ----

	/** Removes {@code player} from {@code ev}: the event's hook, then tracker reverts, boss bar, client sync. */
	public void removePlayer(ActiveEvent ev, ServerPlayer player, RemoveReason reason) {
		if (ev.playerMap().remove(player.getUUID()) == null) return;
		safely(ev, "onPlayerRemoved", () -> ev.event().onPlayerRemoved(ev, player, reason));
		safely(ev, "effects", () -> ev.effects().revert(player));
		safely(ev, "modifiers", () -> ev.modifiers().revert(player));
		ServerBossEvent bar = ev.bossBar();
		if (bar != null) bar.removePlayer(player);
		sync(player);
	}

	/**
	 * Logout, death, dimension change: removes {@code player} from every instance affecting it, and reverts the
	 * effects / modifiers of every other instance that changed it without affecting it (e.g. glow_party lighting up a
	 * nearby player), so nothing is saved with the player or carried into another dimension.
	 */
	private void removeEverywhere(ServerPlayer player, RemoveReason reason) {
		for (ActiveEvent ev : List.copyOf(active)) {
			if (ev.playerMap().get(player.getUUID()) == player) {
				removePlayer(ev, player, reason);
			} else if (ev.effects().tracks(player) || ev.modifiers().tracks(player)) {
				safely(ev, "effects", () -> ev.effects().revert(player));
				safely(ev, "modifiers", () -> ev.modifiers().revert(player));
			}
		}
	}

	/** Ends {@code ev}: removes every player, calls onStop, reverts all trackers, removes the boss bar. Idempotent. */
	public void stop(ActiveEvent ev, StopReason reason) {
		if (ev.isStopped()) return;
		ev.markStopped();
		for (ServerPlayer p : ev.players()) removePlayer(ev, p, RemoveReason.EVENT_ENDED);
		safely(ev, "onStop", () -> ev.event().onStop(ev, reason));
		safely(ev, "entities", () -> ev.entities().revertAll());
		safely(ev, "names", () -> ev.names().revertAll());
		safely(ev, "blocks", () -> ev.blocks().revertAll());
		safely(ev, "effects", () -> ev.effects().revertAll());
		safely(ev, "modifiers", () -> ev.modifiers().revertAll());
		ServerBossEvent bar = ev.bossBar();
		if (bar != null) bar.removeAllPlayers();
		active.remove(ev);
		byUuid.remove(ev.uuid());
	}

	/** Ends every running instance (and clears all queues for MODE_OFF / SERVER_STOPPING). */
	public void stopAll(StopReason reason) {
		for (ActiveEvent ev : List.copyOf(active)) stop(ev, reason);
		if (reason == StopReason.MODE_OFF || reason == StopReason.SERVER_STOPPING) {
			for (PlayerState st : states.values()) st.queue.clear();
		}
	}

	/**
	 * Ends every instance affecting {@code player}; returns how many. A world-scope instance that also affects other
	 * players only lets {@code player} go ({@link RemoveReason#STOPPED}: their per-player effects are reverted and the
	 * boss bar disappears for them) and goes on for the others; it ends when {@code player} was the last one.
	 */
	public int stopFor(ServerPlayer player) {
		int n = 0;
		for (ActiveEvent ev : List.copyOf(active)) {
			if (ev.playerMap().get(player.getUUID()) != player) continue;
			if (ev.context().isWorldScope() && ev.playerMap().size() > 1) {
				removePlayer(ev, player, RemoveReason.STOPPED);
			} else {
				stop(ev, StopReason.FORCED);
			}
			n++;
		}
		return n;
	}

	private void onLeave(ServerPlayer player) {
		removeEverywhere(player, RemoveReason.LOGOUT);
		PlayerState st = states.get(player.getUUID());
		if (st != null) st.lastLevel = -1;
	}

	private static void afterDeath(LivingEntity entity, DamageSource source) {
		EventManager m = current;
		if (m == null) return;
		if (entity instanceof ServerPlayer player) {
			m.removeEverywhere(player, RemoveReason.DEATH);
			PlayerState st = m.states.get(player.getUUID());
			if (st != null) {
				st.queue.clear();
				st.lastLevel = -1;
			}
			return;
		}
		OwnedEntities.onDeath(entity); // a dead replacement is never turned back
		if (source.getEntity() instanceof ServerPlayer killer) {
			for (ActiveEvent ev : List.copyOf(m.active)) m.guarded(ev, () -> ev.event().afterKill(ev, killer, entity, source));
		}
	}

	/** After OwnedEntities.onConversion: tells the owning (running) instance that one of its mobs converted. */
	private static void ownedConverted(Mob previous, Mob converted) {
		EventManager m = current;
		Marks.OwnerMark mark = converted.getAttached(Marks.OWNER);
		if (m == null || mark == null) return;
		ActiveEvent ev = m.byUuid.get(mark.owner());
		if (ev == null || ev.isStopped() || !ev.entities().owns(converted)) return;
		m.guarded(ev, () -> ev.event().onOwnedConverted(ev, previous, converted));
	}

	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		EventManager m = current;
		if (m == null || m.active.isEmpty()) return true;
		for (ActiveEvent ev : List.copyOf(m.active)) {
			if (ev.isStopped()) continue;
			try {
				if (!ev.event().allowDamage(ev, entity, source, amount)) return false;
			} catch (RuntimeException e) {
				logOnce(ev.id(), "allowDamage", e);
			}
		}
		return true;
	}

	private void onUnload(Entity entity) {
		if (entity instanceof ServerPlayer) return; // players: LEAVE / dimension change / death paths
		for (ActiveEvent ev : active) {
			ev.entities().forget(entity);
			ev.effects().forget(entity);
			ev.modifiers().forget(entity);
			ev.names().forget(entity);
		}
	}

	// ---- boss bars and client sync ----

	private void updateBar(ActiveEvent ev) {
		ServerBossEvent bar = ev.bossBar();
		if (bar == null) return;
		bar.setName(barName(ev));
		bar.setProgress(Stacking.progress(ev.remainingTicks(), ev.totalTicks()));
	}

	/** "Name — 0:42" (chaosaholic.bossbar.format). */
	public static Component barName(ActiveEvent ev) {
		return Texts.tr("chaosaholic.bossbar.format", Texts.name(ev.event()), time(ev.remainingTicks()).withStyle(ChatFormatting.GRAY));
	}

	/** Remaining time: "42 s" under a minute, "1:05" from one minute. */
	public static net.minecraft.network.chat.MutableComponent time(int ticks) {
		TimeFormat.Parts parts = TimeFormat.parts(ticks);
		if (parts.underMinute()) return Texts.tr("chaosaholic.time.seconds", String.valueOf(parts.seconds()));
		return Texts.tr("chaosaholic.time.minutes", String.valueOf(parts.minutes()), parts.paddedSeconds());
	}

	/** Sends the list of timed events affecting {@code player} to a modded client. */
	public void sync(ServerPlayer player) {
		if (player.isRemoved() || !ServerPlayNetworking.canSend(player, ActiveEventsPayload.TYPE)) return;
		player.connection.send(ServerPlayNetworking.createClientboundPacket(new ActiveEventsPayload(entries(player))));
	}

	/** What {@link #sync} sends: timed, running events affecting {@code player}. */
	public List<ActiveEventsPayload.Entry> entries(ServerPlayer player) {
		List<ActiveEventsPayload.Entry> out = new ArrayList<>();
		for (ActiveEvent ev : active) {
			if (ev.isStopped() || ev.event().isInstant() || ev.playerMap().get(player.getUUID()) != player) continue;
			out.add(new ActiveEventsPayload.Entry(ev.id(), ev.remainingTicks(), ev.totalTicks()));
		}
		return out;
	}

	// ---- queries ----

	/** Running instances (a copy). */
	public List<ActiveEvent> activeEvents() {
		return List.copyOf(active);
	}

	/** Running instances affecting {@code player}. */
	public List<ActiveEvent> activeFor(ServerPlayer player) {
		List<ActiveEvent> out = new ArrayList<>();
		for (ActiveEvent ev : active) if (!ev.isStopped() && ev.playerMap().get(player.getUUID()) == player) out.add(ev);
		return out;
	}

	/** The running instance of {@code event} affecting {@code player}, or null. */
	public @Nullable ActiveEvent find(ChaosEvent event, ServerPlayer player) {
		for (ActiveEvent ev : active) {
			if (ev.event() == event && !ev.isStopped() && ev.playerMap().get(player.getUUID()) == player) return ev;
		}
		return null;
	}

	/** Whether an instance of the event with {@code id} currently affects {@code player} (for mixins of events). */
	public static boolean isActiveFor(ServerPlayer player, String id) {
		EventManager m = current;
		if (m == null) return false;
		for (ActiveEvent ev : m.active) {
			if (ev.id().equals(id) && !ev.isStopped() && ev.playerMap().get(player.getUUID()) == player) return true;
		}
		return false;
	}

	/** Timed events affecting {@code player} (the per-player cap counts these). */
	public int activeCount(ServerPlayer player) {
		int n = 0;
		for (ActiveEvent ev : active) {
			if (!ev.isStopped() && !ev.event().isInstant() && ev.playerMap().get(player.getUUID()) == player) n++;
		}
		return n;
	}

	/** Pending triggers of {@code player}. */
	public int pending(ServerPlayer player) {
		PlayerState st = states.get(player.getUUID());
		return st == null ? 0 : st.queue.pending();
	}

	/** Events started (or extended) from {@code player}'s queue since the server started. */
	public int startedFromQueue(ServerPlayer player) {
		PlayerState st = states.get(player.getUUID());
		return st == null ? 0 : st.started;
	}

	// ---- error handling ----

	/** Runs an event hook; on an exception logs it and stops the instance (ERROR). Returns false if it threw. */
	private boolean guarded(ActiveEvent ev, Runnable hook) {
		try {
			hook.run();
			return true;
		} catch (RuntimeException e) {
			logOnce(ev.id(), "hook", e);
			stop(ev, StopReason.ERROR);
			return false;
		}
	}

	/** Runs a cleanup step; an exception is logged and the next step still runs. */
	private static void safely(ActiveEvent ev, String what, Runnable step) {
		try {
			step.run();
		} catch (RuntimeException e) {
			logOnce(ev.id(), what, e);
		}
	}

	private static void logOnce(String id, String what, RuntimeException e) {
		if (LOGGED.add(id + "/" + what)) {
			Chaosaholic.LOGGER.error("Chaos event {} failed in {}", id, what, e);
		} else {
			Chaosaholic.LOGGER.debug("Chaos event {} failed in {}", id, what, e);
		}
	}
}
