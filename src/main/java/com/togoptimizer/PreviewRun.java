package com.togoptimizer;

import lombok.Getter;

/**
 * A simulated player following the plugin's advice on the real streams, so the plugin can be seen
 * working from outside the wall room without spending a game.
 */
final class PreviewRun
{
	@Getter
	private final TearsGame player;
	private final Planner planner;
	private final long seed;
	@Getter
	private Planner.Plan plan;
	// The planner's choice, held steady through near-ties like the highlight a player would follow
	@Getter
	private int shown = -1;
	private int shownSince;
	@Getter
	private int waitTicks = -1;

	PreviewRun(int startTick, int ticks, int startX, int startY, int reaction, Planner planner, long seed)
	{
		player = new TearsGame();
		player.tick = startTick;
		player.endTick = startTick + ticks;
		player.x = startX;
		player.y = startY;
		player.reaction = reaction;
		this.planner = planner;
		this.seed = seed;
	}

	boolean isOver()
	{
		return player.over();
	}

	/**
	 * Advances the player through a tick of the real game, after its streams have moved.
	 */
	void tick(StreamTracker tracker, int tick)
	{
		if (isOver())
		{
			return;
		}

		tracker.fill(player);
		advance(tick);
	}

	/**
	 * Advances the player through a tick of recorded streams.
	 */
	void tick(int[] colour, int[] nextMove, int tick)
	{
		if (isOver())
		{
			return;
		}
		System.arraycopy(colour, 0, player.colour, 0, Walls.COUNT);
		System.arraycopy(nextMove, 0, player.nextMove, 0, Walls.COUNT);
		advance(tick);
	}

	private void advance(int tick)
	{
		player.tick = tick;
		// The same futures every tick, so the estimates don't jitter from one tick to the next
		plan = planner.plan(player, seed);
		int previous = shown;
		shown = Planner.steady(shown, tick - shownSince, plan, Planner.STEADY_MARGIN);
		if (shown != previous)
		{
			shownSince = tick;
		}
		// Worked out before the tick finishes, while the game still shows this tick's streams
		waitTicks = Planner.waitTicksFor(player, shown);
		// Moves whenever the shown wall changes, as a player following it would
		player.request(shown);
		player.finishTick();
	}
}
