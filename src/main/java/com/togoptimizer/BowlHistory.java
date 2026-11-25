package com.togoptimizer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;
import net.runelite.api.Experience;
import net.runelite.api.Skill;

/** Completed bowls whose XP gain was observed while the player drank. */
final class BowlHistory
{
	private static final Gson GSON = new GsonBuilder().serializeNulls().create();

	static final class Entry
	{
		final long recordedAt;
		@Nullable final Skill skill;
		final int xp;
		@Nullable final Integer tears;

		Entry(long recordedAt, @Nullable Skill skill, int xp, @Nullable Integer tears)
		{
			this.recordedAt = recordedAt;
			this.skill = skill;
			this.xp = xp;
			this.tears = tears;
		}
	}

	/** All eligible skills tied on whole XP can receive the bowl, since RuneLite hides fractional XP. */
	static final class Before
	{
		private final EnumSet<Skill> candidates;
		private final int startingXp;
		private final RewardPreview preview;
		private final int maxTears;

		private Before(EnumSet<Skill> candidates, int startingXp, RewardPreview preview, int maxTears)
		{
			this.candidates = candidates;
			this.startingXp = startingXp;
			this.preview = preview;
			this.maxTears = maxTears;
		}

		@Nullable
		static Before capture(ToIntFunction<Skill> xp, EnumSet<Skill> locked, boolean hardDiary, int questPoints)
		{
			RewardPreview preview = RewardPreview.select(xp, locked, hardDiary);
			if (preview == null)
			{
				return null;
			}
			int lowest = xp.applyAsInt(preview.skill);
			EnumSet<Skill> candidates = EnumSet.copyOf(RewardPreview.REWARD_SKILLS);
			candidates.removeIf(skill -> locked.contains(skill) || xp.applyAsInt(skill) != lowest);
			return new Before(candidates, lowest, preview, questPoints);
		}

		@Nullable
		Entry award(ToIntFunction<Skill> currentXp, long when, @Nullable Integer tears)
		{
			Entry result = null;
			for (Skill skill : candidates)
			{
				int after = currentXp.applyAsInt(skill);
				int gain = after - startingXp;
				if (gain == 0)
				{
					continue;
				}
				if (result != null || after > Experience.MAX_SKILL_XP || !preview.matchesObserved(gain, tears, maxTears))
				{
					return null;
				}
				result = new Entry(when, skill, gain, tears);
			}
			// Without a gain there is no observed recipient. Only these two cases prove a zero award.
			if (result == null && (Integer.valueOf(0).equals(tears) || startingXp == Experience.MAX_SKILL_XP))
			{
				return new Entry(when, null, 0, tears);
			}
			return result;
		}
	}

	private BowlHistory()
	{
	}

	static List<Entry> decode(@Nullable String json)
	{
		if (json == null)
		{
			return List.of();
		}
		try
		{
			JsonArray entries = GSON.fromJson(json, JsonArray.class);
			List<Entry> valid = new ArrayList<>();
			if (entries != null)
			{
				for (JsonElement row : entries)
				{
					if (!row.isJsonObject())
					{
						continue;
					}
					Entry entry = decodeEntry(row.getAsJsonObject());
					if (entry != null)
					{
						valid.add(entry);
					}
				}
			}
			return List.copyOf(valid);
		}
		catch (JsonParseException ex)
		{
			return List.of();
		}
	}

	@Nullable
	private static Entry decodeEntry(JsonObject data)
	{
		// Gson otherwise treats missing numbers as zero and unknown skill names as null.
		if (!data.has("skill") || !data.has("xp") || !data.get("xp").isJsonPrimitive()
			|| !data.getAsJsonPrimitive("xp").isNumber())
		{
			return null;
		}
		try
		{
			int xp = data.get("xp").getAsBigDecimal().intValueExact();
			Entry entry = GSON.fromJson(data, Entry.class);
			if (entry.recordedAt <= 0 || (entry.tears != null && entry.tears < 0))
			{
				return null;
			}
			if (xp == 0)
			{
				return data.get("skill").isJsonNull() ? entry : null;
			}
			return xp > 0 && xp <= Experience.MAX_SKILL_XP && entry.skill != null
				&& RewardPreview.REWARD_SKILLS.contains(entry.skill) && !Integer.valueOf(0).equals(entry.tears) ? entry : null;
		}
		catch (JsonParseException | ArithmeticException | NumberFormatException ex)
		{
			return null;
		}
	}

	static String encode(List<Entry> history)
	{
		return GSON.toJson(history);
	}

	static List<Entry> add(List<Entry> history, Entry earned)
	{
		// The game permits at most one bowl on any UTC date. Replayed events must not add a second entry.
		if (!history.isEmpty() && history.get(0).recordedAt / 86_400_000L == earned.recordedAt / 86_400_000L)
		{
			return history;
		}
		List<Entry> updated = new ArrayList<>(history.size() + 1);
		updated.add(earned);
		updated.addAll(history);
		return List.copyOf(updated);
	}
}
