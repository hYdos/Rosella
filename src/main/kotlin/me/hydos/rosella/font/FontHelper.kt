package me.hydos.rosella.font

import me.hydos.rosella.render.resource.Resource
import java.awt.Font

object FontHelper {

	private val FONTS = HashMap<Resource, CachedFont>()

	fun loadFont(fontFile: Resource): CachedFont? {
		val startTime = System.currentTimeMillis()
		if (!FONTS.containsKey(fontFile)) {
			val font = Font.createFont(0, fontFile.openStream())
			FONTS[fontFile] = CachedFont(font)
		}
		println("Loaded font in " + (System.currentTimeMillis() - startTime) + " ms")
		return FONTS[fontFile]
	}
}