package com.soundider;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
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
import net.runelite.client.util.Filepath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "Sound IDer",
	internalName = "sound-ider",
	description = "Logs raw RuneLite sound events to a local JSONL file for later inspection.",
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
	@Inject private SoundIDerConfig config;
	@Inject private Gson gson;

	private final AtomicLong exportSequence = new AtomicLong();
	private final ArrayDeque<RecentAnimation> recentAnimations = new ArrayDeque<>();
	private ExecutorService exportExecutor;
	private volatile Filepath exportFile;
	private Gson exportGson;

	@Provides
	SoundIDerConfig provideConfig(ConfigManager manager)
	{
		return manager.getConfig(SoundIDerConfig.class);
	}

	@Override
	protected void startUp()
	{
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
		scanLoadedAmbientSounds();
	}

	@Override
	protected void shutDown()
	{
		recentAnimations.clear();
		if (exportExecutor != null)
		{
			exportExecutor.shutdownNow();
			exportExecutor = null;
		}
		exportFile = null;
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Actor actor = event.getActor();
		if (actor == null || actor.getName() == null || actor.getAnimation() < 0)
		{
			return;
		}

		recentAnimations.addLast(new RecentAnimation(actor, client.getGameCycle()));
		while (recentAnimations.size() > MAX_RECENT_ANIMATIONS)
		{
			recentAnimations.removeFirst();
		}
		pruneRecentAnimations();
	}

	@Subscribe
	public void onSoundEffectPlayed(SoundEffectPlayed event)
	{
		if (!config.logEffects())
		{
			return;
		}

		Actor source = event.getSource();
		if (source == null)
		{
			source = inferAnimationSource();
		}
		exportActorSound(event.getSoundId(), SoundKind.EFFECT, source, true);
	}

	@Subscribe
	public void onAreaSoundEffectPlayed(AreaSoundEffectPlayed event)
	{
		if (!config.logArea())
		{
			return;
		}

		boolean audible = isAreaSoundAudible(event);
		if (!audible && !config.logInaudibleArea())
		{
			return;
		}

		Actor source = event.getSource();
		if (source == null)
		{
			source = inferAreaSource(event);
		}
		exportActorSound(event.getSoundId(), SoundKind.AREA, source, audible);
	}

	@Subscribe
	public void onAmbientSoundEffectCreated(AmbientSoundEffectCreated event)
	{
		if (config.logAmbient())
		{
			exportAmbientSound(event.getAmbientSoundEffect(), "Ambient");
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() != GameState.LOGGED_IN)
		{
			recentAnimations.clear();
			return;
		}

		if (config.logAmbient())
		{
			scanLoadedAmbientSoundsNow();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!SoundIDerConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		if ("logAmbient".equals(event.getKey()) && config.logAmbient())
		{
			scanLoadedAmbientSounds();
		}
	}

	private boolean isAreaSoundAudible(AreaSoundEffectPlayed event)
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return true;
		}

		LocalPoint point = player.getLocalLocation();
		if (point == null)
		{
			return true;
		}

		int distance = Math.abs(point.getSceneX() - event.getSceneX())
			+ Math.abs(point.getSceneY() - event.getSceneY());
		return distance <= event.getRange();
	}

	private Actor inferAreaSource(AreaSoundEffectPlayed event)
	{
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return null;
		}

		Actor match = null;
		for (NPC npc : worldView.npcs())
		{
			if (isOnSoundTile(npc, event))
			{
				if (match != null)
				{
					return null;
				}
				match = npc;
			}
		}

		for (Player player : worldView.players())
		{
			if (isOnSoundTile(player, event))
			{
				if (match != null)
				{
					return null;
				}
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

	private Actor inferAnimationSource()
	{
		pruneRecentAnimations();
		if (recentAnimations.isEmpty())
		{
			return null;
		}

		Player localPlayer = client.getLocalPlayer();
		Actor interactionTarget = localPlayer == null ? null : localPlayer.getInteracting();
		if (interactionTarget != null)
		{
			for (RecentAnimation recent : recentAnimations)
			{
				if (recent.actor == interactionTarget)
				{
					return interactionTarget;
				}
			}
		}

		Actor match = null;
		for (RecentAnimation recent : recentAnimations)
		{
			if (match == null)
			{
				match = recent.actor;
			}
			else if (match != recent.actor)
			{
				return null;
			}
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

	private void exportActorSound(int soundId, SoundKind kind, Actor source, boolean audible)
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

		exportEntry(new SoundLogEntry(
			System.currentTimeMillis(),
			soundId,
			kind,
			sourceName,
			sourceId,
			sourceType,
			audible));
	}

	private void exportAmbientSound(AmbientSoundEffect ambient, String sourceName)
	{
		if (ambient == null)
		{
			return;
		}

		Set<Integer> ids = new LinkedHashSet<>();
		if (ambient.getSoundEffectId() >= 0)
		{
			ids.add(ambient.getSoundEffectId());
		}

		int[] background = ambient.getBackgroundSoundEffectIds();
		if (background != null)
		{
			for (int id : background)
			{
				if (id >= 0)
				{
					ids.add(id);
				}
			}
		}

		for (Integer id : ids)
		{
			exportEntry(new SoundLogEntry(
				System.currentTimeMillis(),
				id,
				SoundKind.AMBIENT,
				sourceName,
				null,
				"Ambient",
				true));
		}
	}

	private void scanLoadedAmbientSounds()
	{
		clientThread.invokeLater(() ->
		{
			if (config.logAmbient() && client.getGameState() == GameState.LOGGED_IN)
			{
				scanLoadedAmbientSoundsNow();
			}
		});
	}

	@SuppressWarnings("deprecation")
	private void scanLoadedAmbientSoundsNow()
	{
		for (AmbientSoundEffect ambient : client.getAmbientSoundEffects())
		{
			exportAmbientSound(ambient, "Ambient");
		}
	}

	private void exportEntry(SoundLogEntry entry)
	{
		ExecutorService executor = exportExecutor;
		if (executor == null || executor.isShutdown())
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

		executor.execute(() ->
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
}
