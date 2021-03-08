package me.hydos.rosella.shader

import me.hydos.rosella.resource.Resource
import me.hydos.rosella.shader.ubo.ModelUbo

class Shader(val resource: Resource) {

	fun updateUbo(ubo: ModelUbo) {

	}

	enum class ValueType {
		IMAGE_SAMPLER,
		UBO
	}
}
