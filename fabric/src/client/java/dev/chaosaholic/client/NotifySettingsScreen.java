package dev.chaosaholic.client;

import java.util.function.BiFunction;
import java.util.function.Function;

import dev.chaosaholic.core.NotifySettings;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Event sound / event messages / screen effects ON|OFF and Done. Every toggle is saved right away. Opened from Mod Menu. */
public class NotifySettingsScreen extends Screen {
	private static final String SOUND = "chaosaholic.settings.notifySound";
	private static final String MESSAGE = "chaosaholic.settings.notifyMessage";
	private static final String EFFECTS = "chaosaholic.settings.screenEffects";

	private final @Nullable Screen parent;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

	public NotifySettingsScreen(@Nullable Screen parent) {
		super(Component.translatable("chaosaholic.settings.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		layout.addTitleHeader(this.title, this.font);
		LinearLayout contents = layout.addToContents(LinearLayout.vertical().spacing(8));
		contents.addChild(toggle(SOUND, NotifySettings::sound, NotifySettings::withSound));
		contents.addChild(toggle(MESSAGE, NotifySettings::message, NotifySettings::withMessage));
		contents.addChild(toggle(EFFECTS, NotifySettings::effects, NotifySettings::withEffects));
		layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).width(200).build());
		layout.visitWidgets(this::addRenderableWidget);
		repositionElements();
	}

	private static CycleButton<Boolean> toggle(String key, Function<NotifySettings, Boolean> getter,
			BiFunction<NotifySettings, Boolean, NotifySettings> setter) {
		return CycleButton.onOffBuilder(getter.apply(NotifyConfig.get()))
				.withTooltip(v -> Tooltip.create(Component.translatable(key + ".tooltip")))
				.create(0, 0, 210, 20, Component.translatable(key),
						(b, v) -> NotifyConfig.set(setter.apply(NotifyConfig.get(), v)));
	}

	@Override
	protected void repositionElements() {
		layout.arrangeElements();
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}
}
