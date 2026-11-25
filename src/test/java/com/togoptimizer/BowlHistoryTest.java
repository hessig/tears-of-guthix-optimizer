package com.togoptimizer;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.*;

public class BowlHistoryTest
{
	private static final long DAY = 86_400_000L;
	private final EnumMap<Skill, Integer> xp = new EnumMap<>(Skill.class);
	private final EnumSet<Skill> locked = EnumSet.noneOf(Skill.class);

	public BowlHistoryTest()
	{
		for (Skill skill : Skill.values())
		{
			xp.put(skill, 50_000);
		}
		xp.put(Skill.FISHING, 20_000);
	}

	@Test
	public void actualXpDeterminesTheRecipientEvenWhenTwoSkillsAppearTied()
	{
		xp.put(Skill.ATTACK, 20_000);
		BowlHistory.Before before = BowlHistory.Before.capture(skill -> {
			assertTrue("Only trainable skills can be read", RewardPreview.REWARD_SKILLS.contains(skill));
			return xp.get(skill);
		}, locked, true, 300);
		xp.put(Skill.FISHING, 35_180);
		xp.put(Skill.MINING, 50_100);
		BowlHistory.Entry entry = before.award(xp::get, DAY, 230);
		assertEquals(Skill.FISHING, entry.skill);
		assertEquals(15_180, entry.xp);
		assertEquals(Integer.valueOf(230), entry.tears);
		xp.put(Skill.ATTACK, 35_180);
		assertNull(before.award(xp::get, DAY, 230));
	}

	@Test
	public void excludesLockedUnrelatedAndWrongSizedXpGains()
	{
		locked.add(Skill.SAILING);
		xp.put(Skill.SAILING, 20_000);
		BowlHistory.Before before = BowlHistory.Before.capture(xp::get, locked, false, 300);
		assertNull(before.award(xp::get, DAY, 20));
		xp.put(Skill.SAILING, 21_200);
		xp.put(Skill.MINING, 51_200);
		assertNull(before.award(xp::get, DAY, 20));
		xp.put(Skill.FISHING, 20_110);
		assertNull(before.award(xp::get, DAY, 20));
		assertNull(before.award(xp::get, DAY, null));
		xp.put(Skill.FISHING, 21_200);
		BowlHistory.Entry entry = before.award(xp::get, DAY, null);
		assertEquals(1_200, entry.xp);
		assertNull(entry.tears);
		xp.put(Skill.FISHING, 20_000 + 301 * 60);
		assertNull(before.award(xp::get, DAY, null));
	}

	@Test
	public void storesObservedWholeXpIncludingAFractionOrCappedAward()
	{
		xp.put(Skill.FISHING, 1_000);
		BowlHistory.Before before = BowlHistory.Before.capture(xp::get, locked, true, 300);
		xp.put(Skill.FISHING, 1_151); // 150.7 XP, crossing an additional whole-XP boundary.
		assertEquals(151, before.award(xp::get, DAY, 10).xp);
		xp.replaceAll((skill, old) -> Experience.MAX_SKILL_XP);
		xp.put(Skill.AGILITY, Experience.MAX_SKILL_XP - 7);
		before = BowlHistory.Before.capture(xp::get, locked, false, 300);
		xp.put(Skill.AGILITY, Experience.MAX_SKILL_XP);
		assertEquals(7, before.award(xp::get, DAY, 200).xp);
		assertEquals(7, before.award(xp::get, DAY, null).xp);
		assertNull(before.award(xp::get, DAY, 0));
	}

	@Test
	public void onlyAnEmptyBowlOrACappedCharacterProvesAZeroReward()
	{
		BowlHistory.Before before = BowlHistory.Before.capture(xp::get, locked, false, 300);
		assertNull(before.award(xp::get, DAY, null));
		assertNull(before.award(xp::get, DAY, 30));
		BowlHistory.Entry empty = before.award(xp::get, DAY, 0);
		assertEquals(0, empty.xp);
		assertNull(empty.skill);
		assertEquals(Integer.valueOf(0), empty.tears);
		xp.replaceAll((skill, old) -> Experience.MAX_SKILL_XP);
		before = BowlHistory.Before.capture(xp::get, locked, true, 300);
		BowlHistory.Entry capped = before.award(xp::get, 8 * DAY, 230);
		assertEquals(0, capped.xp);
		assertNull(capped.skill);
		List<BowlHistory.Entry> rows = BowlHistory.decode(BowlHistory.encode(List.of(capped, empty)));
		assertEquals(2, rows.size());
		assertNull(rows.get(0).skill);
		assertEquals(Integer.valueOf(230), rows.get(0).tears);
		assertEquals(Integer.valueOf(0), rows.get(1).tears);
		assertTrue(BowlHistory.decode("[{\"recordedAt\":86400000,\"skill\":null},"
			+ "{\"recordedAt\":86400000,\"skill\":null,\"xp\":0.25},"
			+ "{\"recordedAt\":86400000,\"xp\":0},"
			+ "{\"recordedAt\":86400000,\"xp\":0,\"skill\":\"Unknown skill\"}]").isEmpty());
	}

	@Test
	public void savesDifferentWeeksNewestFirstAndIgnoresAReplay()
	{
		BowlHistory.Entry earlier = new BowlHistory.Entry(DAY, Skill.FISHING, 10_000, 180);
		BowlHistory.Entry later = new BowlHistory.Entry(8 * DAY, Skill.SLAYER, 12_456, null);
		List<BowlHistory.Entry> rows = BowlHistory.add(BowlHistory.add(List.of(), earlier), later);
		assertSame(rows, BowlHistory.add(rows, new BowlHistory.Entry(8 * DAY + 1000, Skill.SLAYER, 12_456, null)));
		List<BowlHistory.Entry> loaded = BowlHistory.decode(BowlHistory.encode(rows));
		assertEquals(2, loaded.size());
		assertEquals(later.recordedAt, loaded.get(0).recordedAt);
		assertEquals(Skill.SLAYER, loaded.get(0).skill);
		assertEquals(later.xp, loaded.get(0).xp);
		assertNull(loaded.get(0).tears);
		assertEquals(earlier.tears, loaded.get(1).tears);
		assertTrue(BowlHistory.decode(null).isEmpty());
		assertTrue(BowlHistory.decode("bad history").isEmpty());
		assertTrue(BowlHistory.decode("[{},null]").isEmpty());
	}

	@Test
	public void missingTearCountUsesLastObservedXpWithTheNextSkillAndItsCap()
	{
		RewardPreview fishing = RewardPreview.select(xp::get, locked, false);
		BowlReward.Estimate estimate = BowlReward.estimateFor(fishing, null, null, null, 15_180);
		assertEquals(BowlReward.Basis.LAST_XP, estimate.basis);
		assertEquals(Skill.FISHING, estimate.skill);
		assertEquals(Integer.valueOf(15_180), estimate.xp);
		assertNull(estimate.tears);
		assertEquals("~15,180 Fishing XP (using last recorded XP)", estimate.line());
		assertEquals(Integer.valueOf(600), estimate.forCount(10).xp);
		assertEquals(BowlReward.Basis.LAST, BowlReward.estimateFor(fishing, null, null, 220, 15_180).basis);
		xp.replaceAll((skill, old) -> Experience.MAX_SKILL_XP);
		xp.put(Skill.PRAYER, Experience.MAX_SKILL_XP - 15);
		RewardPreview nearlyCapped = RewardPreview.select(xp::get, locked, false);
		assertEquals(Integer.valueOf(15), BowlReward.estimateFor(nearlyCapped, null, null, null, 15_180).xp);
	}
}
