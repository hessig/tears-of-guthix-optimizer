package com.togoptimizer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

final class TearsPanel extends PluginPanel
{
	private static final int EXAMPLE_TEARS = 100;
	private static final Color BLUE = new Color(130, 206, 241);
	private static final Color READY = new Color(135, 213, 155);
	private static final Color MUTED = new Color(190, 195, 199);
	private static final DateTimeFormatter UPDATED = DateTimeFormatter.ofPattern("MMM d, uuuu · h:mm a z", Locale.ENGLISH)
		.withZone(ZoneId.systemDefault());
	private static final DateTimeFormatter RUN_DATE = DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH)
		.withZone(ZoneId.systemDefault());

	private final Runnable onOpen;
	private final JLabel resetStatus = label("", 15, Color.WHITE, Font.BOLD);
	private final JLabel resetTime = label("", 12, Color.WHITE, Font.PLAIN);
	private final JTextArea requirements = note("");
	private final JTextArea lastUpdate = note("");
	private final JTextArea refreshHint = note("To refresh, open Quest List → Tears of Guthix.");
	private final JPanel rewardCard = section("Bowl reward");
	private final JLabel skill = label("", 15, Color.WHITE, Font.BOLD);
	private final JLabel amount = label("", 21, BLUE, Font.BOLD);
	private final JLabel basis = label("", 12, MUTED, Font.PLAIN);
	private final JPanel countRow = new JPanel(new BorderLayout(6, 0));
	private final JSpinner count = new JSpinner(new SpinnerNumberModel(EXAMPLE_TEARS, 0, 9999, 1));
	private final JButton useLast = new JButton("Use last bowl");
	private final JPanel historyCard = section("Past rewards");
	private final JComboBox<BowlHistory.Entry> historyBox = new JComboBox<>();
	private final JTextArea emptyHistory = note("Finish a run with the plugin on to record its reward.");
	private List<BowlHistory.Entry> history = List.of();

	private volatile boolean active;
	private boolean settingCount;
	@Nullable private BowlReward.Estimate reward;
	@Nullable private Integer customCount;

	TearsPanel(Runnable onOpen)
	{
		this.onOpen = onOpen;
		setBorder(BorderFactory.createEmptyBorder(9, 7, 7, 7));
		setLayout(new GridBagLayout());
		row(this, label("Tears of Guthix", 17, Color.WHITE, Font.BOLD), 0);

		JPanel resetCard = section("Weekly reset");
		row(resetCard, resetStatus, 6);
		row(resetCard, resetTime, 4);
		row(resetCard, requirements, 5);
		row(resetCard, lastUpdate, 8);
		row(resetCard, refreshHint, 3);
		row(this, resetCard, 8);

		row(rewardCard, skill, 6);
		row(rewardCard, amount, 2);
		row(rewardCard, basis, 3);
		countRow.setOpaque(false);
		JLabel countLabel = label("Tears", 12, Color.WHITE, Font.PLAIN);
		countLabel.setLabelFor(count);
		countRow.add(countLabel, BorderLayout.WEST);
		count.setEditor(new JSpinner.NumberEditor(count, "0"));
		count.getAccessibleContext().setAccessibleName("Tears in the estimated bowl");
		countRow.add(count, BorderLayout.EAST);
		row(rewardCard, countRow, 8);
		row(rewardCard, useLast, 5);
		row(this, rewardCard, 8);

		historyBox.setRenderer(new RewardRenderer());
		historyBox.setMaximumRowCount(6);
		historyBox.getAccessibleContext().setAccessibleName("Past rewards by date");
		row(historyCard, historyBox, 6);
		row(historyCard, emptyHistory, 5);
		row(this, historyCard, 8);

		count.addChangeListener(e ->
		{
			if (reward != null && !settingCount)
			{
				Integer entered = (Integer) count.getValue();
				customCount = entered.equals(reward.tears) ? null : entered;
				displayReward();
			}
		});
		useLast.addActionListener(e ->
		{
			if (reward == null)
			{
				return;
			}
			// When we know only last game's XP, let the player opt into a tear-count forecast.
			customCount = reward.basis == BowlReward.Basis.LAST_XP && customCount == null ? EXAMPLE_TEARS : null;
			displayReward();
		});
		update(WeeklyReminder.Summary.NO_PROFILE, null, List.of());
	}

	private static JTextArea note(String text)
	{
		JTextArea area = new JTextArea(text);
		area.setEditable(false);
		area.setFocusable(false);
		area.setOpaque(false);
		area.setBorder(null);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setForeground(MUTED);
		area.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		return area;
	}

	private static JPanel section(String title)
	{
		JPanel card = new JPanel(new GridBagLayout());
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createEmptyBorder(9, 10, 10, 10));
		row(card, label(title, 12, BLUE, Font.BOLD), 0);
		return card;
	}

	private static void row(JPanel parent, JComponent component, int top)
	{
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.insets = new Insets(top, 0, 0, 0);
		parent.add(component, c);
	}

	private static JLabel label(String text, int size, Color color, int style)
	{
		JLabel label = new JLabel(text);
		label.setForeground(color);
		label.setFont(size <= 12 ? new Font(Font.SANS_SERIF, style, 12)
			: label.getFont().deriveFont(style, (float) size));
		return label;
	}

	@Override
	public void onActivate()
	{
		active = true;
		onOpen.run();
	}

	@Override
	public void onDeactivate()
	{
		active = false;
	}

	boolean isPanelActive()
	{
		return active;
	}

	/** Swing receives immutable values captured on the client thread. */
	void update(WeeklyReminder.Summary reset, @Nullable BowlReward.Estimate estimate, List<BowlHistory.Entry> entries)
	{
		boolean playing = estimate != null && estimate.basis == BowlReward.Basis.CURRENT;
		resetStatus.setText(playing ? "Run in progress" : reset.headline);
		resetStatus.setForeground(!playing && reset.ready ? READY : Color.WHITE);
		resetTime.setText(reset.resetTime);
		resetTime.setVisible(!playing && reset.resetTime != null);
		resetTime.setToolTipText(reset.resetUtc);
		String detail = playing ? "Your next reset is set when you drink the bowl." : reset.details;
		if (!requirements.getText().equals(detail))
		{
			requirements.setText(detail);
		}
		String updated = reset.updatedAt == null ? "Last update unknown"
			: "Last updated\n" + UPDATED.format(Instant.ofEpochMilli(reset.updatedAt));
		if (!lastUpdate.getText().equals(updated))
		{
			lastUpdate.setText(updated);
		}
		lastUpdate.setVisible(reset.canRefresh);
		refreshHint.setVisible(reset.canRefresh);
		reward = estimate;
		rewardCard.setVisible(estimate != null);
		if (estimate == null || playing)
		{
			customCount = null;
		}
		if (estimate != null)
		{
			displayReward();
		}
		historyCard.setVisible(reset.canRefresh || !entries.isEmpty());
		historyBox.setVisible(!entries.isEmpty());
		emptyHistory.setVisible(entries.isEmpty());
		if (!entries.equals(history))
		{
			updateHistory(entries);
		}
	}

	private void updateHistory(List<BowlHistory.Entry> entries)
	{
		// Follow the newest run unless the player deliberately selected an older reward.
		int index = historyBox.getSelectedIndex();
		BowlHistory.Entry browsing = index > 0 ? history.get(index) : null;
		history = entries;
		historyBox.setModel(new DefaultComboBoxModel<>(entries.toArray(new BowlHistory.Entry[0])));
		if (browsing != null)
		{
			for (int i = 0; i < entries.size(); i++)
			{
				if (entries.get(i).recordedAt == browsing.recordedAt)
				{
					historyBox.setSelectedIndex(i);
					break;
				}
			}
		}
	}

	private static final class RewardRenderer extends DefaultListCellRenderer
	{
		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus)
		{
			super.getListCellRendererComponent(list, value, index, selected, focus);
			if (value instanceof BowlHistory.Entry)
			{
				BowlHistory.Entry entry = (BowlHistory.Entry) value;
				String date = RUN_DATE.format(Instant.ofEpochMilli(entry.recordedAt));
				String award = entry.skill == null ? "No XP awarded"
					: String.format(Locale.ENGLISH, "%,d %s XP", entry.xp, entry.skill.getName());
				setText("<html>" + date + "<br><b>" + award + "</b></html>");
			}
			return this;
		}
	}

	private void displayReward()
	{
		boolean live = reward.basis == BowlReward.Basis.CURRENT;
		int automatic = reward.tears == null ? EXAMPLE_TEARS : reward.tears;
		int tears = customCount == null ? automatic : customCount;
		boolean lastXp = reward.basis == BowlReward.Basis.LAST_XP;
		boolean usingLastXp = lastXp && customCount == null;
		BowlReward.Estimate shown = customCount == null && reward.xp != null ? reward : reward.forCount(tears);
		skill.setText(shown.skill.getName());
		amount.setText(String.format(Locale.ENGLISH, "~%,d XP", shown.xp));
		String source = reward.tears == null ? "Example bowl" : "Last bowl";
		if (live)
		{
			source = tears + (tears == 1 ? " tear in bowl" : " tears in bowl");
		}
		else if (customCount != null)
		{
			source = "Custom bowl";
		}
		basis.setText(usingLastXp ? "Based on last recorded XP" : source + "  ·  " + shown.perTear + " XP/tear");
		countRow.setVisible(!live && !usingLastXp);
		String restore = lastXp ? "Use last reward" : "Use last bowl";
		useLast.setText(usingLastXp ? "Estimate by tear count" : restore);
		useLast.setVisible(lastXp || (!live && reward.tears != null && customCount != null));
		if (countRow.isVisible() && (Integer) count.getValue() != tears)
		{
			// A model update is not an edit by the player.
			settingCount = true;
			count.setValue(tears);
			settingCount = false;
		}
	}
}
