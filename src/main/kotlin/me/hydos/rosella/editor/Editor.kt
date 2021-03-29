package me.hydos.rosella.editor

import me.hydos.rosella.Rosella
import me.hydos.rosella.render.io.Window
import me.hydos.rosella.render.material.Material
import me.hydos.rosella.render.model.ColourGuiRenderObject
import me.hydos.rosella.render.model.GuiRenderObject
import me.hydos.rosella.render.resource.Global
import me.hydos.rosella.render.resource.Identifier
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
				ShaderPair.PoolObjType.UBO,
				ShaderPair.PoolObjType.COMBINED_IMG_SAMPLER
			)
		)

		rosella.registerMaterial(
			panel, Material(
				Global.ensureResource(Identifier("example", "textures/background/background01.png")), //TODO: make this part not be needed
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

		// Left Panel
		rosella.addRenderObject(
			ColourGuiRenderObject(
				panel,
				Vector3f(41 / 255f, 41 / 255f, 41 / 255f)
			).apply {
				scale(0.3f, 0.5f)
				translate(2.2f / rosella.getWidth() - 2.2f, 1.5f / rosella.getHeight() - 0.33f)
			}
		)

		// Bottom Panel
		rosella.addRenderObject(
			ColourGuiRenderObject(
				panel,
				Vector3f(51 / 255f, 51 / 255f, 51 / 255f)
			).apply {
				scale(2f, 0.28f)
				translate(2.2f / rosella.getWidth() - 0.001f, 1.5f / rosella.getHeight() + 0.8f)
			}
		)

		var y = 0
		while(y <= 2) {
			var x = 0
			while(x <= 13) {
				rosella.addRenderObject(
					GuiRenderObject(
						folder,
						-0.8f
					).apply {
						scale(0.05f, 0.04f)
						translate(1.5f / rosella.getWidth() - 7.4f + 1.33f * x, 1.5f / rosella.getHeight() + 2.88f + y * 1.6f)
					}
				)
				x++
			}
			y++
		}
	}
}