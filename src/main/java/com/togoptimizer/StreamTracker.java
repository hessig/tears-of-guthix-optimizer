package com.togoptimizer;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import javax.annotation.Nullable;

/**
 * Follows the streams on the weeping walls: their colours, when each will next move, and the order
 * the world moves them in.
 *
 * Streams present when the cave loads all appear on the same tick, so how long they have left is
 * unknown until they next move. A real move is one stream on one tick, and all six move on
 * consecutive ticks once per cycle, so after one cycle everything is known.
 */
final class StreamTracker
{
	// The order that makes green tears easiest to avoid: all three greens move before the blues
	static final String OPTIMAL_ORDER = "gggbbb";
	private static final int UNKNOWN = Integer.MIN_VALUE;
	private static final int STREAMS = 6;

	private final int[] colour = new int[Walls.COUNT];
	private final int[] spawnTick = new int[Walls.COUNT];

	// Walls a stream appeared on this tick, to tell a cave load (several at once) from a move (one)
	private int spawnsTick = UNKNOWN;
	private final Deque<Integer> spawnsThisTick = new ArrayDeque<>();

	// Recent moves as {tick, colour}, to spot a full cycle of six on consecutive ticks
	private final Deque<int[]> moves = new ArrayDeque<>();
	@Nullable
	private String order;
	private int lastLife = UNKNOWN;

	StreamTracker()
	{
		reset();
	}

	void reset()
	{
		Arrays.fill(colour, TearsGame.NONE);
		Arrays.fill(spawnTick, UNKNOWN);
		spawnsTick = UNKNOWN;
		spawnsThisTick.clear();
		moves.clear();
		order = null;
		lastLife = UNKNOWN;
	}

	/**
	 * A wall started showing a colour ({@link TearsGame#NONE} when its stream left).
	 */
	void wallChanged(int wall, int newColour, int tick)
	{
		if (newColour == TearsGame.NONE)
		{
			if (colour[wall] != TearsGame.NONE && spawnTick[wall] != UNKNOWN)
			{
				lastLife = tick - spawnTick[wall];
			}
			colour[wall] = TearsGame.NONE;
			spawnTick[wall] = UNKNOWN;
			return;
		}

		if (spawnsTick != tick)
		{
			spawnsTick = tick;
			spawnsThisTick.clear();
		}
		spawnsThisTick.add(wall);
		colour[wall] = newColour;

		if (spawnsThisTick.size() == 1)
		{
			spawnTick[wall] = tick;
			recordMove(tick, newColour);
		}
		else
		{
			// Several at once is the cave loading, not streams moving
			for (int w : spawnsThisTick)
			{
				spawnTick[w] = UNKNOWN;
			}
			if (!moves.isEmpty() && moves.peekLast()[0] == tick)
			{
				moves.pollLast();
			}
		}
	}

	private void recordMove(int tick, int moveColour)
	{
		if (!moves.isEmpty() && moves.peekLast()[0] != tick - 1)
		{
			moves.clear();
		}
		moves.add(new int[]{tick, moveColour});
		if (moves.size() == STREAMS)
		{
			StringBuilder sb = new StringBuilder();
			for (int[] move : moves)
			{
				sb.append(move[1] == TearsGame.BLUE ? 'b' : 'g');
			}
			order = sb.toString();
			moves.clear();
		}
	}

	/**
	 * @return the colours in the order this world moves its streams, such as "gggbbb", or null
	 * until a full cycle has been seen
	 */
	@Nullable
	String getOrder()
	{
		return order;
	}

	/**
	 * @return how many ticks the most recently departed stream stayed, or -1 if unknown
	 */
	int getLastLife()
	{
		return lastLife == UNKNOWN ? -1 : lastLife;
	}

	/**
	 * @return true once every stream's next move is known
	 */
	boolean isComplete()
	{
		int known = 0;
		for (int wall = 0; wall < Walls.COUNT; wall++)
		{
			if (colour[wall] != TearsGame.NONE)
			{
				if (spawnTick[wall] == UNKNOWN)
				{
					return false;
				}
				known++;
			}
		}
		return known == STREAMS;
	}

	int colour(int wall)
	{
		return colour[wall];
	}

	/**
	 * @return the tick the stream on this wall next moves, or -1 if the wall is empty or unknown
	 */
	int nextMove(int wall)
	{
		return colour[wall] == TearsGame.NONE || spawnTick[wall] == UNKNOWN
			? -1
			: spawnTick[wall] + TearsGame.STREAM_LIFE;
	}

	/**
	 * Copies the streams into a game for planning. Only meaningful once {@link #isComplete()}.
	 */
	void fill(TearsGame game)
	{
		for (int wall = 0; wall < Walls.COUNT; wall++)
		{
			game.colour[wall] = colour[wall];
			game.nextMove[wall] = nextMove(wall);
		}
	}
}
