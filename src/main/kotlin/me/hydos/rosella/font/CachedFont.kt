package me.hydos.rosella.font

import java.awt.Font

class CachedFont(font: Font) {

	private var allChars: String = ""
	val charMap = HashMap<Char, CharacterGlyph>()

	init {
		// Load All Glyphs in font into string for parsing
		var c = 0x0000.toChar()
		while (c.toInt() <= 64258) {
			if (font.canDisplay(c)) {
				allChars += c
			}
			c++
		}

		println(allChars)
	}
}