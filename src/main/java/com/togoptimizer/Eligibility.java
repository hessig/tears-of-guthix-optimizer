package com.togoptimizer;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * When the minigame can next be played. The OSRS Wiki: Juna resets at the start of the 7th day after
 * the last game, and the player must also have gained 100,000 experience or a quest point since.
 * Days start at the game's daily reset, midnight UTC.
 */
final class Eligibility
{
	static final int DAYS = 7;
	static final long XP_NEEDED = 100_000;
	// Juna's replies when asked too early: "...Come back in 7 days." after a story, and "It has not been
	// long since your last visit. Come again in 7 days." from the Story option
	private static final Pattern COME_BACK = Pattern.compile("come (?:back|again) in (\\d+) days?", Pattern.CASE_INSENSITIVE);
	private static final Pattern TOMORROW = Pattern.compile("come (?:back|again) tomorrow", Pattern.CASE_INSENSITIVE);

	private Eligibility()
	{
	}

	/**
	 * @return when a game finishing at {@code gameEnd} lets the player play again
	 */
	static long unlockAfterGame(long gameEnd)
	{
		return startOfDay(gameEnd, DAYS);
	}

	/**
	 * @return when the player can play again if Juna says to come back in {@code days} days
	 */
	static long unlockInDays(int days, long now)
	{
		return startOfDay(now, days);
	}

	private static long startOfDay(long millis, int daysLater)
	{
		LocalDate day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate();
		return day.plusDays(daysLater).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
	}

	/**
	 * @return the days Juna says to come back in, or null if the text isn't that reply
	 */
	@Nullable
	static Integer daysFromJuna(String text)
	{
		Matcher matcher = COME_BACK.matcher(text);
		if (matcher.find())
		{
			return Integer.parseInt(matcher.group(1));
		}
		return TOMORROW.matcher(text).find() ? 1 : null;
	}

	/**
	 * @return time left as days, hours and minutes, such as "6d 9h 55m"
	 */
	static String formatRemaining(long millis)
	{
		long minutes = Math.max(0, (millis + 59_999) / 60_000);
		long days = minutes / (24 * 60);
		long hours = minutes / 60 % 24;
		long mins = minutes % 60;
		if (days > 0)
		{
			return days + "d " + hours + "h " + mins + "m";
		}
		return hours > 0 ? hours + "h " + mins + "m" : mins + "m";
	}

	/**
	 * @param qpRequired the quest points needed to play again, from the game; 0 if there's no requirement
	 * @param xpAtLastGame total experience when the last game ended, or null if the plugin didn't see it
	 * @return what's still needed, or null if the experience or quest point condition is met
	 */
	@Nullable
	static String requirement(int qpRequired, int questPoints, long totalXp, @Nullable Long xpAtLastGame)
	{
		if (qpRequired <= 0 || questPoints >= qpRequired)
		{
			return null;
		}
		int qp = qpRequired - questPoints;
		String questPointText = qp + " more quest point" + (qp == 1 ? "" : "s");
		if (xpAtLastGame == null)
		{
			return "Needs " + questPointText + " or 100,000 XP since your last game";
		}
		long xp = XP_NEEDED - (totalXp - xpAtLastGame);
		if (xp <= 0)
		{
			return null;
		}
		return "Needs " + questPointText + " or " + String.format("%,d", xp) + " XP";
	}
}
