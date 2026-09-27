package com.togoptimizer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PlannerTest
{
	@Test
	public void onlyBlueWallsAndWaitingAreOptions()
	{
		TearsGame game = TearsGameTest.game("gggbbb", 300, 1, 5);
		game.moveStreams();
		Planner.Plan plan = new Planner(50, 40).plan(game, 1);

		for (int wall = 0; wall < Walls.COUNT; wall++)
		{
			// The player hasn't picked a wall yet, so there's nowhere to wait
			boolean option = game.colour[wall] == TearsGame.BLUE;
			assertEquals("wall " + wall, option, !Double.isNaN(plan.getExpected()[wall]));
		}
		assertEquals(TearsGame.BLUE, game.colour[plan.getBest()]);
	}

	@Test
	public void waitingAtAnEmptyWallIsAnOption()
	{
		TearsGame game = TearsGameTest.game("gggbbb", 300, 1, 5);
		game.moveStreams();
		int empty = 0;
		while (game.colour[empty] != TearsGame.NONE)
		{
			empty++;
		}
		game.target = empty;

		Planner.Plan plan = new Planner(50, 40).plan(game, 1);

		assertTrue(!Double.isNaN(plan.getExpected()[empty]));
	}

	@Test
	public void optionsStillDifferWhileAClickIsOnItsWay()
	{
		// A click already on its way used to lock every option into the same future
		TearsGame game = TearsGameTest.game("gggbbb", 300, 1, 5);
		game.moveStreams();
		int firstBlue = 0;
		while (game.colour[firstBlue] != TearsGame.BLUE)
		{
			firstBlue++;
		}
		game.request(firstBlue);

		double[] expected = new Planner(100, 40).plan(game, 1).getExpected();

		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;
		for (double e : expected)
		{
			if (!Double.isNaN(e))
			{
				min = Math.min(min, e);
				max = Math.max(max, e);
			}
		}
		assertTrue(max - min > 0.1);
	}

	private static Planner.Plan plan(double... expected)
	{
		int best = -1;
		for (int i = 0; i < expected.length; i++)
		{
			if (!Double.isNaN(expected[i]) && (best < 0 || expected[i] > expected[best]))
			{
				best = i;
			}
		}
		return new Planner.Plan(expected, best);
	}

	@Test
	public void theShownChoiceHoldsThroughNearTies()
	{
		double n = Double.NaN;
		assertEquals(2, Planner.steady(2, plan(n, 30.4, 30.1, n), 0.5));
		// Clearly beaten
		assertEquals(1, Planner.steady(2, plan(n, 31.0, 30.1, n), 0.5));
		// No longer an option
		assertEquals(1, Planner.steady(3, plan(n, 30.4, 30.1, n), 0.5));
		// Nothing shown yet
		assertEquals(1, Planner.steady(-1, plan(n, 30.4, 30.1, n), 0.5));
	}

	@Test
	public void waitingLastsUntilTheBurstsLastBlueMoves()
	{
		TearsGame game = new TearsGame();
		game.tick = 100;
		game.colour[0] = TearsGame.BLUE;
		game.nextMove[0] = 101;
		game.colour[1] = TearsGame.BLUE;
		game.nextMove[1] = 102;
		// Moved on this tick, so it isn't due again for a whole cycle
		game.colour[2] = TearsGame.BLUE;
		game.nextMove[2] = 116;

		assertEquals(2, Planner.waitTicks(game));

		game.tick = 102;
		game.nextMove[0] = 117;
		game.nextMove[1] = 118;
		assertEquals(-1, Planner.waitTicks(game));
	}

	@Test
	public void waitingIsOnlyOfferedWhileABlueCanStillLand()
	{
		TearsGame game = new TearsGame();
		game.tick = 100;
		game.endTick = 400;
		game.x = Walls.standX(4);
		game.y = Walls.standY(4);
		game.target = 4;
		game.reaction = 2;
		game.rng = new java.util.Random(1);
		int[] blues = {0, 2, 7};
		int[] greens = {1, 5, 8};
		for (int i = 0; i < 3; i++)
		{
			game.colour[blues[i]] = TearsGame.BLUE;
			game.colour[greens[i]] = TearsGame.GREEN;
			game.nextMove[greens[i]] = 110 + i;
		}
		// Every blue has just moved, so none can land on wall 4 for over ten ticks
		game.nextMove[0] = 114;
		game.nextMove[2] = 115;
		game.nextMove[7] = 116;
		assertTrue(Double.isNaN(new Planner(50, 40).plan(game, 1).getExpected()[4]));
		assertEquals(-1, Planner.waitTicksFor(game, 4));

		// Two blues still to move this burst: waiting is an option, for two more ticks
		game.nextMove[2] = 101;
		game.nextMove[7] = 102;
		assertTrue(!Double.isNaN(new Planner(50, 40).plan(game, 1).getExpected()[4]));
		assertEquals(2, Planner.waitTicksFor(game, 4));
	}

	@Test
	public void beatsGoingToTheNearestBlue()
	{
		// Simulations put the planner well ahead of going to the nearest blue
		int games = 30;
		double planned = 0;
		double nearest = 0;
		Planner planner = new Planner(100, 40);
		for (int seed = 0; seed < games; seed++)
		{
			TearsGame a = TearsGameTest.game("gggbbb", 300, 1, seed);
			a.runUntil(300, planner.asPolicy(seed));
			planned += a.tears;

			TearsGame b = TearsGameTest.game("gggbbb", 300, 1, seed);
			b.runUntil(300, TearsGame::nearestBlue);
			nearest += b.tears;
		}
		double gain = (planned - nearest) / games;
		assertTrue("gain was " + gain, gain > 3);
	}
}
