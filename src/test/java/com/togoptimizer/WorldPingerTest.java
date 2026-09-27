package com.togoptimizer;

import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class WorldPingerTest
{
	@Test
	public void fastestWorldsComeFirst()
	{
		Map<Integer, Integer> pings = Map.of(329, 95, 518, 20, 429, 40);

		assertEquals(List.of(518, 429, 329), WorldPinger.byPing(List.of(329, 518, 429), pings::get, 3));
	}

	@Test
	public void unmeasuredWorldsFollowInTheirOriginalOrder()
	{
		Map<Integer, Integer> pings = Map.of(429, 40);

		assertEquals(List.of(429, 329, 518), WorldPinger.byPing(List.of(329, 518, 429), pings::get, 3));
	}

	@Test
	public void aWorldIsFasterWhenItsPingIsLowerByTheMargin()
	{
		assertEquals(true, WorldPinger.isFaster(20, 30, 10));
		assertEquals(false, WorldPinger.isFaster(21, 30, 10));
		assertEquals(false, WorldPinger.isFaster(null, 30, 10));
		assertEquals(false, WorldPinger.isFaster(20, null, 10));
	}

	@Test
	public void onlyTheRequestedNumberAreReturned()
	{
		Map<Integer, Integer> pings = Map.of(329, 95, 518, 20, 429, 40);

		assertEquals(List.of(518), WorldPinger.byPing(List.of(329, 518, 429), pings::get, 1));
	}
}
