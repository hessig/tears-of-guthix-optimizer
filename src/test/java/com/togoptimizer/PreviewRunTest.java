package com.togoptimizer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PreviewRunTest
{
	private static final int WATCH_TICKS = 30;
	private static final int PREVIEW_TICKS = 273;

	@Test
	public void previewPlaysTheRealStreamsLikeAPlayerFollowingThePlugin()
	{
		for (long seed = 0; seed < 5; seed++)
		{
			TearsGame streams = TearsGameTest.game("gggbbb", 100_000, 2, seed);
			TearsGame mirror = TearsGameTest.game("gggbbb", 100_000, 2, seed);
			StreamTracker tracker = new StreamTracker();
			int[] before = new int[Walls.COUNT];
			Planner planner = new Planner(100, 40);
			int[] shown = {-1};

			PreviewRun preview = null;
			for (int tick = 0; tick < WATCH_TICKS + PREVIEW_TICKS; tick++)
			{
				System.arraycopy(streams.colour, 0, before, 0, Walls.COUNT);
				streams.moveStreams();
				for (int wall = 0; wall < Walls.COUNT; wall++)
				{
					if (tick == 0 || streams.colour[wall] != before[wall])
					{
						tracker.wallChanged(wall, streams.colour[wall], tick);
					}
				}

				if (tick == WATCH_TICKS)
				{
					assertTrue(tracker.isComplete());
					preview = new PreviewRun(tick, PREVIEW_TICKS, TearsGameTest.ENTRANCE_X, TearsGameTest.ENTRANCE_Y, 2, planner, seed);
					mirror.endTick = tick + PREVIEW_TICKS;
				}

				if (preview != null)
				{
					preview.tick(tracker, tick);
					// The same strategy played directly: plan, hold steady, go where it points
					mirror.moveStreams();
					Planner.Plan plan = planner.plan(mirror, seed);
					shown[0] = Planner.steady(shown[0], plan, Planner.STEADY_MARGIN);
					mirror.request(shown[0]);
					mirror.finishTick();
				}
				else
				{
					mirror.moveStreams();
					mirror.tick++;
				}
				streams.tick++;
			}

			assertTrue(preview.isOver());
			assertEquals(mirror.tears, preview.getPlayer().tears);
		}
	}
}
