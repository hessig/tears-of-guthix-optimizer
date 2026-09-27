package com.togoptimizer;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class TogOptimizerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(TogOptimizerPlugin.class);
		RuneLite.main(args);
	}
}
