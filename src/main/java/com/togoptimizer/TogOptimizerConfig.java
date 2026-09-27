package com.togoptimizer;

import java.awt.Color;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;

@ConfigGroup(TogOptimizerConfig.GROUP)
public interface TogOptimizerConfig extends Config
{
	String GROUP = "togoptimizer";

	@ConfigSection(
		name = "Juna",
		description = "What's shown above Juna before a game: the world's stream order and when to start",
		position = 10
	)
	String juna = "juna";

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
		description = "Hides the preview shown outside the wall room, where the walls show the plugin's advice on the "
			+ "live streams before you play.",
		position = 4
	)
	default boolean onlyInRoom()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showWorldStatus",
		name = "World status above Juna",
		description = "Shows above Juna whether this world moves its streams green, green, green, blue, blue, blue, "
			+ "the order that makes green tears easiest to avoid.",
		position = 0,
		section = juna
	)
	default boolean showWorldStatus()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showStoryTiming",
		name = "When to enter",
		description = "Tell Juna a story and stop on her last line, where she says she'll let you into the cave. A "
			+ "countdown then shows when to continue, so your game starts at a good point in the stream cycle. Shown on "
			+ "worlds with the best stream order.",
		position = 1,
		section = juna
	)
	default boolean showStoryTiming()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showNextGame",
		name = "Next game countdown",
		description = "Shows above Juna how long until you can play again, from your last game or from what Juna "
			+ "last told you.",
		position = 2,
		section = juna
	)
	default boolean showNextGame()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showRequirement",
		name = "Quest point or XP needed",
		description = "Shows above Juna the quest point or experience you still need to gain before playing again.",
		position = 3,
		section = juna
	)
	default boolean showRequirement()
	{
		return false;
	}

	@ConfigItem(
		keyName = "fetchWorldList",
		name = "Look up worlds online",
		description = "Reads the list of worlds and their stream orders reported by ToG Crowdsourcing users "
			+ "(togcrowdsourcing.com), to check this world before the streams have been seen and to suggest better "
			+ "ones, nearest you first. This sends your IP address to that site. Nothing about your account is sent.",
		warning = "Enabling this setting reads data from togcrowdsourcing.com, a third-party site not controlled or "
			+ "verified by the RuneLite developers, which will see your IP address. No account information is shared. "
			+ "Continue?",
		position = 4,
		section = juna
	)
	default boolean fetchWorldList()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hopHotkey",
		name = "Hop to best world",
		description = "Hops to the nearest world with the best stream order. Only works in the Tears of Guthix cave, "
			+ "outside the wall room, while on a world without the best order, and needs Look up worlds online.",
		position = 5,
		section = juna
	)
	default Keybind hopHotkey()
	{
		return new Keybind(KeyEvent.VK_UP, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
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
