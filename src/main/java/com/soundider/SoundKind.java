package com.soundider;

enum SoundKind
{
	EFFECT("E", "Effect"),
	AREA("A", "Area"),
	AMBIENT("M", "Ambient");

	private final String shortName;
	private final String displayName;

	SoundKind(String shortName, String displayName)
	{
		this.shortName = shortName;
		this.displayName = displayName;
	}

	String getShortName()
	{
		return shortName;
	}

	String getDisplayName()
	{
		return displayName;
	}
}
