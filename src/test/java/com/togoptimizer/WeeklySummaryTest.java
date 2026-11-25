package com.togoptimizer;

import java.time.Instant;
import java.time.ZoneId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class WeeklySummaryTest
{
	private static final ZoneId PACIFIC = ZoneId.of("America/Los_Angeles");

	@Test
	public void showsTheLocalCalendarDayAndTheUtcReset()
	{
		long reset = Instant.parse("2026-11-27T00:00:00Z").toEpochMilli();
		long lastCheck = Instant.parse("2026-11-23T21:16:13Z").toEpochMilli();
		WeeklyReminder.Summary summary = WeeklyReminder.Summary.forVisit(reset, reset - 61_000,
			"Needs 1 more quest point or 325 XP", PACIFIC, lastCheck);
		assertEquals(Long.valueOf(lastCheck), summary.updatedAt);
		assertTrue(summary.canRefresh);
		assertEquals("Thu, Nov 26 · 4:00 PM PST", summary.resetTime);
		assertEquals("Fri, Nov 27, 2026 · 00:00 UTC", summary.resetUtc);
		assertEquals("Resets in 2m", summary.headline);
		assertEquals("Needs 1 more quest point or 325 XP.", summary.details);
		assertFalse(summary.ready);
	}

	@Test
	public void usesDaylightSavingAtTheResetInstant()
	{
		long reset = Instant.parse("2026-03-09T00:00:00Z").toEpochMilli();
		long now = Instant.parse("2026-03-07T12:00:00Z").toEpochMilli();
		WeeklyReminder.Summary summary = WeeklyReminder.Summary.forVisit(reset, now, null, PACIFIC, null);
		assertNull(summary.updatedAt);
		assertEquals("Sun, Mar 8 · 5:00 PM PDT", summary.resetTime);
		assertFalse(summary.ready);
	}

	@Test
	public void anElapsedCooldownStillRequiresProgress()
	{
		long reset = Instant.parse("2026-11-27T00:00:00Z").toEpochMilli();
		WeeklyReminder.Summary unknown = WeeklyReminder.Summary.forVisit(reset, reset,
			"Check your quest journal for the remaining XP or quest point", PACIFIC, null);
		assertEquals("Weekly wait complete", unknown.headline);
		assertFalse(unknown.ready);
		assertNull(unknown.resetTime);
		WeeklyReminder.Summary ready = WeeklyReminder.Summary.forVisit(reset, reset, null, PACIFIC, reset);
		assertEquals(Long.valueOf(reset), ready.updatedAt);
		assertTrue(ready.ready);
		assertNull(ready.resetTime);
		assertNull(ready.resetUtc);
	}
}
