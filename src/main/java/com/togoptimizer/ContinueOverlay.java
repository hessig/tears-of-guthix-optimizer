package com.togoptimizer;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * The continue countdown just above Juna's dialogue, where the player is looking while it's open.
 */
class ContinueOverlay extends Overlay
{
	private static final int GAP = 6;

	private final Client client;
	private final TogOptimizerPlugin plugin;
	private final TogOptimizerConfig config;

	@Inject
	private ContinueOverlay(Client client, TogOptimizerPlugin plugin, TogOptimizerConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showEntryTiming() || plugin.getUnlockAt() != null || plugin.getRequirement() != null)
		{
			return null;
		}
		String cue = JunaOverlay.continueCue(plugin);
		Widget dialogue = client.getWidget(InterfaceID.ChatLeft.TEXT);
		if (cue == null || dialogue == null || dialogue.isHidden())
		{
			return null;
		}
		Rectangle bounds = dialogue.getBounds();
		int width = graphics.getFontMetrics().stringWidth(cue);
		Point location = new Point(bounds.x + (bounds.width - width) / 2, bounds.y - GAP);
		OverlayUtil.renderTextLocation(graphics, location, cue, JunaOverlay.cueColour(cue));
		return null;
	}
}
