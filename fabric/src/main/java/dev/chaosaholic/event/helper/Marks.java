package dev.chaosaholic.event.helper;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.chaosaholic.Chaosaholic;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;

/**
 * Persistent Fabric data attachments that make entity changes crash- and unload-safe. They are saved with the
 * entity; when an entity carrying one is loaded while its owner instance is not running (server restart, crash,
 * chunk reloaded after the event ended), {@link OwnedEntities#onEntityLoad} reverts it.
 */
public final class Marks {
	/** Scoreboard tag on every Chaosaholic-owned entity (visible in /data, usable in selectors). */
	public static final String OWNED_TAG = "chaosaholic_owned";

	/**
	 * On an owned entity: the owner instance and, for a replacement (e.g. a chicken standing in for a zombie), the
	 * full saved data of the original to restore. Owned entities without {@code restore} are never saved to disk
	 * (EntityMixin), so they vanish with their chunk or a crash.
	 */
	public record OwnerMark(UUID owner, Optional<CompoundTag> restore) {
		public static final Codec<OwnerMark> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("owner").forGetter(OwnerMark::owner),
				CompoundTag.CODEC.optionalFieldOf("restore").forGetter(OwnerMark::restore)
		).apply(i, OwnerMark::new));
	}

	/** On a renamed entity: the owner instance and the original custom name / visibility. */
	public record NameMark(UUID owner, Optional<Component> name, boolean visible) {
		public static final Codec<NameMark> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("owner").forGetter(NameMark::owner),
				ComponentSerialization.CODEC.optionalFieldOf("name").forGetter(NameMark::name),
				Codec.BOOL.optionalFieldOf("visible", false).forGetter(NameMark::visible)
		).apply(i, NameMark::new));
	}

	public static final AttachmentType<OwnerMark> OWNER = AttachmentRegistry.createPersistent(
			Identifier.fromNamespaceAndPath(Chaosaholic.MOD_ID, "owner"), OwnerMark.CODEC);

	public static final AttachmentType<NameMark> NAME = AttachmentRegistry.createPersistent(
			Identifier.fromNamespaceAndPath(Chaosaholic.MOD_ID, "name"), NameMark.CODEC);

	private Marks() {}

	/** Forces class loading (attachment registration) from Chaosaholic#onInitialize. */
	public static void register() {}
}
