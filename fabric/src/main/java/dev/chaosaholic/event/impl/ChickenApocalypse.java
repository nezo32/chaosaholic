package dev.chaosaholic.event.impl;

import java.util.List;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventContext;
import dev.chaosaholic.event.StopReason;
import dev.chaosaholic.event.helper.Area;
import dev.chaosaholic.event.helper.Marks;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.phys.Vec3;

/**
 * Weird: for 30-60 s the mobs within {@link #RADIUS} blocks of each affected player (at most {@link #MAX_CHICKENS}
 * per instance, nearest first) become chickens, and turn back at the end.
 *
 * <p>Which mobs: {@link Mob}s that are {@link Area#isFairGame fair game} (no players, no bosses, nothing tamed, nothing
 * with a custom name - so name-tagged pets and named villagers are safe -, nothing already changed by another event,
 * nothing riding or ridden), minus chickens, leashed mobs (the leash would snap) and mobs in water (a fish or squid
 * would come back wherever its chicken walked to). Armor stands and other non-mob living entities are never touched.
 *
 * <p>How: {@code ev.entities().replace(mob, chicken)} keeps the original's full saved data (UUID, health, name, effects,
 * equipment, inventory, villager trades, ...) on the chicken; at the end the original is loaded back at the chicken's
 * position and rotation. The chicken is saved with that data, so a chunk unload keeps it and the next load turns it
 * back if the event is over, the same after a crash or a server restart; the end of the event, a stop, the last player
 * logging out, dying or leaving and the server stopping all restore it. If the chicken dies, the original is gone for
 * good (it drops what a chicken drops, not the original's items; the dying chicken loses the original's data at
 * once, so neither the end of the event nor a save during its death animation can bring it back). A chicken of a
 * baby mob is a chick. An extension converts newcomers up to the cap.
 */
public final class ChickenApocalypse extends ChaosEvent {
	public static final double RADIUS = 12.0;
	/** Chickens (converted mobs) per instance. */
	public static final int MAX_CHICKENS = 16;

	public ChickenApocalypse() {
		super("chicken_apocalypse", Category.WEIRD, 30, 60);
	}

	/** Whether {@code e} may be turned into a chicken (see the class comment). */
	public static boolean isCandidate(Entity e) {
		if (!(e instanceof Mob) || e instanceof Chicken || !Area.isFairGame(e)) return false;
		if (e instanceof Leashable leashable && leashable.isLeashed()) return false;
		return !e.isInWater();
	}

	private static List<Mob> candidates(ServerLevel level, Vec3 center, int max) {
		return Area.entities(level, center, RADIUS, Mob.class, ChickenApocalypse::isCandidate, max);
	}

	@Override
	public boolean canStart(EventContext ctx) {
		for (ServerPlayer p : ctx.players()) if (!candidates(ctx.level(), p.position(), 1).isEmpty()) return true;
		return false;
	}

	@Override
	public void onPlayerAdded(ActiveEvent ev, ServerPlayer player) {
		convertAround(ev, player);
	}

	@Override
	public void onExtended(ActiveEvent ev, int addedTicks) {
		for (ServerPlayer p : ev.players()) convertAround(ev, p);
	}

	private void convertAround(ActiveEvent ev, ServerPlayer player) {
		ServerLevel level = ev.level();
		int room = MAX_CHICKENS - ev.entities().count();
		if (room <= 0) return;
		for (Mob mob : candidates(level, player.position(), room)) {
			if (ev.entities().count() >= MAX_CHICKENS || !ev.entities().canSpawn()) return;
			Chicken chicken = EntityTypes.CHICKEN.create(level, EntitySpawnReason.CONVERSION);
			if (chicken == null) continue;
			if (mob.isBaby()) chicken.setBaby(true);
			chicken.setPersistenceRequired();
			double x = mob.getX();
			double y = mob.getY() + mob.getBbHeight() / 2;
			double z = mob.getZ();
			if (ev.entities().replace(mob, chicken) == null) continue;
			level.sendParticles(ParticleTypes.POOF, x, y, z, 10, 0.3, 0.4, 0.3, 0.03);
			level.sendParticles(ParticleTypes.WHITE_SMOKE, x, y, z, 6, 0.3, 0.3, 0.3, 0.02);
		}
	}

	@Override
	public void onTick(ActiveEvent ev) {
		releaseDead(ev);
	}

	@Override
	public void onStop(ActiveEvent ev, StopReason reason) {
		releaseDead(ev); // also a chicken killed in this very tick
		// the framework turns every living chicken back right after this
		for (Entity e : ev.entities().list()) {
			ev.level().sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.3, e.getZ(), 10, 0.3, 0.4, 0.3, 0.03);
		}
	}

	/**
	 * A killed chicken stays in the level for its death animation (about a second) and would otherwise still be
	 * turned back (or saved with the original). It loses the original's data and leaves the tracker: the original is
	 * gone for good.
	 */
	private static void releaseDead(ActiveEvent ev) {
		for (Entity e : ev.entities().list()) {
			if (e instanceof LivingEntity living && living.isDeadOrDying()) {
				e.removeAttached(Marks.OWNER);
				ev.entities().forget(e);
			}
		}
	}

	@Override
	public Holder<SoundEvent> startSound() {
		return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.CHICKEN_EGG);
	}

	@Override
	public float startSoundPitch() {
		return 0.8F;
	}

	@Override
	public float startSoundVolume() {
		return 1.0F;
	}
}
