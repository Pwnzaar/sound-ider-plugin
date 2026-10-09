package com.soundider;

final class SoundExportEvent
{
	private final long sequence;
	private final long timestamp;
	private final int soundId;
	private final String type;
	private final String source;
	private final Integer sourceId;
	private final String sourceType;
	private final boolean audible;

	SoundExportEvent(long sequence, long timestamp, int soundId, String type, String source,
		Integer sourceId, String sourceType, boolean audible)
	{
		this.sequence = sequence;
		this.timestamp = timestamp;
		this.soundId = soundId;
		this.type = type;
		this.source = source;
		this.sourceId = sourceId;
		this.sourceType = sourceType;
		this.audible = audible;
	}
}
