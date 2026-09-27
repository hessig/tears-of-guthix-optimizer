package com.togoptimizer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntFunction;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Measures how long it takes to reach worlds, so suggestions favour ones near the player. Each world
 * is measured once per login, since the player's location doesn't change while logged in. Only Jagex's
 * game servers are contacted.
 */
@Slf4j
@Singleton
class WorldPinger
{
	private static final int GAME_PORT = 43594;
	private static final int TIMEOUT_MS = 2000;

	private final Map<Integer, Integer> pings = new ConcurrentHashMap<>();
	private final Set<Integer> requested = ConcurrentHashMap.newKeySet();
	@Nullable
	private ExecutorService executor;

	/**
	 * Measures a world in the background, unless it has been already this login.
	 */
	synchronized void pingOnce(int world, String address)
	{
		if (!requested.add(world))
		{
			return;
		}
		if (executor == null)
		{
			executor = Executors.newSingleThreadExecutor();
		}
		executor.execute(() ->
		{
			int ms = ping(address);
			if (ms >= 0)
			{
				pings.put(world, ms);
			}
		});
	}

	/**
	 * @return milliseconds to open a connection to the world, or -1 if it couldn't be reached
	 */
	private static int ping(String address)
	{
		try
		{
			// Resolved first so the lookup isn't counted
			InetAddress host = InetAddress.getByName(address);
			long start = System.nanoTime();
			try (Socket socket = new Socket())
			{
				socket.connect(new InetSocketAddress(host, GAME_PORT), TIMEOUT_MS);
			}
			return (int) ((System.nanoTime() - start) / 1_000_000);
		}
		catch (IOException e)
		{
			log.debug("Couldn't reach world {}", address, e);
			return -1;
		}
	}

	@Nullable
	Integer pingOf(int world)
	{
		return pings.get(world);
	}

	/**
	 * Forgets all measurements, for a new login that may be from somewhere else.
	 */
	void clear()
	{
		pings.clear();
		requested.clear();
	}

	synchronized void shutDown()
	{
		if (executor != null)
		{
			executor.shutdownNow();
			executor = null;
		}
		clear();
	}

	/**
	 * @return true if both pings are known and the candidate's is lower by at least {@code by} ms
	 */
	static boolean isFaster(@Nullable Integer candidate, @Nullable Integer current, int by)
	{
		return candidate != null && current != null && candidate <= current - by;
	}

	/**
	 * Orders candidate worlds by ping, fastest first. Worlds not measured yet keep their original
	 * order after the measured ones.
	 */
	static List<Integer> byPing(List<Integer> candidates, IntFunction<Integer> ping, int limit)
	{
		List<Integer> measured = new ArrayList<>();
		List<Integer> unmeasured = new ArrayList<>();
		for (int world : candidates)
		{
			(ping.apply(world) != null ? measured : unmeasured).add(world);
		}
		measured.sort(Comparator.comparingInt(ping::apply));
		measured.addAll(unmeasured);
		return measured.subList(0, Math.min(limit, measured.size()));
	}
}
