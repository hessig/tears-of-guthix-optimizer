package com.togoptimizer;

import java.util.EnumSet;
import net.runelite.api.Skill;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class BowlRewardTest
{
	private final BowlReward.RoomBowl bowl = new BowlReward.RoomBowl();
	private final RewardPreview fishing = RewardPreview.select(
		skill -> skill == Skill.FISHING ? 0 : 1, EnumSet.noneOf(Skill.class), false);

	@Test
	public void keepsTheFinalCountIncludingGreenLossesAndALateChange()
	{
		bowl.sample(100, 40, true);
		bowl.sample(95, 41, true);
		bowl.changed(91, 42);
		bowl.changed(0, 43);
		bowl.sample(0, 43, false);
		assertEquals(91, bowl.finalCount(0, 45));
		assertEquals(89, bowl.finalCount(89, 45));
	}

	@Test
	public void lossOfTheLastTearDuringPlayIsRecordedAsAnEmptyBowl()
	{
		bowl.sample(1, 50, true);
		bowl.changed(0, 51);
		bowl.sample(0, 51, true);
		bowl.sample(0, 52, false);
		assertEquals(0, bowl.finalCount(0, 54));
	}

	@Test
	public void anAmbiguousLastTearFallsBackToTheRate()
	{
		bowl.sample(1, 50, true);
		bowl.sample(0, 51, false);
		assertEquals(-1, bowl.finalCount(0, 52));
		assertEquals(1, bowl.finalCount(1, 52));
	}

	@Test
	public void rejectsExpiredSamplesAndUpdatesOutsideTheExitWindow()
	{
		bowl.sample(50, 100, true);
		bowl.changed(51, 102);
		bowl.changed(999, 103);
		assertEquals(51, bowl.finalCount(0, 115));
		assertEquals(-1, bowl.finalCount(0, 116));
		assertEquals(-1, bowl.finalCount(0, 99));
	}

	@Test
	public void logoutClearsTheSampleEvenWhenTickNumbersOverlap()
	{
		bowl.sample(175, 23, true);
		bowl.reset();
		bowl.changed(176, 24);
		bowl.sample(0, 25, false);
		assertEquals(-1, bowl.finalCount(0, 26));
		bowl.sample(0, 27, true);
		assertEquals(0, bowl.finalCount(0, 28));
	}

	@Test
	public void aRequestedOrCurrentCountTakesPrecedenceOverLastWeeksBowl()
	{
		BowlReward.Estimate requested = BowlReward.estimateFor(fishing, 120, 1, 99);
		assertEquals(BowlReward.Basis.CUSTOM, requested.basis);
		assertEquals(Integer.valueOf(120), requested.tears);
		assertEquals(Integer.valueOf(1_200), requested.xp);
		assertEquals("~1,200 Fishing XP (120 tears requested)", requested.line());
		assertEquals("~0 Fishing XP (0 tears requested)", BowlReward.estimateFor(fishing, 0, 1, 99).line());
		BowlReward.Estimate current = BowlReward.estimateFor(fishing, null, 1, 99);
		assertEquals(BowlReward.Basis.CURRENT, current.basis);
		assertEquals("~10 Fishing XP (1 tear in your bowl)", current.line());
		assertEquals("~0 Fishing XP (0 tears in your bowl)", BowlReward.estimateFor(fishing, null, 0, 99).line());
	}

	@Test
	public void beforeEnteringShowsLastBowlOrTheRateIfTheBowlIsUnknown()
	{
		BowlReward.Estimate previous = BowlReward.estimateFor(fishing, null, null, 99);
		assertEquals(BowlReward.Basis.LAST, previous.basis);
		assertEquals("~990 Fishing XP (99 tears to match last game)", previous.line());
		assertEquals("~0 Fishing XP (0 tears to match last game)", BowlReward.estimateFor(fishing, null, null, 0).line());
		BowlReward.Estimate rate = BowlReward.estimateFor(fishing, null, null, null);
		assertEquals(BowlReward.Basis.RATE, rate.basis);
		assertEquals(Skill.FISHING, rate.skill);
		assertEquals("10", rate.perTear);
		assertNull(rate.tears);
		assertNull(rate.xp);
		assertEquals("10 Fishing XP/tear", rate.line());
		assertEquals(BowlReward.Basis.RATE, BowlReward.estimateFor(fishing, null, null, -1).basis);
	}

	@Test
	public void customCountBeforeAFirstGameMatchesTheChatProjection()
	{
		RewardPreview diaryReward = RewardPreview.select(
			skill -> skill == Skill.FISHING ? 1_000 : 9_000, EnumSet.noneOf(Skill.class), true);
		BowlReward.Estimate before = BowlReward.estimateFor(diaryReward, null, null, null);
		BowlReward.Estimate custom = before.forCount(100);
		assertEquals(BowlReward.Basis.CUSTOM, custom.basis);
		assertEquals(Integer.valueOf(100), custom.tears);
		assertEquals(Integer.valueOf(1_507), custom.xp);
		assertEquals("15.07", custom.perTear);
		assertEquals(before.skill, custom.skill);
		assertEquals(BowlReward.estimateFor(diaryReward, 100, null, null).line(), custom.line());
		assertEquals(Integer.valueOf(0), before.forCount(0).xp);
		assertNull(before.tears);
	}
}
