package com.togoptimizer;

import java.awt.Component;
import java.awt.Container;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class TearsPanelTest
{
	private static final long SYNCED = Instant.parse("2026-11-25T17:45:00Z").toEpochMilli();
	private static final WeeklyReminder.Summary RESET = WeeklyReminder.Summary.forVisit(SYNCED + 86_400_000,
		SYNCED, null, ZoneId.of("UTC"), SYNCED);
	private static final RewardPreview NEXT = RewardPreview.select(skill -> skill == Skill.FISHING ? 0 : 1,
		EnumSet.noneOf(Skill.class), false);
	private static final BowlHistory.Entry NEWER = new BowlHistory.Entry(SYNCED - 7 * 86_400_000L, Skill.SLAYER, 12_804, 194);
	private static final BowlHistory.Entry OLDER = new BowlHistory.Entry(SYNCED - 14 * 86_400_000L, Skill.MINING, 15_180, 230);

	@Test
	public void showsActualRewardsAndKeepsTheSelectedRunAcrossRefreshes() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			TearsPanel panel = new TearsPanel(() -> {});
			List<BowlHistory.Entry> runs = List.of(NEWER, OLDER);
			BowlReward.Estimate next = BowlReward.estimateFor(NEXT, null, null, NEWER.tears);
			panel.update(RESET, next, runs);
			assertNotNull(find(panel, JTextArea.class, a -> a.getText().startsWith("Last updated\nNov")));
			assertNotNull(find(panel, JTextArea.class, a -> a.isVisible() && a.getText().contains("Quest List → Tears of Guthix")));
			assertNotNull(find(panel, JLabel.class, label -> "Fishing".equals(label.getText())));
			JComboBox<?> history = find(panel, JComboBox.class, b -> b.isVisible());
			assertNotNull(history);
			assertSame(NEWER, history.getSelectedItem());
			history.setSelectedIndex(1);
			String text = renderedSelection(history);
			assertTrue(text, text.contains("Nov") && text.contains("15,180 Mining XP"));
			Object originalModel = history.getModel();
			panel.update(RESET, next, runs);
			assertSame(originalModel, history.getModel());
			assertSame(OLDER, history.getSelectedItem());
			BowlHistory.Entry newest = new BowlHistory.Entry(SYNCED, Skill.FISHING, 1_600, 160);
			panel.update(RESET, next, List.of(newest, NEWER, OLDER));
			assertSame(OLDER, history.getSelectedItem());
			BowlHistory.Entry emptyBowl = new BowlHistory.Entry(SYNCED + 7 * 86_400_000L, null, 0, 0);
			panel.update(RESET, next, List.of(emptyBowl, newest, NEWER, OLDER));
			history.setSelectedIndex(0);
			assertTrue(renderedSelection(history).contains("No XP awarded"));
			panel.update(WeeklyReminder.Summary.NO_PROFILE, null, List.of());
			assertEquals(0, history.getItemCount());
			assertFalse(history.isVisible());
			assertNull(find(panel, JTextArea.class, a -> a.isVisible() && a.getText().contains("Quest List")));
		});
	}

	@Test
	public void lastXpIsAnAmountUntilThePlayerChoosesATearCount() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			TearsPanel panel = new TearsPanel(() -> {});
			BowlReward.Estimate previousXp = BowlReward.estimateFor(NEXT, null, null, null, 12_804);
			panel.update(RESET, previousXp, List.of());
			assertNotNull(find(panel, JLabel.class, label -> "~12,804 XP".equals(label.getText())));
			JButton toggle = find(panel, JButton.class, b -> "Estimate by tear count".equals(b.getText()));
			assertNotNull(toggle);
			assertTrue(toggle.isVisible());
			toggle.doClick(0);
			JSpinner spinner = find(panel, JSpinner.class, s -> true);
			assertNotNull(spinner);
			spinner.setValue(88);
			panel.update(RESET, previousXp, List.of());
			assertNotNull(find(panel, JLabel.class, label -> "~880 XP".equals(label.getText())));
			assertEquals("Use last reward", toggle.getText());
			toggle.doClick(0);
			assertNotNull(find(panel, JLabel.class, label -> "~12,804 XP".equals(label.getText())));
		});
	}

	private static <T> String renderedSelection(JComboBox<T> box)
	{
		int selected = box.getSelectedIndex();
		Component label = box.getRenderer().getListCellRendererComponent(new JList<>(), box.getItemAt(selected),
			selected, false, false);
		return ((JLabel) label).getText();
	}

	private static <T extends Component> T find(Container root, Class<T> type, Predicate<T> wanted)
	{
		for (Component child : root.getComponents())
		{
			if (type.isInstance(child) && wanted.test(type.cast(child)))
			{
				return type.cast(child);
			}
			if (child instanceof Container)
			{
				T found = find((Container) child, type, wanted);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}
}
