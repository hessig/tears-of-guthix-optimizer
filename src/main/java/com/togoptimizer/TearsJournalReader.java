package com.togoptimizer;

import javax.annotation.Nullable;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.util.Text;

final class TearsJournalReader
{
	private TearsJournalReader()
	{
	}

	/** Read after the game tick's widget updates. WidgetLoaded can precede the server's text. */
	@Nullable
	static String read(Client client)
	{
		Widget title = client.getWidget(InterfaceID.Questjournal.TITLE);
		if (title == null || title.isHidden())
		{
			return null;
		}
		String name = title.getText();
		if (name == null || !Quest.TEARS_OF_GUTHIX.getName().equalsIgnoreCase(
			Text.removeTags(name).replace('\u00a0', ' ').trim()))
		{
			return null;
		}

		Widget layer = client.getWidget(InterfaceID.Questjournal.TEXTLAYER);
		if (layer == null || layer.isHidden())
		{
			return null;
		}
		// In interface 119, QJ1 through QJ210 are direct text children of this scroll layer.
		StringBuilder body = new StringBuilder();
		appendRows(body, layer.getStaticChildren());
		appendRows(body, layer.getDynamicChildren());
		return body.length() == 0 ? null : body.toString();
	}

	private static void appendRows(StringBuilder body, Widget[] rows)
	{
		for (Widget row : rows)
		{
			if (row == null || row.getType() != WidgetType.TEXT || row.isSelfHidden())
			{
				continue;
			}
			String text = row.getText();
			if (text != null && !text.isEmpty())
			{
				if (body.length() > 0)
				{
					body.append('\n');
				}
				body.append(text);
			}
		}
	}
}
