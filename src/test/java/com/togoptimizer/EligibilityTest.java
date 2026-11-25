package com.togoptimizer;

import java.time.Instant;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class EligibilityTest
{
	private static long at(String instant)
	{
		return Instant.parse(instant).toEpochMilli();
	}

	@Test
	public void aGameUnlocksTheNextAtTheStartOfTheSeventhDay()
	{
		assertEquals(at("2026-10-04T00:00:00Z"), Eligibility.unlockAfterGame(at("2026-09-27T13:18:23Z")));
		// A game just before the daily reset still counts from its own day
		assertEquals(at("2026-10-04T00:00:00Z"), Eligibility.unlockAfterGame(at("2026-09-27T23:59:00Z")));
	}

	@Test
	public void junasDaysCountFromTodaysReset()
	{
		assertEquals(at("2026-10-04T00:00:00Z"), Eligibility.unlockInDays(7, at("2026-09-27T18:10:00Z")));
	}

	@Test
	public void readsJunasReply()
	{
		// Both of her replies, as seen in game
		assertEquals(Integer.valueOf(7), Eligibility.daysFromJuna("Your stories have entertained me. But I will not permit any "
			+ "adventurer to access the tears more than once a week. Come back in 7 days."));
		assertEquals(Integer.valueOf(7), Eligibility.daysFromJuna("It has not been long since your last visit. Come again in 7 days."));
		assertEquals(Integer.valueOf(1), Eligibility.daysFromJuna("Come back in 1 day."));
		assertEquals(Integer.valueOf(1), Eligibility.daysFromJuna("Come back tomorrow."));
		assertNull(Eligibility.daysFromJuna("Tell me a story and I will let you into the cave."));
	}

	@Test
	public void formatsTimeLeft()
	{
		long minute = 60_000;
		assertEquals("6d 9h 55m", Eligibility.formatRemaining(((6 * 24 + 9) * 60 + 55) * minute));
		assertEquals("2h 0m", Eligibility.formatRemaining(120 * minute));
		assertEquals("1m", Eligibility.formatRemaining(1_000));
	}

	@Test
	public void describesWhatsStillNeeded()
	{
		// Quest points already met, or no requirement recorded
		assertNull(Eligibility.requirement(323, 323, 0, null));
		assertNull(Eligibility.requirement(0, 5, 0, null));
		// Experience gained since the last game counts instead
		assertEquals("Needs 1 more quest point or 38,500 XP", Eligibility.requirement(323, 322, 1_061_500, 1_000_000L));
		assertNull(Eligibility.requirement(323, 322, 1_100_000, 1_000_000L));
		// Without a record of the last game's experience
		// Without the experience at the last game, the player may already be able to play
		assertEquals("May need 2 more quest points or 100,000 XP", Eligibility.requirement(324, 322, 0, null));
	}

	@Test
	public void syncUsesRemainingXpAndSupportsTotalsAboveTwoBillion()
	{
		int remaining = Eligibility.journalXp("25,000");
		long total = 3_000_000_000L;
		Long baseline = Eligibility.journalBaseline(3, remaining, total);
		assertEquals(Long.valueOf(total - 75_000), baseline);
		assertEquals("Needs 1 more quest point or 1 XP", Eligibility.trackedRequirement(true, 126, 125, total + 24_999, baseline));
		assertNull(Eligibility.trackedRequirement(true, 126, 125, total + 25_000, baseline));
		assertNull(Eligibility.trackedRequirement(true, 126, 126, total, baseline));
		// The journal can confirm progress without revealing which of XP and QP qualified the player.
		assertNull(Eligibility.journalBaseline(0, 0, 10_000));
	}

	@Test
	public void anExpiredDateAloneDoesNotClaimReadiness()
	{
		String unknown = "Check your quest journal for the remaining XP or quest point";
		assertEquals(unknown, Eligibility.trackedRequirement(true, 0, 230, 1_000_000, null));
		assertEquals(unknown, Eligibility.trackedRequirement(true, 0, 230, 1_000_000, 1_100_000L));
		assertNull(Eligibility.trackedRequirement(true, 0, 230, 1_100_000, 1_000_000L));
		// Legacy entries can use either the game's QP target or the old saved XP baseline.
		assertNull(Eligibility.trackedRequirement(true, 230, 230, 1_000_000, null));
		assertEquals("May need 1 more quest point or 100,000 XP",
			Eligibility.trackedRequirement(true, 231, 230, 1_000_000, null));
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsMalformedJournalAmount()
	{
		Eligibility.journalXp("5,0");
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsImpossibleJournalBaseline()
	{
		Eligibility.journalBaseline(2, 1, 1_000);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsImpossibleJournalDays()
	{
		Eligibility.journalBaseline(8, 0, 1_000);
	}
}
