package com.togoptimizer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Above Juna: whether this world moves its streams in the best order, better worlds if not, and when
 * to tell a story so the game starts at a good time.
 */
class JunaOverlay extends Overlay
{
	private static final int LINE_HEIGHT = 15;
	private static final int TEXT_HEIGHT = 300;

	private final Client client;
	private final TogOptimizerPlugin plugin;
	private final TogOptimizerConfig config;

	@Inject
	private JunaOverlay(Client client, TogOptimizerPlugin plugin, TogOptimizerConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	/**
	 * @return the countdown for continuing past Juna's last line, or null if it isn't on screen
	 */
	static String continueCue(TogOptimizerPlugin plugin)
	{
		if (!plugin.isJunasLastLine())
		{
			return null;
		}
		int wait = plugin.ticksUntilContinue();
		if (wait < 0)
		{
			return "Continue: timing the streams...";
		}
		return wait == 0 ? "Continue now" : "Continue in " + wait;
	}

	static Color cueColour(String cue)
	{
		if (cue.equals("Continue now"))
		{
			return Color.GREEN;
		}
		return cue.startsWith("Continue in") ? Color.WHITE : Color.LIGHT_GRAY;
	}

	private void addHopHint(List<String> texts, List<Color> colours, int world)
	{
		String hotkey = plugin.hopHotkeyText();
		if (hotkey != null)
		{
			texts.add(hotkey + " to hop to " + world);
			colours.add(Color.YELLOW);
		}
	}

	private String describeWorld(int world)
	{
		Integer ping = plugin.pingOf(world);
		return ping == null ? String.valueOf(world) : world + " (" + ping + " ms)";
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!plugin.inCave())
		{
			return null;
		}
		GameObject juna = plugin.getJuna();
		if (juna == null)
		{
			return null;
		}

		List<String> texts = new ArrayList<>();
		List<Color> colours = new ArrayList<>();

		String order = plugin.worldOrder();
		boolean optimal = StreamTracker.OPTIMAL_ORDER.equals(order);
		if (config.showWorldStatus())
		{
			if (order == null)
			{
				texts.add("Watching the streams...");
				colours.add(Color.LIGHT_GRAY);
			}
			else if (optimal)
			{
				texts.add("Best stream order (gggbbb)");
				colours.add(Color.GREEN);
				Integer faster = config.fetchWorldList() ? plugin.fasterWorld() : null;
				if (faster != null)
				{
					texts.add("Faster gggbbb world: " + describeWorld(faster) + ", this world "
						+ plugin.pingOf(plugin.currentWorld()) + " ms");
					colours.add(Color.WHITE);
					addHopHint(texts, colours, faster);
				}
			}
			else
			{
				texts.add("Stream order " + order + ", not gggbbb");
				colours.add(Color.RED);
				List<Integer> worlds = config.fetchWorldList() ? plugin.suggestedWorlds() : List.of();
				if (!worlds.isEmpty())
				{
					texts.add("gggbbb worlds: " + worlds.stream().map(this::describeWorld).collect(Collectors.joining(", ")));
					colours.add(Color.WHITE);
					addHopHint(texts, colours, worlds.get(0));
				}
			}
		}

		Long unlock = plugin.unlockAt();
		String requirement = plugin.requirementText();
		if (config.showNextGame() && unlock != null)
		{
			texts.add("Next game in " + Eligibility.formatRemaining(unlock - System.currentTimeMillis()));
			colours.add(Color.ORANGE);
		}
		if (config.showRequirement() && requirement != null)
		{
			texts.add(requirement);
			colours.add(Color.ORANGE);
		}

		// Story timing only matters when the player can actually play
		boolean canPlay = unlock == null && requirement == null;
		if (config.showStoryTiming() && optimal && canPlay && !plugin.playerInRoom())
		{
			String cue = continueCue(plugin);
			if (cue != null)
			{
				texts.add(cue);
				colours.add(cueColour(cue));
			}
			else
			{
				texts.add("Tell story, then wait on Juna's last line");
				colours.add(Color.WHITE);
			}
		}

		for (int i = 0; i < texts.size(); i++)
		{
			Point location = Perspective.getCanvasTextLocation(client, graphics, juna.getLocalLocation(), texts.get(i), TEXT_HEIGHT);
			if (location != null)
			{
				OverlayUtil.renderTextLocation(graphics, new Point(location.getX(), location.getY() + i * LINE_HEIGHT),
					texts.get(i), colours.get(i));
			}
		}
		return null;
	}
}
