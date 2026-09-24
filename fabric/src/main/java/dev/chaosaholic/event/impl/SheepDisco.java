package dev.chaosaholic.event.impl;

import java.util.List;
import java.util.Optional;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventContext;
import dev.chaosaholic.event.helper.Marks;
import dev.chaosaholic.event.helper.Spots;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;

/**
 * Weird: {@link #MIN_SHEEP}-{@link #MAX_SHEEP} rainbow sheep (named {@value #JEB}, the game's rainbow Easter egg; the
 * name is not always shown, only while a player looks directly at a sheep, like any hidden custom name) appear
 * {@link #MIN_DISTANCE}-{@link #MAX_DISTANCE} blocks around each affected player for 30-45 s, with a looping
 * note-block tune, NOTE particles over every sheep and little dance hops.
 *
 * <p>The sheep are owned entities ({@code ev.entities()}): removed at the end (never dropping anything), never saved
 * (chunk unload / crash leave nothing), capped by the owned-entity caps. No farming: they take no damage ({@link #allowDamage}: no
 * mutton, no wool, no XP from killing them; only /kill or the void, which bypass invulnerability, can remove one early),
 * cannot be sheared (the {@code SheepMixin} makes {@code readyForShearing} false for them: players, dispensers and
 * golems alike) and cannot breed (a breeding cooldown longer than the event). Dyeing them only wastes the dye.
 * Harmless in every difficulty, Peaceful included; {@link #canStart} is false when no spot fits a sheep.
 */
public final class SheepDisco extends ChaosEvent {
	/** Game Easter egg name (the client renders sheep with this name in cycling rainbow colours), not player text. */
	public static final String JEB = "jeb_";
	/** Scoreboard tag of the disco sheep (SheepMixin: not shearable). */
	public static final String DISCO_TAG = "chaosaholic_disco";
	public static final int MIN_SHEEP = 3;
	public static final int MAX_SHEEP = 5;
	public static final double MIN_DISTANCE = 2.0;
	public static final double MAX_DISTANCE = 5.0;
	/** Positive age = breeding cooldown in ticks; longer than any run (180 s cap), so the sheep never breed. */
	public static final int NO_BREEDING_AGE = 6000;
	/** NOTE particles and dance hops every half second. */
	public static final int PARTICLE_TICKS = 10;
	/** One note of the tune every 5 ticks (a steady beat of 4 notes per second). */
	public static final int BEAT_TICKS = 5;
	/** Start arpeggio (presentation.md): NOTE_BLOCK_BIT at these pitches, {@link #ARPEGGIO_TICKS} apart. */
	private static final float[] ARPEGGIO = {1.0F, 1.26F, 1.5F, 2.0F};
	private static final int ARPEGGIO_TICKS = 4;
	/** The loop: note-block semitones (0-24, 12 = pitch 1.0), -1 = rest. 16 beats = 4 s. */
	private static final int[] TUNE = {6, 13, 10, 13, 6, 13, 10, 18, 8, 15, 11, 15, 8, 15, 11, -1};
	/** Kick drum on every 4th beat. */
	private static final int KICK_EVERY = 4;

	public SheepDisco() {
		super("sheep_disco", Category.WEIRD, 30, 45);
	}

	@Override
	public boolean canStart(EventContext ctx) {
		return Spots.near(ctx.level(), ctx.trigger().position(), MIN_DISTANCE, MAX_DISTANCE, ctx.random(), EntityTypes.SHEEP).isPresent();
	}

	@Override
	public void onPlayerAdded(ActiveEvent ev, ServerPlayer player) {
		spawnSheep(ev, player);
		for (int i = 0; i < ARPEGGIO.length; i++) {
			float pitch = ARPEGGIO[i];
			ev.schedule(i * ARPEGGIO_TICKS, () -> {
				if (ev.isAffected(player)) note(ev.level(), player.position(), SoundEvents.NOTE_BLOCK_BIT, 0.8F, pitch);
			});
		}
	}

	/** Spawns this player's sheep (random count, capped by the owned-entity caps). Returns how many spawned. */
	public static int spawnSheep(ActiveEvent ev, ServerPlayer player) {
		ServerLevel level = ev.level();
		RandomSource random = ev.random();
		int wanted = MIN_SHEEP + random.nextInt(MAX_SHEEP - MIN_SHEEP + 1);
		int spawned = 0;
		for (int i = 0; i < wanted && ev.entities().canSpawn(); i++) {
			Optional<Vec3> spot = Spots.near(level, player.position(), MIN_DISTANCE, MAX_DISTANCE, random, EntityTypes.SHEEP);
			if (spot.isEmpty()) continue;
			Sheep sheep = EntityTypes.SHEEP.create(level, EntitySpawnReason.EVENT);
			if (sheep == null) continue;
			Vec3 at = spot.get();
			sheep.snapTo(at.x, at.y, at.z, random.nextFloat() * 360.0F, 0.0F);
			sheep.setColor(DyeColor.byId(random.nextInt(16)));
			sheep.setCustomName(Component.literal(JEB));
			sheep.setCustomNameVisible(false);
			sheep.setAge(NO_BREEDING_AGE);
			sheep.addTag(DISCO_TAG);
			if (ev.entities().spawn(sheep) == null) break;
			spawned++;
			level.sendParticles(ParticleTypes.NOTE, at.x, at.y + 1.4, at.z, 0, random.nextDouble(), 0.0, 0.0, 1.0);
		}
		return spawned;
	}

	@Override
	public void onTick(ActiveEvent ev) {
		ServerLevel level = ev.level();
		if (ev.every(PARTICLE_TICKS)) {
			List<Entity> sheep = ev.entities().list();
			for (int i = 0; i < sheep.size(); i++) {
				Entity e = sheep.get(i);
				// count 0 = one particle whose x speed picks the colour (0..1 across the note-block hues)
				double hue = ((ev.age() / PARTICLE_TICKS + i * 5) % 25) / 24.0;
				level.sendParticles(ParticleTypes.NOTE, e.getX(), e.getY() + e.getBbHeight() + 0.4, e.getZ(), 0, hue, 0.0, 0.0, 1.0);
				if (e.onGround() && ev.random().nextInt(3) == 0) e.push(0.0, 0.35, 0.0); // dance hop
			}
		}
		if (ev.every(BEAT_TICKS)) {
			int beat = ev.age() / BEAT_TICKS;
			int semitone = TUNE[beat % TUNE.length];
			for (ServerPlayer p : ev.players()) {
				if (semitone >= 0) note(level, p.position(), SoundEvents.NOTE_BLOCK_BIT, 0.5F, pitch(semitone));
				if (beat % KICK_EVERY == 0) note(level, p.position(), SoundEvents.NOTE_BLOCK_BASEDRUM, 0.6F, 1.0F);
			}
		}
	}

	/**
	 * The sheep take no damage at all (so no mutton, wool or XP from killing them), except from sources that bypass
	 * invulnerability (/kill, the void). Done through the routed ALLOW_DAMAGE hook: Entity#setInvulnerable exists
	 * only on 26.2 (26.3 renamed it).
	 */
	@Override
	public boolean allowDamage(ActiveEvent ev, LivingEntity entity, DamageSource source, float amount) {
		return !ev.entities().owns(entity) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
	}

	/** True for a sheep spawned by sheep_disco (owned and tagged): never shearable. */
	public static boolean isDiscoSheep(Entity entity) {
		return entity.entityTags().contains(DISCO_TAG) && entity.hasAttached(Marks.OWNER);
	}

	/** Note-block pitch of a semitone 0..24 (12 = 1.0). */
	static float pitch(int semitone) {
		return (float) Math.pow(2.0, (semitone - 12) / 12.0);
	}

	private static void note(ServerLevel level, Vec3 at, Holder<SoundEvent> sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.RECORDS, volume, pitch);
	}

	@Override
	public Holder<SoundEvent> startSound() {
		return SoundEvents.NOTE_BLOCK_BIT;
	}
}
