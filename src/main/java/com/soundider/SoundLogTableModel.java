package com.soundider;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;

final class SoundLogTableModel extends AbstractTableModel
{
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
		.withZone(ZoneId.systemDefault());

	private final List<SoundLogEntry> entries = new ArrayList<>();
	private boolean showSource;
	private boolean showType;

	@Override
	public int getRowCount() { return entries.size(); }

	@Override
	public int getColumnCount() { return 1; }

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) { return format(entries.get(rowIndex)); }

	@Override
	public String getColumnName(int column) { return "Sound"; }

	SoundLogEntry getEntry(int row) { return entries.get(row); }

	void addEntry(SoundLogEntry entry, int maxEntries)
	{
		entries.add(entry);
		int limit = Math.max(100, maxEntries);
		while (entries.size() > limit)
		{
			entries.remove(0);
		}
		fireTableDataChanged();
	}

	void clear()
	{
		entries.clear();
		fireTableDataChanged();
	}

	void setDisplayOptions(boolean showSource, boolean showType)
	{
		this.showSource = showSource;
		this.showType = showType;
		fireTableDataChanged();
	}

	String format(SoundLogEntry entry)
	{
		StringBuilder out = new StringBuilder();
		if (showType)
		{
			out.append('[').append(entry.getKind().getShortName()).append("] ");
		}
		out.append(entry.getSoundId());
		if (showSource)
		{
			out.append(" | ").append(entry.getSourceDisplay());
			if (!entry.isAudible()) out.append(" | OUT OF RANGE");
		}
		return out.toString();
	}

	String formatFull(SoundLogEntry entry)
	{
		return TIME_FORMAT.format(Instant.ofEpochMilli(entry.getTimestamp()))
			+ " | " + entry.getKind().getDisplayName()
			+ " | " + entry.getSoundId()
			+ " | " + entry.getSourceDisplay()
			+ (entry.isAudible() ? "" : " | OUT OF RANGE");
	}

	String getSearchText(int row) { return entries.get(row).getSearchText(); }
}
