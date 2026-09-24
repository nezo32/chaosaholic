package dev.chaosaholic.event.impl;

import java.util.List;

import dev.chaosaholic.core.ChaosLimits;
import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventContext;
import dev.chaosaholic.event.StopReason;
import dev.chaosaholic.event.helper.Area;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Weird: for 30-60 s the mobs within {@link #RADIUS} blocks of each affected player (at most {@link #MAX_CHICKENS}
 * per instance, nearest first) become chickens, and turn back at the end.
 *
 * <p>Which mobs: {@link Mob}s that are {@link Area#isFairGame fair game} (no players, no bosses, nothing tamed, nothing
 * with a custom name - so name-tagged pets and named villagers are safe -, nothing already changed by another event,
 * nothing riding or ridden), minus chickens, leashed mobs and mobs holding a leash (the leash would snap), mobs in
 * water or lava (a fish or strider would come back wherever its chicken walked to), mobs that are not standing on
 * something (flying or falling), allays and happy ghasts, villagers with a profession and wandering traders (their
 * trades), and iron golems built by a player. Armor stands and other non-mob living entities are never touched.
 *
 * <p>How: {@code ev.entities().replace(mob, chicken)} keeps the original's full saved data (UUID, health, name, effects,
 * equipment, inventory, villager trades, ...) on the chicken; at the end the original is loaded back at the chicken's
 * position and rotation (where it fits: see OwnedEntities). The chicken is saved with that data, so a chunk unload
 * keeps it and the next load turns it back if the event is over, the same after a crash or a server restart; the
 * end of the event, a stop, the last player logging out, dying or leaving and the server stopping all restore it.
 *
 * <p>The chickens take damage only from players (and from the void and /kill): no fox, lava, cactus or suffocation
 * can cost a player the original. If a player kills a chicken, the original is gone for good (it drops what a chicken
 * drops, not the original's items; the dying chicken loses the original's data at once, see
 * OwnedEntities#onDeath). The chickens neither breed nor lay eggs (nothing is left behind). A chicken of a baby mob
 * is a chick. An extension converts newcomers up to the cap.
 */
public final class ChickenApocalypse extends ChaosEvent {
	public static final double RADIUS = 12.0;
	/** Chickens (converted mobs) per instance. */
	public static final int MAX_CHICKENS = 16;
	/** Breeding cooldown and egg timer kept on the chickens: longer than any run (extensions included). */
	public static final int NO_BREEDING_TICKS = ChaosLimits.MAX_REMAINING_TICKS + 20 * ChaosLimits.TICKS_PER_SECOND;

	public ChickenApocalypse() {
		super("chicken_apocalypse", Category.WEIRD, 30, 60);
	}

	/** Whether {@code e} may be turned into a chicken (see the class comment). */
	public static boolean isCandidate(Entity e) {
		if (!(e instanceof Mob mob) || e instanceof Chicken || !Area.isFairGame(e)) return false;
		if (e instanceof Allay || e instanceof HappyGhast) return false;
		if (e instanceof AbstractVillager && !(e instanceof Villager villager && hasNoTrades(villager))) return false; // wandering traders too
		if (e instanceof IronGolem golem && golem.isPlayerCreated()) return false;
		if (mob.isLeashed() || !Leashable.leashableLeashedTo(e).isEmpty()) return false;
		if (e.isInLiquid()) return false; // water or lava
		return isStanding(e);
	}

	/** Unemployed villagers and nitwits never trade. */
	private static boolean hasNoTrades(Villager villager) {
		var profession = villager.getVillagerData().profession();
		return profession.is(VillagerProfession.NONE) || profession.is(VillagerProfession.NITWIT);
	}

	/** On the ground, or something solid right under its feet (a mob that has not ticked yet is not on ground). */
	private static boolean isStanding(Entity e) {
		return e.onGround() || !e.level().noCollision(e, e.getBoundingBox().move(0.0, -0.25, 0.0));
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
			noOffspring(chicken);
			chicken.setPersistenceRequired();
			double x = mob.getX();
			double y = mob.getY() + mob.getBbHeight() / 2;
			double z = mob.getZ();
			if (ev.entities().replace(mob, chicken) == null) continue;
			level.sendParticles(ParticleTypes.POOF, x, y, z, 10, 0.3, 0.4, 0.3, 0.03);
			level.sendParticles(ParticleTypes.WHITE_SMOKE, x, y, z, 6, 0.3, 0.3, 0.3, 0.02);
		}
	}

	/** No breeding and no eggs (a chick that grew up during the run is caught by the next {@link #onTick}). */
	private static void noOffspring(Chicken chicken) {
		chicken.eggTime = Math.max(chicken.eggTime, NO_BREEDING_TICKS);
		if (chicken.isBaby()) return;
		if (chicken.getAge() < NO_BREEDING_TICKS / 2) chicken.setAge(NO_BREEDING_TICKS);
		chicken.resetLove();
	}

	@Override
	public void onTick(ActiveEvent ev) {
		if (!ev.every(ChaosLimits.TICKS_PER_SECOND)) return;
		for (Entity e : ev.entities().list()) if (e instanceof Chicken chicken) noOffspring(chicken);
	}

	/** The chickens are hurt only by players (directly or by their projectiles), the void and /kill. */
	@Override
	public boolean allowDamage(ActiveEvent ev, LivingEntity entity, DamageSource source, float amount) {
		if (!ev.entities().owns(entity)) return true;
		return source.getEntity() instanceof Player || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
	}

	@Override
	public void onStop(ActiveEvent ev, StopReason reason) {
		// the framework turns every living chicken back right after this (a dying one is just discarded)
		for (Entity e : ev.entities().list()) {
			ev.level().sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.3, e.getZ(), 10, 0.3, 0.4, 0.3, 0.03);
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
