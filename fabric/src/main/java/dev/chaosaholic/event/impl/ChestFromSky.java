package dev.chaosaholic.event.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventContext;
import dev.chaosaholic.event.helper.Spots;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Good, instant: a chest filled from a random vanilla structure loot table ({@link #LOOT_TABLES}) lands on a safe spot
 * {@link #MIN_DISTANCE}-{@link #MAX_DISTANCE} blocks (horizontal) from each affected player, facing them. The chest
 * is a normal, permanent block: it is the reward, so no tracker (nothing is reverted). The loot is rolled when the
 * chest is first opened (vanilla loot-table chest). The "fall" is visual: an end-rod trail down onto the spot, then
 * a poof and a wind burst.
 *
 * <p>Safety: the chest only goes into an <em>air</em> block (never replaces plants, snow, fluids or any other block,
 * so never a container) that {@link Spots} rates safe for a player-sized box (sturdy floor, no hazard, headroom),
 * inside the world border, with no living entity in it (never on a player). No such spot → {@link #canStart} is
 * false and another event is rolled.
 */
public final class ChestFromSky extends ChaosEvent {
	public static final String ID = "chest_from_sky";
	public static final int MIN_DISTANCE = 2;
	public static final int MAX_DISTANCE = 5;
	/** Height of the visual trail above the chest. */
	public static final int TRAIL_HEIGHT = 8;

	/** Structure chest tables present in 26.2 and 26.3, early-to-mid game value. */
	public static final List<ResourceKey<LootTable>> LOOT_TABLES = List.of(
			BuiltInLootTables.SIMPLE_DUNGEON,
			BuiltInLootTables.ABANDONED_MINESHAFT,
			BuiltInLootTables.DESERT_PYRAMID,
			BuiltInLootTables.JUNGLE_TEMPLE,
			BuiltInLootTables.SHIPWRECK_TREASURE,
			BuiltInLootTables.BURIED_TREASURE,
			BuiltInLootTables.VILLAGE_WEAPONSMITH,
			BuiltInLootTables.PILLAGER_OUTPOST,
			BuiltInLootTables.RUINED_PORTAL,
			BuiltInLootTables.IGLOO_CHEST);

	public ChestFromSky() {
		super("chest_from_sky", Category.GOOD, INSTANT, INSTANT);
	}

	@Override
	public boolean canStart(EventContext ctx) {
		for (ServerPlayer p : ctx.players()) {
			if (findSpot(ctx.level(), p.blockPosition(), null).isPresent()) return true;
		}
		return false;
	}

	@Override
	public void onPlayerAdded(ActiveEvent ev, ServerPlayer player) {
		ServerLevel level = ev.level();
		Optional<BlockPos> spot = findSpot(level, player.blockPosition(), ev.random());
		if (spot.isEmpty()) return; // world scope: this player has no room (another one made canStart true)
		BlockPos pos = spot.get();
		BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing(pos, player.position()));
		if (!level.setBlockAndUpdate(pos, chest)) return;
		ResourceKey<LootTable> table = LOOT_TABLES.get(ev.random().nextInt(LOOT_TABLES.size()));
		RandomizableContainer.setBlockEntityLootTable(level, ev.random(), pos, table);
		double x = pos.getX() + 0.5;
		double z = pos.getZ() + 0.5;
		for (int i = 0; i < TRAIL_HEIGHT * 2; i++) {
			level.sendParticles(ParticleTypes.END_ROD, x, pos.getY() + 1.0 + i * 0.5, z, 1, 0.05, 0.1, 0.05, 0.0);
		}
		level.sendParticles(ParticleTypes.POOF, x, pos.getY() + 0.3, z, 12, 0.4, 0.2, 0.4, 0.02);
		level.playSound(null, x, pos.getY() + 0.5, z, SoundEvents.WIND_CHARGE_BURST, SoundSource.BLOCKS, 0.5F, 1.2F);
	}

	/**
	 * A spot for the chest {@link #MIN_DISTANCE}-{@link #MAX_DISTANCE} blocks from {@code center}: columns in random
	 * order when {@code random} is given, a fixed order otherwise (canStart: side-effect free, same answer).
	 */
	public static Optional<BlockPos> findSpot(ServerLevel level, BlockPos center, @Nullable RandomSource random) {
		List<int[]> columns = new ArrayList<>();
		for (int dx = -MAX_DISTANCE; dx <= MAX_DISTANCE; dx++) {
			for (int dz = -MAX_DISTANCE; dz <= MAX_DISTANCE; dz++) {
				int d2 = dx * dx + dz * dz;
				if (d2 >= MIN_DISTANCE * MIN_DISTANCE && d2 <= MAX_DISTANCE * MAX_DISTANCE) columns.add(new int[] {dx, dz});
			}
		}
		if (random != null) {
			for (int i = columns.size() - 1; i > 0; i--) {
				int j = random.nextInt(i + 1);
				int[] t = columns.get(i);
				columns.set(i, columns.get(j));
				columns.set(j, t);
			}
		}
		for (int[] c : columns) {
			Optional<Vec3> feet = Spots.column(level, center.getX() + c[0], center.getY(), center.getZ() + c[1], EntityTypes.PLAYER);
			if (feet.isEmpty()) continue;
			BlockPos pos = BlockPos.containing(feet.get());
			if (canPlace(level, pos)) return Optional.of(pos);
		}
		return Optional.empty();
	}

	/** Air (nothing is ever replaced), inside the border, and no living entity (a player) standing in the block. */
	public static boolean canPlace(ServerLevel level, BlockPos pos) {
		if (!level.getBlockState(pos).isAir() || !level.getWorldBorder().isWithinBounds(pos)) return false;
		return level.getEntities((Entity) null, new AABB(pos), e -> e instanceof LivingEntity).isEmpty();
	}

	/** The chest's front looks at the player. */
	static Direction facing(BlockPos chest, Vec3 player) {
		double dx = player.x - (chest.getX() + 0.5);
		double dz = player.z - (chest.getZ() + 0.5);
		if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? Direction.EAST : Direction.WEST;
		return dz > 0 ? Direction.SOUTH : Direction.NORTH;
	}

	@Override
	public Holder<SoundEvent> startSound() {
		return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.CHEST_OPEN);
	}

	@Override
	public float startSoundPitch() {
		return 0.8F;
	}
}
