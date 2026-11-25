package com.togoptimizer;

import static com.togoptimizer.TogOptimizerConfig.LoginResetFrequency.DAILY;
import static com.togoptimizer.TogOptimizerConfig.LoginResetFrequency.EVERY_LOGIN;
import static com.togoptimizer.TogOptimizerConfig.LoginResetFrequency.OFF;
import net.runelite.api.GameState;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class LoginReminderTest
{
	private final LoginReminder login = new LoginReminder();
	private static final long TODAY = 20_782L;

	private void signIn()
	{
		login.onGameStateChanged(GameState.LOGIN_SCREEN);
		login.onGameStateChanged(GameState.LOGGING_IN);
		login.onGameStateChanged(GameState.LOADING);
		login.onGameStateChanged(GameState.LOGGED_IN);
	}

	@Test
	public void notifiesOnEachRealLoginAndOnlyOnceWithinIt()
	{
		signIn();
		assertTrue(login.takeIfDue(EVERY_LOGIN, TODAY, null));
		assertFalse(login.takeIfDue(EVERY_LOGIN, TODAY, TODAY));
		signIn();
		assertTrue(login.takeIfDue(EVERY_LOGIN, TODAY, TODAY));
	}

	@Test
	public void skipsWorldHopsMapLoadsAndAutomaticReconnects()
	{
		signIn();
		assertTrue(login.takeIfDue(EVERY_LOGIN, TODAY, null));
		for (GameState transition : new GameState[] {GameState.HOPPING, GameState.LOADING, GameState.CONNECTION_LOST})
		{
			login.onGameStateChanged(transition);
			login.onGameStateChanged(GameState.LOGGING_IN);
			login.onGameStateChanged(GameState.LOADING);
			login.onGameStateChanged(GameState.LOGGED_IN);
			assertFalse(login.takeIfDue(EVERY_LOGIN, TODAY, TODAY));
		}
	}

	@Test
	public void dailyChoiceUsesThisCharactersSavedUtcDayAcrossRestarts()
	{
		login.start(GameState.LOGIN_SCREEN);
		assertTrue(login.takeIfDue(DAILY, TODAY, null));
		login.start(GameState.LOGGING_IN);
		assertFalse(login.takeIfDue(DAILY, TODAY, TODAY));
		assertFalse(login.takeIfDue(DAILY, TODAY + 1, TODAY));
		signIn();
		assertTrue(login.takeIfDue(DAILY, TODAY + 1, TODAY));
	}

	@Test
	public void enablingThePluginOrTheSettingDuringASessionWaitsUntilTheNextLogin()
	{
		login.start(GameState.LOGGED_IN);
		assertFalse(login.takeIfDue(EVERY_LOGIN, TODAY, null));
		signIn();
		assertFalse(login.takeIfDue(OFF, TODAY, null));
		assertFalse(login.takeIfDue(EVERY_LOGIN, TODAY, null));
		signIn();
		assertTrue(login.takeIfDue(EVERY_LOGIN, TODAY, null));
	}
}
