package com.togoptimizer;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Stream orders reported by ToG Crowdsourcing users. Read only: this plugin never submits anything.
 */
@Slf4j
@Singleton
class WorldList
{
	private static final String URL = "https://togcrowdsourcing.com/worldinfo";
	private static final long REFRESH_MS = 10 * 60 * 1000;

	static class Entry
	{
		@SerializedName("world_number")
		int world;
		@SerializedName("stream_order")
		String order;
		int hits;
	}

	private final OkHttpClient http;
	private final Gson gson;

	private volatile Map<Integer, Entry> worlds = Collections.emptyMap();
	private long lastRequest;

	@Inject
	WorldList(OkHttpClient http, Gson gson)
	{
		this.http = http;
		this.gson = gson;
	}

	/**
	 * Fetches the list unless it was requested recently.
	 */
	void refreshIfStale()
	{
		long now = System.currentTimeMillis();
		if (now - lastRequest < REFRESH_MS)
		{
			return;
		}
		lastRequest = now;

		Request request = new Request.Builder().url(URL).build();
		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Couldn't fetch the ToG world list", e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					if (!response.isSuccessful() || body == null)
					{
						log.debug("ToG world list request failed: {}", response.code());
						return;
					}
					worlds = parse(gson, body.string());
				}
				catch (IOException | JsonParseException e)
				{
					log.debug("Couldn't read the ToG world list", e);
				}
			}
		});
	}

	static Map<Integer, Entry> parse(Gson gson, String json)
	{
		Entry[] entries = gson.fromJson(json, Entry[].class);
		Map<Integer, Entry> byWorld = new HashMap<>();
		if (entries != null)
		{
			for (Entry entry : entries)
			{
				if (entry != null && entry.order != null)
				{
					byWorld.put(entry.world, entry);
				}
			}
		}
		return byWorld;
	}

	void clear()
	{
		worlds = Collections.emptyMap();
		lastRequest = 0;
	}

	@Nullable
	String orderOf(int world)
	{
		Entry entry = worlds.get(world);
		return entry == null ? null : entry.order;
	}

	/**
	 * @return up to {@code limit} optimal worlds, most reported first
	 */
	List<Integer> optimalWorlds(int limit, IntPredicate usable)
	{
		return optimalWorlds(worlds, limit, usable);
	}

	static List<Integer> optimalWorlds(Map<Integer, Entry> worlds, int limit, IntPredicate usable)
	{
		List<Entry> optimal = new ArrayList<>();
		for (Entry entry : worlds.values())
		{
			if (StreamTracker.OPTIMAL_ORDER.equals(entry.order) && usable.test(entry.world))
			{
				optimal.add(entry);
			}
		}
		optimal.sort(Comparator.comparingInt((Entry e) -> e.hits).reversed());

		List<Integer> result = new ArrayList<>();
		for (int i = 0; i < Math.min(limit, optimal.size()); i++)
		{
			result.add(optimal.get(i).world);
		}
		return result;
	}
}
