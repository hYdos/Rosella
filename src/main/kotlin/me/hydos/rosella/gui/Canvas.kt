package me.hydos.rosella.gui

import me.hydos.rosella.Rosella
import me.hydos.rosella.render.io.Window
import me.hydos.rosella.render.material.Material
import me.hydos.rosella.render.model.GuiRenderObject
import me.hydos.rosella.render.resource.Global
import me.hydos.rosella.render.resource.Identifier
import me.hydos.rosella.render.resource.Resource
import me.hydos.rosella.render.shader.Shader
import me.hydos.rosella.render.shader.ShaderPair
import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.vulkan.VK10

class Canvas(val rosella: Rosella, window: Window, scale: Vector2f = Vector2f(1920000f, 1080000f)) {

	private var width = rosella.getWidth().toInt()
	private var height = rosella.getHeight().toInt()
	private var oldXPixelScale = width / scale.x
	private var oldYPixelScale = height / scale.y
	private var xPixelScale = oldXPixelScale
	private var yPixelScale = oldYPixelScale

	private val scaleMagicConstant = 0.000307f

	var canvasObjects = ArrayList<String>()

	// Shaders
	private val guiShader = Identifier("rosella", "guiShader")
	private val colourGuiShader = Identifier("rosella", "colourGuiShader")

	// Materials
	private val colouredGuiMat = Identifier("rosella", "colouredGuiMaterial")

	init {
		window.onWindowResize(this::onResize)

		rosella.registerShader(
			guiShader, ShaderPair(
				Shader(Global.ensureResource(Identifier("rosella", "shaders/gui.v.glsl"))),
				Shader(Global.ensureResource(Identifier("rosella", "shaders/gui.f.glsl"))),
				rosella.device,
				rosella.memory,
				100,
				ShaderPair.PoolObjType.UBO,
				ShaderPair.PoolObjType.COMBINED_IMG_SAMPLER
			)
		)

		rosella.registerShader(
			colourGuiShader, ShaderPair(
				Shader(Global.ensureResource(Identifier("rosella", "shaders/gui.v.glsl"))),
				Shader(Global.ensureResource(Identifier("rosella", "shaders/gui.colour.f.glsl"))),
				rosella.device,
				rosella.memory,
				100,
				ShaderPair.PoolObjType.UBO
			)
		)

		rosella.registerMaterial(
			colouredGuiMat, Material(
				Resource.Empty,
				colourGuiShader,
				VK10.VK_FORMAT_R8G8B8A8_SRGB,
				false
			)
		)
	}

	private fun onResize(width: Int, height: Int) {
		this.width = width
		this.height = height
		oldXPixelScale = xPixelScale
		oldYPixelScale = yPixelScale
		xPixelScale = width / 1920f
		yPixelScale = height / 1080f


	}

	fun createGuiMaterial(texture: Identifier, textureFormat: Int, blend: Boolean): Material {
		return Material(
			Global.ensureResource(texture),
			guiShader,
			textureFormat,
			blend
		)
	}

	fun addRect(name: String, x: Int, y: Int, width: Int, height: Int, layer: Layer, colour: Vector3f) {
		addRect(name, x, y, width, height, layer, colouredGuiMat, colour)
	}

	fun addRect(
		name: String,
		x: Int,
		y: Int,
		width: Int,
		height: Int,
		layer: Layer,
		material: Identifier,
		colour: Vector3f = Vector3f(0f, 0f, 0f)
	) {
		rosella.addRenderObject(
			GuiRenderObject(
				material,
				layer.z,
				colour
			).apply {
				translate(x * xPixelScale, y * yPixelScale)
				scale(width * (xPixelScale * scaleMagicConstant), height * (yPixelScale * scaleMagicConstant))
			},
			name
		)
		canvasObjects.add(name)
	}

	fun getWidth(): Int {
		return (width / xPixelScale).toInt()
	}

	fun getHeight(): Int {
		return (height / yPixelScale).toInt()
	}
}

enum class Layer(val z: Float) {
	BACKGROUND(-0.1f),
	BACKGROUND2(-0.2f),
	BACKGROUND3(-0.3f),
	FOREGROUND1(-0.4f),
	FOREGROUND2(-0.5f),
	FOREGROUND3(-0.6f),
	OVERLAY1(-0.7f),
	OVERLAY2(-0.8f)
}
