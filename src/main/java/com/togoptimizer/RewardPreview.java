/*
 * Copyright (c) 2026, Juna Reminder contributors
 * SPDX-License-Identifier: BSD-2-Clause
 */
package com.togoptimizer;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.VarbitID;

/** The XP a bowl would award at the character's current skill XP and diary completion. */
final class RewardPreview
{
	private static final EnumSet<WorldType> SPECIAL_WORLDS = EnumSet.of(WorldType.DEADMAN,
		WorldType.SEASONAL, WorldType.TOURNAMENT_WORLD, WorldType.QUEST_SPEEDRUNNING, WorldType.PVP_ARENA,
		WorldType.LAST_MAN_STANDING, WorldType.BETA_WORLD, WorldType.NOSAVE_MODE);

	// The game's tie order changed with Sailing and differs from Skill.values().
	private static final Skill[] TIE_ORDER = {
		Skill.ATTACK, Skill.STRENGTH, Skill.RANGED, Skill.MAGIC, Skill.DEFENCE, Skill.HITPOINTS,
		Skill.PRAYER, Skill.AGILITY, Skill.HERBLORE, Skill.THIEVING, Skill.CRAFTING, Skill.RUNECRAFT,
		Skill.MINING, Skill.SMITHING, Skill.FISHING, Skill.COOKING, Skill.FIREMAKING, Skill.WOODCUTTING,
		Skill.FLETCHING, Skill.SLAYER, Skill.FARMING, Skill.CONSTRUCTION, Skill.HUNTER, Skill.SAILING
	};
	static final Set<Skill> REWARD_SKILLS = Set.of(TIE_ORDER);

	final Skill skill;
	private final int xpToCap;
	private final int hundredthsPerTear;
	private final String displayRate;

	private RewardPreview(Skill skill, int skillXp, boolean hardDiary)
	{
		this.skill = skill;
		xpToCap = Experience.MAX_SKILL_XP - skillXp;
		// Base XP rises by one tenth per 27 skill XP. The diary adds 10% to the bowl.
		int tenthsPerTear = Math.min(600, 100 + skillXp / 27);
		hundredthsPerTear = tenthsPerTear * (hardDiary ? 11 : 10);
		long cappedRate = Math.min(hundredthsPerTear, (long) xpToCap * 100);
		displayRate = BigDecimal.valueOf(cappedRate, 2).stripTrailingZeros().toPlainString();
	}

	/** Call on the client thread. Special game modes have different XP or skill rules. */
	static boolean specialWorld(Client client)
	{
		return !Collections.disjoint(client.getWorldType(), SPECIAL_WORLDS);
	}

	@Nullable
	static RewardPreview read(Client client)
	{
		if (client.getOverallExperience() <= 0 || specialWorld(client))
		{
			return null;
		}
		return select(client::getSkillExperience, lockedSkills(client),
			client.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_HARD_COMPLETE) > 0);
	}

	static EnumSet<Skill> lockedSkills(Client client)
	{
		EnumSet<Skill> locked = EnumSet.noneOf(Skill.class);
		if (Quest.DRUIDIC_RITUAL.getState(client) != QuestState.FINISHED)
		{
			locked.add(Skill.HERBLORE);
		}
		if (Quest.RUNE_MYSTERIES.getState(client) != QuestState.FINISHED)
		{
			locked.add(Skill.RUNECRAFT);
		}
		if (Quest.PANDEMONIUM.getState(client) != QuestState.FINISHED)
		{
			locked.add(Skill.SAILING);
		}
		if (client.getVarbitValue(VarbitID.POH_HOUSE_LOCATION) <= 0)
		{
			locked.add(Skill.CONSTRUCTION);
		}
		return locked;
	}

	@Nullable
	static RewardPreview select(ToIntFunction<Skill> experience, EnumSet<Skill> locked, boolean hardDiary)
	{
		Skill lowest = null;
		int lowestXp = Integer.MAX_VALUE;
		for (Skill candidate : TIE_ORDER)
		{
			if (locked.contains(candidate))
			{
				continue;
			}
			int xp = experience.applyAsInt(candidate);
			if (xp < 0 || xp > Experience.MAX_SKILL_XP)
			{
				return null;
			}
			if (xp < lowestXp)
			{
				lowest = candidate;
				lowestXp = xp;
			}
		}
		return lowest == null ? null : new RewardPreview(lowest, lowestXp, hardDiary);
	}

	/** Projected whole XP. RuneLite exposes the current skill XP rounded down to a whole number. */
	int xpFor(int tears)
	{
		if (tears < 0)
		{
			throw new IllegalArgumentException("A bowl must contain a nonnegative number of tears");
		}
		long bowlXp = (long) hundredthsPerTear * tears / 100;
		return (int) Math.min(xpToCap, bowlXp);
	}

	int capAward(int xp)
	{
		return Math.min(xpToCap, xp);
	}

	/** A whole-XP change can differ by one when the bowl or starting XP contains a fraction. */
	boolean matchesObserved(int xp, @Nullable Integer tears, int maxTears)
	{
		if (xp <= 0 || xp > xpToCap)
		{
			return false;
		}
		// One quest point buys one tick, which can award at most one tear.
		if (tears == null && xp > ((long) maxTears * hundredthsPerTear + 99) / 100)
		{
			return false;
		}
		if (tears == null && xp == xpToCap)
		{
			return true;
		}
		// When the final counter was cleared, still require a gain consistent with a whole number of tears.
		int count = tears != null ? tears : (int) Math.round((double) xp * 100 / hundredthsPerTear);
		if (count <= 0)
		{
			return false;
		}
		long hundredths = (long) count * hundredthsPerTear;
		return xp == xpFor(count) || (hundredths % 100 != 0 && xp == hundredths / 100 + 1);
	}

	/** Displayable XP rate for one tear, including the diary bonus and remaining capacity. */
	String perTear()
	{
		return displayRate;
	}
}
