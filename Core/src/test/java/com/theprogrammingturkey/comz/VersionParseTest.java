package com.theprogrammingturkey.comz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link COMZombies#extractVersion(String)} without a running server. */
class VersionParseTest
{
	@Test
	void extractsVersionFromRealPurpurString()
	{
		String real = "26.3-2647-4cd2cf6 (MC: 26.3)";
		assertEquals("26.3", COMZombies.extractVersion(real));
	}

	@Test
	void extractsVersionFromCraftBukkitString()
	{
		assertEquals("1.21.1", COMZombies.extractVersion("git-CraftBukkit-35 (MC: 1.21.1)"));
	}

	@Test
	void returnsNullWhenNoMcSegment()
	{
		assertNull(COMZombies.extractVersion("26.3-2647"));
	}

	@Test
	void returnsNullForNullInput()
	{
		assertNull(COMZombies.extractVersion(null));
	}

	@Test
	void doesNotCaptureTrailingParenthesis()
	{
		// Regression: a capture group that included ")" produced "26.3)" and failed to parse.
		String v = COMZombies.extractVersion("(MC: 26.3)");
		assertEquals("26.3", v);
	}
}
