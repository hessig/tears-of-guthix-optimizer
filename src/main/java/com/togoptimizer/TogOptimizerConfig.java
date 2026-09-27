package com.togoptimizer;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(TogOptimizerConfig.GROUP)
public interface TogOptimizerConfig extends Config
{
	String GROUP = "togoptimizer";

	@ConfigSection(
		name = "Debug",
		description = "Tools for testing the plugin and reporting problems",
		position = 20,
		closedByDefault = true
	)
	String debug = "debug";

	@ConfigItem(
		keyName = "showExpected",
		name = "Show expected tears",
		description = "Shows on the best wall how many tears going there now is expected to collect over the next "
			+ "40 ticks.",
		position = 0
	)
	default boolean showExpected()
	{
		return false;
	}

	@ConfigItem(
		keyName = "highlightBest",
		name = "Highlight best wall",
		description = "Also outlines the wall with the most expected tears.",
		position = 1
	)
	default boolean highlightBest()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightColour",
		name = "Highlight colour",
		description = "Colour of the outline and number on the best wall.",
		position = 2
	)
	default Color highlightColour()
	{
		return new Color(0x3DA5FF);
	}

	@ConfigItem(
		keyName = "waitColour",
		name = "Wait colour",
		description = "Colour used when the best move is to stay at your empty wall for a blue to land, with a "
			+ "countdown of the ticks left before it's time to give up.",
		position = 3
	)
	default Color waitColour()
	{
		return Color.YELLOW;
	}

	@ConfigItem(
		keyName = "onlyInRoom",
		name = "Only show in the wall room",
		description = "Hides the preview shown while you're outside the wall room, which follows a simulated player "
			+ "on the real streams so you can see how the plugin works before playing.",
		position = 4
	)
	default boolean onlyInRoom()
	{
		return false;
	}

	@ConfigItem(
		keyName = "debugLogging",
		name = "Debug logging",
		description = "Logs stream movements, the minigame's state and the plugin's estimates to the client log.",
		position = 0,
		section = debug
	)
	default boolean debugLogging()
	{
		return false;
	}
}
