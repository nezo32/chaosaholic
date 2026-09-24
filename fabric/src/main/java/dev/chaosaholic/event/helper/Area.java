package dev.chaosaholic.event.helper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import dev.chaosaholic.core.ChaosLimits;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Capped area queries. The radius is clamped to {@link ChaosLimits#MAX_RADIUS} and at most
 * {@link ChaosLimits#MAX_AREA_ENTITIES} entities are returned, nearest first; the search is a sphere.
 */
public final class Area {
	private Area() {}

	public static <T extends Entity> List<T> entities(ServerLevel level, Vec3 center, double radius, Class<T> type, Predicate<? super T> filter) {
		return entities(level, center, radius, type, filter, ChaosLimits.MAX_AREA_ENTITIES);
	}

	public static <T extends Entity> List<T> entities(ServerLevel level, Vec3 center, double radius, Class<T> type,
			Predicate<? super T> filter, int max) {
		double r = ChaosLimits.clampRadius(radius);
		int limit = Math.max(0, Math.min(max, ChaosLimits.MAX_AREA_ENTITIES));
		if (r <= 0 || limit == 0) return List.of();
		double r2 = r * r;
		List<T> found = new ArrayList<>(level.getEntitiesOfClass(type, new AABB(center, center).inflate(r),
				e -> e.isAlive() && !e.isRemoved() && e.position().distanceToSqr(center) <= r2 && filter.test(e)));
		found.sort(Comparator.comparingDouble(e -> e.position().distanceToSqr(center)));
		return found.size() > limit ? new ArrayList<>(found.subList(0, limit)) : found;
	}

	/**
	 * Default "fair game" filter for mob-changing events: living, not a player, not a boss (dragon, wither, warden,
	 * elder guardian), not tamed / owned by a player, not named with a name tag, not owned by an event, not riding.
	 */
	public static boolean isFairGame(Entity e) {
		if (!(e instanceof LivingEntity) || e instanceof Player) return false;
		if (e instanceof EnderDragon || e instanceof WitherBoss || e instanceof Warden || e instanceof ElderGuardian) return false;
		if (e instanceof OwnableEntity ownable && ownable.getOwnerReference() != null) return false;
		if (e.hasCustomName() || e.hasAttached(Marks.OWNER) || e.hasAttached(Marks.NAME)) return false;
		return !e.isPassenger() && !e.isVehicle();
	}

	/** Living non-player entities around {@code center} that pass {@link #isFairGame}. */
	public static List<LivingEntity> mobs(ServerLevel level, Vec3 center, double radius) {
		return entities(level, center, radius, LivingEntity.class, Area::isFairGame);
	}
}
