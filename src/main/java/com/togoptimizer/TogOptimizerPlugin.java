package com.togoptimizer;

import com.google.inject.Provides;
import java.util.Arrays;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.api.widgets.Widget;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Tears of Guthix Optimizer",
	description = "ToG: highlights the best weeping wall to collect from, and when to wait for a blue stream",
	tags = {"tog", "tears", "guthix", "tears of guthix", "weeping wall", "juna", "minigame"}
)
public class TogOptimizerPlugin extends Plugin
{
	static final int TOG_REGION = 12948;
	// Floor tiles in front of the walls, where the minigame is played
	private static final int ROOM_MIN_X = 3257;
	private static final int ROOM_MAX_X = 3260;
	private static final int ROOM_MIN_Y = 9515;
	private static final int ROOM_MAX_Y = 9519;
	// Where players appear when they enter the wall room, seen for everyone who entered while watched
	private static final int ENTRANCE_X = 3257;
	private static final int ENTRANCE_Y = 9517;
	static final int HORIZON = 40;
	private static final int ROLLOUTS = 200;
	// Ticks from a stream leaving to the player clicking; a real game measured one to three
	private static final int REACTION = 2;

	@Inject
	private Client client;

	@Inject
	private TogOptimizerConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private WallOverlay wallOverlay;

	@Inject
	private JunaOverlay junaOverlay;

	@Inject
	private ContinueOverlay continueOverlay;

	@Inject
	private ConfigManager configManager;

	// Per-account records of when the next game unlocks and the experience when the last one ended
	private static final String UNLOCK_AT_KEY = "unlockAt";
	private static final String XP_AT_LAST_GAME_KEY = "xpAtLastGame";
	private int lastTears;
	// The reward experience lands as a game ends, so it's recorded a few ticks later
	private int recordXpTick = -1;
	private String lastDialogue = "";
	// Whether Juna's last line, the one that lets the player in, is on screen
	@Getter
	private boolean junasLastLine;

	@Getter
	private final StreamTracker tracker = new StreamTracker();
	private final Planner planner = new Planner(ROLLOUTS, HORIZON);

	// The latest estimate for the player, or for the preview player while outside the wall room
	@Getter
	@Nullable
	private Planner.Plan plan;
	@Nullable
	private PreviewRun preview;
	// The wall to highlight, which only changes when another is clearly better
	@Getter
	private int shownBest = -1;

	// The wall the player last chose to collect from, or -1
	private int collectingWall = -1;
	// The tick the player stepped into the wall room, when their time started, or -1
	private int gameStartTick = -1;

	// The stream object currently showing on each wall, for outlining
	private final DecorativeObject[] wallObjects = new DecorativeObject[Walls.COUNT];
	// The permanent weeping wall behind each stream, outlined while waiting at an empty wall
	private final TileObject[] baseWalls = new TileObject[Walls.COUNT];
	// Ticks left to wait for a blue to land on the player's empty wall, or -1 if not waiting
	@Getter
	private int waitTicks = -1;

	// Juna is scenery rather than an NPC
	@Getter
	@Nullable
	private GameObject juna;

	@Provides
	TogOptimizerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TogOptimizerConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(wallOverlay);
		overlayManager.add(junaOverlay);
		overlayManager.add(continueOverlay);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(wallOverlay);
		overlayManager.remove(junaOverlay);
		overlayManager.remove(continueOverlay);
		tracker.reset();
		Arrays.fill(wallObjects, null);
		Arrays.fill(baseWalls, null);
		preview = null;
		plan = null;
		shownBest = -1;
		waitTicks = -1;
		collectingWall = -1;
		juna = null;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOADING || state == GameState.HOPPING || state == GameState.LOGIN_SCREEN)
		{
			// The streams are sent again after a load, with no way to tell how long they have left
			tracker.reset();
			preview = null;
			Arrays.fill(wallObjects, null);
			Arrays.fill(baseWalls, null);
			plan = null;
			shownBest = -1;
			waitTicks = -1;
			collectingWall = -1;
			gameStartTick = -1;
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		if (event.getGameObject().getId() == ObjectID.TOG_JUNA)
		{
			juna = event.getGameObject();
		}
		baseWall(event.getGameObject(), true);
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		if (event.getGameObject() == juna)
		{
			juna = null;
		}
		baseWall(event.getGameObject(), false);
	}

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned event)
	{
		baseWall(event.getGroundObject(), true);
	}

	@Subscribe
	public void onGroundObjectDespawned(GroundObjectDespawned event)
	{
		baseWall(event.getGroundObject(), false);
	}

	@Subscribe
	public void onWallObjectSpawned(WallObjectSpawned event)
	{
		baseWall(event.getWallObject(), true);
	}

	@Subscribe
	public void onWallObjectDespawned(WallObjectDespawned event)
	{
		baseWall(event.getWallObject(), false);
	}

	private void baseWall(TileObject object, boolean spawned)
	{
		if (object.getId() != ObjectID.TOG_WEEPINGWALL)
		{
			return;
		}
		int wall = Walls.at(object.getWorldLocation().getX(), object.getWorldLocation().getY());
		if (wall < 0)
		{
			return;
		}
		if (spawned)
		{
			baseWalls[wall] = object;
		}
		else if (baseWalls[wall] == object)
		{
			baseWalls[wall] = null;
		}
	}

	@Subscribe
	public void onDecorativeObjectSpawned(DecorativeObjectSpawned event)
	{
		wallObject(event.getDecorativeObject(), true);
	}

	@Subscribe
	public void onDecorativeObjectDespawned(DecorativeObjectDespawned event)
	{
		wallObject(event.getDecorativeObject(), false);
	}

	private void wallObject(DecorativeObject object, boolean spawned)
	{
		baseWall(object, spawned);
		int colour = colourOf(object.getId());
		if (colour == Integer.MIN_VALUE)
		{
			return;
		}
		WorldPoint location = object.getWorldLocation();
		int wall = Walls.at(location.getX(), location.getY());
		if (wall < 0)
		{
			return;
		}

		int tick = client.getTickCount();
		if (spawned)
		{
			wallObjects[wall] = object;
			tracker.wallChanged(wall, colour, tick);
		}
		if (!spawned && wallObjects[wall] == object)
		{
			wallObjects[wall] = null;
		}
		if (!spawned && colour != TearsGame.NONE && tracker.colour(wall) == colour)
		{
			// A stream's object leaving, in case the empty wall isn't sent as its own object
			tracker.wallChanged(wall, TearsGame.NONE, tick);
		}

		if (config.debugLogging())
		{
			log.info("togoptimizer wall tick={} wall={} object={} {} colour={} lastLife={} order={} complete={}",
				tick, wall, object.getId(), spawned ? "spawned" : "despawned", colour, tracker.getLastLife(),
				tracker.getOrder(), tracker.isComplete());
		}
	}

	/**
	 * @return the colour a weeping wall object shows, or MIN_VALUE if it isn't one
	 */
	private static int colourOf(int objectId)
	{
		switch (objectId)
		{
			case ObjectID.TOG_WEEPING_WALL_GOOD_R:
			case ObjectID.TOG_WEEPING_WALL_GOOD_L:
				return TearsGame.BLUE;
			case ObjectID.TOG_WEEPING_WALL_BAD_R:
			case ObjectID.TOG_WEEPING_WALL_BAD_L:
				return TearsGame.GREEN;
			case ObjectID.TOG_WEEPING_WALL_OFF_R:
			case ObjectID.TOG_WEEPING_WALL_OFF_L:
				return TearsGame.NONE;
			default:
				return Integer.MIN_VALUE;
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!inCave())
		{
			return;
		}
		int wall = -1;
		if ("Collect-from".equals(event.getMenuOption()))
		{
			WorldPoint point = WorldPoint.fromScene(client.getTopLevelWorldView(), event.getParam0(), event.getParam1(),
				client.getTopLevelWorldView().getPlane());
			wall = Walls.at(point.getX(), point.getY());
		}
		// Any other click walks the player away from the wall
		collectingWall = wall;

		if (config.debugLogging())
		{
			log.info("togoptimizer click tick={} option={} target={} id={} wall={}", client.getTickCount(),
				event.getMenuOption(), event.getMenuTarget(), event.getId(), wall);
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarbitId() == VarbitID.TOG_TEARS_COLLECTED)
		{
			// The count resets when the game ends and the reward is given
			if (event.getValue() == 0 && lastTears > 0)
			{
				gameEnded();
			}
			lastTears = event.getValue();
		}

		// Logged so a real game shows which of these track time left, collecting, and tears
		if (config.debugLogging() && isTogVariable(event))
		{
			log.info("togoptimizer var tick={} varbit={} varp={} value={}", client.getTickCount(), event.getVarbitId(),
				event.getVarpId(), event.getValue());
		}
	}

	private static boolean isTogVariable(VarbitChanged event)
	{
		int varbit = event.getVarbitId();
		return varbit >= VarbitID.TOG_JUNA_BOWL && varbit <= VarbitID.TOG_QP_BEFORE_RETURN
			|| event.getVarpId() == VarPlayerID.TOG_MINIGAME;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (recordXpTick >= 0 && client.getTickCount() >= recordXpTick)
		{
			recordXpTick = -1;
			configManager.setRSProfileConfiguration(TogOptimizerConfig.GROUP, XP_AT_LAST_GAME_KEY, client.getOverallExperience());
		}


		if (!inCave())
		{
			plan = null;
			shownBest = -1;
			waitTicks = -1;
			preview = null;
			return;
		}

		readDialogue();

		int tick = client.getTickCount();
		if (!tracker.isComplete())
		{
			plan = null;
			shownBest = -1;
			waitTicks = -1;
			return;
		}

		Player player = client.getLocalPlayer();
		WorldPoint location = player.getWorldLocation();
		if (!inRoom(location))
		{
			gameStartTick = -1;
			if (config.onlyInRoom())
			{
				preview = null;
				plan = null;
				shownBest = -1;
				waitTicks = -1;
			}
			else
			{
				previewTick(tick);
			}
			return;
		}
		preview = null;
		int questPoints = client.getVarpValue(VarPlayerID.QP);
		if (gameStartTick < 0)
		{
			gameStartTick = tick;
			if (config.debugLogging())
			{
				log.info("togoptimizer game start tick={} questPoints={}", tick, questPoints);
			}
		}

		TearsGame game = new TearsGame();
		tracker.fill(game);
		game.tick = tick;
		// The minigame gives one tick per quest point
		game.endTick = gameStartTick + questPoints;
		game.x = location.getX();
		game.y = location.getY();
		game.target = collectingWall;
		game.reaction = REACTION;
		boolean atWall = collectingWall >= 0 && game.x == Walls.standX(collectingWall) && game.y == Walls.standY(collectingWall);
		game.readyTick = atWall || collectingWall < 0 ? tick : tick + Walls.travel(game.x, game.y, collectingWall) + TearsGame.START_DELAY;

		// The same futures every tick, so the estimates don't jitter from one tick to the next
		plan = planner.plan(game, 0);
		shownBest = Planner.steady(shownBest, plan, Planner.STEADY_MARGIN);
		waitTicks = Planner.waitTicksFor(game, shownBest);

		if (config.debugLogging())
		{
			log.info("togoptimizer player tick={} at={},{} collecting={} tears={} best={} expected={}", tick, game.x,
				game.y, collectingWall, client.getVarbitValue(VarbitID.TOG_TEARS_COLLECTED), plan.getBest(),
				describe(plan));
		}
	}

	private void previewTick(int tick)
	{
		if (preview == null || preview.isOver())
		{
			int questPoints = client.getVarpValue(VarPlayerID.QP);
			preview = new PreviewRun(tick, Math.max(questPoints, HORIZON), ENTRANCE_X, ENTRANCE_Y, REACTION, planner, tick);
		}
		preview.tick(tracker, tick);
		plan = preview.getPlan();
		shownBest = preview.getShown();
		waitTicks = preview.getWaitTicks();
	}

	private void gameEnded()
	{
		long now = System.currentTimeMillis();
		configManager.setRSProfileConfiguration(TogOptimizerConfig.GROUP, UNLOCK_AT_KEY, Eligibility.unlockAfterGame(now));
		recordXpTick = client.getTickCount() + 3;
		if (config.debugLogging())
		{
			log.info("togoptimizer game ended tick={} unlockAt={}", client.getTickCount(), Eligibility.unlockAfterGame(now));
		}
	}

	/**
	 * Watches dialogue for Juna saying how many days until the player can play again.
	 */
	private void readDialogue()
	{
		String text = dialogueText(InterfaceID.ChatLeft.TEXT);
		if (text == null)
		{
			text = dialogueText(InterfaceID.Messagebox.TEXT);
		}
		if (text == null)
		{
			text = dialogueText(InterfaceID.Objectbox.TEXT);
		}
		junasLastLine = text != null && text.contains(StoryTiming.LAST_LINE);
		if (text == null || text.equals(lastDialogue))
		{
			return;
		}
		lastDialogue = text;
		if (config.debugLogging())
		{
			log.info("togoptimizer dialogue tick={} text={}", client.getTickCount(), text);
		}
		Integer days = Eligibility.daysFromJuna(text);
		if (days != null)
		{
			configManager.setRSProfileConfiguration(TogOptimizerConfig.GROUP, UNLOCK_AT_KEY,
				Eligibility.unlockInDays(days, System.currentTimeMillis()));
		}
	}

	@Nullable
	private String dialogueText(int widgetId)
	{
		Widget widget = client.getWidget(widgetId);
		if (widget == null || widget.isHidden() || widget.getText() == null)
		{
			return null;
		}
		return Text.removeTags(widget.getText().replace("<br>", " "));
	}

	/**
	 * @return when this account can next play, or null if unknown or already possible
	 */
	@Nullable
	Long unlockAt()
	{
		Long unlock = configManager.getRSProfileConfiguration(TogOptimizerConfig.GROUP, UNLOCK_AT_KEY, Long.class);
		return unlock == null || unlock <= System.currentTimeMillis() ? null : unlock;
	}

	/**
	 * @return the quest point or experience still needed to play again, or null if met
	 */
	@Nullable
	String requirementText()
	{
		Long xpAtLastGame = configManager.getRSProfileConfiguration(TogOptimizerConfig.GROUP, XP_AT_LAST_GAME_KEY, Long.class);
		return Eligibility.requirement(client.getVarbitValue(VarbitID.TOG_QP_BEFORE_RETURN),
			client.getVarpValue(VarPlayerID.QP), client.getOverallExperience(), xpAtLastGame);
	}

	private static String describe(@Nullable Planner.Plan plan)
	{
		if (plan == null)
		{
			return "-";
		}
		StringBuilder sb = new StringBuilder();
		double[] expected = plan.getExpected();
		for (int wall = 0; wall < expected.length; wall++)
		{
			if (!Double.isNaN(expected[wall]))
			{
				sb.append(wall).append('=').append(String.format("%.1f", expected[wall])).append(' ');
			}
		}
		return sb.toString().trim();
	}

	@Nullable
	DecorativeObject wallObject(int wall)
	{
		return wallObjects[wall];
	}

	@Nullable
	TileObject baseWall(int wall)
	{
		return baseWalls[wall];
	}

	boolean inCave()
	{
		if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
		{
			return false;
		}
		return client.getLocalPlayer().getWorldLocation().getRegionID() == TOG_REGION;
	}

	boolean playerInRoom()
	{
		return client.getLocalPlayer() != null && inRoom(client.getLocalPlayer().getWorldLocation());
	}

	/**
	 * Only the blue streams' timings are needed, so this is known as soon as the blues have each moved
	 * once.
	 *
	 * @return 0 if now is a good time to continue past Juna's last line, the ticks to wait otherwise, or
	 * -1 if unknown
	 */
	int ticksUntilContinue()
	{
		if (!StreamTracker.OPTIMAL_ORDER.equals(worldOrder()))
		{
			return -1;
		}
		int[] colour = new int[Walls.COUNT];
		int[] nextMove = new int[Walls.COUNT];
		for (int wall = 0; wall < Walls.COUNT; wall++)
		{
			colour[wall] = tracker.colour(wall);
			nextMove[wall] = tracker.nextMove(wall);
			if (colour[wall] == TearsGame.BLUE && nextMove[wall] < 0)
			{
				return -1;
			}
		}
		int firstBlue = StoryTiming.firstBlueMove(colour, nextMove);
		return firstBlue < 0 ? -1 : StoryTiming.ticksUntilContinue(firstBlue, client.getTickCount());
	}

	private static boolean inRoom(WorldPoint location)
	{
		return location.getX() >= ROOM_MIN_X && location.getX() <= ROOM_MAX_X
			&& location.getY() >= ROOM_MIN_Y && location.getY() <= ROOM_MAX_Y;
	}

	/**
	 * @return this world's stream order once the streams have been watched for a cycle, or null
	 */
	@Nullable
	String worldOrder()
	{
		return tracker.getOrder();
	}
}
