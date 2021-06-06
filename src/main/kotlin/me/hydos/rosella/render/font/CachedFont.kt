package me.hydos.rosella.render.font

import me.hydos.rosella.Rosella
import me.hydos.rosella.render.material.Material
import me.hydos.rosella.render.model.StringRenderObject
import me.hydos.rosella.render.resource.Global
import me.hydos.rosella.render.resource.Identifier
import me.hydos.rosella.render.shader.RawShaderProgram
import org.joml.Vector3f
import org.lwjgl.vulkan.VK10
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import kotlin.math.ceil


class CachedFont(font: Font, rosella: Rosella) {

	private val fontShader = Identifier("rosella", "font_shader")
	private val fontMaterial = Identifier("rosella", "font_texture")

	var allChars = ""
	var allCharsLength = 0f
	val charMap = HashMap<Char, GlyphInfo>()
	private val fontImage: BufferedImage

	init {
		var c = 0x0000.toChar()
		while (c < Char.MAX_VALUE) {
			if (font.canDisplay(c)) {
				allChars += c
			}
			c++
		}

		fontImage = toImage(font)
/*		val outputfile = File("image.png")
		outputfile.createNewFile()
		ImageIO.write(fontImage, "png", outputfile)*/

		rosella.registerShader(
			fontShader, RawShaderProgram(
				Global.ensureResource(Identifier("rosella", "shaders/gui.v.glsl")),
				Global.ensureResource(Identifier("rosella", "shaders/gui.f.glsl")),
				rosella.device,
				rosella.memory,
				99999,
				RawShaderProgram.PoolObjType.UBO,
				RawShaderProgram.PoolObjType.COMBINED_IMG_SAMPLER
			)
		)

		// Load All Glyphs in font into string for parsing
		var xOffset = 0f

		val image = BufferedImage(1, 1, BufferedImage.TYPE_4BYTE_ABGR)
		val g = image.graphics
		g.font = font
		val context = g.fontMetrics.fontRenderContext
		c = 0x0000.toChar()

		rosella.registerMaterial(
			fontMaterial, Material(
				Global.fromBufferedImage(fontImage, fontMaterial),
				fontShader,
				VK10.VK_FORMAT_R8G8B8A8_UNORM,
				false
			)
		)

		while (c < Char.MAX_VALUE) {
			if (font.canDisplay(c)) {
				val rect = font.getStringBounds(c.toString(), context)
				charMap[c] = GlyphInfo(xOffset, rect.width.toFloat())
				xOffset += rect.width.toFloat()
			}
			c++
		}
		g.dispose()
	}

	private fun toImage(font: Font): BufferedImage {
		var image = BufferedImage(1, 1, BufferedImage.TYPE_4BYTE_ABGR)
		var g = image.graphics
		g.font = font
		val context = g.fontMetrics.fontRenderContext
		val rect = font.getStringBounds(allChars, context)
		allCharsLength = rect.width.toFloat()
		g.dispose()

		image = BufferedImage(
			ceil(rect.width).toInt(),
			ceil(rect.height).toInt(), BufferedImage.TYPE_4BYTE_ABGR
		)
		g = image.graphics
		g.color = Color.black

		g.font = font

		val fm = g.fontMetrics

		g.drawString(allChars, 0, fm.ascent)
		g.dispose()
		return image
	}

	fun createString(
		string: String,
		colour: Vector3f,
		z: Float,
		scale: Float,
		translateX: Float,
		translateZ: Float
	): StringRenderObject {
		return StringRenderObject(fontMaterial, this, string, z, colour, scale, scale, translateX, translateZ)
	}
}