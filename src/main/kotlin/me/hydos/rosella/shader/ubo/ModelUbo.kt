package me.hydos.rosella.shader.ubo

import org.joml.Matrix4f


class ModelUbo {
	companion object {
		const val MAT4f_SIZE = 16 * java.lang.Float.BYTES
		const val SIZEOF = 3 * MAT4f_SIZE
	}

	var model: Matrix4f = Matrix4f()
	var view: Matrix4f = Matrix4f()
	var proj: Matrix4f = Matrix4f()
}