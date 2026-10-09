package com.soundider;

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.JFrame;
import net.runelite.client.config.ConfigManager;

final class SoundIDerWindow extends JFrame
{
	private final SoundIDerView view;

	SoundIDerWindow(SoundIDerPlugin plugin, SoundIDerConfig config, ConfigManager configManager,
		SoundLogTableModel model)
	{
		super("Sound IDer");
		setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		setMinimumSize(new Dimension(500, 450));
		setSize(new Dimension(720, 600));
		setLocationByPlatform(true);
		setLayout(new BorderLayout());
		view = new SoundIDerView(plugin, config, configManager, model, true, () -> setVisible(false));
		add(view, BorderLayout.CENTER);
	}

	void refreshFromConfig() { view.refreshFromConfig(); }
	void refreshRecordingState() { view.refreshRecordingState(); }

	@Override
	public void dispose()
	{
		view.dispose();
		super.dispose();
	}
}
