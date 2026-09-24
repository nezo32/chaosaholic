package dev.chaosaholic.event;

import dev.chaosaholic.event.impl.AnvilRain;
import dev.chaosaholic.event.impl.BeeSwarm;
import dev.chaosaholic.event.impl.Blackout;
import dev.chaosaholic.event.impl.BouncyFloor;
import dev.chaosaholic.event.impl.Butterfingers;
import dev.chaosaholic.event.impl.ChestFromSky;
import dev.chaosaholic.event.impl.ChickenApocalypse;
import dev.chaosaholic.event.impl.DoubleXp;
import dev.chaosaholic.event.impl.EternalNight;
import dev.chaosaholic.event.impl.FeatherFall;
import dev.chaosaholic.event.impl.GlowParty;
import dev.chaosaholic.event.impl.GravityFlip;
import dev.chaosaholic.event.impl.HealingAura;
import dev.chaosaholic.event.impl.HungerGames;
import dev.chaosaholic.event.impl.IronSkin;
import dev.chaosaholic.event.impl.LightningStorm;
import dev.chaosaholic.event.impl.LootPinata;
import dev.chaosaholic.event.impl.MidasHour;
import dev.chaosaholic.event.impl.MobSurprise;
import dev.chaosaholic.event.impl.MoonJump;
import dev.chaosaholic.event.impl.RandomTeleport;
import dev.chaosaholic.event.impl.ScreenShake;
import dev.chaosaholic.event.impl.SheepDisco;
import dev.chaosaholic.event.impl.Slippery;
import dev.chaosaholic.event.impl.Sluggish;
import dev.chaosaholic.event.impl.SpeedDemon;
import dev.chaosaholic.event.impl.Swap;
import dev.chaosaholic.event.impl.TinyWorld;
import dev.chaosaholic.event.impl.TntRain;
import dev.chaosaholic.event.impl.UpsideDown;

/**
 * The registration list: one line per event, grouped good / bad / weird. Every event class already exists (the
 * unimplemented ones return false from canStart), so implementing an event never touches this file.
 */
public final class ChaosEvents {
	private ChaosEvents() {}

	/** Called once from Chaosaholic#onInitialize (both sides: the client needs names, categories and sounds). */
	public static void register() {
		EventRegistry.register(new MidasHour());
		EventRegistry.register(new FeatherFall());
		EventRegistry.register(new LootPinata());
		EventRegistry.register(new SpeedDemon());
		EventRegistry.register(new ChestFromSky());
		EventRegistry.register(new DoubleXp());
		EventRegistry.register(new HealingAura());
		EventRegistry.register(new IronSkin());
		EventRegistry.register(new MoonJump());
		EventRegistry.register(new TntRain());
		EventRegistry.register(new AnvilRain());
		EventRegistry.register(new MobSurprise());
		EventRegistry.register(new HungerGames());
		EventRegistry.register(new Butterfingers());
		EventRegistry.register(new EternalNight());
		EventRegistry.register(new LightningStorm());
		EventRegistry.register(new Sluggish());
		EventRegistry.register(new BeeSwarm());
		EventRegistry.register(new Blackout());
		EventRegistry.register(new GravityFlip());
		EventRegistry.register(new ChickenApocalypse());
		EventRegistry.register(new TinyWorld());
		EventRegistry.register(new BouncyFloor());
		EventRegistry.register(new UpsideDown());
		EventRegistry.register(new Swap());
		EventRegistry.register(new SheepDisco());
		EventRegistry.register(new Slippery());
		EventRegistry.register(new RandomTeleport());
		EventRegistry.register(new ScreenShake());
		EventRegistry.register(new GlowParty());
	}
}
