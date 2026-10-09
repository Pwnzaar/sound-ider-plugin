package com.soundider;

import java.awt.BorderLayout;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;

final class SoundIDerPanel extends PluginPanel
{
	private final SoundIDerView view;

	SoundIDerPanel(SoundIDerPlugin plugin, SoundIDerConfig config, ConfigManager configManager,
		SoundLogTableModel model)
	{
		super(false);
		setLayout(new BorderLayout());
		view = new SoundIDerView(plugin, config, configManager, model, false, null);
		add(view, BorderLayout.CENTER);
	}

	void refreshFromConfig() { view.refreshFromConfig(); }
	void refreshRecordingState() { view.refreshRecordingState(); }
	void dispose() { view.dispose(); }
}
