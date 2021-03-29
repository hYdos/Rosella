package me.hydos.rosella.editor

import me.hydos.rosella.Rosella
import me.hydos.rosella.render.io.Window
import me.hydos.rosella.render.material.Material
import me.hydos.rosella.render.model.ColourGuiRenderObject
import me.hydos.rosella.render.resource.Global
import me.hydos.rosella.render.resource.Identifier
import me.hydos.rosella.render.resource.Resource
import me.hydos.rosella.render.shader.Shader
import me.hydos.rosella.render.shader.ShaderPair
import org.joml.Vector3f
import org.lwjgl.vulkan.VK10

object Editor {

	private val window: Window = Window("Rosella Scene Editor", 1920, 1080, true)
	private val rosella: Rosella = Rosella("sceneEditor", false, window)

	private val guiShader = Identifier("rosella", "guiShader")
	private val colourGuiShader = Identifier("rosella", "colourGuiShader")

	val panel = Identifier("rosella", "behindPanel")

	// Icons
	val folder = Identifier("rosella", "folder")

	@JvmStatic
	fun main(args: Array<String>) {
		createGui()
		rosella.renderer.rebuildCommandBuffers(rosella.renderer.renderPass, rosella)
		window.onMainLoop {
			rosella.renderer.render(rosella)
		}
		window.start(rosella)
	}

	private fun createGui() {
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
			panel, Material(
				Resource.Empty,
				colourGuiShader,
				VK10.VK_FORMAT_R8G8B8A8_SRGB,
				false
			)
		)

		rosella.registerMaterial(
			folder, Material(
				Global.ensureResource(Identifier("rosella", "editor/gui/folder.png")),
				guiShader,
				VK10.VK_FORMAT_R8G8B8A8_SRGB,
				true
			)
		)
		rosella.reloadMaterials()

		addSidePanel(1.63f, -0.33f) // Left Panel
		addWidePanel(-0.133f, 1f) // Bottom Panel

//		// Render Folders
//		var y = 0
//		while (y <= 2) {
//			var x = 0
//			while (x <= 13) {
//				addFolder(-(7.4f + 1.33f * x), 2.88f + y * 1.6f)
//				x++
//			}
//			y++
//		}
	}

//	private fun addFolder(x: Float, y: Float) {
//		rosella.addRenderObject(
//			GuiRenderObject(
//				folder,
//				-0.8f
//			).apply {
//				scale(0.03f, 0.02f)
//				translate(
//					0.75f / rosella.getWidth() + x,
//					0.75f / rosella.getHeight() + y
//				)
//			}
//		)
//	}

	private fun addSidePanel(x: Float, y: Float) {
		rosella.addRenderObject(
			ColourGuiRenderObject(
				panel,
				Vector3f(60f / 255, 63f / 255, 65f / 255)
			).apply {
				scale(0.36f, rosella.getHeight())
				translate(2f / rosella.getWidth() + x, 1.5f / rosella.getHeight() + y)
			}
		)
	}

	private fun addWidePanel(x: Float, y: Float) {
		rosella.addRenderObject(
			ColourGuiRenderObject(
				panel,
				Vector3f(55f / 255, 58f / 255, 60f / 255)
			).apply {
				scale(1.2f, 0.28f)
				translate(1.2f / rosella.getWidth() + x, 1.5f / rosella.getHeight() + y)
			}
		)
	}
}