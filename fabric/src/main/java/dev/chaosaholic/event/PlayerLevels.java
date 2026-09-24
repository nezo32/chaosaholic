package dev.chaosaholic.event;

import com.mojang.serialization.Codec;
import dev.chaosaholic.Chaosaholic;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;

/**
 * The anti-farming mark (highest level reached since the last death, see core.LevelMark), saved with the player.
 * Not copied on death: the respawned player starts without one, which re-initializes it to the current level.
 */
public final class PlayerLevels {
	public static final AttachmentType<Integer> MARK = AttachmentRegistry.create(
			Identifier.fromNamespaceAndPath(Chaosaholic.MOD_ID, "level_mark"), builder -> builder.persistent(Codec.INT));

	private PlayerLevels() {}

	/** Forces class loading (attachment registration) from Chaosaholic#onInitialize. */
	public static void register() {}
}
