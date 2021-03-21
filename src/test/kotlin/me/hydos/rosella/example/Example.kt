package me.hydos.rosella.example

import me.hydos.rosella.Rosella
import me.hydos.rosella.audio.playback
import me.hydos.rosella.io.Window
import me.hydos.rosella.material.Material
import me.hydos.rosella.model.GuiRenderObject
import me.hydos.rosella.resource.Global
import me.hydos.rosella.resource.Identifier
import me.hydos.rosella.shader.Shader
import me.hydos.rosella.shader.ShaderPair

object Example {

	val screen = Window("Rosella Engine", 1280, 720)
	val engine = Rosella("Portal2 in Rosella", false, screen)

	val menuBackground = Identifier("rosella", "menu_background")
	val portalLogo = Identifier("rosella", "portal_logo")

	val basicShader = Identifier("rosella", "example_shader")
	val guiShader = Identifier("rosella", "gui_shader")

	@JvmStatic
	fun main(args: Array<String>) {
		loadShaders()
		loadMaterials()
		setupMainMenuScene()
		playback("rosella/sounds/music/mainmenu/portal2_background01.ogg")
		doMainLoop()
	}

	private fun setupMainMenuScene() {
		engine.addRenderObject(
			GuiRenderObject(
				menuBackground
			)
		)

		// engine.addRenderObject(
		// 	GuiRenderObject(
		// 		portalLogo
		// 	)
		// )
	}

	private fun loadMaterials() {
		engine.registerMaterial(
			menuBackground, Material(
				Global.ensureResource(Identifier("rosella", "textures/background/background01.png")),
				guiShader
			)
		)
		engine.registerMaterial(
			portalLogo, Material(
				Global.ensureResource(Identifier("rosella", "textures/gui/portal2logo.png")),
				guiShader
			)
		)
		engine.reloadMaterials()
	}

	private fun loadShaders() {
		engine.registerShader(
			basicShader, ShaderPair(
				Shader(Global.ensureResource(Identifier("rosella", "shaders/base.v.glsl"))),
				Shader(Global.ensureResource(Identifier("rosella", "shaders/base.f.glsl"))),
				engine.device,
				engine.memory,
				3,
				ShaderPair.PoolObjType.UBO,
				ShaderPair.PoolObjType.COMBINED_IMG_SAMPLER
			)
		)

		engine.registerShader(
			guiShader, ShaderPair(
				Shader(Global.ensureResource(Identifier("rosella", "shaders/gui.v.glsl"))),
				Shader(Global.ensureResource(Identifier("rosella", "shaders/base.f.glsl"))),
				engine.device,
				engine.memory,
				3,
				ShaderPair.PoolObjType.UBO,
				ShaderPair.PoolObjType.COMBINED_IMG_SAMPLER
			)
		)
	}

	private fun doMainLoop() {
		engine.renderer.createCommandBuffers(engine.renderer.renderPass, engine)
		screen.onMainLoop {
			engine.renderer.render(engine)
		}
		screen.start(engine)
	}
}
