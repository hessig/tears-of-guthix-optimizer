package com.togoptimizer;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;

/** Samples the player's own bowl, independently of the optimizer's simulated games. */
final class BowlReward
{
	private static final String LAST_BOWL = "lastRewardTears";
	private static final String HISTORY = "bowlRewardHistory";
	private final Client client;
	private final ConfigManager configManager;
	private final RoomBowl bowl = new RoomBowl();
	private int nextSkillCheck;
	@Nullable
	private RewardPreview skill;
	private String profile;
	private List<BowlHistory.Entry> history;
	private BowlHistory.Before before;
	private long beforeTotal;
	private int beforeTick = -1;
	private int drinkTick = -1;
	private boolean trackingAward;
	private long drankAt;
	private Integer finalTears;

	@Inject
	BowlReward(Client client, ConfigManager configManager)
	{
		this.client = client;
		this.configManager = configManager;
	}

	void reset()
	{
		bowl.reset();
		nextSkillCheck = 0;
		skill = null;
		profile = null;
		history = null;
		before = null;
		beforeTick = -1;
		drinkTick = -1;
		trackingAward = false;
	}

	private boolean loadProfile()
	{
		String current = configManager.getRSProfileKey();
		if (!Objects.equals(current, profile))
		{
			reset();
			profile = current;
		}
		return profile != null;
	}

	void sampleRoom()
	{
		if (!loadProfile())
		{
			return;
		}
		int tick = client.getTickCount();
		boolean playing = client.getVarbitValue(VarbitID.TOG_COUNTDOWN) > 0;
		bowl.sample(client.getVarbitValue(VarbitID.TOG_TEARS_COLLECTED), tick, playing);
		if (playing && !trackingAward && !RewardPreview.specialWorld(client))
		{
			long total = client.getOverallExperience();
			if (total > 0 && (before == null || total != beforeTotal || tick - beforeTick > 15))
			{
				before = BowlHistory.Before.capture(client::getSkillExperience, RewardPreview.lockedSkills(client),
					client.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_HARD_COMPLETE) > 0, client.getVarpValue(VarPlayerID.QP));
				beforeTotal = total;
			}
			beforeTick = tick;
		}
	}

	void tearsChanged(int value)
	{
		bowl.changed(value, client.getTickCount());
	}

	void drank()
	{
		if (!loadProfile())
		{
			return;
		}
		int tick = client.getTickCount();
		if (drinkTick >= 0 && tick >= drinkTick && tick - drinkTick <= 15)
		{
			return;
		}
		drinkTick = tick;
		int count = bowl.finalCount(client.getVarbitValue(VarbitID.TOG_TEARS_COLLECTED), tick);
		if (count >= 0)
		{
			configManager.setRSProfileConfiguration(TogOptimizerConfig.GROUP, LAST_BOWL, count);
		}
		else
		{
			configManager.unsetRSProfileConfiguration(TogOptimizerConfig.GROUP, LAST_BOWL);
		}
		bowl.reset();
		skill = null;
		nextSkillCheck = 0;
		if (before != null && tick >= beforeTick && tick - beforeTick <= 15)
		{
			trackingAward = true;
			drankAt = System.currentTimeMillis();
			finalTears = count < 0 ? null : count;
		}
		else
		{
			before = null;
		}
	}

	/** Observe the post-packet skill totals, even when the sidebar and all notifications are off. */
	void checkReward()
	{
		if (!trackingAward || !loadProfile() || before == null)
		{
			return;
		}
		int elapsed = client.getTickCount() - drinkTick;
		if (elapsed >= 0 && elapsed <= 6)
		{
			BowlHistory.Entry earned = before.award(client::getSkillExperience, drankAt, finalTears);
			if (earned == null)
			{
				return;
			}
			List<BowlHistory.Entry> current = history();
			List<BowlHistory.Entry> updated = BowlHistory.add(current, earned);
			if (updated != current)
			{
				configManager.setRSProfileConfiguration(TogOptimizerConfig.GROUP, HISTORY, BowlHistory.encode(updated));
				history = updated;
			}
		}
		trackingAward = false;
		before = null;
	}

	/** Immutable, newest first. There's no way to reconstruct old XP awards from a saved tear count. */
	List<BowlHistory.Entry> history()
	{
		if (!loadProfile())
		{
			return List.of();
		}
		if (history == null)
		{
			history = BowlHistory.decode(configManager.getRSProfileConfiguration(TogOptimizerConfig.GROUP, HISTORY, String.class));
		}
		return history;
	}

	@Nullable
	String estimate(boolean inRoom, @Nullable Integer requestedCount, boolean refresh)
	{
		Estimate result = projection(inRoom, requestedCount, refresh);
		return result == null ? null : result.line();
	}

	/** Read on the client thread. The returned estimate can also be used by Swing. */
	@Nullable
	Estimate projection(boolean inRoom, @Nullable Integer requestedCount, boolean refresh)
	{
		if (!loadProfile())
		{
			return null;
		}
		int tick = client.getTickCount();
		if (refresh || tick >= nextSkillCheck)
		{
			skill = RewardPreview.read(client);
			nextSkillCheck = tick + 10;
		}
		if (skill == null)
		{
			return null;
		}
		Integer current = requestedCount == null && inRoom ? client.getVarbitValue(VarbitID.TOG_TEARS_COLLECTED) : null;
		Integer previous = requestedCount == null && !inRoom
			? configManager.getRSProfileConfiguration(TogOptimizerConfig.GROUP, LAST_BOWL, Integer.class) : null;
		Integer previousXp = null;
		if (requestedCount == null && current == null && previous == null && !history().isEmpty())
		{
			previousXp = history.get(0).xp;
		}
		return estimateFor(skill, requestedCount, current, previous, previousXp);
	}

	static Estimate estimateFor(RewardPreview skill, @Nullable Integer requested, @Nullable Integer current,
		@Nullable Integer previous)
	{
		return estimateFor(skill, requested, current, previous, null);
	}

	static Estimate estimateFor(RewardPreview skill, @Nullable Integer requested, @Nullable Integer current,
		@Nullable Integer previous, @Nullable Integer previousXp)
	{
		if (requested != null)
		{
			return new Estimate(skill, Basis.CUSTOM, requested);
		}
		if (current != null)
		{
			return new Estimate(skill, Basis.CURRENT, current);
		}
		if (previous != null && previous >= 0)
		{
			return new Estimate(skill, Basis.LAST, previous);
		}
		if (previousXp != null && previousXp >= 0)
		{
			return new Estimate(skill, Basis.LAST_XP, null, skill.capAward(previousXp));
		}
		return new Estimate(skill, Basis.RATE, null);
	}

	enum Basis
	{
		CURRENT("in your bowl"),
		LAST("to match last game"),
		LAST_XP("using last recorded XP"),
		CUSTOM("requested"),
		RATE("");

		private final String description;

		Basis(String description)
		{
			this.description = description;
		}
	}

	static final class Estimate
	{
		private final RewardPreview reward;
		final Skill skill;
		final String perTear;
		final Basis basis;
		@Nullable
		final Integer tears;
		@Nullable
		final Integer xp;

		private Estimate(RewardPreview reward, Basis basis, @Nullable Integer tears)
		{
			this(reward, basis, tears, tears == null ? null : reward.xpFor(tears));
		}

		private Estimate(RewardPreview reward, Basis basis, @Nullable Integer tears, @Nullable Integer xp)
		{
			this.reward = reward;
			this.skill = reward.skill;
			this.perTear = reward.perTear();
			this.basis = basis;
			this.tears = tears;
			this.xp = xp;
		}

		Estimate forCount(int count)
		{
			return new Estimate(reward, Basis.CUSTOM, count);
		}

		String line()
		{
			if (basis == Basis.LAST_XP)
			{
				return String.format(Locale.ENGLISH, "~%,d %s XP (%s)", xp, skill.getName(), basis.description);
			}
			if (tears == null)
			{
				return perTear + " " + skill.getName() + " XP/tear";
			}
			return String.format(Locale.ENGLISH, "~%,d %s XP (%,d %s %s)", xp,
				skill.getName(), tears, tears == 1 ? "tear" : "tears", basis.description);
		}
	}

	/** Keeps the final visible count until the local player's drinking animation. */
	static final class RoomBowl
	{
		private int tears = -1;
		private int roomTick = -1;

		void reset()
		{
			tears = -1;
			roomTick = -1;
		}

		void sample(int value, int tick, boolean timerRunning)
		{
			// Zero during play is a real empty bowl. Once time runs out, the game clears the counter.
			if (value > 0 || timerRunning)
			{
				tears = value;
				roomTick = tick;
			}
		}

		void changed(int value, int tick)
		{
			// The last increment or loss may be delivered while the player is leaving the room.
			if (value > 0 && sampledWithin(tick, 2))
			{
				tears = value;
			}
		}

		int finalCount(int live, int tick)
		{
			if (live > 0)
			{
				return live;
			}
			// A last observed 1 followed only by the end-of-game zero could also be a final green loss.
			// Leave the count unknown so next week's estimate falls back to XP per tear.
			return sampledWithin(tick, 15) && tears != 1 ? tears : -1;
		}

		private boolean sampledWithin(int tick, int limit)
		{
			return roomTick >= 0 && tick >= roomTick && tick - roomTick <= limit;
		}
	}
}
