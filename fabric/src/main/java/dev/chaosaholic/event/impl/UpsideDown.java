package dev.chaosaholic.event.impl;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.helper.Area;
import dev.chaosaholic.event.helper.Marks;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.warden.Warden;
import org.jspecify.annotations.Nullable;

/**
 * Weird: for 30-60 s the mobs around each affected player (radius {@link #RADIUS}, at most {@link #MAX_FLIPPED} per
 * instance) are renamed {@value #DINNERBONE}, which every client (vanilla too) renders upside down. The area is
 * scanned again every {@link #RESCAN_TICKS} ticks, so mobs that walk in later flip as well.
 *
 * <p>Names go through {@code ev.names()} (TrackedNames): the original custom name (or none) and its visibility are
 * kept in a persistent attachment and restored at the end, after a chunk reload and after a crash; a flipped mob that
 * converts (zombie drowning into a drowned) hands the mark to what it became. The flip name is not shown above the
 * mob: it keeps the visibility of the mob's own name, so like any hidden custom name it only appears while a player
 * looks directly at the mob. Players are never renamed (the renderer flips players only by their account name). Named
 * and tamed mobs flip too (the change is purely cosmetic and always restored); bosses, entities of other events (owned
 * or renamed) and mobs already called Dinnerbone/Grumm do not. A mob a player renames with a name tag during the event
 * keeps that new name at the end (TrackedNames only restores a mob that still carries the flip name). The names belong
 * to the mobs, not to a player: a player leaving early changes nothing, they are restored when the instance ends.
 */
public final class UpsideDown extends ChaosEvent {
	/** Game Easter egg name (the client renders entities with this name upside down), not player text. */
	public static final String DINNERBONE = "Dinnerbone";
	/** The other Easter egg name with the same effect: such mobs are already upside down and left alone. */
	private static final String GRUMM = "Grumm";
	/** Scan radius around each affected player. */
	public static final double RADIUS = 16.0;
	/** Mobs flipped per instance at most (world scope included). */
	public static final int MAX_FLIPPED = 32;
	/** Rescan period for newcomers (area scans at most once a second). */
	public static final int RESCAN_TICKS = 20;

	/** Number of mobs this instance flipped (the cap). */
	private static final class State {
		int flipped;
	}

	public UpsideDown() {
		super("upside_down", Category.WEIRD, 30, 60);
	}

	@Override
	public void onPlayerAdded(ActiveEvent ev, ServerPlayer player) {
		flipAround(ev, player);
	}

	@Override
	public void onTick(ActiveEvent ev) {
		if (ev.age() == 0 || !ev.every(RESCAN_TICKS)) return;
		for (ServerPlayer p : ev.players()) flipAround(ev, p);
	}

	/**
	 * Flips every eligible mob around {@code player} that is not flipped yet, within the instance cap. Returns the
	 * number of mobs flipped by this call.
	 */
	public static int flipAround(ActiveEvent ev, ServerPlayer player) {
		State s = ev.state(State::new);
		if (s.flipped >= MAX_FLIPPED) return 0;
		ServerLevel level = ev.level();
		int n = 0;
		for (LivingEntity mob : Area.entities(level, player.position(), RADIUS, LivingEntity.class, UpsideDown::canFlip)) {
			if (s.flipped >= MAX_FLIPPED) break;
			if (!ev.names().rename(mob, Component.literal(DINNERBONE), mob.isCustomNameVisible())) continue;
			s.flipped++;
			n++;
			level.sendParticles(ParticleTypes.WITCH, mob.getX(), mob.getY() + mob.getBbHeight() + 0.2, mob.getZ(), 6, 0.3, 0.2, 0.3, 0.02);
		}
		return n;
	}

	/**
	 * A mob (not a player, armor stand, ...), not a boss (its boss bar would show the name), not owned or renamed by an event (that
	 * includes the jeb_ sheep of sheep_disco) and not already flipped by its own name.
	 */
	public static boolean canFlip(Entity e) {
		if (!(e instanceof Mob)) return false; // no players, armor stands or other non-mob living entities
		if (e instanceof EnderDragon || e instanceof WitherBoss || e instanceof Warden || e instanceof ElderGuardian) return false;
		if (e.hasAttached(Marks.OWNER) || e.hasAttached(Marks.NAME)) return false;
		return !isFlipName(e.getCustomName());
	}

	private static boolean isFlipName(@Nullable Component name) {
		if (name == null) return false;
		String s = name.getString();
		return DINNERBONE.equals(s) || GRUMM.equals(s);
	}

	@Override
	public Holder<SoundEvent> startSound() {
		return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EVOKER_PREPARE_WOLOLO);
	}

	@Override
	public float startSoundPitch() {
		return 1.5F;
	}

	@Override
	public float startSoundVolume() {
		return 0.6F;
	}
}
