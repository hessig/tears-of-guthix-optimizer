package com.togoptimizer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.TileObject;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

/**
 * Expected tears at the best wall, and optionally an outline around it.
 */
class WallOverlay extends Overlay
{
	private static final int TEXT_HEIGHT = 120;
	private static final int OUTLINE_WIDTH = 2;
	private static final int OUTLINE_FEATHER = 4;

	private final Client client;
	private final TogOptimizerPlugin plugin;
	private final TogOptimizerConfig config;
	private final ModelOutlineRenderer outlines;

	@Inject
	private WallOverlay(Client client, TogOptimizerPlugin plugin, TogOptimizerConfig config, ModelOutlineRenderer outlines)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.outlines = outlines;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Planner.Plan plan = plugin.getPlan();
		int shown = plugin.getShownBest();
		if (plan == null || shown < 0)
		{
			return null;
		}

		int waitTicks = plugin.getWaitTicks();
		if (waitTicks >= 0)
		{
			return renderWait(graphics, shown, waitTicks);
		}

		if (config.highlightBest())
		{
			DecorativeObject wall = plugin.wallObject(shown);
			if (wall != null)
			{
				outlines.drawOutline(wall, OUTLINE_WIDTH, config.highlightColour(), OUTLINE_FEATHER);
			}
		}

		if (!config.showExpected())
		{
			return null;
		}
		drawText(graphics, shown, String.format("%.1f", plan.getExpected()[shown]), config.highlightColour());
		return null;
	}

	/**
	 * Staying put for a blue to land: the empty wall in the wait colour, and how many ticks are left
	 * before giving up. The countdown shows even without numbers, as it's the only sign the plugin is
	 * saying to wait.
	 */
	private Dimension renderWait(Graphics2D graphics, int shown, int waitTicks)
	{
		if (!config.highlightBest() && !config.showExpected())
		{
			return null;
		}
		if (config.highlightBest())
		{
			// Neither an empty wall's stream object (no faces) nor the weeping wall (an invisible flat panel)
			// has a visible model to outline, so the weeping wall's clickable area over the crack is drawn
			TileObject wall = plugin.baseWall(shown);
			Shape area = wall == null ? null : wall.getClickbox();
			if (area != null)
			{
				OverlayUtil.renderPolygon(graphics, area, config.waitColour());
			}
		}
		drawText(graphics, shown, waitTicks > 0 ? "Wait " + waitTicks : "Wait", config.waitColour());
		return null;
	}

	private void drawText(Graphics2D graphics, int shown, String text, Color colour)
	{
		LocalPoint point = LocalPoint.fromWorld(client.getTopLevelWorldView(), Walls.wallX(shown), Walls.wallY(shown));
		if (point == null)
		{
			return;
		}
		Point location = Perspective.getCanvasTextLocation(client, graphics, point, text, TEXT_HEIGHT);
		if (location != null)
		{
			OverlayUtil.renderTextLocation(graphics, location, text, colour);
		}
	}
}
