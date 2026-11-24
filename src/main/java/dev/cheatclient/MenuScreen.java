package dev.cheatclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Menue im Stil des Videos (Hotwire/Mod-Manager): dunkles Violett, pinke Akzentlinie,
 * Seitenleiste links, lila Fortschrittsbalken, Reihen mit Icon-Kachel + dimmer Zweitzeile.
 */
public class MenuScreen extends Screen {
	// Farben aus dem Video
	private static final int PINK = 0xFFFC7FC2, PANEL = 0xF20D0E13, SIDE = 0xFF150E1E, SIDE_SEL = 0xFF1E1830;
	private static final int ROW_HOVER = 0xFF1A1327, ICON = 0xFF272F47, TEXT = 0xFFE8E4F5, DIM = 0xFF6E6A86;
	private static final int PURPLE = 0xFFBD8BFB, PILL_BG = 0xFF25173D, PILL_LINE = 0xFF9881E4, TRACK = 0xFF2A2140;

	private static final int PW = 380, SIDE_W = 72, HEADER = 66, ROW_H = 30, PILL_W = 44;
	private static final String[] TABS = {"Combat", "Movement", "Player", "World"};
	private static final String[] CATS = {"combat", "movement", "player", "world"};
	private static final String[] TARGETS = {"mobs", "players", "all"};

	private int tab = 0, px, py, pw, ph = 204, scroll = 0;

	private record Row(String icon, String name, String desc, String pill, boolean on,
	                   String value, Runnable click, Runnable minus, Runnable plus) {}

	public MenuScreen() { super(Component.literal("Cheat Client")); }

	// ---------- Reihen ----------
	private static String pretty(String n) {
		return switch (n) {
			case "autoaim" -> "Auto Aim";
			case "killaura" -> "Kill Aura";
			case "nofall" -> "No Fall";
			case "xray" -> "X-Ray / Block ESP";
			case "autojump" -> "Auto Jump";
			case "longjump" -> "Long Jump";
			case "fastladder" -> "Fast Ladder";
			case "autowalk" -> "Auto Walk";
			case "safewalk" -> "Safe Walk";
			case "antivoid" -> "Anti Void";
			case "fastfall" -> "Fast Fall";
			case "highjump" -> "High Jump";
			case "airjump" -> "Air Jump";
			case "triggerbot" -> "Trigger Bot";
			case "autoeat" -> "Auto Eat";
			case "antiafk" -> "Anti AFK";
			case "autorespawn" -> "Auto Respawn";
			case "itemesp" -> "Item ESP";
			default -> Character.toUpperCase(n.charAt(0)) + n.substring(1);
		};
	}

	private static String fmt(double v) {
		double r = Math.round(v * 100) / 100.0;
		return r % 1 == 0 ? String.valueOf((int) r) : String.valueOf(r);
	}

	private List<Row> rows() {
		List<Row> r = new ArrayList<>();
		if (tab < 4) {
			for (Hack h : Modules.ALL) {
				if (!h.category.equals(CATS[tab])) continue;
				boolean hasVal = h.value != 0;
				double step = h.value >= 2 ? 0.5 : 0.05;
				r.add(new Row(h.name.substring(0, 1).toUpperCase(), pretty(h.name), h.desc,
					h.enabled ? "ON" : "OFF", h.enabled, hasVal ? fmt(h.value) : null, h::toggle,
					hasVal ? () -> h.value = Math.round(Math.max(step, h.value - step) * 100) / 100.0 : null,
					hasVal ? () -> h.value = Math.round((h.value + step) * 100) / 100.0 : null));
			}
		} else {
			Minecraft mc = Minecraft.getInstance();
			boolean sp = CheatClient.isSingleplayer(mc);
			r.add(new Row("S", "Server-Modus", "Module auf Servern freischalten (nur wo erlaubt)",
				sp ? "SP" : (CheatClient.unlocked ? "ON" : "OFF"), sp || CheatClient.unlocked, null,
				() -> {
					if (sp) return;
					CheatClient.unlocked = !CheatClient.unlocked;
					if (!CheatClient.unlocked) for (Hack h : Modules.ALL) h.setEnabled(false);
				}, null, null));
			r.add(new Row("T", "Ziele", "Fuer Auto Aim und Kill Aura",
				TARGETS[Modules.TARGET], true, null,
				() -> Modules.TARGET = (Modules.TARGET + 1) % 3, null, null));
			r.add(new Row("A", "Aim-Speed", "Max. Grad pro Tick (180 = sofort)",
				null, false, fmt(Modules.AIM_SPEED), null,
				() -> Modules.AIM_SPEED = Math.max(1, Modules.AIM_SPEED - 5),
				() -> Modules.AIM_SPEED = Math.min(180, Modules.AIM_SPEED + 5)));
			r.add(new Row("X", "X-Ray Liste", String.join(", ", Modules.XRAY.targets),
				null, false, null, null, null, null));
			r.add(new Row("C", "Autocmd Befehle", String.join(" | ", Modules.AUTOCMD.commands),
				null, false, null, null, null, null));
		}
		return r;
	}

	// ---------- Layout ----------
	private void calc() {
		pw = Math.min(PW, this.width - 10);
		px = (this.width - pw) / 2;
		ph = Math.max(150, Math.min(264, this.height - 20));
		py = (this.height - ph) / 2;
	}
	private int visible() { return Math.max(1, (ph - HEADER - 6) / ROW_H); }
	private int rx() { return px + SIDE_W + 12; }
	private int rw() { return pw - SIDE_W - 24; }
	private int ry(int i) { return py + HEADER + (i - scroll) * ROW_H; }
	private int pillX() { return rx() + rw() - PILL_W - 4; }
	private int plusX() { return pillX() - 6 - 14; }
	private int valX() { return plusX() - 34; }
	private int minusX() { return valX() - 14; }
	private int tabY(int i) { return i == 4 ? py + ph - 36 : py + 14 + i * 30; }

	private static boolean in(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	// ---------- Rendern ----------
	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		super.extractRenderState(g, mx, my, delta);
		calc();
		var font = Minecraft.getInstance().font;
		Minecraft mc = Minecraft.getInstance();

		// Panel + Akzentlinie + Seitenleiste
		g.fill(px, py, px + pw, py + ph, PANEL);
		g.fill(px, py, px + SIDE_W, py + ph, SIDE);
		g.fill(px, py, px + pw, py + 2, PINK);

		// Tabs
		String[] names = {"Combat", "Movement", "Player", "World", "Settings"};
		for (int i = 0; i < 5; i++) {
			int ty = tabY(i);
			boolean sel = i == tab, hov = in(mx, my, px, ty, SIDE_W, 28);
			if (sel) { g.fill(px, ty, px + SIDE_W, ty + 28, SIDE_SEL); g.fill(px, ty, px + 2, ty + 28, PINK); }
			else if (hov) g.fill(px, ty, px + SIDE_W, ty + 28, 0xFF1A1327);
			g.outline(px + SIDE_W / 2 - 4, ty + 4, 8, 8, sel ? PURPLE : DIM);
			String t = names[i];
			g.text(font, t, px + (SIDE_W - font.width(t)) / 2, ty + 16, sel ? TEXT : DIM, false);
		}

		// Kopf
		int x = rx(), w = rw();
		g.text(font, "Cheat Client", x, py + 14, TEXT, false);
		g.text(font, "Cheat Client", x + 1, py + 14, TEXT, false); // "fett"
		g.text(font, "Tipp: .help im Chat  |  ESC = schliessen", x, py + 28, DIM, false);
		int on = 0;
		for (Hack h : Modules.ALL) if (h.enabled) on++;
		String cnt = on + " of " + Modules.ALL.size() + " mods on";
		g.text(font, cnt, x, py + 44, DIM, false);
		int bx = x + font.width(cnt) + 8, bw = x + w - bx;
		g.fill(bx, py + 47, bx + bw, py + 50, TRACK);
		g.fill(bx, py + 47, bx + bw * on / Math.max(1, Modules.ALL.size()), py + 50, PURPLE);
		String mode = CheatClient.isSingleplayer(mc) ? "Singleplayer" : (CheatClient.unlocked ? "Server (frei)" : "Server (gesperrt)");
		int cw = font.width(mode) + 12;
		g.outline(x + w - cw, py + 10, cw, 16, TRACK);
		g.text(font, mode, x + w - cw + 6, py + 14, PURPLE, false);

		// Reihen
		List<Row> rows = rows();
		scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visible())));
		if (rows.size() > visible()) {
			int trackH = visible() * ROW_H, thumbH = Math.max(12, trackH * visible() / rows.size());
			int thumbY = py + HEADER + (trackH - thumbH) * scroll / Math.max(1, rows.size() - visible());
			g.fill(px + pw - 4, py + HEADER, px + pw - 2, py + HEADER + trackH, TRACK);
			g.fill(px + pw - 4, thumbY, px + pw - 2, thumbY + thumbH, PURPLE);
		}
		for (int i = scroll; i < Math.min(rows.size(), scroll + visible()); i++) {
			Row r = rows.get(i);
			int y = ry(i);
			if (in(mx, my, x, y, w, ROW_H - 2)) g.fill(x, y, x + w, y + ROW_H - 2, ROW_HOVER);
			g.fill(x + 4, y + 4, x + 24, y + 24, ICON);
			g.text(font, r.icon, x + 14 - font.width(r.icon) / 2, y + 10, TEXT, false);
			g.text(font, r.name, x + 32, y + 5, TEXT, false);
			String d = r.desc;
			int maxW = (r.minus != null ? minusX() : pillX()) - (x + 32) - 6;
			while (font.width(d) > maxW && d.length() > 3) d = d.substring(0, d.length() - 4) + "...";
			g.text(font, d, x + 32, y + 16, DIM, false);

			if (r.pill != null) {
				int pxl = pillX();
				if (r.on) { g.fill(pxl, y + 7, pxl + PILL_W, y + 23, PILL_BG); g.outline(pxl, y + 7, PILL_W, 16, PILL_LINE); }
				else g.outline(pxl, y + 7, PILL_W, 16, TRACK);
				g.text(font, r.pill, pxl + (PILL_W - font.width(r.pill)) / 2, y + 11, r.on ? PURPLE : DIM, false);
			}
			if (r.minus != null) {
				g.outline(minusX(), y + 8, 14, 14, TRACK);
				g.text(font, "-", minusX() + 5, y + 11, TEXT, false);
				g.outline(plusX(), y + 8, 14, 14, TRACK);
				g.text(font, "+", plusX() + 4, y + 11, TEXT, false);
				g.text(font, r.value, valX() + (34 - font.width(r.value)) / 2, y + 11, PURPLE, false);
			}
		}
	}

	// ---------- Eingabe ----------
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x(), my = event.y();
		if (event.button() != 0) return super.mouseClicked(event, doubleClick);
		calc();
		for (int i = 0; i < 5; i++) {
			if (in(mx, my, px, tabY(i), SIDE_W, 28)) { tab = i; scroll = 0; return true; }
		}
		List<Row> rows = rows();
		for (int i = scroll; i < Math.min(rows.size(), scroll + visible()); i++) {
			Row r = rows.get(i);
			int y = ry(i);
			if (!in(mx, my, rx(), y, rw(), ROW_H - 2)) continue;
			if (r.minus != null && in(mx, my, minusX(), y + 8, 14, 14)) { run(r.minus); return true; }
			if (r.plus != null && in(mx, my, plusX(), y + 8, 14, 14)) { run(r.plus); return true; }
			if (r.click != null) run(r.click);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	/** Mausrad scrollt die Modulliste (bewusst ohne @Override: Signatur variiert je Version). */
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		int max = Math.max(0, rows().size() - visible());
		scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
		return true;
	}

	private void run(Runnable r) {
		Minecraft mc = Minecraft.getInstance();
		if (tab != 4 && !CheatClient.allowed(mc)) {
			CheatClient.say("Auf Servern erst im Tab Settings (Server-Modus) freischalten.");
			return;
		}
		r.run();
	}
}
