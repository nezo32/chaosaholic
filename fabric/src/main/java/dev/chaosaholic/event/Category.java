package dev.chaosaholic.event;

import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;

/**
 * Event category: colour of the title / boss bar / list entries and the category "sting" played on start
 * (design/presentation.md §1). Bad events are announced with a title (you must notice danger), good and weird ones
 * on the actionbar.
 */
public enum Category {
	GOOD(ChatFormatting.GREEN, BossEvent.BossBarColor.GREEN, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.2F, false),
	BAD(ChatFormatting.RED, BossEvent.BossBarColor.RED, SoundEvents.NOTE_BLOCK_BASS.value(), 1.0F, 0.6F, true),
	WEIRD(ChatFormatting.LIGHT_PURPLE, BossEvent.BossBarColor.PURPLE, SoundEvents.NOTE_BLOCK_BIT.value(), 0.8F, 1.0F, false);

	private final ChatFormatting color;
	private final BossEvent.BossBarColor barColor;
	private final SoundEvent sting;
	private final float volume;
	private final float pitch;
	private final boolean title;

	Category(ChatFormatting color, BossEvent.BossBarColor barColor, SoundEvent sting, float volume, float pitch, boolean title) {
		this.color = color;
		this.barColor = barColor;
		this.sting = sting;
		this.volume = volume;
		this.pitch = pitch;
		this.title = title;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** {@code chaosaholic.category.good|bad|weird} */
	public String langKey() {
		return "chaosaholic.category." + id();
	}

	public ChatFormatting color() {
		return color;
	}

	public BossEvent.BossBarColor barColor() {
		return barColor;
	}

	public Holder<SoundEvent> sting() {
		return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sting);
	}

	public float volume() {
		return volume;
	}

	public float pitch() {
		return pitch;
	}

	/** Announce with title + subtitle (true) or on the actionbar (false). */
	public boolean usesTitle() {
		return title;
	}
}
