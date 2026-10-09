package com.soundider;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableRowSorter;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;

final class SoundIDerView extends JPanel
{
	private static final Color RECORDING_COLOR = new Color(90, 200, 120);
	private static final Color PAUSED_COLOR = new Color(225, 170, 70);

	private final SoundIDerPlugin plugin;
	private final SoundIDerConfig config;
	private final ConfigManager configManager;
	private final SoundLogTableModel model;
	private final JLabel statusLabel = new JLabel();
	private final JButton recordButton = new JButton();
	private final JCheckBox showEffects = new JCheckBox("Effects");
	private final JCheckBox showArea = new JCheckBox("Area");
	private final JCheckBox showAmbient = new JCheckBox("Ambient");
	private final JCheckBox showInaudible = new JCheckBox("Inaudible area sounds");
	private final JCheckBox showSource = new JCheckBox("ID Source");
	private final JCheckBox showType = new JCheckBox("Show type");
	private final JCheckBox autoScroll = new JCheckBox("Auto-scroll");
	private final JCheckBox consumeEffects = new JCheckBox("Effects");
	private final JCheckBox consumeArea = new JCheckBox("Area");
	private final JCheckBox consumeAmbient = new JCheckBox("Ambient");
	private final JTextField filterField = new JTextField();
	private final JTable table = new JTable();
	private final TableRowSorter<SoundLogTableModel> sorter;
	private final TableModelListener autoScrollListener;
	private boolean refreshing;

	SoundIDerView(SoundIDerPlugin plugin, SoundIDerConfig config, ConfigManager configManager,
		SoundLogTableModel model, boolean popOut, Runnable hideAction)
	{
		this.plugin = plugin;
		this.config = config;
		this.configManager = configManager;
		this.model = model;

		setLayout(new BorderLayout(0, 6));
		setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(createControls(popOut, hideAction), BorderLayout.NORTH);

		table.setModel(model);
		table.setTableHeader(null);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setShowGrid(false);
		table.setIntercellSpacing(new Dimension(0, 0));
		table.setRowHeight(23);
		table.setDefaultRenderer(Object.class, new SoundLogCellRenderer(model));
		table.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		table.setFillsViewportHeight(true);
		sorter = new TableRowSorter<>(model);
		table.setRowSorter(sorter);

		installCopyMenu();
		installFilter();
		autoScrollListener = e ->
		{
			if (config.autoScroll())
			{
				SwingUtilities.invokeLater(() ->
				{
					int row = table.getRowCount() - 1;
					if (row >= 0) table.scrollRectToVisible(table.getCellRect(row, 0, true));
				});
			}
		};
		model.addTableModelListener(autoScrollListener);

		JScrollPane scrollPane = new JScrollPane(table);
		scrollPane.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
		add(scrollPane, BorderLayout.CENTER);
		refreshFromConfig();
		refreshRecordingState();
	}


	void dispose()
	{
		model.removeTableModelListener(autoScrollListener);
	}

	private JPanel createControls(boolean popOut, Runnable hideAction)
	{
		JPanel wrapper = new JPanel();
		wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Sound IDer");
		title.setForeground(Color.WHITE);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		wrapper.add(title);
		wrapper.add(Box.createVerticalStrut(3));
		statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		wrapper.add(statusLabel);
		wrapper.add(Box.createVerticalStrut(6));

		JPanel buttons = new JPanel(new GridLayout(1, 3, 4, 0));
		buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
		recordButton.addActionListener(e -> plugin.setRecording(!plugin.isRecording()));
		JButton clear = new JButton("Clear");
		clear.addActionListener(e -> plugin.clearLog());
		JButton pop = new JButton(popOut ? "Hide" : "Pop Out");
		pop.addActionListener(e ->
		{
			if (popOut && hideAction != null) hideAction.run();
			else plugin.showPopOutWindow();
		});
		buttons.add(recordButton);
		buttons.add(clear);
		buttons.add(pop);
		wrapper.add(buttons);
		wrapper.add(Box.createVerticalStrut(7));

		filterField.setToolTipText("Filter by sound ID, source name, NPC ID or type");
		filterField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		filterField.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel filterLabel = new JLabel("Filter");
		filterLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		wrapper.add(filterLabel);
		wrapper.add(filterField);
		wrapper.add(Box.createVerticalStrut(7));

		JPanel display = createGroupPanel("Display");
		display.add(showEffects);
		display.add(showArea);
		display.add(showAmbient);
		display.add(showInaudible);
		display.add(showSource);
		display.add(showType);
		display.add(autoScroll);
		wrapper.add(display);
		wrapper.add(Box.createVerticalStrut(5));

		JPanel consume = createGroupPanel("Consume");
		consume.add(consumeEffects);
		consume.add(consumeArea);
		consume.add(consumeAmbient);
		wrapper.add(consume);

		bind(showEffects, "showEffects");
		bind(showArea, "showArea");
		bind(showAmbient, "showAmbient");
		bind(showInaudible, "showInaudibleArea");
		bind(showSource, "showSource");
		bind(showType, "showType");
		bind(autoScroll, "autoScroll");
		bind(consumeEffects, "consumeEffects");
		bind(consumeArea, "consumeArea");
		bind(consumeAmbient, "consumeAmbient");
		return wrapper;
	}

	private JPanel createGroupPanel(String title)
	{
		JPanel panel = new JPanel(new GridLayout(0, 1));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setBorder(BorderFactory.createTitledBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR), title));
		return panel;
	}

	private void bind(JCheckBox box, String key)
	{
		box.setBackground(ColorScheme.DARK_GRAY_COLOR);
		box.addActionListener(e ->
		{
			if (!refreshing) configManager.setConfiguration(SoundIDerConfig.GROUP, key, box.isSelected());
		});
	}

	void refreshFromConfig()
	{
		refreshing = true;
		try
		{
			showEffects.setSelected(config.showEffects());
			showArea.setSelected(config.showArea());
			showAmbient.setSelected(config.showAmbient());
			showInaudible.setSelected(config.showInaudibleArea());
			showSource.setSelected(config.showSource());
			showType.setSelected(config.showType());
			autoScroll.setSelected(config.autoScroll());
			consumeEffects.setSelected(config.consumeEffects());
			consumeArea.setSelected(config.consumeArea());
			consumeAmbient.setSelected(config.consumeAmbient());
			model.setDisplayOptions(config.showSource(), config.showType());
		}
		finally
		{
			refreshing = false;
		}
	}

	void refreshRecordingState()
	{
		boolean active = plugin.isRecording();
		statusLabel.setText(active ? "● RECORDING" : "○ PAUSED");
		statusLabel.setForeground(active ? RECORDING_COLOR : PAUSED_COLOR);
		recordButton.setText(active ? "Stop" : "Start");
	}

	private void installFilter()
	{
		filterField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override public void insertUpdate(DocumentEvent e) { updateFilter(); }
			@Override public void removeUpdate(DocumentEvent e) { updateFilter(); }
			@Override public void changedUpdate(DocumentEvent e) { updateFilter(); }
		});
	}

	private void updateFilter()
	{
		String text = filterField.getText().trim().toLowerCase(Locale.ROOT);
		if (text.isEmpty())
		{
			sorter.setRowFilter(null);
			return;
		}
		sorter.setRowFilter(new RowFilter<SoundLogTableModel, Integer>()
		{
			@Override
			public boolean include(Entry<? extends SoundLogTableModel, ? extends Integer> entry)
			{
				return model.getSearchText(entry.getIdentifier()).toLowerCase(Locale.ROOT).contains(text);
			}
		});
	}

	private void installCopyMenu()
	{
		table.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		table.addMouseListener(new MouseAdapter()
		{
			@Override public void mousePressed(MouseEvent e) { maybeShowPopup(e); }
			@Override public void mouseReleased(MouseEvent e) { maybeShowPopup(e); }
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e))
				{
					SoundLogEntry entry = getEntryAt(e);
					if (entry != null) copy(Integer.toString(entry.getSoundId()));
				}
			}
		});
	}

	private void maybeShowPopup(MouseEvent e)
	{
		if (!e.isPopupTrigger()) return;
		SoundLogEntry entry = getEntryAt(e);
		if (entry == null) return;
		JPopupMenu menu = new JPopupMenu();
		JMenuItem id = new JMenuItem("Copy Sound ID");
		id.addActionListener(a -> copy(Integer.toString(entry.getSoundId())));
		menu.add(id);
		JMenuItem source = new JMenuItem("Copy Source");
		source.addActionListener(a -> copy(entry.getSourceDisplay()));
		menu.add(source);
		JMenuItem line = new JMenuItem("Copy Displayed Line");
		line.addActionListener(a -> copy(model.format(entry)));
		menu.add(line);
		JMenuItem full = new JMenuItem("Copy Full Entry");
		full.addActionListener(a -> copy(model.formatFull(entry)));
		menu.add(full);
		menu.show(table, e.getX(), e.getY());
	}

	private SoundLogEntry getEntryAt(MouseEvent e)
	{
		int viewRow = table.rowAtPoint(e.getPoint());
		if (viewRow < 0) return null;
		table.setRowSelectionInterval(viewRow, viewRow);
		return model.getEntry(table.convertRowIndexToModel(viewRow));
	}

	private void copy(String value)
	{
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(value), null);
	}
}
