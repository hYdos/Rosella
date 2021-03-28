package me.hydos.rosella.editor

import me.hydos.rosella.Rosella
import me.hydos.rosella.render.io.Window
import me.hydos.rosella.render.material.Material
import me.hydos.rosella.render.model.ColourGuiRenderObject
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

	//Panels
	val leftPanel = Identifier("rosella", "leftPanel")

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
				10,
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
				10,
				ShaderPair.PoolObjType.UBO,
				ShaderPair.PoolObjType.COMBINED_IMG_SAMPLER
			)
		)

		rosella.registerMaterial(
			leftPanel, Material(
				Global.ensureResource(Identifier("rosella", "textures/background/background01.png")),
				colourGuiShader,
				VK10.VK_FORMAT_R8G8B8A8_SRGB,
				false
			)
		)
		rosella.reloadMaterials()

		rosella.addRenderObject(
			ColourGuiRenderObject(
				leftPanel,
				Vector3f(41 / 255f, 41 / 255f, 41 / 255f)
			).apply {
				scale(0.3f, 0.5f)
				translate(-2f, 1.5f / rosella.renderer.swapChain.swapChainExtent.height().toFloat())
			}
		)
	}
}