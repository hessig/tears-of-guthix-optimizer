package com.togoptimizer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class TearsJournalTest
{
	private static final String COMPLETE = "<col=ff0000>QUEST COMPLETE!</col>\n";
	private static final String INTRO = "Now Juna will let me into the cave to collect the Tears if I tell\nher stories of my adventures.\n";
	private static final String NEXT = "I will be able to collect the Tears of Guthix";

	private static void assertReading(String body, int days, Integer xp)
	{
		TearsJournal.Reading reading = TearsJournal.parse(body);
		assertNotNull(reading);
		assertEquals(Integer.valueOf(days), reading.daysRemaining);
		assertEquals(xp, reading.xpRemaining);
	}

	@Test
	public void currentOsrsJournalReportsFiveDaysAndEnoughAdventures()
	{
		// Transcribed from the player's completed journal, including the displayed line breaks.
		assertReading(COMPLETE + INTRO + "I have had enough adventures to tell Juna more stories. I will\n"
			+ "be able to collect the Tears of Guthix in 5 days.", 5, 0);
	}

	@Test
	public void handlesAdjacentWidgetRowsAndColourAroundTheCount()
	{
		assertReading("<str>Earlier quest step</str><br>" + COMPLETE + INTRO
			+ "I have had enough adventures to tell Juna more stories.<br>I will be able to collect "
			+ "the Tears of Guthix <col=800000>in\u00a01 day<col=000080>.", 1, 0);
	}

	// These additional complete sentence forms are recorded in 2009scape's historical drawJournal.
	// They are compatibility fixtures. The varied XP values below are test inputs.
	@Test
	public void historicalCombinedConditionReportsXpStillToGain()
	{
		assertReading(COMPLETE + INTRO + NEXT + " in 3 days, as\nlong as I gain either "
			+ "<col=ff0000>1 Quest Point</col> or <col=ff0000>78,432 total XP</col>", 3, 78_432);
	}

	@Test
	public void aCompleteXpOnlyConditionEstablishesTheTimerHasElapsed()
	{
		assertReading(COMPLETE + NEXT + ", as long as I\ngain either 1 Quest Point or 100000 total XP", 0, 100_000);
	}

	@Test
	public void aBareDaySentenceConfirmsOnlyTheTimer()
	{
		assertReading(COMPLETE + NEXT + " in 7 days.", 7, null);
	}

	@Test
	public void aCompletedPersonalInvitationIsReady()
	{
		assertReading(COMPLETE + INTRO + "I have had enough adventures to tell Juna more stories,\n"
			+ "and a week has passed since I last collected the Tears. I\ncan visit Juna again now.", 0, 0);
	}

	@Test
	public void rejectsUnknownGeneralAndIncompleteText()
	{
		assertNull(TearsJournal.parse(null));
		assertNull(TearsJournal.parse("I can start this quest by talking to Juna in the Lumbridge Swamp Caves."));
		assertNull(TearsJournal.parse(INTRO + NEXT + " in 5 days."));
		assertNull(TearsJournal.parse(COMPLETE + INTRO));
		assertNull(TearsJournal.parse(COMPLETE + INTRO + "I have had enough adventures to tell Juna more stories."));
		assertNull(TearsJournal.parse(COMPLETE + NEXT + " in 5 days"));
		assertNull(TearsJournal.parse(COMPLETE + NEXT + " in 5 days, as long as I gain either 1 Quest Point or"));
		assertNull(TearsJournal.parse(COMPLETE + "I have already gained 92,123 total XP."));
		assertNull(TearsJournal.parse(COMPLETE + "<str>I can visit Juna again now.</str>"));
	}

	@Test
	public void rejectsContradictionsAndImpossibleAmounts()
	{
		assertNull(TearsJournal.parse(COMPLETE + NEXT + " in 8 days."));
		assertNull(TearsJournal.parse(COMPLETE + NEXT + ", as long as I gain either 1 Quest Point or 100,001 total XP"));
		assertNull(TearsJournal.parse(COMPLETE + NEXT + ", as long as I gain either 1 Quest Point or 9,12 total XP"));
		assertNull(TearsJournal.parse(COMPLETE + "I have had enough adventures to tell Juna more stories. " + NEXT
			+ " in 3 days, as long as I gain either 1 Quest Point or 450 total XP"));
		assertNull(TearsJournal.parse(COMPLETE + "A week has passed since I last collected the Tears. " + NEXT + " in 5 days."));
		assertNull(TearsJournal.parse(COMPLETE + NEXT + " in 5 days. I can visit Juna again now."));
	}
}
