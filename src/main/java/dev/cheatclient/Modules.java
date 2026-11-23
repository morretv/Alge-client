package dev.cheatclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

public final class Modules {
	public static final List<Hack> ALL = new ArrayList<>();
	/** Ziele: 0 = Monster, 1 = Spieler, 2 = alle Lebewesen */
	public static int TARGET = 0;
	/** Max. Drehung pro Tick in Grad (180 = instant) */
	public static double AIM_SPEED = 180;

	// Combat
	public static final AutoAim AUTOAIM = add(new AutoAim());
	public static final KillAura KILLAURA = add(new KillAura());
	public static final Velocity VELOCITY = add(new Velocity());
	public static final Criticals CRITICALS = add(new Criticals());
	// Movement
	public static final Fly FLY = add(new Fly());
	public static final Speed SPEED = add(new Speed());
	public static final Sprint SPRINT = add(new Sprint());
	// Player
	public static final NoFall NOFALL = add(new NoFall());
	public static final AutoCmd AUTOCMD = add(new AutoCmd());
	// World
	public static final Fullbright FULLBRIGHT = add(new Fullbright());
	public static final XRay XRAY = add(new XRay());
	public static final Esp ESP = add(new Esp());
	public static final Tracers TRACERS = add(new Tracers());
	public static final StorageEsp STORAGE = add(new StorageEsp());
	public static final BaseFinder BASEFINDER = add(new BaseFinder());
	// zusaetzliche Movement/Player-Module
	public static final Scaffold SCAFFOLD = add(new Scaffold());
	public static final GotoWalk GOTO = add(new GotoWalk());

	private static <T extends Hack> T add(T h) { ALL.add(h); return h; }

	public static Hack find(String n) {
		n = n.toLowerCase(Locale.ROOT);
		if (n.equals("esp") || n.equals("blockesp")) n = "xray";
		for (Hack h : ALL) if (h.name.equals(n)) return h;
		return null;
	}

	// ---------- helpers ----------
	static Vec3 wishDir(Minecraft mc, LocalPlayer p) {
		var o = mc.options;
		double f = (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0);
		double s = (o.keyLeft.isDown() ? 1 : 0) - (o.keyRight.isDown() ? 1 : 0);
		if (f == 0 && s == 0) return Vec3.ZERO;
		double yaw = Math.toRadians(p.getYRot());
		double x = -Math.sin(yaw) * f + Math.cos(yaw) * s;
		double z = Math.cos(yaw) * f + Math.sin(yaw) * s;
		double len = Math.sqrt(x * x + z * z);
		return new Vec3(x / len, 0, z / len);
	}

	static LivingEntity nearest(Minecraft mc, LocalPlayer p, double range) {
		List<net.minecraft.world.entity.Entity> list = mc.level.getEntities(p,
			p.getBoundingBox().inflate(range),
			e -> e instanceof LivingEntity le && le.isAlive() && switch (TARGET) {
				case 1 -> e instanceof net.minecraft.world.entity.player.Player;
				case 2 -> true;
				default -> le.getType().getCategory() == net.minecraft.world.entity.MobCategory.MONSTER;
			});
		LivingEntity best = null;
		double bd = Double.MAX_VALUE;
		for (var e : list) {
			double d = p.distanceToSqr(e);
			if (d < bd && d <= range * range) { bd = d; best = (LivingEntity) e; }
		}
		return best;
	}

	/** Wie nearest(), aber liefert ALLE passenden Lebewesen im Radius (fuer ESP/Tracers). */
	static List<LivingEntity> entities(Minecraft mc, LocalPlayer p, double range) {
		List<LivingEntity> out = new ArrayList<>();
		for (var e : mc.level.getEntities(p, p.getBoundingBox().inflate(range),
				e2 -> e2 instanceof LivingEntity le && le.isAlive() && switch (TARGET) {
					case 1 -> e2 instanceof net.minecraft.world.entity.player.Player;
					case 2 -> true;
					default -> le.getType().getCategory() == net.minecraft.world.entity.MobCategory.MONSTER;
				})) {
			if (p.distanceToSqr(e) <= range * range) out.add((LivingEntity) e);
		}
		return out;
	}

	/** Dreht Richtung Ziel (begrenzt durch AIM_SPEED). true = Ziel (fast) anvisiert. */
	static boolean face(LocalPlayer p, LivingEntity t) {
		Vec3 eye = p.getEyePosition();
		Vec3 c = t.getBoundingBox().getCenter();
		double dx = c.x - eye.x, dy = c.y - eye.y, dz = c.z - eye.z;
		double h = Math.sqrt(dx * dx + dz * dz);
		double yawT = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
		double pitT = -Math.toDegrees(Math.atan2(dy, h));
		float dYaw = net.minecraft.util.Mth.wrapDegrees((float) (yawT - p.getYRot()));
		float dPit = (float) (pitT - p.getXRot());
		float max = (float) AIM_SPEED;
		float sy = Math.max(-max, Math.min(max, dYaw));
		float sp = Math.max(-max, Math.min(max, dPit));
		p.setYRot(p.getYRot() + sy);
		p.setXRot(net.minecraft.util.Mth.clamp(p.getXRot() + sp, -90f, 90f));
		return Math.abs(dYaw - sy) < 5f && Math.abs(dPit - sp) < 5f;
	}

	// ---------- Combat ----------
	public static class AutoAim extends Hack {
		AutoAim() { super("autoaim", "combat", "Zielt auf naechstes Ziel (Wert = Reichweite)", 6.0); }
		@Override public void tick(Minecraft mc, LocalPlayer p) {
			LivingEntity t = nearest(mc, p, value);
			if (t != null) face(p, t);
		}
	}

	public static class KillAura extends Hack {
		KillAura() { super("killaura", "combat", "Greift Ziele automatisch an (Wert = Reichweite)", 3.5); }
		@Override public void tick(Minecraft mc, LocalPlayer p) {
			if (p.getAttackStrengthScale(0.5f) < 1.0f) return;
			LivingEntity t = nearest(mc, p, value);
			if (t == null) return;
			if (!face(p, t)) return;
			mc.gameMode.attack(p, t);
			p.swing(InteractionHand.MAIN_HAND);
		}
	}

	/**
	 * Verringert/entfernt erlittenen Rueckstoss. value = Multiplikator
	 * (0 = kein Rueckstoss, 1 = normal). Wirkt ueber {@code mixin/VelocityMixin}.
	 */
	public static class Velocity extends Hack {
		Velocity() { super("velocity", "combat", "Weniger/kein Rueckstoss (Wert 0=keiner .. 1=normal)", 0.3); }
	}

	/** Springt kurz bevor zugeschlagen wird, um eher kritische Treffer zu landen. */
	public static class Criticals extends Hack {
		Criticals() { super("criticals", "combat", "Springt automatisch fuer kritische Treffer (Wert = Reichweite)", 3.5); }

		@Override
		public void tick(Minecraft mc, LocalPlayer p) {
			if (!p.onGround() || p.getDeltaMovement().y > 0.1) return;
			LivingEntity t = nearest(mc, p, value);
			if (t != null) p.jumpFromGround();
		}
	}

	// ---------- Movement ----------
	public static class Fly extends Hack {
		Fly() { super("fly", "movement", "Fliegen (Wert = Tempo, Space hoch / Shift runter)", 0.5); }
		@Override public void tick(Minecraft mc, LocalPlayer p) {
			Vec3 d = wishDir(mc, p).scale(value);
			double y = mc.options.keyJump.isDown() ? value : (mc.options.keyShift.isDown() ? -value : 0);
			p.setDeltaMovement(d.x, y, d.z);
			p.fallDistance = 0;
		}
	}

	public static class Speed extends Hack {
		Speed() { super("speed", "movement", "Schneller laufen (Wert = Tempo)", 0.35); }
		@Override public void tick(Minecraft mc, LocalPlayer p) {
			if (FLY.enabled) return;
			Vec3 d = wishDir(mc, p).scale(value);
			if (d == Vec3.ZERO) return;
			Vec3 v = p.getDeltaMovement();
			p.setDeltaMovement(d.x, v.y, d.z);
		}
	}

	public static class Sprint extends Hack {
		Sprint() { super("sprint", "movement", "Automatisch sprinten", 0); }
		@Override public void tick(Minecraft mc, LocalPlayer p) {
			if (mc.options.keyUp.isDown() && !p.horizontalCollision) p.setSprinting(true);
		}
	}

	// ---------- Player ----------
	public static class NoFall extends Hack {
		NoFall() { super("nofall", "player", "Kein Fallschaden", 0); }
		@Override public void tick(Minecraft mc, LocalPlayer p) {
			if (p.fallDistance > 2.5) p.setOnGround(true);
		}
	}

	/**
	 * Fuehrt beim Rechtsklick (Benutzen-Taste) konfigurierbare Chat-Befehle aus.
	 * Platzhalter: {player} = Spielername, {balance} = letzte erkannte Zahl aus dem
	 * Scoreboard (Sidebar), z.B. fuer ein Kontostand-/Auto-Sell-Makro auf Servern,
	 * die das per Chat-Befehl anbieten. Inspiriert vom "DonutBalance" Konzept, hier
	 * selbst geschrieben statt kopiert.
	 */
	public static class AutoCmd extends Hack {
		public final List<String> commands = new CopyOnWriteArrayList<>(List.of("sell all"));
		private boolean wasDown = false;
		private long lastRun = 0;
		private String lastBalance = "0";
		private static final java.util.regex.Pattern NUM =
			java.util.regex.Pattern.compile("[\\d][\\d.,]*");

		AutoCmd() {
			// value = Cooldown in Millisekunden zwischen zwei Ausfuehrungen
			super("autocmd", "player", "Rechtsklick fuehrt Befehle aus ({player}, {balance})", 500);
		}

		@Override
		public void tick(Minecraft mc, LocalPlayer p) {
			updateBalance(mc);
			boolean down = mc.options.keyUse.isDown();
			boolean pressed = down && !wasDown;
			wasDown = down;
			if (!pressed) return;
			long now = System.currentTimeMillis();
			if (now - lastRun < (long) value) return;
			lastRun = now;
			String name = p.getGameProfile().getName();
			for (String template : commands) {
				String cmd = template.replace("{player}", name).replace("{balance}", lastBalance);
				if (cmd.startsWith("/")) cmd = cmd.substring(1);
				if (p.connection != null) p.connection.sendCommand(cmd);
			}
		}

		/** Liest die Sidebar-Scoreboard-Zeilen und merkt sich die erste gefundene Zahl. */
		private void updateBalance(Minecraft mc) {
			try {
				var board = mc.level.getScoreboard();
				var obj = board.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR);
				if (obj == null) return;
				for (var score : board.listPlayerScores(obj)) {
					String line = score.owner();
					var m = NUM.matcher(line);
					if (m.find()) { lastBalance = m.group().replace(",", ""); return; }
				}
			} catch (Throwable ignored) {
				// Scoreboard-API kann sich je nach Version unterscheiden; Modul bleibt
				// dann nutzbar, {balance} liefert einfach den zuletzt bekannten Wert.
			}
		}
	}

	// ---------- World ----------
	public static class Fullbright extends Hack {
		Fullbright() { super("fullbright", "world", "Immer hell (Nachtsicht)", 0); }
		@Override public void tick(Minecraft mc, LocalPlayer p) {
			MobEffectInstance e = p.getEffect(MobEffects.NIGHT_VISION);
			if (e == null || e.getDuration() < 300)
				p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1200, 0, false, false));
		}
		@Override public void onDisable() {
			var p = Minecraft.getInstance().player;
			if (p != null) p.removeEffect(MobEffects.NIGHT_VISION);
		}
	}

	/**
	 * Boxen/Umrandungen um Spieler/Mobs, auch durch Waende sichtbar. Die
	 * eigentliche Zeichenarbeit (Render-Hook, Linien ohne Tiefentest) macht
	 * die Klasse {@link dev.cheatclient.EspRenderer}; dieses Modul haelt nur
	 * Zustand + Reichweite. Wird nicht per tick() aktiv, sondern einmal pro
	 * Frame von EspRenderer abgefragt.
	 */
	public static class Esp extends Hack {
		Esp() { super("esp", "world", "Boxen um Spieler/Mobs, auch durch Waende (Wert = Reichweite)", 48.0); }
	}

	/** Linien von der Kamera zu Spielern/Mobs. Zeichnen: siehe EspRenderer. */
	public static class Tracers extends Hack {
		Tracers() { super("tracers", "world", "Linien zu Spielern/Mobs (Wert = Reichweite)", 48.0); }
	}

	/** Platziert beim Laufen automatisch einen Block unter die Fuesse. */
	public static class Scaffold extends Hack {
		Scaffold() { super("scaffold", "player", "Baut automatisch eine Bruecke unter dir", 0); }

		@Override
		public void tick(Minecraft mc, LocalPlayer p) {
			if (!p.onGround() && p.getDeltaMovement().y < -0.1) return; // faellt gerade richtig -> nichts tun
			var below = p.blockPosition().below();
			if (!mc.level.getBlockState(below).isAir()) return; // da ist schon was

			int slot = findBlockSlot(p);
			if (slot < 0) return; // kein Block im Inventar
			int old = p.getInventory().getSelectedSlot();
			if (old != slot) p.getInventory().setSelectedSlot(slot);
			try {
				var hit = new net.minecraft.world.phys.BlockHitResult(
					p.position().subtract(0, 0.1, 0), net.minecraft.core.Direction.UP, below, false);
				mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, hit);
				p.swing(InteractionHand.MAIN_HAND);
			} finally {
				if (old != slot) p.getInventory().setSelectedSlot(old);
			}
		}

		private static int findBlockSlot(LocalPlayer p) {
			for (int i = 0; i < 9; i++) {
				var stack = p.getInventory().getItem(i);
				if (!stack.isEmpty() && stack.getItem() instanceof net.minecraft.world.item.BlockItem) return i;
			}
			return -1;
		}
	}

	/**
	 * Einfacher "geh zu X Y Z"-Autowalk. Kein echter Pfadfinder wie Baritone
	 * (kein A*, keine Umgehung komplexer Hindernisse) - laeuft geradewegs
	 * Richtung Ziel und springt, wenn er vorne blockiert ist.
	 */
	public static class GotoWalk extends Hack {
		private double tx, ty, tz;
		private boolean hasTarget = false;

		GotoWalk() { super("goto", "movement", "Laeuft automatisch zu X Y Z (kein echter Pfadfinder)", 0.28); }

		public void setTarget(double x, double y, double z) {
			tx = x; ty = y; tz = z; hasTarget = true; setEnabled(true);
		}

		@Override
		public void onDisable() { hasTarget = false; }

		@Override
		public void tick(Minecraft mc, LocalPlayer p) {
			if (!hasTarget) { setEnabled(false); return; }
			double dx = tx - p.getX(), dz = tz - p.getZ(), dy = ty - p.getY();
			double distXZ = Math.sqrt(dx * dx + dz * dz);
			if (distXZ < 1.2 && Math.abs(dy) < 1.5) {
				CheatClient.say("Ziel erreicht.");
				setEnabled(false);
				return;
			}
			float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			p.setYRot(yaw);
			Vec3 dir = new Vec3(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw))).scale(value);
			var v = p.getDeltaMovement();
			p.setDeltaMovement(dir.x, v.y, dir.z);
			p.setSprinting(true);
			// einfache Hindernis-Erkennung: vorne blockiert -> springen
			if (p.horizontalCollision && p.onGround()) p.jumpFromGround();
		}
	}

	/**
	 * Hebt Kisten/Fässer/Shulker/Öfen in der Naehe hervor, auch durch Waende.
	 * Nur Zustand - gezeichnet wird in {@link dev.cheatclient.EspRenderer}.
	 */
	public static class StorageEsp extends Hack {
		StorageEsp() { super("storageesp", "world", "Hebt Kisten/Shulker/Oefen hervor (Wert = Reichweite)", 48.0); }
	}

	/**
	 * Einfache Basen-Suche: markiert Chunks, in denen sich Spieler laut
	 * {@code inhabitedTime} bereits laengere Zeit aufgehalten haben - ein
	 * grober, aber mit oeffentlicher Vanilla-API machbarer Hinweis auf
	 * moegliche Basen/Spielerbauten in der Naehe. Kein Ersatz fuer echte
	 * "NewChunks"-Mods, die dafuer Regiondateien auswerten.
	 */
	public static class BaseFinder extends Hack {
		BaseFinder() { super("basefinder", "world", "Markiert Chunks mit Spieleraktivitaet (Wert = Mindest-Ticks, 6000=5min)", 6000); }
	}

	public static class XRay extends Hack {
		public final List<String> targets =
			new CopyOnWriteArrayList<>(List.of("_ore", "ancient_debris", "spawner"));
		XRay() { super("xray", "world", "X-Ray / Block-ESP: zeigt nur Bloecke aus der Liste", 0); }
		@Override public void onEnable() { reload(); }
		@Override public void onDisable() { reload(); }
		public void reload() {
			try { Minecraft.getInstance().levelRenderer.allChanged(); } catch (Throwable ignored) {}
		}
		public boolean matches(BlockState s) {
			String id = s.getBlock().getDescriptionId();
			for (String t : targets) if (id.contains(t)) return true;
			return false;
		}
	}
}
