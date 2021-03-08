package me.hydos.rosella.shader

import me.hydos.rosella.shader.ubo.ModelUbo

class Shader(val shaderLocation: String) {


	fun updateUbo(ubo: ModelUbo) {

	}

	enum class ValueType {
		IMAGE_SAMPLER,
		UBO
	}
}