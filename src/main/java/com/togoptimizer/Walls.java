package com.togoptimizer;

/**
 * The nine weeping walls in the Tears of Guthix cave, indexed south wall (west to east), east wall
 * (south to north), then north wall (west to east).
 */
final class Walls
{
	static final int COUNT = 9;

	// Wall tiles from the OSRS Wiki's weeping wall map
	private static final int[][] WALL = {
		{3257, 9514}, {3258, 9514}, {3259, 9514},
		{3261, 9516}, {3261, 9517}, {3261, 9518},
		{3257, 9520}, {3258, 9520}, {3259, 9520},
	};

	// The floor tile in front of each wall, where the player stands to collect
	private static final int[][] STAND = {
		{3257, 9515}, {3258, 9515}, {3259, 9515},
		{3260, 9516}, {3260, 9517}, {3260, 9518},
		{3257, 9519}, {3258, 9519}, {3259, 9519},
	};

	private Walls()
	{
	}

	/**
	 * @return the wall at this tile, or -1 if it isn't one
	 */
	static int at(int x, int y)
	{
		for (int i = 0; i < COUNT; i++)
		{
			if (WALL[i][0] == x && WALL[i][1] == y)
			{
				return i;
			}
		}
		return -1;
	}

	static int wallX(int wall)
	{
		return WALL[wall][0];
	}

	static int wallY(int wall)
	{
		return WALL[wall][1];
	}

	static int standX(int wall)
	{
		return STAND[wall][0];
	}

	static int standY(int wall)
	{
		return STAND[wall][1];
	}

	/**
	 * Ticks to get from a tile to a wall's standing tile across the open cave floor. Everyone walks in
	 * the minigame, one tile a tick, even with run on.
	 */
	static int travel(int x, int y, int wall)
	{
		return Math.max(Math.abs(x - standX(wall)), Math.abs(y - standY(wall)));
	}
}
