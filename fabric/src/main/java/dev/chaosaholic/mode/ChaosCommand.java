package dev.chaosaholic.mode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.chaosaholic.Texts;
import dev.chaosaholic.core.ChaosLimits;
import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventManager;
import dev.chaosaholic.event.EventRegistry;
import dev.chaosaholic.event.StopReason;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * /chaosaholic, permission level 2 (gamemasters) like /gamerule:
 * <pre>
 * /chaosaholic [on|off|status]
 * /chaosaholic scope [player|world]
 * /chaosaholic events
 * /chaosaholic event &lt;id&gt; [on|off|status]
 * /chaosaholic event &lt;id&gt; weight [0..1000]
 * /chaosaholic trigger &lt;id&gt; [targets]   forced start: ignores the mode switch, the event's switch and weight
 * /chaosaholic roll [targets]           random roll like a level-up (weights, switches), ignores the mode switch
 * /chaosaholic stop [targets]           ends every event affecting the targets
 * </pre>
 * Results: on/off/status 1 = ON, 0 = OFF; trigger/roll/stop = number of players / events affected.
 */
public final class ChaosCommand {
	private ChaosCommand() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("chaosaholic")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(c -> status(c.getSource()))
				.then(Commands.literal("on").executes(c -> setMode(c.getSource(), true)))
				.then(Commands.literal("off").executes(c -> setMode(c.getSource(), false)))
				.then(Commands.literal("status").executes(c -> status(c.getSource())))
				.then(Commands.literal("scope")
						.executes(c -> scopeStatus(c.getSource()))
						.then(Commands.literal("player").executes(c -> setScope(c.getSource(), Scope.PLAYER)))
						.then(Commands.literal("world").executes(c -> setScope(c.getSource(), Scope.WORLD))))
				.then(Commands.literal("events").executes(c -> listEvents(c.getSource())))
				.then(Commands.literal("event").then(eventId()
						.executes(c -> eventStatus(c))
						.then(Commands.literal("on").executes(c -> setEvent(c, true)))
						.then(Commands.literal("off").executes(c -> setEvent(c, false)))
						.then(Commands.literal("status").executes(c -> eventStatus(c)))
						.then(Commands.literal("weight")
								.executes(c -> weightStatus(c))
								.then(Commands.argument("weight", IntegerArgumentType.integer(ChaosLimits.MIN_WEIGHT, ChaosLimits.MAX_WEIGHT))
										.executes(c -> setWeight(c, IntegerArgumentType.getInteger(c, "weight")))))))
				.then(Commands.literal("trigger").then(eventId()
						.executes(c -> trigger(c, List.of(c.getSource().getPlayerOrException())))
						.then(Commands.argument("targets", EntityArgument.players())
								.executes(c -> trigger(c, EntityArgument.getPlayers(c, "targets"))))))
				.then(Commands.literal("roll")
						.executes(c -> roll(c.getSource(), List.of(c.getSource().getPlayerOrException())))
						.then(Commands.argument("targets", EntityArgument.players())
								.executes(c -> roll(c.getSource(), EntityArgument.getPlayers(c, "targets")))))
				.then(Commands.literal("stop")
						.executes(c -> stop(c.getSource(), List.of(c.getSource().getPlayerOrException())))
						.then(Commands.argument("targets", EntityArgument.players())
								.executes(c -> stop(c.getSource(), EntityArgument.getPlayers(c, "targets"))))));
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> eventId() {
		return Commands.argument("id", StringArgumentType.word())
				.suggests((c, builder) -> SharedSuggestionProvider.suggest(EventRegistry.ids(), builder));
	}

	/** The event named by the "id" argument, or null after sending the unknown-event error. */
	private static @Nullable ChaosEvent event(CommandContext<CommandSourceStack> c) {
		String id = StringArgumentType.getString(c, "id");
		ChaosEvent event = EventRegistry.get(id);
		if (event == null) c.getSource().sendFailure(Texts.tr("chaosaholic.command.error.unknownEvent", id));
		return event;
	}

	private static ChaosSettings settings(CommandSourceStack source) {
		return ChaosSettings.get(source.getServer());
	}

	private static int setMode(CommandSourceStack source, boolean value) {
		settings(source).setEnabled(value);
		if (!value) EventManager.get(source.getServer()).stopAll(StopReason.MODE_OFF);
		source.sendSuccess(() -> Texts.tr(value ? "chaosaholic.command.on" : "chaosaholic.command.off"), true);
		return value ? 1 : 0;
	}

	private static int status(CommandSourceStack source) {
		boolean on = settings(source).enabled();
		source.sendSuccess(() -> Texts.tr(on ? "chaosaholic.command.status.on" : "chaosaholic.command.status.off"), false);
		List<ActiveEvent> active = EventManager.get(source.getServer()).activeEvents();
		if (active.isEmpty()) {
			source.sendSuccess(() -> Texts.tr("chaosaholic.command.status.none"), false);
		} else {
			List<Component> names = new ArrayList<>();
			for (ActiveEvent ev : active) names.add(Texts.name(ev.event()));
			source.sendSuccess(() -> Texts.tr("chaosaholic.command.status.active", ComponentUtils.formatList(names, Function.identity())), false);
		}
		return on ? 1 : 0;
	}

	private static Component scopeName(Scope scope) {
		return Texts.tr("chaosaholic.command.scope." + scope.id());
	}

	private static int scopeStatus(CommandSourceStack source) {
		Scope scope = settings(source).scope();
		source.sendSuccess(() -> Texts.tr("chaosaholic.command.scope.status", scopeName(scope)), false);
		return scope.ordinal();
	}

	private static int setScope(CommandSourceStack source, Scope scope) {
		settings(source).setScope(scope);
		source.sendSuccess(() -> Texts.tr("chaosaholic.command.scope.set", scopeName(scope)), true);
		return scope.ordinal();
	}

	private static int listEvents(CommandSourceStack source) {
		ChaosSettings settings = settings(source);
		List<ChaosEvent> all = EventRegistry.all();
		int enabled = 0;
		for (ChaosEvent e : all) if (settings.isEventEnabled(e)) enabled++;
		int count = enabled;
		source.sendSuccess(() -> Texts.tr("chaosaholic.command.events.header", String.valueOf(count), String.valueOf(all.size())), false);
		for (ChaosEvent e : all) {
			Component line = Texts.tr("chaosaholic.command.events.line", Texts.name(e), Texts.category(e),
					Texts.onOff(settings.isEventEnabled(e)), Texts.tr("chaosaholic.command.events.weight", String.valueOf(settings.weight(e))))
					.withStyle(style -> style
							.withHoverEvent(new HoverEvent.ShowText(Texts.tr("chaosaholic.command.events.hover", Texts.description(e), e.id())))
							.withClickEvent(new ClickEvent.SuggestCommand("/chaosaholic event " + e.id() + " ")));
			source.sendSuccess(() -> line, false);
		}
		return enabled;
	}

	private static int eventStatus(CommandContext<CommandSourceStack> c) {
		ChaosEvent event = event(c);
		if (event == null) return 0;
		boolean on = settings(c.getSource()).isEventEnabled(event);
		c.getSource().sendSuccess(() -> Texts.tr("chaosaholic.command.event.status", Texts.name(event), Texts.onOff(on)), false);
		return on ? 1 : 0;
	}

	private static int setEvent(CommandContext<CommandSourceStack> c, boolean value) {
		ChaosEvent event = event(c);
		if (event == null) return 0;
		settings(c.getSource()).setEventEnabled(event, value);
		c.getSource().sendSuccess(() -> Texts.tr(value ? "chaosaholic.command.event.on" : "chaosaholic.command.event.off", Texts.name(event)), true);
		return value ? 1 : 0;
	}

	private static int weightStatus(CommandContext<CommandSourceStack> c) {
		ChaosEvent event = event(c);
		if (event == null) return 0;
		int weight = settings(c.getSource()).weight(event);
		c.getSource().sendSuccess(() -> Texts.tr("chaosaholic.command.event.weight.status", Texts.name(event), String.valueOf(weight)), false);
		return weight;
	}

	private static int setWeight(CommandContext<CommandSourceStack> c, int weight) {
		ChaosEvent event = event(c);
		if (event == null) return 0;
		settings(c.getSource()).setWeight(event, weight);
		int stored = settings(c.getSource()).weight(event);
		c.getSource().sendSuccess(() -> Texts.tr("chaosaholic.command.event.weight.set", Texts.name(event), String.valueOf(stored)), true);
		return stored;
	}

	private static int trigger(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> targets) {
		ChaosEvent event = event(c);
		if (event == null) return 0;
		EventManager manager = EventManager.get(c.getSource().getServer());
		int started = 0;
		for (ServerPlayer target : targets) {
			if (manager.trigger(event, target).isPresent()) {
				started++;
				c.getSource().sendSuccess(() -> Texts.tr("chaosaholic.command.trigger", Texts.name(event), target.getDisplayName()), true);
			} else {
				c.getSource().sendFailure(refusal(manager.refusal(target, event), event, target));
			}
		}
		return started;
	}

	private static int roll(CommandSourceStack source, Collection<ServerPlayer> targets) {
		EventManager manager = EventManager.get(source.getServer());
		int started = 0;
		for (ServerPlayer target : targets) {
			var result = manager.roll(target);
			if (result.isPresent()) {
				started++;
				ChaosEvent event = result.get().event();
				source.sendSuccess(() -> Texts.tr("chaosaholic.command.trigger.random", Texts.name(event), target.getDisplayName()), true);
			} else {
				source.sendFailure(refusal(manager.refusal(target, null), null, target));
			}
		}
		return started;
	}

	/** The error line for a trigger ({@code event}) or roll ({@code event} null) that started nothing. */
	private static Component refusal(EventManager.Refusal refusal, @Nullable ChaosEvent event, ServerPlayer target) {
		Component player = target.getDisplayName();
		return switch (refusal) {
			case INELIGIBLE -> Texts.tr("chaosaholic.command.error.ineligible", player);
			case NO_ROOM -> Texts.tr("chaosaholic.command.error.noRoom", player, String.valueOf(ChaosLimits.MAX_ACTIVE_PER_PLAYER));
			case ALL_OFF -> Texts.tr("chaosaholic.command.error.noEvents");
			case CANNOT_START -> event == null
					? Texts.tr("chaosaholic.command.error.nothingCanStart", player)
					: Texts.tr("chaosaholic.command.error.noTarget", Texts.name(event), player);
		};
	}

	private static int stop(CommandSourceStack source, Collection<ServerPlayer> targets) throws CommandSyntaxException {
		EventManager manager = EventManager.get(source.getServer());
		int stopped = 0;
		for (ServerPlayer target : targets) stopped += manager.stopFor(target);
		int count = stopped;
		source.sendSuccess(() -> Texts.tr("chaosaholic.command.stop", String.valueOf(count)), true);
		return stopped;
	}
}
