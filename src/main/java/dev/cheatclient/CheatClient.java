package dev.cheatclient;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.Locale;

public class CheatClient implements ClientModInitializer {
	/** Auf Servern erst nach ".server on" aktiv (pro Verbindung). */
	static boolean unlocked = false;
	private static boolean warned = false;
	private static boolean openMenu = false;

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(CheatClient::tick);
		ClientSendMessageEvents.ALLOW_CHAT.register(CheatClient::onChat);
		EspRenderer.init();
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			unlocked = false;
			for (Hack h : Modules.ALL) h.setEnabled(false);
		});
	}

	static boolean isSingleplayer(Minecraft mc) { return mc.getSingleplayerServer() != null; }
	static boolean allowed(Minecraft mc) { return isSingleplayer(mc) || unlocked; }

	static ServerPlayer serverPlayer(Minecraft mc) {
		var s = mc.getSingleplayerServer();
		if (s == null || mc.player == null) return null;
		return s.getPlayerList().getPlayer(mc.player.getUUID());
	}

	static void say(String text) {
		var mc = Minecraft.getInstance();
		if (mc.player != null) mc.player.displayClientMessage(Component.literal("§b[Cheat] §f" + text), false);
	}

	private static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null) return;
		if (openMenu && mc.gui.screen() == null) { openMenu = false; mc.gui.setScreen(new MenuScreen()); }
		if (!allowed(mc)) {
			boolean any = false;
			for (Hack h : Modules.ALL) if (h.enabled) { h.setEnabled(false); any = true; }
			if (any && !warned) { say("Auf Servern erst mit .server on freischalten."); warned = true; }
			return;
		}
		warned = false;
		for (Hack h : Modules.ALL) {
			if (!h.enabled) continue;
			try { h.tick(mc, mc.player); }
			catch (Throwable t) { h.setEnabled(false); say(h.name + " Fehler, deaktiviert: " + t); }
		}
	}

	private static boolean onChat(String text) {
		if (!text.startsWith(".") || text.startsWith("..")) return true;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return true;
		try { handle(mc, text.substring(1).trim()); }
		catch (Throwable t) { say("Fehler: " + t); }
		return false; // Nachricht nicht an den Chat senden
	}

	private static void handle(Minecraft mc, String cmd) {
		String[] a = cmd.split("\\s+");
		String name = a[0].toLowerCase(Locale.ROOT);

		switch (name) {
			case "", "help" -> { help(); return; }
			case "list", "modules" -> { list(); return; }
			case "menu", "gui" -> { openMenu = true; return; }
			case "server" -> {
				if (isSingleplayer(mc)) { say("Singleplayer: ist immer freigeschaltet."); return; }
				boolean on = a.length < 2 || !a[1].equalsIgnoreCase("off");
				unlocked = on;
				if (!on) for (Hack h : Modules.ALL) h.setEnabled(false);
				say(on ? "§aServer-Modus AN §7(nur nutzen, wo Cheats erlaubt sind)" : "§cServer-Modus AUS");
				return;
			}
			case "target" -> {
				if (a.length < 2) { say("Nutzung: .target mobs|players|all"); return; }
				switch (a[1].toLowerCase(Locale.ROOT)) {
					case "mobs" -> Modules.TARGET = 0;
					case "players" -> Modules.TARGET = 1;
					case "all" -> Modules.TARGET = 2;
					default -> { say("mobs | players | all"); return; }
				}
				say("Ziele: " + a[1]);
				return;
			}
			case "aimspeed" -> {
				if (a.length < 2) { say("Aim-Speed: " + Modules.AIM_SPEED + " Grad/Tick"); return; }
				Modules.AIM_SPEED = Math.max(1, Math.min(180, Double.parseDouble(a[1])));
				say("Aim-Speed: " + Modules.AIM_SPEED + " Grad/Tick");
				return;
			}
			default -> {}
		}

		if (!allowed(mc)) { say("Auf Servern erst mit .server on freischalten."); return; }

		switch (name) {
			case "gm" -> { gm(mc, a); return; }
			case "goto" -> {
				if (a.length >= 2 && a[1].equalsIgnoreCase("off")) { Modules.GOTO.setEnabled(false); say("Goto gestoppt."); return; }
				if (a.length < 4) { say("Nutzung: .goto <x> <y> <z>  oder  .goto off"); return; }
				try {
					double x = Double.parseDouble(a[1]), y = Double.parseDouble(a[2]), z = Double.parseDouble(a[3]);
					Modules.GOTO.setTarget(x, y, z);
					say("Laufe zu " + x + " " + y + " " + z + " (kein echter Pfadfinder, nur geradeaus + springen)");
				} catch (NumberFormatException e) { say("Ungueltige Koordinaten."); }
				return;
			}
			case "heal" -> {
				ServerPlayer sp = serverPlayer(mc);
				if (sp == null) { say(".heal geht nur im Singleplayer."); return; }
				mc.getSingleplayerServer().execute(() -> {
					sp.setHealth(sp.getMaxHealth());
					sp.getFoodData().setFoodLevel(20);
				});
				say("Geheilt.");
				return;
			}
			default -> {}
		}

		Hack h = Modules.find(name);
		if (h == null) { say("Unbekannt: ." + name + "  (.help)"); return; }

		if (h == Modules.XRAY && a.length >= 2) {
			var x = Modules.XRAY;
			switch (a[1].toLowerCase(Locale.ROOT)) {
				case "add" -> { if (a.length > 2) { x.targets.add(a[2]); x.reload(); say("Hinzugefuegt: " + a[2]); return; } }
				case "remove" -> { if (a.length > 2) { x.targets.remove(a[2]); x.reload(); say("Entfernt: " + a[2]); return; } }
				case "list" -> { say("Xray-Liste: " + x.targets); return; }
				default -> {}
			}
		}

		if (h == Modules.AUTOCMD && a.length >= 2) {
			var c = Modules.AUTOCMD;
			switch (a[1].toLowerCase(Locale.ROOT)) {
				case "add" -> {
					if (a.length > 2) {
						String tmpl = cmd.substring(cmd.indexOf(a[2]));
						c.commands.add(tmpl);
						say("Befehl hinzugefuegt: " + tmpl);
						return;
					}
				}
				case "remove" -> { if (a.length > 2 && c.commands.size() > Integer.parseInt(a[2]))
					{ say("Entfernt: " + c.commands.remove((int) Integer.parseInt(a[2]))); return; } }
				case "list" -> {
					say("Befehle (Rechtsklick), Cooldown " + (long) c.value + "ms:");
					for (int i = 0; i < c.commands.size(); i++) say(" [" + i + "] " + c.commands.get(i));
					return;
				}
				default -> {}
			}
		}

		if (a.length > 1) {
			String arg = a[1].toLowerCase(Locale.ROOT);
			if (arg.equals("on")) h.setEnabled(true);
			else if (arg.equals("off")) h.setEnabled(false);
			else {
				try { h.value = Double.parseDouble(arg); h.setEnabled(true); }
				catch (NumberFormatException e) { say("Ungueltiger Wert: " + arg); return; }
			}
		} else h.toggle();
		say(h.name + ": " + (h.enabled ? "§aAN" : "§cAUS") + (h.value != 0 ? " §7(Wert " + h.value + ")" : ""));
	}

	private static void gm(Minecraft mc, String[] a) {
		ServerPlayer sp = serverPlayer(mc);
		if (sp == null) { say(".gm geht nur im Singleplayer."); return; }
		if (a.length < 2) { say("Nutzung: .gm creative|survival|adventure|spectator"); return; }
		GameType t = switch (a[1].toLowerCase(Locale.ROOT)) {
			case "creative", "c", "1" -> GameType.CREATIVE;
			case "survival", "s", "0" -> GameType.SURVIVAL;
			case "adventure", "a", "2" -> GameType.ADVENTURE;
			case "spectator", "sp", "3" -> GameType.SPECTATOR;
			default -> null;
		};
		if (t == null) { say("Unbekannter Modus: " + a[1]); return; }
		mc.getSingleplayerServer().execute(() -> sp.setGameMode(t));
		say("Gamemode: " + a[1]);
	}

	private static void help() {
		say(".menu = Menue  |  .<modul> [on|off|zahl]  .list  .server on|off");
		say(".target mobs|players|all  .aimspeed <grad/tick>");
		say("Nur Singleplayer: .gm <modus>  .heal");
		say("Xray: .xray add|remove <text>  .xray list  (Alias .esp)");
		say("Autocmd: .autocmd add <befehl>  .autocmd remove <nr>  .autocmd list  .autocmd <cooldown_ms>");
		say("Goto: .goto <x> <y> <z>  .goto off   |  Esp/Tracers/Scaffold: .esp .tracers .scaffold [on|off|zahl]");
		say("PvP/Finder: .velocity .criticals .storageesp .basefinder [on|off|zahl]");
	}

	private static void list() {
		for (String cat : new String[]{"combat", "movement", "player", "world"}) {
			StringBuilder sb = new StringBuilder("§e" + cat + ": ");
			for (Hack h : Modules.ALL) if (h.category.equals(cat))
				sb.append(h.enabled ? "§a" : "§7").append(h.name).append(" ");
			say(sb.toString());
		}
	}
}
