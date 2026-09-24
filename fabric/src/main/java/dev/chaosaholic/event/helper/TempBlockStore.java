package dev.chaosaholic.event.helper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.chaosaholic.Chaosaholic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Every temporary block of every running event instance, saved as {@code data/chaosaholic/temp_blocks.dat}. Normally
 * empty on disk (instances restore their blocks when they end, and SERVER_STOPPING ends them all before the final
 * save); after a crash, SERVER_STARTED restores whatever is still listed.
 */
public final class TempBlockStore extends SavedData {
	public record Entry(ResourceKey<Level> dimension, BlockPos pos, BlockState original, BlockState placed, UUID owner) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Entry::dimension),
				BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
				BlockState.CODEC.fieldOf("original").forGetter(Entry::original),
				BlockState.CODEC.fieldOf("placed").forGetter(Entry::placed),
				UUIDUtil.CODEC.fieldOf("owner").forGetter(Entry::owner)
		).apply(i, Entry::new));
	}

	public static final Codec<TempBlockStore> CODEC = Entry.CODEC.listOf().optionalFieldOf("blocks", List.of())
			.xmap(TempBlockStore::new, s -> List.copyOf(s.entries)).codec();

	public static final SavedDataType<TempBlockStore> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath(Chaosaholic.MOD_ID, "temp_blocks"), TempBlockStore::new, CODEC, null);

	private final List<Entry> entries;
	private boolean unsaved;

	public TempBlockStore() {
		this(List.of());
	}

	private TempBlockStore(List<Entry> entries) {
		this.entries = new ArrayList<>(entries);
	}

	public static TempBlockStore get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public List<Entry> entries() {
		return List.copyOf(entries);
	}

	public boolean contains(ResourceKey<Level> dimension, BlockPos pos) {
		for (Entry e : entries) if (e.dimension().equals(dimension) && e.pos().equals(pos)) return true;
		return false;
	}

	public int count(UUID owner) {
		int n = 0;
		for (Entry e : entries) if (e.owner().equals(owner)) n++;
		return n;
	}

	void add(Entry entry) {
		entries.add(entry);
		changed();
	}

	/** Restores (and forgets) every block of {@code owner}. */
	void restore(MinecraftServer server, UUID owner) {
		List<Entry> mine = new ArrayList<>();
		entries.removeIf(e -> {
			if (!e.owner().equals(owner)) return false;
			mine.add(e);
			return true;
		});
		if (mine.isEmpty()) return;
		changed();
		for (Entry e : mine) restore(server, e);
	}

	/** SERVER_STARTED: nothing runs yet, so every listed block is a leftover of a crash. */
	public static void restoreAll(MinecraftServer server) {
		TempBlockStore store = get(server);
		if (store.entries.isEmpty()) return;
		List<Entry> all = new ArrayList<>(store.entries);
		store.entries.clear();
		store.changed();
		for (Entry e : all) restore(server, e);
		Chaosaholic.LOGGER.info("Restored {} temporary chaos blocks left over from the last run", all.size());
	}

	/** Puts the original back if the block is still what the event placed (a player's change is kept). */
	private static void restore(MinecraftServer server, Entry e) {
		ServerLevel level = server.getLevel(e.dimension());
		if (level == null) return;
		if (level.getBlockState(e.pos()) == e.placed()) {
			level.setBlock(e.pos(), e.original(), Block.UPDATE_ALL);
		}
	}

	private void changed() {
		setDirty();
		unsaved = true;
	}

	/** EventManager, once a second: write changes right away so a crash cannot lose them. */
	public static void flush(MinecraftServer server) {
		TempBlockStore store = server.getDataStorage().get(TYPE);
		if (store == null || !store.unsaved) return;
		store.unsaved = false;
		server.getDataStorage().scheduleSave();
	}
}
