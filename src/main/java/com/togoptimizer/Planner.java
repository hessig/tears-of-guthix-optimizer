package com.togoptimizer;

import java.util.Arrays;
import java.util.Random;
import lombok.Value;

/**
 * Estimates how many tears each choice of wall leads to, by playing the same random futures out
 * from each choice and averaging.
 */
final class Planner
{
	@Value
	static class Plan
	{
		// Expected tears over the horizon if the player picks each wall now; NaN where it isn't an option
		double[] expected;
		// The wall with the most expected tears, or -1 if there is no choice to make
		int best;
	}

	// How much better another wall must be before the recommendation moves to it, in tears. Two walls'
	// values cross as streams age; at 0.5 the highlight reversed about 12 times a game, at 1.5 about
	// once, with no measurable cost in tears.
	static final double STEADY_MARGIN = 1.5;
	// The six streams move on consecutive ticks, so a cycle's remaining moves all fall within this many
	private static final int BURST_TICKS = 5;

	private final int rollouts;
	private final int horizon;

	Planner(int rollouts, int horizon)
	{
		this.rollouts = rollouts;
		this.horizon = horizon;
	}

	/**
	 * @param game the current game, after this tick's streams have moved
	 */
	Plan plan(TearsGame game, long seed)
	{
		double[] expected = new double[Walls.COUNT];
		Arrays.fill(expected, Double.NaN);

		Random seeds = new Random(seed);
		long[] futures = new long[rollouts];
		for (int i = 0; i < rollouts; i++)
		{
			futures[i] = seeds.nextLong();
		}

		int best = -1;
		for (int wall = 0; wall < Walls.COUNT; wall++)
		{
			if (!isOption(game, wall))
			{
				continue;
			}

			long total = 0;
			for (long future : futures)
			{
				TearsGame sim = game.copy(new Random(future));
				int start = sim.tears;
				// Choosing this option replaces any click already on its way
				sim.pendingWall = -1;
				sim.request(wall);
				sim.finishTick();
				sim.runUntil(game.tick + horizon, TearsGame::nearestBlue);
				total += sim.tears - start;
			}
			expected[wall] = total / (double) rollouts;
			if (best < 0 || expected[wall] > expected[best])
			{
				best = wall;
			}
		}
		return new Plan(expected, best);
	}

	/**
	 * Keeps showing the previous choice while it's still an option within {@code margin} tears of the
	 * best, so near-ties don't make the recommendation flicker between walls.
	 */
	static int steady(int previous, Plan plan, double margin)
	{
		int best = plan.getBest();
		if (previous < 0 || best < 0)
		{
			return best;
		}
		double[] expected = plan.getExpected();
		return !Double.isNaN(expected[previous]) && expected[previous] >= expected[best] - margin ? previous : best;
	}

	/**
	 * How long waiting at an empty wall can still pay off: blues can only land when they move, and a
	 * cycle's moves come on consecutive ticks, so once the last blue due soon has moved, there's no
	 * point waiting any longer.
	 *
	 * @return ticks until the last blue due to move in the current burst, or -1 if none is due
	 */
	static int waitTicks(TearsGame game)
	{
		int last = -1;
		for (int wall = 0; wall < Walls.COUNT; wall++)
		{
			int due = game.nextMove[wall] - game.tick;
			if (game.colour[wall] == TearsGame.BLUE && due > 0 && due <= BURST_TICKS)
			{
				last = Math.max(last, due);
			}
		}
		return last;
	}

	/**
	 * Any blue wall, or the empty wall the player is already at while a blue could still land there
	 */
	private static boolean isOption(TearsGame game, int wall)
	{
		return game.colour[wall] == TearsGame.BLUE
			|| wall == game.target && game.colour[wall] == TearsGame.NONE && waitTicks(game) > 0;
	}

	/**
	 * @return ticks left to wait if {@code shown} means staying at the player's own empty wall, or -1
	 */
	static int waitTicksFor(TearsGame game, int shown)
	{
		boolean waiting = shown >= 0 && shown == game.target && game.colour[shown] == TearsGame.NONE;
		return waiting ? waitTicks(game) : -1;
	}

	/**
	 * Follows the planner's advice whenever the current wall stops giving blue tears.
	 */
	TearsGame.Policy asPolicy(long seed)
	{
		// Separate from the game's own randomness, so the game plays out the same as under other policies
		Random seeds = new Random(seed);
		return game ->
		{
			if (game.target >= 0 && game.colour[game.target] == TearsGame.BLUE)
			{
				return -1;
			}
			return plan(game, seeds.nextLong()).getBest();
		};
	}
}
