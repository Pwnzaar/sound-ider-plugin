package com.soundider;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Actor;
import net.runelite.api.AmbientSoundEffect;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.AmbientSoundEffectCreated;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.AreaSoundEffectPlayed;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.SoundEffectPlayed;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.Filepath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "Sound IDer",
	internalName = "sound-ider",
	description = "Live sound ID inspector with source identification and optional sound consuming",
	tags = {"sound", "audio", "debug", "developer", "id", "npc"}
)
public class SoundIDerPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(SoundIDerPlugin.class);
	private static final String EXPORT_FILE_NAME = "events.jsonl";
	private static final int ANIMATION_SOURCE_WINDOW_CYCLES = 8;
	private static final int MAX_RECENT_ANIMATIONS = 16;

	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private ClientToolbar clientToolbar;
	@Inject private ConfigManager configManager;
	@Inject private SoundIDerConfig config;
	@Inject private Gson gson;

	private final SoundLogTableModel logModel = new SoundLogTableModel();
	private final AtomicLong exportSequence = new AtomicLong();
	private final ArrayDeque<RecentAnimation> recentAnimations = new ArrayDeque<>();
	private ExecutorService exportExecutor;
	private volatile Filepath exportFile;
	private Gson exportGson;
	private volatile boolean recording = true;
	private NavigationButton navigationButton;
	private SoundIDerPanel panel;
	private SoundIDerWindow popOutWindow;

	@Provides
	SoundIDerConfig provideConfig(ConfigManager manager)
	{
		return manager.getConfig(SoundIDerConfig.class);
	}

	@Override
	protected void startUp()
	{
		recording = true;
		exportSequence.set(0);
		recentAnimations.clear();
		exportFile = null;
		exportGson = gson.newBuilder().serializeNulls().create();
		exportExecutor = Executors.newSingleThreadExecutor(r ->
		{
			Thread thread = new Thread(r, "sound-ider-export");
			thread.setDaemon(true);
			return thread;
		});
		logModel.setDisplayOptions(config.showSource(), config.showType());
		SwingUtilities.invokeLater(() ->
		{
			panel = new SoundIDerPanel(this, config, configManager, logModel);
			navigationButton = NavigationButton.builder()
				.tooltip("Sound IDer")
				.icon(createIcon())
				.priority(6)
				.panel(panel)
				.build();
			clientToolbar.addNavigation(navigationButton);
		});
		scanLoadedAmbientSounds();
	}

	@Override
	protected void shutDown()
	{
		recording = false;
		recentAnimations.clear();
		if (exportExecutor != null)
		{
			exportExecutor.shutdownNow();
			exportExecutor = null;
		}
		exportFile = null;
		if (config.consumeAmbient())
		{
			reloadScene();
		}
		SwingUtilities.invokeLater(() ->
		{
			if (navigationButton != null) clientToolbar.removeNavigation(navigationButton);
			if (panel != null) panel.dispose();
			if (popOutWindow != null) popOutWindow.dispose();
			navigationButton = null;
			panel = null;
			popOutWindow = null;
			logModel.clear();
		});
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Actor actor = event.getActor();
		if (actor == null || actor.getName() == null || actor.getAnimation() < 0) return;

		recentAnimations.addLast(new RecentAnimation(actor, client.getGameCycle()));
		while (recentAnimations.size() > MAX_RECENT_ANIMATIONS) recentAnimations.removeFirst();
		pruneRecentAnimations();
	}

	@Subscribe
	public void onSoundEffectPlayed(SoundEffectPlayed event)
	{
		if (config.consumeEffects()) event.consume();
		if (recording && config.showEffects())
		{
			Actor source = event.getSource();
			if (source == null) source = inferAnimationSource();
			addActorSound(event.getSoundId(), SoundKind.EFFECT, source, true);
		}
	}

	@Subscribe
	public void onAreaSoundEffectPlayed(AreaSoundEffectPlayed event)
	{
		if (config.consumeArea()) event.consume();
		if (!recording || !config.showArea()) return;

		boolean audible = isAreaSoundAudible(event);
		if (audible || config.showInaudibleArea())
		{
			Actor source = event.getSource();
			if (source == null)
			{
				source = inferAreaSource(event);
			}
			addActorSound(event.getSoundId(), SoundKind.AREA, source, audible);
		}
	}

	@Subscribe
	public void onAmbientSoundEffectCreated(AmbientSoundEffectCreated event)
	{
		AmbientSoundEffect ambient = event.getAmbientSoundEffect();
		if (recording && config.showAmbient()) addAmbientSound(ambient, "Ambient");
		if (config.consumeAmbient()) clearAmbientSounds();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() != GameState.LOGGED_IN)
		{
			recentAnimations.clear();
			return;
		}
		if (recording && config.showAmbient()) scanLoadedAmbientSoundsNow();
		if (config.consumeAmbient()) clearAmbientSounds();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!SoundIDerConfig.GROUP.equals(event.getGroup())) return;
		if ("consumeAmbient".equals(event.getKey())) reloadScene();
		if ("showAmbient".equals(event.getKey()) && config.showAmbient() && recording) scanLoadedAmbientSounds();

		SwingUtilities.invokeLater(() ->
		{
			logModel.setDisplayOptions(config.showSource(), config.showType());
			if (panel != null) panel.refreshFromConfig();
			if (popOutWindow != null) popOutWindow.refreshFromConfig();
		});
	}

	boolean isRecording() { return recording; }

	void setRecording(boolean value)
	{
		recording = value;
		if (recording && config.showAmbient()) scanLoadedAmbientSounds();
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null) panel.refreshRecordingState();
			if (popOutWindow != null) popOutWindow.refreshRecordingState();
		});
	}

	void clearLog()
	{
		SwingUtilities.invokeLater(logModel::clear);
	}

	void showPopOutWindow()
	{
		SwingUtilities.invokeLater(() ->
		{
			if (popOutWindow == null) popOutWindow = new SoundIDerWindow(this, config, configManager, logModel);
			popOutWindow.setVisible(true);
			popOutWindow.toFront();
		});
	}

	private void reloadScene()
	{
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN) client.setGameState(GameState.LOADING);
		});
	}

	private boolean isAreaSoundAudible(AreaSoundEffectPlayed event)
	{
		Player player = client.getLocalPlayer();
		if (player == null) return true;
		LocalPoint point = player.getLocalLocation();
		if (point == null) return true;
		int distance = Math.abs(point.getSceneX() - event.getSceneX())
			+ Math.abs(point.getSceneY() - event.getSceneY());
		return distance <= event.getRange();
	}

	/**
	 * Lightweight fallback for area sounds where RuneLite does not provide an actor.
	 * Only returns a source when exactly one loaded NPC/player occupies the sound tile.
	 */
	private Actor inferAreaSource(AreaSoundEffectPlayed event)
	{
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null) return null;

		Actor match = null;
		for (NPC npc : worldView.npcs())
		{
			if (isOnSoundTile(npc, event))
			{
				if (match != null) return null;
				match = npc;
			}
		}

		for (Player player : worldView.players())
		{
			if (isOnSoundTile(player, event))
			{
				if (match != null) return null;
				match = player;
			}
		}

		return match;
	}

	private static boolean isOnSoundTile(Actor actor, AreaSoundEffectPlayed event)
	{
		LocalPoint point = actor.getLocalLocation();
		return point != null
			&& point.getSceneX() == event.getSceneX()
			&& point.getSceneY() == event.getSceneY();
	}

	/**
	 * Passive fallback for source-less effect sounds. It only considers actors whose
	 * animation changed in the immediately preceding client cycles. If the local
	 * player's current interaction target is among those actors, prefer it; otherwise
	 * only return a source when the recent actor is unambiguous.
	 */
	private Actor inferAnimationSource()
	{
		pruneRecentAnimations();
		if (recentAnimations.isEmpty()) return null;

		Player localPlayer = client.getLocalPlayer();
		Actor interactionTarget = localPlayer == null ? null : localPlayer.getInteracting();
		if (interactionTarget != null)
		{
			for (RecentAnimation recent : recentAnimations)
			{
				if (recent.actor == interactionTarget) return interactionTarget;
			}
		}

		Actor match = null;
		for (RecentAnimation recent : recentAnimations)
		{
			if (match == null) match = recent.actor;
			else if (match != recent.actor) return null;
		}
		return match;
	}

	private void pruneRecentAnimations()
	{
		int minimumCycle = client.getGameCycle() - ANIMATION_SOURCE_WINDOW_CYCLES;
		while (!recentAnimations.isEmpty() && recentAnimations.peekFirst().cycle < minimumCycle)
		{
			recentAnimations.removeFirst();
		}
	}

	private static final class RecentAnimation
	{
		private final Actor actor;
		private final int cycle;

		private RecentAnimation(Actor actor, int cycle)
		{
			this.actor = actor;
			this.cycle = cycle;
		}
	}

	private void addActorSound(int soundId, SoundKind kind, Actor source, boolean audible)
	{
		String sourceName;
		String sourceType;
		Integer sourceId = null;
		if (source instanceof NPC)
		{
			NPC npc = (NPC) source;
			sourceName = npc.getName();
			sourceId = npc.getId();
			sourceType = "NPC";
		}
		else if (source instanceof Player)
		{
			Player player = (Player) source;
			sourceName = player.getName();
			sourceType = player == client.getLocalPlayer() ? "Local player" : "Player";
		}
		else
		{
			sourceType = kind == SoundKind.AREA ? "Environment" : "Unknown";
			sourceName = "Unknown";
		}
		addEntry(new SoundLogEntry(System.currentTimeMillis(), soundId, kind,
			sourceName, sourceId, sourceType, audible));
	}

	private void addAmbientSound(AmbientSoundEffect ambient, String sourceName)
	{
		if (ambient == null) return;
		Set<Integer> ids = new LinkedHashSet<>();
		if (ambient.getSoundEffectId() >= 0) ids.add(ambient.getSoundEffectId());
		int[] background = ambient.getBackgroundSoundEffectIds();
		if (background != null)
		{
			for (int id : background) if (id >= 0) ids.add(id);
		}
		for (Integer id : ids)
		{
			addEntry(new SoundLogEntry(System.currentTimeMillis(), id, SoundKind.AMBIENT,
				sourceName, null, "Ambient", true));
		}
	}

	private void scanLoadedAmbientSounds()
	{
		clientThread.invokeLater(() ->
		{
			if (recording && config.showAmbient() && client.getGameState() == GameState.LOGGED_IN)
			{
				scanLoadedAmbientSoundsNow();
				if (config.consumeAmbient()) clearAmbientSounds();
			}
		});
	}

	@SuppressWarnings("deprecation")
	private void clearAmbientSounds()
	{
		client.getAmbientSoundEffects().clear();
	}

	@SuppressWarnings("deprecation")
	private void scanLoadedAmbientSoundsNow()
	{
		for (AmbientSoundEffect ambient : client.getAmbientSoundEffects())
		{
			addAmbientSound(ambient, "Ambient");
		}
	}

	private void addEntry(SoundLogEntry entry)
	{
		exportEntry(entry);
		SwingUtilities.invokeLater(() -> logModel.addEntry(entry, config.maxEntries()));
	}

	private void exportEntry(SoundLogEntry entry)
	{
		if (!config.exportJsonl() || exportExecutor == null || exportExecutor.isShutdown())
		{
			return;
		}

		final SoundExportEvent exportEvent = new SoundExportEvent(
			exportSequence.incrementAndGet(),
			entry.getTimestamp(),
			entry.getSoundId(),
			entry.getKind().getDisplayName().toUpperCase(),
			entry.getSourceName(),
			entry.getSourceId(),
			entry.getSourceType(),
			entry.isAudible());

		exportExecutor.execute(() ->
		{
			try
			{
				Filepath file = getExportFile();
				String line = exportGson.toJson(exportEvent) + System.lineSeparator();
				file.write(line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			}
			catch (Exception ex)
			{
				log.debug("Unable to export Sound IDer event", ex);
			}
		});
	}

	private Filepath getExportFile() throws Exception
	{
		Filepath file = exportFile;
		if (file != null)
		{
			return file;
		}

		Filepath directory = getPluginDirectory();
		if (!directory.exists())
		{
			directory.createDirectories();
		}
		exportFile = directory.joinSegment(EXPORT_FILE_NAME);
		return exportFile;
	}

	private static BufferedImage createIcon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(new Color(220, 138, 0));
			g.setStroke(new BasicStroke(2.0f));
			g.drawArc(2, 4, 6, 8, -70, 140);
			g.drawArc(5, 2, 8, 12, -70, 140);
			g.fillOval(1, 7, 3, 3);
		}
		finally
		{
			g.dispose();
		}
		return image;
	}
}
