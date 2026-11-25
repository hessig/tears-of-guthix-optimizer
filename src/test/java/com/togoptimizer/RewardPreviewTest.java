/*
 * Copyright (c) 2026, Juna Reminder contributors
 * SPDX-License-Identifier: BSD-2-Clause
 */
package com.togoptimizer;

import java.util.EnumMap;
import java.util.EnumSet;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class RewardPreviewTest
{
	private final EnumMap<Skill, Integer> xp = new EnumMap<>(Skill.class);
	private final EnumSet<Skill> locked = EnumSet.noneOf(Skill.class);

	public RewardPreviewTest()
	{
		for (Skill skill : Skill.values())
		{
			xp.put(skill, 100_000);
		}
	}

	private RewardPreview preview(boolean hardDiary)
	{
		return RewardPreview.select(xp::get, locked, hardDiary);
	}

	@Test
	public void choosesLeastExperienceAndBreaksTiesInGameOrder()
	{
		xp.put(Skill.DEFENCE, 20);
		xp.put(Skill.MAGIC, 20);
		xp.put(Skill.RANGED, 20);
		assertEquals(Skill.RANGED, preview(false).skill);
		xp.put(Skill.MAGIC, 19);
		assertEquals(Skill.MAGIC, preview(false).skill);
	}

	@Test
	public void ignoresSkillsUntilTheyAreUnlocked()
	{
		locked.addAll(EnumSet.of(Skill.HERBLORE, Skill.RUNECRAFT, Skill.CONSTRUCTION, Skill.SAILING));
		for (Skill skill : locked)
		{
			xp.put(skill, 0);
		}
		assertEquals(Skill.ATTACK, preview(false).skill);
		for (Skill skill : new Skill[]{Skill.SAILING, Skill.CONSTRUCTION, Skill.RUNECRAFT, Skill.HERBLORE})
		{
			locked.remove(skill);
			assertEquals(skill, preview(false).skill);
		}
		assertEquals("10", preview(false).perTear());
	}

	@Test
	public void liveBowlEstimatesUseTheSameRateForEveryTear()
	{
		// Observed runs reported on the OSRS Wiki's Tears of Guthix talk page.
		xp.put(Skill.HUNTER, 2_937);
		RewardPreview preview = preview(false);
		assertEquals("20.8", preview.perTear());
		assertEquals(0, preview.xpFor(0));
		assertEquals(4_160, preview.xpFor(200));
		assertEquals(4_139, preview.xpFor(199));
		xp.put(Skill.HUNTER, 7_097);
		assertEquals(7_602, preview(false).xpFor(210));
	}

	@Test
	public void includesTheFractionalDiaryBonusBeforeRoundingTheBowl()
	{
		xp.put(Skill.HUNTER, 1_000);
		assertEquals("15.07", preview(true).perTear());
		assertEquals(1_507, preview(true).xpFor(100));
		xp.put(Skill.HUNTER, 13_499);
		assertEquals(599, preview(false).xpFor(10));
		xp.put(Skill.HUNTER, 13_500);
		assertEquals("60", preview(false).perTear());
		assertEquals("66", preview(true).perTear());
		assertEquals(13_200, preview(true).xpFor(200));
	}

	@Test
	public void bothRatesAndBowlEstimatesRespectTheSkillXpCap()
	{
		xp.replaceAll((skill, previous) -> Experience.MAX_SKILL_XP);
		assertEquals(0, preview(true).xpFor(1));
		assertEquals("0", preview(true).perTear());
		xp.put(Skill.HUNTER, Experience.MAX_SKILL_XP - 4);
		assertEquals("4", preview(false).perTear());
		assertEquals(4, preview(false).xpFor(Integer.MAX_VALUE));
	}

	@Test
	public void incompleteSkillDataDoesNotProduceAReward()
	{
		xp.put(Skill.AGILITY, -1);
		assertNull(preview(false));
	}
}
