package com.togoptimizer;

/**
 * When to continue past Juna's last line, which lets the player into the cave, so the game starts at a
 * good point in the stream cycle. Her earlier lines take however long the player takes to click
 * through, but the last one waits, so timing the final click is reliable.
 *
 * Simulations found the most tears when the first blue of a cycle moves one tick after the player
 * enters, and within about 1.5 tears of that from one tick before to two after.
 */
final class EntryTiming
{
	// Ticks from continuing past Juna's last line to the game starting, from one game continued as soon as
	// the cue allowed. The debug log records both, to refine it.
	static final int CONTINUE_TICKS = 10;
	// Her last line before letting the player in. "I will let you into the cave" comes a line earlier, and
	// also in her prompt for a story
	static final String LAST_LINE = "Collect as much as you can from the blue streams";
	// When the first blue moves relative to the player entering, from one tick before to two after
	private static final int EARLIEST_ENTRY = -1;
	private static final int LATEST_ENTRY = 2;

	private EntryTiming()
	{
	}

	/**
	 * @return the tick the first blue of the cycle next moves, or -1 if no blue is known
	 */
	static int firstBlueMove(int[] colour, int[] nextMove)
	{
		int first = -1;
		for (int wall = 0; wall < Walls.COUNT; wall++)
		{
			if (colour[wall] != TearsGame.BLUE || nextMove[wall] < 0)
			{
				continue;
			}
			// The first blue of a burst has no blue moving the tick before it; a blue that moved on that
			// tick is already a whole cycle further on
			boolean follows = false;
			for (int other = 0; other < Walls.COUNT; other++)
			{
				if (colour[other] == TearsGame.BLUE && other != wall
					&& (nextMove[other] == nextMove[wall] - 1 || nextMove[other] == nextMove[wall] - 1 + TearsGame.STREAM_LIFE))
				{
					follows = true;
					break;
				}
			}
			if (!follows && (first < 0 || nextMove[wall] < first))
			{
				first = nextMove[wall];
			}
		}
		return first;
	}

	/**
	 * @return 0 if continuing past Juna's last line now starts the game at a good time, otherwise the
	 * ticks to wait
	 */
	static int ticksUntilContinue(int firstBlueMove, int tick)
	{
		int entry = entryOffset(firstBlueMove, tick);
		if (entry >= EARLIEST_ENTRY && entry <= LATEST_ENTRY)
		{
			return 0;
		}
		// Waiting a tick moves the entry one tick later in the cycle, bringing the first blue one closer
		return Math.floorMod(entry - LATEST_ENTRY, TearsGame.STREAM_LIFE);
	}

	/**
	 * @return how many ticks after entering the first blue would move, if the player continued now
	 */
	static int entryOffset(int firstBlueMove, int tick)
	{
		int entry = Math.floorMod(firstBlueMove - tick - CONTINUE_TICKS, TearsGame.STREAM_LIFE);
		return entry > TearsGame.STREAM_LIFE / 2 ? entry - TearsGame.STREAM_LIFE : entry;
	}

	/**
	 * @return ticks the player entered after the best window, negative for before it, or 0 within it
	 */
	static int ticksLate(int entryOffset)
	{
		if (entryOffset < EARLIEST_ENTRY)
		{
			return EARLIEST_ENTRY - entryOffset;
		}
		return entryOffset > LATEST_ENTRY ? LATEST_ENTRY - entryOffset : 0;
	}
}
