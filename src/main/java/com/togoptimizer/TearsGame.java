package com.togoptimizer;

import java.util.Random;

/**
 * A Tears of Guthix game that can be stepped forward a tick at a time, used to simulate what happens
 * after each choice.
 *
 * Mechanics, from the OSRS Wiki: three blue and three green streams occupy nine walls. Each stream
 * stays for 16 ticks, then moves to a random empty wall other than its own, keeping its colour. A
 * player collecting from a wall keeps collecting when it changes, gaining a tear per tick on blue
 * and losing one on green.
 */
final class TearsGame
{
	static final int NONE = 0;
	static final int BLUE = 1;
	static final int GREEN = -1;

	static final int STREAM_LIFE = 16;
	// Ticks between reaching a wall and the first tear. Measured in game: the first tear came the
	// distance plus two ticks after each click, and other players started collecting two ticks after
	// arriving.
	static final int START_DELAY = 2;

	/**
	 * Picks the wall to collect from next, or -1 to carry on as before.
	 */
	interface Policy
	{
		int choose(TearsGame game);
	}

	// Colour on each wall, which is also the tears it gives per tick
	final int[] colour = new int[Walls.COUNT];
	// Tick the stream on each wall moves on; unused for empty walls
	final int[] nextMove = new int[Walls.COUNT];

	int tick;
	int endTick;
	int x;
	int y;
	int target = -1;
	int readyTick;
	int pendingWall = -1;
	int pendingTick;
	int tears;
	// Ticks between the player choosing a wall and the click taking effect
	int reaction;
	Random rng;

	TearsGame copy(Random rng)
	{
		TearsGame game = new TearsGame();
		System.arraycopy(colour, 0, game.colour, 0, Walls.COUNT);
		System.arraycopy(nextMove, 0, game.nextMove, 0, Walls.COUNT);
		game.tick = tick;
		game.endTick = endTick;
		game.x = x;
		game.y = y;
		game.target = target;
		game.readyTick = readyTick;
		game.pendingWall = pendingWall;
		game.pendingTick = pendingTick;
		game.tears = tears;
		game.reaction = reaction;
		game.rng = rng;
		return game;
	}

	int remaining()
	{
		return endTick - tick;
	}

	boolean over()
	{
		return tick >= endTick;
	}

	void moveStreams()
	{
		for (int wall = 0; wall < Walls.COUNT; wall++)
		{
			if (colour[wall] != NONE && nextMove[wall] == tick)
			{
				int destination = randomEmptyWall();
				colour[destination] = colour[wall];
				nextMove[destination] = tick + STREAM_LIFE;
				colour[wall] = NONE;
			}
		}
	}

	private int randomEmptyWall()
	{
		int empty = 0;
		for (int c : colour)
		{
			if (c == NONE)
			{
				empty++;
			}
		}
		int pick = rng.nextInt(empty);
		for (int wall = 0; wall < Walls.COUNT; wall++)
		{
			if (colour[wall] == NONE && pick-- == 0)
			{
				return wall;
			}
		}
		throw new IllegalStateException("no empty wall");
	}

	/**
	 * Chooses a wall; the click lands after the reaction time. Ignored while another click is on its
	 * way, since the player is committed to it.
	 */
	void request(int wall)
	{
		if (wall < 0 || wall == target || pendingWall >= 0)
		{
			return;
		}
		pendingWall = wall;
		pendingTick = tick + reaction;
	}

	void step(Policy policy)
	{
		moveStreams();
		if (pendingWall < 0)
		{
			request(policy.choose(this));
		}
		finishTick();
	}

	/**
	 * The rest of a tick once streams have moved and the player has chosen.
	 */
	void finishTick()
	{
		if (pendingWall >= 0 && tick >= pendingTick)
		{
			goTo(pendingWall);
			pendingWall = -1;
		}
		if (target >= 0 && tick >= readyTick)
		{
			tears += colour[target];
		}
		tick++;
	}

	private void goTo(int wall)
	{
		if (wall == target)
		{
			return;
		}
		readyTick = tick + Walls.travel(x, y, wall) + START_DELAY;
		x = Walls.standX(wall);
		y = Walls.standY(wall);
		target = wall;
	}

	void runUntil(int stopTick, Policy policy)
	{
		int stop = Math.min(stopTick, endTick);
		while (tick < stop)
		{
			step(policy);
		}
	}

	/**
	 * When the current wall stops giving blue tears, go to the nearest blue one, preferring the one
	 * that lasts longer on a tie.
	 */
	static int nearestBlue(TearsGame game)
	{
		if (game.target >= 0 && game.colour[game.target] == BLUE)
		{
			return -1;
		}
		int best = -1;
		for (int wall = 0; wall < Walls.COUNT; wall++)
		{
			if (game.colour[wall] != BLUE)
			{
				continue;
			}
			if (best < 0)
			{
				best = wall;
				continue;
			}
			int travel = Walls.travel(game.x, game.y, wall);
			int bestTravel = Walls.travel(game.x, game.y, best);
			if (travel < bestTravel || travel == bestTravel && game.nextMove[wall] > game.nextMove[best])
			{
				best = wall;
			}
		}
		return best;
	}
}
