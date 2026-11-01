package com.togoptimizer;

import java.awt.Color;
import java.util.List;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class GameSummaryTest
{
	private static final int START = 100;
	private static final int LENGTH = 20;
	// Where players enter the wall room: two tiles from wall 0
	private static final int ENTRANCE_X = 3257;
	private static final int ENTRANCE_Y = 9517;
	// Walking two tiles and starting to collect
	private static final int READY = 2 + TearsGame.START_DELAY;
	private static final int AFTER = 20;

	/**
	 * A game with one blue stream, on wall 0 the whole time, highlighted throughout, and the player walking
	 * there after clicking it on the given tick.
	 */
	private static GameRecord game(int clickTick, int clickWall)
	{
		GameRecord record = new GameRecord();
		record.start = START;
		record.length = LENGTH;
		int[] colour = new int[Walls.COUNT];
		int[] nextMove = new int[Walls.COUNT];
		colour[0] = TearsGame.BLUE;
		nextMove[0] = START + 1000;
		for (int tick = START - GameRecord.BEFORE; tick < START + LENGTH + AFTER; tick++)
		{
			record.streams(tick, colour, nextMove);
		}
		record.click(clickTick, clickWall, 0);
		int tears = 0;
		for (int tick = START; tick < START + LENGTH; tick++)
		{
			// Started collecting a tick after the click, plus the walk
			boolean collecting = clickWall == 0 && tick >= clickTick - 1 + READY;
			tears += collecting ? 1 : 0;
			int x = tick < clickTick ? ENTRANCE_X : Walls.standX(clickWall);
			int y = tick < clickTick ? ENTRANCE_Y : Walls.standY(clickWall);
			record.tick(tick, new GameRecord.Tick(x, y, tears, tick < clickTick ? -1 : clickWall, 0));
		}
		record.tears = tears;
		record.end = START + LENGTH;
		return record;
	}

	@Test
	public void hindsightGoesStraightToTheBlue()
	{
		assertEquals(LENGTH - READY, GameSummary.hindsight(game(START + 1, 0), START, ENTRANCE_X, ENTRANCE_Y));
	}

	@Test
	public void theReplayOfAPromptClickMatchesHindsight()
	{
		GameRecord record = game(START + 1, 0);
		assertEquals(LENGTH - READY, GameSummary.replay(record, record.getClicks(), ENTRANCE_X, ENTRANCE_Y));
	}

	@Test
	public void lateClicksCostTheTicksTheyWereLate()
	{
		GameSummary.Result result = GameSummary.summarise(game(START + 4, 0), new Planner(50, 40), ENTRANCE_X, ENTRANCE_Y);
		assertEquals(LENGTH - READY, result.getPerfect());
		assertEquals(LENGTH - READY - 3, result.getTears());
		assertEquals(3, result.getLateClicks());
		assertEquals(0, result.getOffHighlight());
		assertEquals(0, result.getExtraWalking());
	}

	@Test
	public void aClickOffTheHighlightCostsWhatFollowingItWouldHaveAdded()
	{
		GameSummary.Result result = GameSummary.summarise(game(START + 1, 6), new Planner(50, 40), ENTRANCE_X, ENTRANCE_Y);
		assertEquals(0, result.getTears());
		assertEquals(LENGTH - READY, result.getOffHighlight());
	}

	@Test
	public void lostTearsAreGreen()
	{
		GameRecord record = new GameRecord();
		int[] tears = {3, 4, 2, 2, 1, 5};
		for (int i = 0; i < tears.length; i++)
		{
			record.tick(i, new GameRecord.Tick(0, 0, tears[i], 0, 0));
		}
		assertEquals(3, GameSummary.green(record.getTicks()));
	}

	@Test
	public void aLateEntryCostsWhatEnteringOnTheCountdownWouldHaveAdded()
	{
		GameRecord record = game(START + 1, 0);
		// The blue dries up ten ticks in, so entering two ticks sooner collects two more
		int[] none = new int[Walls.COUNT];
		for (int tick = START + 10; tick < START + LENGTH + AFTER; tick++)
		{
			record.streams(tick, none, none);
		}
		record.ticksLate = 2;
		GameSummary.Result result = GameSummary.summarise(record, new Planner(50, 40), ENTRANCE_X, ENTRANCE_Y);
		assertEquals(10 - READY + 2, result.getPerfect());
		assertEquals(2, result.getEntry());
		assertEquals("Lost 2 to a late entry.", GameSummary.lines(result, null).get(1));
	}

	@Test
	public void anEarlyEntryIsNamedSo()
	{
		GameSummary.Result result = new GameSummary.Result(240, 243, 270, 3, false, 0, 0, 0, 0);
		assertEquals("Lost 3 to an early entry.", GameSummary.lines(result, null).get(1));
	}

	@Test
	public void theSummaryNamesOnlyGapsThatCostSomething()
	{
		GameSummary.Result result = new GameSummary.Result(204, 249, 276, 5, true, 18, 11, 4, 7);
		assertEquals(List.of(
			"204 tears. Following the plugin perfectly: 249. Hindsight max: 276.",
			"Lost 5 to a late entry, 18 to late clicks, 11 to clicks off the highlight, 4 to extra walking and 7 to green streams."),
			GameSummary.lines(result, null));

		result = new GameSummary.Result(247, 252, 281, 0, false, 3, 0, 2, 0);
		assertEquals("Lost 3 to late clicks and 2 to extra walking.", GameSummary.lines(result, null).get(1));
	}

	@Test
	public void aGameAsGoodAsThePluginIsOneLine()
	{
		assertEquals(List.of("251 tears, the same as following the plugin perfectly. Hindsight max: 279."),
			GameSummary.lines(new GameSummary.Result(251, 251, 279, 0, false, 0, 0, 0, 0), null));
		assertEquals(List.of("247 tears, more than following the plugin perfectly (246). Hindsight max: 274."),
			GameSummary.lines(new GameSummary.Result(247, 246, 274, 0, false, 0, 0, 0, 0), null));
	}

	@Test
	public void coloursWrapEachPart()
	{
		GameSummary.Palette palette = new GameSummary.Palette(Color.GREEN, Color.BLUE, Color.RED);
		List<String> lines = GameSummary.lines(new GameSummary.Result(10, 12, 15, 0, false, 2, 0, 0, 0), palette);
		assertEquals("<col=00ff00>10 tears</col>. Following the plugin perfectly: <col=0000ff>12</col>. Hindsight max: "
			+ "<col=0000ff>15</col>.", lines.get(0));
		assertEquals("Lost <col=ff0000>2 to late clicks</col>.", lines.get(1));
	}
}
