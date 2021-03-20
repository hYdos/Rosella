package me.hydos.example

import me.hydos.rosella.Rosella
import me.hydos.rosella.io.Window
import me.hydos.rosella.material.Material
import me.hydos.rosella.model.GuiRenderObject
import me.hydos.rosella.resource.Global
import me.hydos.rosella.resource.Identifier
import me.hydos.rosella.shader.Shader
import me.hydos.rosella.shader.ShaderPair

object Example {

	@JvmStatic
	fun main(args: Array<String>) {
		val screen = Window("Rosella Engine", 1280, 720)
		val engine = Rosella("Rosella Example", false, screen)

		val factCore = Identifier("rosella", "fact_core")
		val chalet = Identifier("rosella", "chalet")
		val guiTexture = Identifier("rosella", "gui_texture")

		val basicShader = Identifier("rosella", "example_shader")
		val guiShader = Identifier("rosella", "gui_shader")

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

		// Registers the materials so the engine can use them
		engine.registerMaterial(
			factCore, Material(
				Global.ensureResource(Identifier("rosella", "textures/fact_core_0.png")),
				basicShader
			)
		)
		engine.registerMaterial(
			chalet, Material(
				Global.ensureResource(Identifier("rosella", "textures/chalet.jpg")),
				basicShader
			)
		)
		// Special gui material
		engine.registerMaterial(
			guiTexture, Material(
				Global.ensureResource(Identifier("rosella", "textures/yortfuckinhaw.png")),
				guiShader
			)
		)
		engine.reloadMaterials()

//		engine.addRenderObject(
//			RenderObject(
//				Global.ensureResource(Identifier("rosella", "models/fact_core.gltf")),
//				factCore
//			)
//		)

		engine.addRenderObject(
			GuiRenderObject(
				guiTexture
			)
		)

//		engine.addRenderObject(
//			RenderObject(
//				Global.ensureResource(Identifier("rosella", "models/chalet.obj")),
//				chalet
//			)
//		)

		engine.renderer.createCommandBuffers(engine.renderer.renderPass, engine)

		// Reload the engine's command buffers so our new render objects are rendered
		engine.renderer.createCommandBuffers(engine.renderer.renderPass, engine)

		// Register events so we can interact and run game logic
		screen.onMainLoop {
			engine.renderer.render(engine)
		}

		// Start the screens main loop.
		screen.start(engine)
	}
}
