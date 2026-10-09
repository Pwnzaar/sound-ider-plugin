package com.soundider;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(SoundIDerConfig.GROUP)
public interface SoundIDerConfig extends Config
{
	String GROUP = "soundider";

	@ConfigSection(name = "Display", description = "Sounds shown in the live feed", position = 0)
	String DISPLAY = "display";

	@ConfigSection(name = "Consume", description = "Sound types blocked from playing", position = 1)
	String CONSUME = "consume";

	@ConfigSection(name = "Feed", description = "Feed formatting", position = 2)
	String FEED = "feed";

	@ConfigSection(name = "Export", description = "Optional local machine-readable event export", position = 3)
	String EXPORT = "export";

	@ConfigItem(keyName = "showEffects", name = "Effects", description = "Show normal effects", section = DISPLAY, position = 0)
	default boolean showEffects() { return true; }

	@ConfigItem(keyName = "showArea", name = "Area", description = "Show area sounds", section = DISPLAY, position = 1)
	default boolean showArea() { return true; }

	@ConfigItem(keyName = "showAmbient", name = "Ambient", description = "Show ambient sounds", section = DISPLAY, position = 2)
	default boolean showAmbient() { return true; }

	@ConfigItem(keyName = "showInaudibleArea", name = "Inaudible area sounds", description = "Show area events outside hearing range", section = DISPLAY, position = 3)
	default boolean showInaudibleArea() { return true; }

	@ConfigItem(keyName = "consumeEffects", name = "Consume effects", description = "Block normal effects", section = CONSUME, position = 0)
	default boolean consumeEffects() { return false; }

	@ConfigItem(keyName = "consumeArea", name = "Consume area", description = "Block area sounds", section = CONSUME, position = 1)
	default boolean consumeArea() { return false; }

	@ConfigItem(keyName = "consumeAmbient", name = "Consume ambient", description = "Block ambient scene sounds", section = CONSUME, position = 2)
	default boolean consumeAmbient() { return false; }

	@ConfigItem(keyName = "showSource", name = "ID Source", description = "Append the source to each ID", section = FEED, position = 0)
	default boolean showSource() { return false; }

	@ConfigItem(keyName = "showType", name = "Show type", description = "Prefix Effect, Area and Ambient entries", section = FEED, position = 1)
	default boolean showType() { return false; }

	@ConfigItem(keyName = "autoScroll", name = "Auto-scroll", description = "Keep the newest entry visible", section = FEED, position = 2)
	default boolean autoScroll() { return true; }

	@Range(min = 100, max = 10000)
	@ConfigItem(keyName = "maxEntries", name = "Maximum entries", description = "Maximum retained sound events", section = FEED, position = 3)
	default int maxEntries() { return 2000; }

	@ConfigItem(
		keyName = "exportJsonl",
		name = "Export JSONL",
		description = "Append displayed sound events to plugin-data/sound-ider/events.jsonl for local external tools. No network communication is used.",
		section = EXPORT,
		position = 0
	)
	default boolean exportJsonl() { return false; }
}
