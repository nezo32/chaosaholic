package dev.chaosaholic.event.impl;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventContext;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodData;

/**
 * Bad: Hunger III for 30-45 s (0.015 exhaustion per tick: one saturation or food point every ~13 s).
 *
 * <p>Never starves anyone: vanilla starvation only hurts at food level 0 (and kills on Hard / Hardcore), so while
 * the event runs the food level of every affected player is kept at {@link #MIN_FOOD} or above, every tick, after
 * the players' own food tick; the starvation timer never runs. Refused on Peaceful, where the food bar never drops.
 */
public final class HungerGames extends ChaosEvent {
	/** Amplifier 2 = Hunger III. */
	public static final int AMPLIFIER = 2;
	/** Lowest food level while the event runs (half a drumstick): starvation needs 0. */
	public static final int MIN_FOOD = 1;

	public HungerGames() {
		super("hunger_games", Category.BAD, 30, 45);
	}

	@Override
	public boolean canStart(EventContext ctx) {
		return !ctx.isPeaceful();
	}

	@Override
	public void onPlayerAdded(ActiveEvent ev, ServerPlayer player) {
		ev.effects().give(player, MobEffects.HUNGER, AMPLIFIER);
		keepFed(player);
		ev.level().sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.6, player.getZ(), 6, 0.3, 0.2, 0.3, 0.02);
	}

	@Override
	public void onTick(ActiveEvent ev) {
		for (ServerPlayer p : ev.players()) keepFed(p);
	}

	/** Raises the food level to {@link #MIN_FOOD} if it dropped below. */
	private static void keepFed(ServerPlayer player) {
		FoodData food = player.getFoodData();
		if (food.getFoodLevel() < MIN_FOOD) food.setFoodLevel(MIN_FOOD);
	}

	@Override
	public Holder<SoundEvent> startSound() {
		return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.PLAYER_BURP);
	}

	@Override
	public float startSoundPitch() {
		return 0.6F;
	}
}
