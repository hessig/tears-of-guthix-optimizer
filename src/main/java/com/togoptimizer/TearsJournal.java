package com.togoptimizer;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Reads the personal eligibility paragraph from the completed Tears of Guthix quest journal. */
final class TearsJournal
{
	private static final String COMPLETE = "quest complete!";
	private static final String INTRO = "now juna will let me into the cave to collect the tears if i tell her stories of my adventures.";
	private static final String ENOUGH = "i have had enough adventures to tell juna more stories";
	private static final String WEEK = "a week has passed since i last collected the tears.";
	private static final String VISIT_NOW = "i can visit juna again now.";
	private static final Pattern BREAK = Pattern.compile("<br\\s*/?>|&nbsp;", Pattern.CASE_INSENSITIVE);
	private static final Pattern COLOUR = Pattern.compile("</?(?:col|shad|u)(?:=[^<>]*)?>", Pattern.CASE_INSENSITIVE);
	private static final Pattern SPACE = Pattern.compile("\\s+");
	private static final Pattern NEXT_VISIT = Pattern.compile("i will be able to collect the tears of guthix"
		+ "(?: in ([1-7]|one) days?)?"
		+ "(?:,? as (?:long|soon) as i gain either (?:1|one) quest point or "
		+ "([1-9][0-9]{0,2},[0-9]{3}|[1-9][0-9]{0,5}) total xp)?([.]?)");

	static final class Reading
	{
		final Integer daysRemaining;
		@Nullable final Integer xpRemaining;

		private Reading(int daysRemaining, @Nullable Integer xpRemaining)
		{
			this.daysRemaining = daysRemaining;
			this.xpRemaining = xpRemaining;
		}
	}

	private TearsJournal()
	{
	}

	/** The caller verifies the journal title. Missing numbers and generic quest text do not imply readiness. */
	@Nullable
	static Reading parse(@Nullable String body)
	{
		if (body == null)
		{
			return null;
		}
		String uncoloured = COLOUR.matcher(body).replaceAll("");
		String lines = BREAK.matcher(uncoloured).replaceAll(" ").replace('\u00a0', ' ');
		String text = SPACE.matcher(lines).replaceAll(" ").trim().toLowerCase(Locale.ROOT);
		int marker = text.indexOf(COMPLETE);
		if (marker < 0)
		{
			return null;
		}
		String personal = text.substring(marker + COMPLETE.length()).trim();
		if (personal.startsWith(INTRO))
		{
			personal = personal.substring(INTRO.length()).trim();
		}

		// The ready paragraph joins these clauses with ", and". The countdown paragraph uses a full stop.
		personal = personal.replace(ENOUGH + ", and ", ENOUGH + ". ");
		boolean enough = personal.startsWith(ENOUGH + ".");
		if (enough)
		{
			personal = personal.substring(ENOUGH.length() + 1).trim();
		}
		boolean weekPassed = personal.startsWith(WEEK);
		if (weekPassed)
		{
			personal = personal.substring(WEEK.length()).trim();
		}
		if (personal.equals(VISIT_NOW))
		{
			return new Reading(0, 0);
		}

		Matcher next = NEXT_VISIT.matcher(personal);
		if (!next.matches())
		{
			return null;
		}
		String days = next.group(1);
		String xp = next.group(2);
		if ((days == null && xp == null) || (weekPassed && days != null) || (enough && xp != null))
		{
			return null;
		}
		int daysRemaining = days == null ? 0 : "one".equals(days) ? 1 : Integer.parseInt(days);
		if (xp != null)
		{
			int remaining = Integer.parseInt(xp.replace(",", ""));
			return remaining <= Eligibility.XP_NEEDED ? new Reading(daysRemaining, remaining) : null;
		}
		// A trailing clause can still be loading. A bare but complete day sentence establishes the timer alone.
		return next.group(3).isEmpty() ? null : new Reading(daysRemaining, enough ? 0 : null);
	}
}
