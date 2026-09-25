package dev.chaosaholic.event.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.RemoveReason;
import dev.chaosaholic.event.helper.SafeLanding;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Weird: for 30-60 s landing bounces the player back up like a slime block, with no fall damage. Two transient
 * modifiers: BOUNCINESS {@link #BOUNCINESS} (ADD_VALUE, restitution 0..1) and FALL_DAMAGE_MULTIPLIER -1
 * (ADD_MULTIPLIED_TOTAL: 0). Both attributes are synced, so the client (even a vanilla one) predicts the bounce.
 * Sneaking suppresses the bounce (vanilla). Fall damage ({@link DamageTypes#FALL} only: ender pearls still hurt) of
 * affected players is also cancelled in {@link #allowDamage} as a second guard. {@code ITEM_SLIME} particles on every
 * hard landing. A player still in the air (mid-bounce or falling) when they leave the event (end, stop, logout,
 * dimension change, Creative) gets the {@link SafeLanding} Slow Falling tail, so the last bounce never lands with
 * full damage.
 */
public final class BouncyFloor extends ChaosEvent {
	/** Restitution: a landing keeps 80 % of the vertical speed. */
	public static final double BOUNCINESS = 0.8;
	/** ADD_MULTIPLIED_TOTAL -1: fall damage multiplier 0. */
	public static final double NO_FALL_DAMAGE = -1.0;
	/** Fall distance (blocks) from which a landing shows the bounce particles. */
	public static final double PARTICLE_FALL = 1.5;

	/** Last seen fall distance per player: the server resets it on the landing tick itself. */
	private static final class State {
		final Map<UUID, Double> fall = new HashMap<>();
	}

	public BouncyFloor() {
		super("bouncy_floor", Category.WEIRD, 30, 60);
	}

	@Override
	public void onPlayerAdded(ActiveEvent ev, ServerPlayer player) {
		ev.modifiers().add(player, Attributes.BOUNCINESS, BOUNCINESS, AttributeModifier.Operation.ADD_VALUE);
		ev.modifiers().add(player, Attributes.FALL_DAMAGE_MULTIPLIER, NO_FALL_DAMAGE, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		ev.level().sendParticles(ParticleTypes.ITEM_SLIME, player.getX(), player.getY() + 0.2, player.getZ(), 16, 0.5, 0.1, 0.5, 0.1);
	}

	@Override
	public void onTick(ActiveEvent ev) {
		State s = ev.state(State::new);
		for (ServerPlayer p : ev.players()) {
			double before = s.fall.getOrDefault(p.getUUID(), 0.0);
			if (p.onGround()) {
				if (before >= PARTICLE_FALL) {
					ev.level().sendParticles(ParticleTypes.ITEM_SLIME, p.getX(), p.getY() + 0.1, p.getZ(), 10, 0.4, 0.05, 0.4, 0.1);
				}
				s.fall.remove(p.getUUID());
			} else if (p.fallDistance > 0.0) {
				s.fall.put(p.getUUID(), p.fallDistance);
			}
		}
	}

	@Override
	public void onPlayerRemoved(ActiveEvent ev, ServerPlayer player, RemoveReason reason) {
		ev.state(State::new).fall.remove(player.getUUID());
		SafeLanding.give(player, reason);
	}

	@Override
	public boolean allowDamage(ActiveEvent ev, LivingEntity entity, DamageSource source, float amount) {
		return !(ev.isAffected(entity) && source.is(DamageTypes.FALL));
	}

	@Override
	public Holder<SoundEvent> startSound() {
		return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.SLIME_JUMP);
	}
}
