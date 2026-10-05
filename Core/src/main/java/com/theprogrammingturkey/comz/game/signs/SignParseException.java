package com.theprogrammingturkey.comz.game.signs;

/**
 * Thrown when the lines typed onto a new game sign are invalid. Holds the error text to show on the sign.
 */
public class SignParseException extends Exception
{
	private final String[] lines;

	public SignParseException(String... lines)
	{
		super(String.join(" ", lines));
		this.lines = new String[4];
		for(int i = 0; i < 4; i++)
			this.lines[i] = i < lines.length ? lines[i] : "";
	}

	public String[] getLines()
	{
		return lines;
	}
}
