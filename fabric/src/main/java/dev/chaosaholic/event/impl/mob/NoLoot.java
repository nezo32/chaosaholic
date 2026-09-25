package dev.chaosaholic.event.impl.mob;

import dev.chaosaholic.Chaosaholic;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Makes an event-spawned mob worthless to farm: no loot table drops, no equipment drops, no experience, no item
 * pickup (so a mob that is discarded at the end never takes a player's items with it) and no zombie reinforcements
 * (which would be ordinary, permanent mobs). Call on a freshly created mob, after finalizeSpawn and before spawning.
 */
public final class NoLoot {
	/**
	 * Death loot table of event mobs. It does not exist, and the server resolves unknown tables to the empty table
	 * (ReloadableServerRegistries#getLootTable), so the mob drops nothing.
	 */
	public static final ResourceKey<LootTable> NO_LOOT = ResourceKey.create(Registries.LOOT_TABLE,
			Identifier.fromNamespaceAndPath(Chaosaholic.MOD_ID, "entities/no_loot"));

	private NoLoot() {}

	public static void apply(Mob mob) {
		// Mob has no setter for its death loot table: set it through its own saved data ("DeathLootTable").
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, mob.registryAccess());
		mob.saveWithoutId(out);
		var tag = out.buildResult();
		tag.putString("DeathLootTable", NO_LOOT.identifier().toString());
		mob.load(TagValueInput.create(ProblemReporter.DISCARDING, mob.registryAccess(), tag));
		for (EquipmentSlot slot : EquipmentSlot.values()) mob.setDropChance(slot, 0.0F);
		mob.setCanPickUpLoot(false);
		mob.skipDropExperience();
		AttributeInstance reinforcements = mob.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
		if (reinforcements != null) reinforcements.setBaseValue(0.0);
	}

	/** True if {@code mob} got {@link #apply} (for tests). */
	public static boolean isApplied(Mob mob) {
		return mob.getLootTable().filter(NO_LOOT::equals).isPresent() && mob.wasExperienceConsumed() && !mob.canPickUpLoot();
	}
}
