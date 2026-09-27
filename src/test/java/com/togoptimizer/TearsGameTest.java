package com.togoptimizer;

import java.util.Random;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class TearsGameTest
{
	static final int ENTRANCE_X = 3257;
	static final int ENTRANCE_Y = 9517;

	static TearsGame game(String order, int ticks, int reaction, long seed)
	{
		return TearsGame.start(order, ticks, ENTRANCE_X, ENTRANCE_Y, reaction, new Random(seed));
	}

	@Test
	public void wallsAreFoundByTile()
	{
		assertEquals(0, Walls.at(3257, 9514));
		assertEquals(4, Walls.at(3261, 9517));
		assertEquals(8, Walls.at(3259, 9520));
		assertEquals(-1, Walls.at(3258, 9517));
	}

	@Test
	public void travelIsOneTileATick()
	{
		// Neighbouring walls on the south side
		assertEquals(1, Walls.travel(Walls.standX(0), Walls.standY(0), 1));
		// South-west corner to north-east corner, four tiles
		assertEquals(4, Walls.travel(Walls.standX(0), Walls.standY(0), 8));
		assertEquals(0, Walls.travel(Walls.standX(3), Walls.standY(3), 3));
	}

	@Test
	public void streamsKeepTheirColoursAndNeverShare()
	{
		TearsGame game = game("gggbbb", 1000, 0, 1);
		for (int i = 0; i < 1000; i++)
		{
			game.step(g -> -1);
			int blue = 0;
			int green = 0;
			for (int c : game.colour)
			{
				blue += c == TearsGame.BLUE ? 1 : 0;
				green += c == TearsGame.GREEN ? 1 : 0;
			}
			assertEquals(3, blue);
			assertEquals(3, green);
		}
	}

	@Test
	public void anotherBlueLandsOnYourWallFiveTimesInNine()
	{
		// The wiki: after the first blue leaves the player's wall, there is a 5/9 chance one of the
		// other two blues lands there
		int trials = 20_000;
		int landed = 0;
		for (int seed = 0; seed < trials; seed++)
		{
			TearsGame game = game("gggbbb", 1000, 0, seed);
			// The game starts as the first blue moves, so its current wall is where it just left
			int left = firstBlueWallBeforeMoving(game);
			game.moveStreams();
			game.tick++;
			game.moveStreams();
			boolean second = game.colour[left] == TearsGame.BLUE;
			game.tick++;
			game.moveStreams();
			if (second || game.colour[left] == TearsGame.BLUE)
			{
				landed++;
			}
		}
		assertEquals(5 / 9.0, landed / (double) trials, 0.01);
	}

	private static int firstBlueWallBeforeMoving(TearsGame game)
	{
		for (int wall = 0; wall < Walls.COUNT; wall++)
		{
			if (game.colour[wall] == TearsGame.BLUE && game.nextMove[wall] == game.tick)
			{
				return wall;
			}
		}
		throw new AssertionError("no blue due to move");
	}

	@Test
	public void nearestBlueScoresAsCalibrated()
	{
		// With timings measured in game (273 QP, two-tick reactions), where a player scored 191 playing
		// well, going to the nearest blue averages about 168
		double total = 0;
		int games = 200;
		for (int seed = 0; seed < games; seed++)
		{
			TearsGame game = game("gggbbb", 273, 2, seed);
			game.runUntil(273, TearsGame::nearestBlue);
			total += game.tears;
		}
		assertEquals(168.0, total / games, 2);
	}

	@Test
	public void noTearsAreCollectedWhileWalking()
	{
		TearsGame game = game("gggbbb", 100, 0, 3);
		game.step(TearsGame::nearestBlue);
		// The entrance is a tile or two from every wall, then collecting starts a tick after arriving
		assertEquals(0, game.tears);
		assertTrue(game.readyTick > game.tick - 1);
	}
}
