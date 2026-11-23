package dev.cheatclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public abstract class Hack {
	public final String name, category, desc;
	public volatile boolean enabled;
	public double value;

	protected Hack(String name, String category, String desc, double value) {
		this.name = name; this.category = category; this.desc = desc; this.value = value;
	}

	public void toggle() { setEnabled(!enabled); }

	public void setEnabled(boolean e) {
		if (e == enabled) return;
		enabled = e;
		if (e) onEnable(); else onDisable();
	}

	public void onEnable() {}
	public void onDisable() {}
	public void tick(Minecraft mc, LocalPlayer p) {}
}
