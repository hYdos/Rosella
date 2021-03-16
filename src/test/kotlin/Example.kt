package me.hydos.example

import me.hydos.rosella.Rosella
import me.hydos.rosella.io.Window
import me.hydos.rosella.material.Material
import me.hydos.rosella.model.Model
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

		val exampleShaderId = Identifier("rosella", "example_shader")

		val exampleShader = ShaderPair(
			Shader(Global.ensureResource(Identifier("rosella", "shaders/base.v.glsl"))),
			Shader(Global.ensureResource(Identifier("rosella", "shaders/base.f.glsl"))),
			engine.device,
			engine.memory,
			ShaderPair.PoolObjType.UBO,
			ShaderPair.PoolObjType.COMBINED_IMG_SAMPLER
		)
		engine.registerShader(exampleShaderId, exampleShader)

		val factCoreMaterial = Material(
			Global.ensureResource(Identifier("rosella", "textures/fact_core_0.png")),
			exampleShaderId
		)

		val chaletMaterial = Material(
			Global.ensureResource(Identifier("rosella", "textures/fact_core_0.png")),
			exampleShaderId
		)
		engine.registerMaterial(factCore, factCoreMaterial)
		engine.registerMaterial(chalet, chaletMaterial)
		engine.reloadMaterials()

		engine.addModel(
			Model(
				Global.ensureResource(Identifier("rosella", "models/fact_core.gltf")),
				factCore
			)
		)
		engine.addModel(
			Model(
				Global.ensureResource(Identifier("rosella", "models/chalet.obj")),
				Identifier("rosella", "chalet")
			)
		)

		engine.renderer.createCommandBuffers(engine.renderer.renderPass, engine)

		// Register events so we can interact and run game logic
		engine.renderer.createCommandBuffers(engine.renderer.renderPass, engine)

		// Register events so we can interact and run game logic
		screen.onMainLoop {
			engine.renderer.render(engine)
		}

		// Start the screens main loop.
		screen.start(engine)
	}
}
