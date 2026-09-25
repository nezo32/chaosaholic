package dev.chaosaholic.event.impl;

import java.util.List;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventManager;
import dev.chaosaholic.event.helper.Marks;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Good: for 60-120 s, every mob an affected player kills drops 1 to {@link #MAX_STACKS_PER_KILL} extra stacks rolled
 * from {@link #POOL} (plain survival items: food, ingots, gems, arrows, a rare golden apple or diamond; nothing
 * Creative-only or operator-only), with a firework puff. At most {@link #MAX_STACKS_PER_EVENT} bonus stacks per run
 * (shared by all players of a world-scope run, not reset by extensions), so spawner farms cannot turn it into an
 * unlimited source. Rewards are normal items (they stay after the end); nothing to clean up.
 *
 * <p>Only real mobs count ({@link Mob}: no armor stands) and never entities spawned or swapped in by another chaos
 * event (they carry {@link Marks#OWNER}). The {@code mob_drops} game rule off disables the bonus, like vanilla loot.
 */
public final class LootPinata extends ChaosEvent {
	public static final String ID = "loot_pinata";
	/** Bonus stacks per kill: 1..3. */
	public static final int MAX_STACKS_PER_KILL = 3;
	/** Bonus stacks per run. */
	public static final int MAX_STACKS_PER_EVENT = 48;

	/** One pool entry: {@code weight} out of the pool total, count uniform in [min, max]. */
	public record Prize(Item item, int min, int max, int weight) {
		ItemStack roll(RandomSource random) {
			return new ItemStack(item, min + random.nextInt(max - min + 1));
		}
	}

	/** The prize pool: survival items only, each stack at most its item's max stack size. */
	public static final List<Prize> POOL = List.of(
			new Prize(Items.BREAD, 1, 3, 12),
			new Prize(Items.COOKED_BEEF, 1, 3, 10),
			new Prize(Items.COOKIE, 2, 5, 8),
			new Prize(Items.APPLE, 1, 3, 8),
			new Prize(Items.ARROW, 4, 10, 10),
			new Prize(Items.TORCH, 4, 8, 8),
			new Prize(Items.COAL, 2, 5, 10),
			new Prize(Items.COPPER_INGOT, 2, 5, 8),
			new Prize(Items.IRON_INGOT, 1, 3, 8),
			new Prize(Items.GOLD_INGOT, 1, 3, 6),
			new Prize(Items.REDSTONE, 2, 6, 6),
			new Prize(Items.LAPIS_LAZULI, 2, 6, 6),
			new Prize(Items.SLIME_BALL, 1, 3, 4),
			new Prize(Items.FIREWORK_ROCKET, 1, 3, 5),
			new Prize(Items.EXPERIENCE_BOTTLE, 1, 3, 4),
			new Prize(Items.EMERALD, 1, 2, 4),
			new Prize(Items.GOLDEN_CARROT, 1, 3, 4),
			new Prize(Items.ENDER_PEARL, 1, 1, 3),
			new Prize(Items.NAME_TAG, 1, 1, 2),
			new Prize(Items.CAKE, 1, 1, 2),
			new Prize(Items.GOLDEN_APPLE, 1, 1, 2),
			new Prize(Items.DIAMOND, 1, 1, 1));

	private static final int TOTAL_WEIGHT = POOL.stream().mapToInt(Prize::weight).sum();

	/** Per-run state: bonus stacks dropped so far. */
	static final class State {
		int dropped;
	}

	public LootPinata() {
		super("loot_pinata", Category.GOOD, 60, 120);
	}

	@Override
	public void onPlayerAdded(ActiveEvent ev, ServerPlayer player) {
		ev.level().sendParticles(ParticleTypes.FIREWORK, player.getX(), player.getY() + 1.0, player.getZ(), 16, 0.6, 0.6, 0.6, 0.05);
	}

	@Override
	public void afterKill(ActiveEvent ev, ServerPlayer killer, LivingEntity victim, DamageSource source) {
		if (!ev.isAffected(killer) || !EventManager.isEligible(killer)) return;
		if (!(victim instanceof Mob) || victim.hasAttached(Marks.OWNER)) return;
		if (!(victim.level() instanceof ServerLevel level) || !level.getGameRules().get(GameRules.MOB_DROPS)) return;
		State state = ev.state(State::new);
		int room = MAX_STACKS_PER_EVENT - state.dropped;
		if (room <= 0) return;
		RandomSource random = ev.random();
		int count = Math.min(room, 1 + random.nextInt(MAX_STACKS_PER_KILL));
		for (int i = 0; i < count; i++) victim.spawnAtLocation(level, pick(random).roll(random));
		state.dropped += count;
		level.sendParticles(ParticleTypes.FIREWORK, victim.getX(), victim.getY() + victim.getBbHeight() / 2, victim.getZ(), 12, 0.3, 0.3, 0.3, 0.08);
	}

	/** Bonus stacks dropped so far by this run (tests, caps). */
	public static int dropped(ActiveEvent ev) {
		return ev.state(State::new).dropped;
	}

	/** Weighted pick from {@link #POOL}. */
	public static Prize pick(RandomSource random) {
		int roll = random.nextInt(TOTAL_WEIGHT);
		for (Prize prize : POOL) {
			roll -= prize.weight();
			if (roll < 0) return prize;
		}
		return POOL.getLast();
	}

	@Override
	public Holder<SoundEvent> startSound() {
		return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.FIREWORK_ROCKET_TWINKLE);
	}

	@Override
	public float startSoundPitch() {
		return 1.2F;
	}
}
