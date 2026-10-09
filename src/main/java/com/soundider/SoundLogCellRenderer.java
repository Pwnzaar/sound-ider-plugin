package com.soundider;

import java.awt.Color;
import java.awt.Component;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;

final class SoundLogCellRenderer extends DefaultTableCellRenderer
{
	private static final Color AREA_COLOR = new Color(235, 205, 80);
	private static final Color AMBIENT_COLOR = new Color(90, 205, 220);
	private static final Color INAUDIBLE_COLOR = new Color(145, 145, 145);
	private final SoundLogTableModel model;

	SoundLogCellRenderer(SoundLogTableModel model)
	{
		this.model = model;
		setBorder(new EmptyBorder(3, 4, 3, 4));
	}

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
		boolean hasFocus, int row, int column)
	{
		super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
		if (!isSelected && row >= 0)
		{
			SoundLogEntry entry = model.getEntry(table.convertRowIndexToModel(row));
			if (!entry.isAudible()) setForeground(INAUDIBLE_COLOR);
			else if (entry.getKind() == SoundKind.AREA) setForeground(AREA_COLOR);
			else if (entry.getKind() == SoundKind.AMBIENT) setForeground(AMBIENT_COLOR);
			else
			{
				Color c = UIManager.getColor("Table.foreground");
				setForeground(c == null ? Color.WHITE : c);
			}
		}
		return this;
	}
}
