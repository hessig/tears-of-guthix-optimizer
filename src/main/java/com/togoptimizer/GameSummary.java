package com.togoptimizer;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Nullable;
import lombok.Value;
import net.runelite.client.util.ColorUtil;

/**
 * Compares a recorded game with the plugin's advice: how many tears following it perfectly would have
 * collected, the most possible with hindsight, and where the player fell behind the advice.
 */
final class GameSummary
{
	@Value
	static class Result
	{
		int tears;
		int perfect;
		int hindsight;
		// Tears lost to entering off the countdown, and whether that was late rather than early
		int entry;
		boolean lateEntry;
		int lateClicks;
		int offHighlight;
		int extraWalking;
		int green;
	}

	@Value
	static class Palette
	{
		Color tears;
		Color reference;
		Color gap;
	}

	private GameSummary()
	{
	}

	static Result summarise(GameRecord record, Planner planner, int startX, int startY)
	{
		int late = record.ticksLate == null ? 0 : record.ticksLate;
		int start = record.streamsAt(record.start - late) == null ? record.start : record.start - late;
		int perfect = follow(record, start, planner, startX, startY);
		int entry = start == record.start ? 0 : Math.max(0, perfect - follow(record, record.start, planner, startX, startY));
		int hindsight = Math.max(perfect, hindsight(record, start, startX, startY));

		// Each gap is what fixing just that would have added, with the player's other clicks unchanged
		TreeMap<Integer, GameRecord.Tick> ticks = record.getTicks();
		List<GameRecord.Click> clicks = record.getClicks();
		int played = replay(record, clicks, startX, startY);
		int lateClicks = replay(record, withoutLateClicks(ticks, clicks), startX, startY) - played;
		int offHighlight = replay(record, onTheHighlight(clicks), startX, startY) - played;
		// The replay always walks the shortest way, so what the player collected below it went to walking
		int extraWalking = played - record.tears;
		return new Result(record.tears, perfect, hindsight, entry, late > 0, Math.max(0, lateClicks),
			Math.max(0, offHighlight), Math.max(0, extraWalking), green(ticks));
	}

	/**
	 * @return tears from following the highlight with no delay on the recorded streams
	 */
	static int follow(GameRecord record, int start, Planner planner, int startX, int startY)
	{
		PreviewRun run = new PreviewRun(start, record.length, startX, startY, 0, planner, 0);
		int[][] streams = null;
		for (int tick = start; !run.isOver(); tick++)
		{
			int[][] recorded = record.streamsAt(tick);
			// A tick the streams weren't known plays as the one before
			streams = recorded == null ? streams : recorded;
			if (streams == null)
			{
				return 0;
			}
			run.tick(streams[0], streams[1], tick);
		}
		return run.getPlayer().tears;
	}

	/**
	 * @return the most tears possible on the recorded streams, knowing where each would land
	 */
	static int hindsight(GameRecord record, int start, int startX, int startY)
	{
		// Ticks until a wall gives tears, after walking there and starting to collect
		int maxWait = Walls.COUNT + TearsGame.START_DELAY + 8;
		// Best tears from the next tick on, by wall (0 for none yet) and ticks until it gives tears
		int[][] next = new int[Walls.COUNT + 1][maxWait + 1];
		int[] colour = new int[Walls.COUNT];
		for (int i = record.length - 1; i >= 0; i--)
		{
			int[][] recorded = record.streamsAt(start + i);
			if (recorded != null)
			{
				colour = recorded[0];
			}
			int[][] value = new int[Walls.COUNT + 1][maxWait + 1];
			for (int target = -1; target < Walls.COUNT; target++)
			{
				int x = target < 0 ? startX : Walls.standX(target);
				int y = target < 0 ? startY : Walls.standY(target);
				for (int wait = 0; wait <= maxWait; wait++)
				{
					int best = (target >= 0 && wait == 0 ? colour[target] : 0) + next[target + 1][Math.max(wait - 1, 0)];
					for (int wall = 0; wall < Walls.COUNT; wall++)
					{
						if (wall != target)
						{
							int ready = Walls.travel(x, y, wall) + TearsGame.START_DELAY;
							best = Math.max(best, next[wall + 1][Math.min(ready - 1, maxWait)]);
						}
					}
					value[target + 1][wait] = best;
				}
			}
			next = value;
		}
		return next[0][0];
	}

	/**
	 * Plays the player's own clicks back on the recorded streams, walking the shortest way to each wall.
	 */
	static int replay(GameRecord record, List<GameRecord.Click> clicks, int startX, int startY)
	{
		TearsGame game = new TearsGame();
		game.tick = record.start;
		game.endTick = record.start + record.length;
		game.x = startX;
		game.y = startY;
		Map<Integer, Integer> chosen = new HashMap<>();
		for (GameRecord.Click click : clicks)
		{
			chosen.put(made(click), click.getWall());
		}
		int[][] streams = null;
		while (!game.over())
		{
			int[][] recorded = record.streamsAt(game.tick);
			streams = recorded == null ? streams : recorded;
			if (streams == null)
			{
				return 0;
			}
			System.arraycopy(streams[0], 0, game.colour, 0, Walls.COUNT);
			System.arraycopy(streams[1], 0, game.nextMove, 0, Walls.COUNT);
			Integer wall = chosen.get(game.tick);
			if (wall != null)
			{
				game.request(wall);
			}
			game.finishTick();
		}
		return game.tears;
	}

	/**
	 * @return the player's clicks, with each one on a wall the highlight had moved to made as soon as it
	 * moved
	 */
	static List<GameRecord.Click> withoutLateClicks(TreeMap<Integer, GameRecord.Tick> ticks, List<GameRecord.Click> clicks)
	{
		List<GameRecord.Click> prompt = new ArrayList<>();
		Map<Integer, Integer> movedTo = new HashMap<>();
		Iterator<Map.Entry<Integer, GameRecord.Tick>> states = ticks.entrySet().iterator();
		Map.Entry<Integer, GameRecord.Tick> state = states.hasNext() ? states.next() : null;
		int shown = -1;
		for (GameRecord.Click click : clicks)
		{
			// The highlight's moves the player had seen when clicking
			for (; state != null && state.getKey() <= made(click); state = states.hasNext() ? states.next() : null)
			{
				GameRecord.Tick tick = state.getValue();
				if (tick.getShown() >= 0 && tick.getShown() != shown && tick.getShown() != tick.getCollecting())
				{
					movedTo.put(tick.getShown(), state.getKey());
				}
				shown = tick.getShown();
			}
			Integer moved = movedTo.remove(click.getWall());
			boolean late = moved != null && click.getWall() == click.getShown() && moved < made(click);
			prompt.add(late ? new GameRecord.Click(moved + 1, click.getWall(), click.getShown()) : click);
		}
		return prompt;
	}

	/**
	 * @return the player's clicks, with each one on a wall the plugin wasn't highlighting moved to the
	 * highlighted wall
	 */
	static List<GameRecord.Click> onTheHighlight(List<GameRecord.Click> clicks)
	{
		List<GameRecord.Click> followed = new ArrayList<>();
		for (GameRecord.Click click : clicks)
		{
			boolean off = click.getShown() >= 0 && click.getWall() != click.getShown();
			followed.add(off ? new GameRecord.Click(click.getTick(), click.getShown(), click.getShown()) : click);
		}
		return followed;
	}

	static int green(TreeMap<Integer, GameRecord.Tick> ticks)
	{
		int lost = 0;
		Integer previous = null;
		for (GameRecord.Tick state : ticks.values())
		{
			if (previous != null && state.getTears() < previous)
			{
				lost += previous - state.getTears();
			}
			previous = state.getTears();
		}
		return lost;
	}

	/**
	 * @return the tick whose state the player saw when clicking: a click is recorded with the tick it's
	 * sent on, after the previous tick's update
	 */
	private static int made(GameRecord.Click click)
	{
		return click.getTick() - 1;
	}

	/**
	 * The summary as chat lines: the score against both references, then the gaps, leaving out any that
	 * cost nothing.
	 */
	static List<String> lines(Result result, @Nullable Palette palette)
	{
		String tears = paint(result.getTears() + " tears", palette == null ? null : palette.getTears());
		String perfect = paint(String.valueOf(result.getPerfect()), palette == null ? null : palette.getReference());
		String hindsight = paint(String.valueOf(result.getHindsight()), palette == null ? null : palette.getReference());
		List<String> lines = new ArrayList<>();
		if (result.getTears() < result.getPerfect())
		{
			lines.add(tears + ". Following the plugin perfectly: " + perfect + ". Hindsight max: " + hindsight + ".");
		}
		else if (result.getTears() == result.getPerfect())
		{
			lines.add(tears + ", the same as following the plugin perfectly. Hindsight max: " + hindsight + ".");
		}
		else
		{
			lines.add(tears + ", more than following the plugin perfectly (" + perfect + "). Hindsight max: " + hindsight + ".");
		}

		Color gap = palette == null ? null : palette.getGap();
		List<String> gaps = new ArrayList<>();
		addGap(gaps, result.getEntry(), result.isLateEntry() ? "a late entry" : "an early entry", gap);
		addGap(gaps, result.getLateClicks(), "late clicks", gap);
		addGap(gaps, result.getOffHighlight(), "clicks off the highlight", gap);
		addGap(gaps, result.getExtraWalking(), "extra walking", gap);
		addGap(gaps, result.getGreen(), "green streams", gap);
		if (!gaps.isEmpty())
		{
			String joined = gaps.size() == 1
				? gaps.get(0)
				: String.join(", ", gaps.subList(0, gaps.size() - 1)) + " and " + gaps.get(gaps.size() - 1);
			lines.add("Lost " + joined + ".");
		}
		return lines;
	}

	private static void addGap(List<String> gaps, int lost, String cause, @Nullable Color colour)
	{
		if (lost > 0)
		{
			gaps.add(paint(lost + " to " + cause, colour));
		}
	}

	private static String paint(String text, @Nullable Color colour)
	{
		return colour == null ? text : ColorUtil.wrapWithColorTag(text, colour);
	}
}
