package dev.chaosaholic.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import dev.chaosaholic.client.CreateWorldModeHolder;
import dev.chaosaholic.mode.ChaosSettings;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Client gametest (not part of {@code build}; run with {@code ./gradlew runClientGameTest} under Xvfb).
 * <ol>
 * <li>The Game tab has "Chaosaholic Mode" directly below "Difficulty" (above "Allow Commands"), ON by default.</li>
 * <li>World 1: toggle OFF/ON, create: the world's settings.dat says ON (written right away).</li>
 * <li>World 2: switch OFF, create: OFF. Cancel after toggling leaks nothing into the next screen.</li>
 * <li>Russian: the button reads «Режим Chaosaholic: …».</li>
 * </ol>
 */
public class ChaosClientGameTest implements FabricClientGameTest {
	private static final String TOGGLE = "chaosaholic.createWorld.toggle";

	@Override
	public void runTest(ClientGameTestContext ctx) {
		openCreateWorld(ctx);
		ctx.takeScreenshot("chaosaholic_create_world_game_tab");
		assertPlacement(ctx);
		if (!uiMode(ctx)) throw new AssertionError("Create World does not start with Chaosaholic Mode ON");
		ctx.clickScreenButton(TOGGLE);
		if (uiMode(ctx)) throw new AssertionError("toggle did not switch OFF");
		ctx.clickScreenButton(TOGGLE);
		if (!uiMode(ctx)) throw new AssertionError("toggle did not switch back ON");
		Path world1 = createWorld(ctx);
		assertMode(ctx, true, "world 1");
		if (!Files.isRegularFile(world1.resolve("data/chaosaholic/settings.dat"))) {
			throw new AssertionError("settings.dat not written right after creating " + world1);
		}
		leaveWorld(ctx);

		// Cancel after switching OFF must not leak into the next screen
		ctx.runOnClient(mc -> CreateWorldScreen.openFresh(mc, () -> mc.gui.setScreen(new TitleScreen())));
		ctx.waitForScreen(CreateWorldScreen.class);
		ctx.clickScreenButton(TOGGLE);
		ctx.clickScreenButton("gui.cancel");
		ctx.waitForScreen(TitleScreen.class);

		openCreateWorld(ctx);
		if (!uiMode(ctx)) throw new AssertionError("fresh screen after Cancel does not start ON");
		ctx.clickScreenButton(TOGGLE);
		Path world2 = createWorld(ctx);
		assertMode(ctx, false, "world 2 (switched OFF)");
		if (world1.equals(world2)) throw new AssertionError("same world folder twice: " + world1);
		leaveWorld(ctx);

		setLanguage(ctx, "ru_ru");
		openCreateWorld(ctx);
		String ru = widgetText(ctx, "Режим Chaosaholic");
		ctx.takeScreenshot("chaosaholic_create_world_game_tab_ru");
		ctx.clickScreenButton("gui.cancel");
		ctx.waitForScreen(TitleScreen.class);
		setLanguage(ctx, "en_us");
		ctx.setScreen(TitleScreen::new);
		System.out.println("CHAOSAHOLIC_CLIENT_TEST_OK ru=\"" + ru + "\" world1=" + world1.getFileName() + " world2=" + world2.getFileName());
	}

	/** Widgets of the Game tab in vertical order: ..., Difficulty, Chaosaholic Mode, Allow Commands, ... */
	private static void assertPlacement(ClientGameTestContext ctx) {
		List<String> order = ctx.computeOnClient(mc -> {
			List<AbstractWidget> widgets = new ArrayList<>();
			for (var child : mc.gui.screen().children()) {
				if (child instanceof AbstractWidget w && w.visible && w.getWidth() >= 150) widgets.add(w);
			}
			widgets.sort((a, b) -> Integer.compare(a.getY(), b.getY()));
			List<String> names = new ArrayList<>();
			for (AbstractWidget w : widgets) names.add(w.getMessage().getString());
			return names;
		});
		int difficulty = indexStartingWith(order, Component.translatable("options.difficulty").getString());
		int mode = indexStartingWith(order, "Chaosaholic Mode");
		int commands = indexStartingWith(order, Component.translatable("selectWorld.allowCommands").getString());
		if (difficulty < 0 || mode < 0 || commands < 0 || mode != difficulty + 1 || commands != mode + 1) {
			throw new AssertionError("expected Difficulty, Chaosaholic Mode, Allow Commands in a row; got " + order);
		}
	}

	private static int indexStartingWith(List<String> list, String prefix) {
		for (int i = 0; i < list.size(); i++) if (list.get(i).startsWith(prefix)) return i;
		return -1;
	}

	/** Same as picking a language in Options → Language, minus saving options.txt. */
	private static void setLanguage(ClientGameTestContext ctx, String code) {
		AtomicReference<CompletableFuture<Void>> reload = new AtomicReference<>();
		ctx.runOnClient(mc -> {
			mc.getLanguageManager().setSelected(code);
			mc.options.languageCode = code;
			reload.set(mc.reloadResourcePacks());
		});
		ctx.waitFor(mc -> reload.get().isDone() && mc.gui.overlay() == null, 20 * 120);
	}

	private static String widgetText(ClientGameTestContext ctx, String prefix) {
		return ctx.computeOnClient(mc -> mc.gui.screen().children().stream()
				.filter(c -> c instanceof AbstractWidget)
				.map(c -> ((AbstractWidget) c).getMessage().getString())
				.filter(t -> t.startsWith(prefix))
				.findFirst()
				.orElseThrow(() -> new AssertionError("no widget starting with \"" + prefix + "\" on " + mc.gui.screen())));
	}

	private static void openCreateWorld(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> CreateWorldScreen.openFresh(mc, () -> {}));
		ctx.waitForScreen(CreateWorldScreen.class);
	}

	private static Path createWorld(ClientGameTestContext ctx) {
		ctx.clickScreenButton("selectWorld.create");
		ctx.waitFor(mc -> mc.getSingleplayerServer() != null && mc.player != null, 20 * 60);
		return ctx.computeOnClient(mc -> mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize());
	}

	private static void assertMode(ClientGameTestContext ctx, boolean expected, String what) {
		boolean actual = ctx.computeOnClient(mc -> ChaosSettings.get(mc.getSingleplayerServer()).enabled());
		if (actual != expected) throw new AssertionError(what + ": mode " + actual + ", expected " + expected);
	}

	/** Leaves the world; otherwise the client-gametest framework fails ("finished while a server is still running"). */
	private static void leaveWorld(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> {
			mc.level.disconnect(Component.translatable("menu.savingLevel"));
			mc.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")), false);
		});
		ctx.waitFor(mc -> mc.level == null && mc.getSingleplayerServer() == null, 20 * 60);
		ctx.setScreen(TitleScreen::new);
		ctx.waitTicks(20);
	}

	private static boolean uiMode(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> ((CreateWorldModeHolder) mc.gui.screen()).chaosaholic$isModeEnabled());
	}
}
