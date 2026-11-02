package com.togoptimizer;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class HopMenuTest
{
	// Menus list the left-click option last

	@Test
	public void theHopOptionSitsJustBelowJunasFirstOption()
	{
		// Talk-to on top, Hop-to added above it
		assertEquals(2, TogOptimizerPlugin.hopPosition(3, 2, 4, false));
		// Hop-to added below Talk-to
		assertEquals(2, TogOptimizerPlugin.hopPosition(2, 3, 4, false));
	}

	@Test
	public void theHopOptionIsTheLeftClickWhenSetTo()
	{
		assertEquals(3, TogOptimizerPlugin.hopPosition(3, 2, 4, true));
		assertEquals(3, TogOptimizerPlugin.hopPosition(2, 3, 4, true));
	}

	@Test
	public void theHopOptionNeverTakesTheLeftClickFromSomethingElse()
	{
		// The mouse is on something in front of Juna, whose option is on top
		assertEquals(1, TogOptimizerPlugin.hopPosition(1, 2, 4, true));
		assertEquals(1, TogOptimizerPlugin.hopPosition(3, 1, 4, true));
	}
}
