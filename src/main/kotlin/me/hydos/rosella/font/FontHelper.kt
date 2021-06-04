package me.hydos.rosella.font

import me.hydos.rosella.render.resource.Resource
import java.awt.Font

object FontHelper {

	private val FONTS = HashMap<Resource, CachedFont>()

	fun loadFont(fontFile: Resource): CachedFont? {
		if (!FONTS.containsKey(fontFile)) {
			val font = Font.createFont(0, fontFile.openStream())
			FONTS[fontFile] = CachedFont(font)
		}
		return FONTS[fontFile]
	}
}