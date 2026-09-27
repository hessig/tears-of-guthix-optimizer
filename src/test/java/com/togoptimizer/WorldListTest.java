package com.togoptimizer;

import com.google.gson.Gson;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class WorldListTest
{
	// The shape togcrowdsourcing.com/worldinfo returns
	private static final String JSON = "["
		+ "{\"world_number\": 510, \"hits\": 4, \"stream_order\": \"gbbgbg\"},"
		+ "{\"world_number\": 329, \"hits\": 1033, \"stream_order\": \"gggbbb\"},"
		+ "{\"world_number\": 518, \"hits\": 392, \"stream_order\": \"gggbbb\"},"
		+ "{\"world_number\": 623, \"hits\": 3, \"stream_order\": \"gggbbb\"},"
		+ "{\"world_number\": 444, \"hits\": 22, \"stream_order\": \"bgbbgg\"}"
		+ "]";

	@Test
	public void parsesTheCrowdsourcedList()
	{
		Map<Integer, WorldList.Entry> worlds = WorldList.parse(new Gson(), JSON);

		assertEquals(5, worlds.size());
		assertEquals("gbbgbg", worlds.get(510).order);
		assertEquals(1033, worlds.get(329).hits);
	}

	@Test
	public void suggestsTheMostReportedOptimalWorlds()
	{
		Map<Integer, WorldList.Entry> worlds = WorldList.parse(new Gson(), JSON);

		assertEquals(List.of(329, 518), WorldList.optimalWorlds(worlds, 2, w -> true));
	}

	@Test
	public void skipsWorldsThatCantBeUsed()
	{
		Map<Integer, WorldList.Entry> worlds = WorldList.parse(new Gson(), JSON);

		assertEquals(List.of(518, 623), WorldList.optimalWorlds(worlds, 3, w -> w != 329));
	}

	@Test
	public void toleratesAnEmptyOrOddResponse()
	{
		assertTrue(WorldList.parse(new Gson(), "[]").isEmpty());
		assertTrue(WorldList.parse(new Gson(), "[{\"world_number\": 1}]").isEmpty());
	}
}
