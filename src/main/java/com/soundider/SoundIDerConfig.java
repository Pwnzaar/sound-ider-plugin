package com.soundider;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(SoundIDerConfig.GROUP)
public interface SoundIDerConfig extends Config
{
	String GROUP = "soundider";

	@ConfigSection(
		name = "Logging",
		description = "Sound event types written to the local JSONL log",
		position = 0
	)
	String LOGGING = "logging";

	@ConfigItem(
		keyName = "logEffects",
		name = "Effects",
		description = "Log normal sound effects",
		section = LOGGING,
		position = 0
	)
	default boolean logEffects()
	{
		return true;
	}

	@ConfigItem(
		keyName = "logArea",
		name = "Area",
		description = "Log area sound events",
		section = LOGGING,
		position = 1
	)
	default boolean logArea()
	{
		return true;
	}

	@ConfigItem(
		keyName = "logAmbient",
		name = "Ambient",
		description = "Log ambient scene sounds",
		section = LOGGING,
		position = 2
	)
	default boolean logAmbient()
	{
		return true;
	}

	@ConfigItem(
		keyName = "logInaudibleArea",
		name = "Inaudible area sounds",
		description = "Include area sound events outside hearing range",
		section = LOGGING,
		position = 3
	)
	default boolean logInaudibleArea()
	{
		return true;
	}
}
