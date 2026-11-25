package com.togoptimizer;

import com.togoptimizer.TogOptimizerConfig.LoginResetFrequency;
import java.time.LocalDate;
import java.time.ZoneOffset;
import javax.annotation.Nullable;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.config.ConfigManager;

/** Sends at most one reset notification for a login, after the character's data has arrived. */
final class LoginReminder
{
	private static final String LAST_DAY = "lastLoginResetDay";
	private boolean pending;

	void start(GameState state)
	{
		pending = state == GameState.UNKNOWN || state == GameState.LOGGING_IN;
		onGameStateChanged(state);
	}

	void onGameStateChanged(GameState state)
	{
		// A login screen starts a new session. Hops and reconnects can go through login states too.
		if (state == GameState.LOGIN_SCREEN || state == GameState.LOGIN_SCREEN_AUTHENTICATOR
			|| state == GameState.STARTING)
		{
			pending = true;
		}
		else if (state == GameState.HOPPING || state == GameState.CONNECTION_LOST)
		{
			pending = false;
		}
	}

	@Nullable
	String check(LoginResetFrequency frequency, Client client, ConfigManager config, WeeklyReminder reminder)
	{
		if (!pending || client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null
			|| client.getOverallExperience() <= 0 || config.getRSProfileKey() == null)
		{
			return null;
		}
		long today = LocalDate.now(ZoneOffset.UTC).toEpochDay();
		Long lastDay = frequency == LoginResetFrequency.DAILY
			? config.getRSProfileConfiguration(TogOptimizerConfig.GROUP, LAST_DAY, Long.class) : null;
		if (!takeIfDue(frequency, today, lastDay))
		{
			return null;
		}
		String message = reminder.loginMessage();
		if (message != null)
		{
			config.setRSProfileConfiguration(TogOptimizerConfig.GROUP, LAST_DAY, today);
		}
		return message;
	}

	boolean takeIfDue(LoginResetFrequency frequency, long today, @Nullable Long lastDay)
	{
		if (!pending)
		{
			return false;
		}
		pending = false;
		return frequency == LoginResetFrequency.EVERY_LOGIN
			|| (frequency == LoginResetFrequency.DAILY && (lastDay == null || lastDay != today));
	}
}
