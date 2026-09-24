package dev.chaosaholic.event;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import dev.chaosaholic.core.ChaosLimits;
import dev.chaosaholic.core.Stacking;
import dev.chaosaholic.event.helper.OwnedEntities;
import dev.chaosaholic.event.helper.TempBlocks;
import dev.chaosaholic.event.helper.TrackedEffects;
import dev.chaosaholic.event.helper.TrackedModifiers;
import dev.chaosaholic.event.helper.TrackedNames;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * One running instance of a {@link ChaosEvent}: its timer, its affected players, its per-instance state, its
 * schedule and its resource trackers. Every change made through a tracker is reverted by the framework when the
 * instance ends (and per player when that player is removed). Server thread only.
 */
public final class ActiveEvent {
	private final UUID uuid = UUID.randomUUID();
	private final ChaosEvent event;
	private final EventContext context;
	private final ServerLevel level;
	private final Map<UUID, ServerPlayer> players = new LinkedHashMap<>();
	private final List<Scheduled> schedule = new ArrayList<>();
	private final TrackedEffects effects;
	private final TrackedModifiers modifiers;
	private final OwnedEntities entities;
	private final TrackedNames names;
	private final TempBlocks blocks;
	private @Nullable ServerBossEvent bossBar;
	private @Nullable Object state;
	private int remaining;
	private int total;
	private int age;
	private boolean stopped;

	private record Scheduled(int at, Runnable task) {}

	ActiveEvent(ChaosEvent event, EventContext context, int duration) {
		this.event = event;
		this.context = context;
		this.level = context.level();
		this.remaining = event.isInstant() ? 0 : Stacking.initial(duration, ChaosLimits.MAX_REMAINING_TICKS);
		this.total = remaining;
		// after event/context: the trackers read the id and level
		this.effects = new TrackedEffects(this);
		this.modifiers = new TrackedModifiers(this);
		this.entities = new OwnedEntities(this);
		this.names = new TrackedNames(this);
		this.blocks = new TempBlocks(this);
	}

	/** Unique per instance (also across restarts); stored on owned entities and temporary blocks. */
	public UUID uuid() {
		return uuid;
	}

	public ChaosEvent event() {
		return event;
	}

	public String id() {
		return event.id();
	}

	/** The context the instance started with (trigger player, scope, difficulty, ...). */
	public EventContext context() {
		return context;
	}

	/** The dimension the instance runs in; players who leave it are removed. */
	public ServerLevel level() {
		return level;
	}

	public RandomSource random() {
		return context.random();
	}

	/** Currently affected players: eligible (survival/adventure, alive, online, in {@link #level()}). A copy. */
	public List<ServerPlayer> players() {
		return List.copyOf(players.values());
	}

	public boolean isAffected(Entity entity) {
		return entity instanceof ServerPlayer p && players.get(p.getUUID()) == p;
	}

	/** Affected player or an entity changed by one of this instance's trackers. */
	public boolean tracks(Entity entity) {
		return isAffected(entity) || effects.tracks(entity) || modifiers.tracks(entity) || entities.owns(entity) || names.tracks(entity);
	}

	/** Ticks since start (0 during onStart / the first onTick). */
	public int age() {
		return age;
	}

	/** True every {@code period} ticks, starting with the first tick. */
	public boolean every(int period) {
		return period > 0 && age % period == 0;
	}

	public int remainingTicks() {
		return remaining;
	}

	public int totalTicks() {
		return total;
	}

	public boolean isStopped() {
		return stopped;
	}

	/**
	 * Per-instance state object, created by {@code factory} on first use. Keep all mutable run data here, never in
	 * fields of the (singleton) ChaosEvent.
	 */
	@SuppressWarnings("unchecked")
	public <T> T state(Supplier<T> factory) {
		if (state == null) state = factory.get();
		return (T) state;
	}

	/** Runs {@code task} {@code delayTicks} from now, during this instance's tick; dropped if the instance ends first. */
	public void schedule(int delayTicks, Runnable task) {
		schedule.add(new Scheduled(age + Math.max(0, delayTicks), task));
	}

	/** Mob effects kept on entities for the whole run (re-applied if removed, e.g. by milk). */
	public TrackedEffects effects() {
		return effects;
	}

	/** Transient attribute modifiers (never saved, so nothing survives a crash). */
	public TrackedModifiers modifiers() {
		return modifiers;
	}

	/** Entities this instance spawned or replaced; removed / restored at the end. */
	public OwnedEntities entities() {
		return entities;
	}

	/** Custom names changed for the run; restored at the end. */
	public TrackedNames names() {
		return names;
	}

	/** Temporary blocks; original states restored at the end (and on the next start after a crash). */
	public TempBlocks blocks() {
		return blocks;
	}

	// ---- framework side (EventManager) ----

	Map<UUID, ServerPlayer> playerMap() {
		return players;
	}

	void setBossBar(@Nullable ServerBossEvent bar) {
		this.bossBar = bar;
	}

	@Nullable ServerBossEvent bossBar() {
		return bossBar;
	}

	void markStopped() {
		stopped = true;
		schedule.clear();
	}

	/** Advances the clock by one tick and runs due scheduled tasks. */
	void advance() {
		age++;
		if (remaining > 0) remaining--;
		if (schedule.isEmpty()) return;
		List<Scheduled> due = new ArrayList<>();
		for (Iterator<Scheduled> it = schedule.iterator(); it.hasNext(); ) {
			Scheduled s = it.next();
			if (s.at() <= age) {
				due.add(s);
				it.remove();
			}
		}
		for (Scheduled s : due) {
			if (stopped) return;
			s.task().run();
		}
	}

	/** Stacking extension: remaining += added (capped), total grows along. */
	void extend(int added) {
		Stacking.Timer t = Stacking.extend(remaining, total, added, ChaosLimits.MAX_REMAINING_TICKS);
		remaining = t.remaining();
		total = t.total();
	}

	/** Gametests and debugging: jump the timer (e.g. to 1 so the instance ends on the next tick). */
	public void setRemainingTicks(int ticks) {
		remaining = Math.max(0, Math.min(ChaosLimits.MAX_REMAINING_TICKS, ticks));
		total = Math.max(total, remaining);
	}

	/** Players that the boss bar currently shows to (for tests). */
	public Collection<ServerPlayer> bossBarPlayers() {
		return bossBar == null ? List.of() : List.copyOf(bossBar.getPlayers());
	}

	public boolean hasBossBar() {
		return bossBar != null;
	}

	@Override
	public String toString() {
		return "ActiveEvent[" + event.id() + " " + uuid + " remaining=" + remaining + " players=" + players.size() + "]";
	}
}
