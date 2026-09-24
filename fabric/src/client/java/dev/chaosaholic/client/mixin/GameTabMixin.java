package dev.chaosaholic.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.chaosaholic.client.CreateWorldModeHolder;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the "Chaosaholic Mode: ON/OFF" toggle to the "Game" tab of the Create World screen, right below
 * "Difficulty" (the third two-argument {@code RowHelper.addChild} of the constructor: name, game mode, difficulty;
 * the same on 26.2 and 26.3). The value lives on the screen (CreateWorldScreenMixin), not in a game rule.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$GameTab")
public abstract class GameTabMixin {
	@Inject(method = "<init>", at = @At(value = "INVOKE", ordinal = 2, shift = At.Shift.AFTER,
			target = "Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;addChild(Lnet/minecraft/client/gui/layouts/LayoutElement;Lnet/minecraft/client/gui/layouts/LayoutSettings;)Lnet/minecraft/client/gui/layouts/LayoutElement;"))
	private void chaosaholic$addToggle(CreateWorldScreen screen, CallbackInfo ci, @Local GridLayout.RowHelper helper) {
		CreateWorldModeHolder holder = (CreateWorldModeHolder) screen;
		helper.addChild(CycleButton.onOffBuilder(holder.chaosaholic$isModeEnabled())
				.withTooltip(value -> Tooltip.create(Component.translatable("chaosaholic.createWorld.toggle.tooltip")))
				.create(0, 0, 210, 20, Component.translatable("chaosaholic.createWorld.toggle"),
						(b, value) -> holder.chaosaholic$setModeEnabled(value)));
	}
}
