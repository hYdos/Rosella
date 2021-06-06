package me.hydos.rosella.render.font

import me.hydos.rosella.Rosella
import me.hydos.rosella.render.resource.Resource
import java.awt.Font

object FontHelper {

	private val FONTS = HashMap<Resource, CachedFont>()

	fun getOrLoadFont(fontFile: Resource, rosella: Rosella): CachedFont? {
		val startTime = System.currentTimeMillis()
		if (!FONTS.containsKey(fontFile)) {
			val font = Font.createFont(Font.TRUETYPE_FONT, fontFile.openStream()).deriveFont(Font.BOLD, 80f)
			FONTS[fontFile] = CachedFont(font, rosella)
		}

		println("Loaded font in " + (System.currentTimeMillis() - startTime) + " ms")
		return FONTS[fontFile]
	}
}