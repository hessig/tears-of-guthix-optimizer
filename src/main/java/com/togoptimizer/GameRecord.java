package com.togoptimizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * What happened in a game, kept for the post-game summary: the streams on every tick, and where the
 * player was, what they had collected and what the plugin advised.
 */
final class GameRecord
{
	// Streams are kept from shortly before the game, for replaying an entry on the countdown when the
	// player entered late. Entries are never more than half a stream's life off.
	static final int BEFORE = TearsGame.STREAM_LIFE;
	// Ticks after the game's time runs out until its last tear has shown in the count
	static final int END_LAG = 2;

	@Value
	static class Tick
	{
		int x;
		int y;
		int tears;
		int collecting;
		// The plugin's highlighted wall, or -1 if none
		int shown;
	}

	@Value
	static class Click
	{
		int tick;
		int wall;
		// The highlighted wall when the player clicked
		int shown;
	}

	// Tick -> {colour, nextMove}
	private final Map<Integer, int[][]> streams = new HashMap<>();
	private final TreeMap<Integer, Tick> ticks = new TreeMap<>();
	private final List<Click> clicks = new ArrayList<>();

	int start = -1;
	int end = -1;
	int length;
	int tears;
	// From EntryTiming.ticksLate when the player continued past Juna's last line, or null if unknown
	@Nullable
	Integer ticksLate;

	void streams(int tick, StreamTracker tracker)
	{
		int[] colour = new int[Walls.COUNT];
		int[] nextMove = new int[Walls.COUNT];
		for (int wall = 0; wall < Walls.COUNT; wall++)
		{
			colour[wall] = tracker.colour(wall);
			nextMove[wall] = tracker.nextMove(wall);
		}
		streams.put(tick, new int[][]{colour, nextMove});
		if (start < 0)
		{
			streams.keySet().removeIf(t -> t < tick - BEFORE);
		}
	}

	void streams(int tick, int[] colour, int[] nextMove)
	{
		streams.put(tick, new int[][]{colour.clone(), nextMove.clone()});
	}

	@Nullable
	int[][] streamsAt(int tick)
	{
		return streams.get(tick);
	}

	void tick(int tick, Tick state)
	{
		ticks.put(tick, state);
	}

	TreeMap<Integer, Tick> getTicks()
	{
		return ticks;
	}

	void click(int tick, int wall, int shown)
	{
		clicks.add(new Click(tick, wall, shown));
	}

	List<Click> getClicks()
	{
		return clicks;
	}

	boolean isOver()
	{
		return end >= 0;
	}
}
