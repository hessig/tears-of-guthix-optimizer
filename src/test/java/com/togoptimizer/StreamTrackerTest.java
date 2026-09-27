package com.togoptimizer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class StreamTrackerTest
{
	/**
	 * Plays a model game and reports its wall changes to a tracker, as the plugin will from the
	 * game's objects. The tracker starts watching at {@code loadTick}, when the cave loads.
	 */
	private static StreamTracker watch(String order, long seed, int loadTick, int untilTick)
	{
		TearsGame game = TearsGameTest.game(order, 10_000, 0, seed);
		StreamTracker tracker = new StreamTracker();
		int[] before = new int[Walls.COUNT];
		while (game.tick < untilTick)
		{
			System.arraycopy(game.colour, 0, before, 0, Walls.COUNT);
			game.moveStreams();
			if (game.tick == loadTick)
			{
				for (int wall = 0; wall < Walls.COUNT; wall++)
				{
					tracker.wallChanged(wall, game.colour[wall], game.tick);
				}
			}
			else if (game.tick > loadTick)
			{
				for (int wall = 0; wall < Walls.COUNT; wall++)
				{
					if (game.colour[wall] != before[wall])
					{
						tracker.wallChanged(wall, game.colour[wall], game.tick);
					}
				}
			}
			game.tick++;
		}

		// Everything the tracker claims to know must match the game
		for (int wall = 0; wall < Walls.COUNT; wall++)
		{
			assertEquals("colour of wall " + wall, game.colour[wall], tracker.colour(wall));
			int next = tracker.nextMove(wall);
			if (next >= 0)
			{
				assertEquals("next move of wall " + wall, game.nextMove[wall], next);
			}
		}
		return tracker;
	}

	@Test
	public void streamsOnLoadHaveUnknownTiming()
	{
		// The game starts mid-cycle; load on its first tick, before any real move is seen
		StreamTracker tracker = watch("gggbbb", 1, 0, 1);

		assertFalse(tracker.isComplete());
		assertNull(tracker.getOrder());
	}

	@Test
	public void oneCycleIsEnoughToKnowEverything()
	{
		for (long seed = 0; seed < 50; seed++)
		{
			// Load partway through a cycle, then watch for just over one more
			int load = (int) (seed % 16);
			StreamTracker tracker = watch("gggbbb", seed, load, load + 16 + 6);

			assertTrue("seed " + seed, tracker.isComplete());
			assertEquals("gggbbb", tracker.getOrder());
		}
	}

	@Test
	public void detectsOtherOrders()
	{
		for (String order : new String[]{"bgbgbg", "bbbggg", "gbbgbg"})
		{
			assertEquals(order, watch(order, 7, 5, 5 + 16 + 6).getOrder());
		}
	}

	@Test
	public void aCycleCutShortByLoadingIsNotAnOrder()
	{
		// The model's game starts as the first blue moves (tick 0), so blues move on ticks 0 to 2 and
		// greens on 13 to 15. Loading on tick 1 sees only part of that cycle.
		StreamTracker tracker = watch("gggbbb", 3, 1, 12);

		assertNull(tracker.getOrder());
	}

	@Test
	public void measuresHowLongStreamsLast()
	{
		StreamTracker tracker = watch("gggbbb", 4, 0, 40);

		assertEquals(TearsGame.STREAM_LIFE, tracker.getLastLife());
	}

	@Test
	public void fillsAGameWithTheTrackedStreams()
	{
		StreamTracker tracker = watch("gggbbb", 8, 3, 30);
		TearsGame game = new TearsGame();

		tracker.fill(game);

		int[] colours = new int[Walls.COUNT];
		for (int wall = 0; wall < Walls.COUNT; wall++)
		{
			colours[wall] = tracker.colour(wall);
		}
		assertArrayEquals(colours, game.colour);
	}

	@Test
	public void resetForgetsEverything()
	{
		StreamTracker tracker = watch("gggbbb", 2, 0, 30);
		tracker.reset();

		assertFalse(tracker.isComplete());
		assertNull(tracker.getOrder());
		assertEquals(-1, tracker.getLastLife());
	}
}
