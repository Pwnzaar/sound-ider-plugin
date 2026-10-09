package com.soundider;

final class SoundLogEntry
{
	private final long timestamp;
	private final int soundId;
	private final SoundKind kind;
	private final String sourceName;
	private final Integer sourceId;
	private final String sourceType;
	private final boolean audible;

	SoundLogEntry(long timestamp, int soundId, SoundKind kind, String sourceName,
		Integer sourceId, String sourceType, boolean audible)
	{
		this.timestamp = timestamp;
		this.soundId = soundId;
		this.kind = kind;
		this.sourceName = sourceName;
		this.sourceId = sourceId;
		this.sourceType = sourceType;
		this.audible = audible;
	}

	long getTimestamp() { return timestamp; }
	int getSoundId() { return soundId; }
	SoundKind getKind() { return kind; }
	String getSourceName() { return sourceName; }
	Integer getSourceId() { return sourceId; }
	String getSourceType() { return sourceType; }
	boolean isAudible() { return audible; }

	String getSourceDisplay()
	{
		String name = sourceName;
		if (name == null || name.trim().isEmpty())
		{
			name = sourceType == null || sourceType.trim().isEmpty() ? "Unknown" : sourceType;
		}
		return sourceId == null ? name : name + " [" + sourceId + "]";
	}

	String getSearchText()
	{
		StringBuilder out = new StringBuilder();
		out.append(soundId).append(' ')
			.append(kind.getShortName()).append(' ')
			.append(kind.getDisplayName()).append(' ');
		if (sourceName != null) out.append(sourceName).append(' ');
		if (sourceId != null) out.append(sourceId).append(' ');
		if (sourceType != null) out.append(sourceType).append(' ');
		if (!audible) out.append("out of range inaudible silent");
		return out.toString();
	}
}
