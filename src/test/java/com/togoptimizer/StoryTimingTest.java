package com.togoptimizer;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class StoryTimingTest
{
	private static final int N = TearsGame.NONE;
	private static final int B = TearsGame.BLUE;
	private static final int G = TearsGame.GREEN;

	@Test
	public void findsTheFirstBlueOfTheCycle()
	{
		// Blues move on ticks 110, 111 and 112; greens earlier
		int[] colour = {B, G, B, N, G, B, N, G, N};
		int[] nextMove = {111, 107, 110, -1, 108, 112, -1, 109, -1};

		assertEquals(110, StoryTiming.firstBlueMove(colour, nextMove));
	}

	@Test
	public void findsItMidBurst()
	{
		// The first blue moved on tick 100 and is next due on 116; the other two move on 101 and 102
		int[] colour = {B, G, B, N, G, B, N, G, N};
		int[] nextMove = {116, 113, 101, -1, 114, 102, -1, 115, -1};

		assertEquals(116, StoryTiming.firstBlueMove(colour, nextMove));
	}

	@Test
	public void onlyTheBluesNeedToBeKnown()
	{
		// Just after a hop: the blues have been seen moving, the greens not yet
		int[] colour = {B, G, B, N, G, B, N, G, N};
		int[] nextMove = {111, -1, 110, -1, -1, 112, -1, -1, -1};

		assertEquals(110, StoryTiming.firstBlueMove(colour, nextMove));
	}

	@Test
	public void theBestMomentLeavesTheFirstBlueMovingJustAfterEntering()
	{
		// Continuing on tick 112 starts the game on 115, one tick before the first blue moves on 116
		assertEquals(0, StoryTiming.ticksUntilContinue(116, 112));
		// Entering from two ticks before it moves to one tick after is still good
		assertEquals(0, StoryTiming.ticksUntilContinue(116, 111));
		assertEquals(0, StoryTiming.ticksUntilContinue(116, 113));
		assertEquals(0, StoryTiming.ticksUntilContinue(116, 114));
		// Too late for this cycle: the next good moment is tick 127, for the move on 132
		assertEquals(12, StoryTiming.ticksUntilContinue(116, 115));
		// Early: count down to the window opening on tick 111
		assertEquals(3, StoryTiming.ticksUntilContinue(116, 108));
	}
}
