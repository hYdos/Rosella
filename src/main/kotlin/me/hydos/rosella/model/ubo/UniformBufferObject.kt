package me.hydos.rosella.model.ubo

import org.joml.Matrix4f


class UniformBufferObject {
	companion object {
		const val SIZEOF = 3 * 16 * java.lang.Float.BYTES
	}

	var model: Matrix4f = Matrix4f()
	var view: Matrix4f = Matrix4f()
	var proj: Matrix4f = Matrix4f()
}