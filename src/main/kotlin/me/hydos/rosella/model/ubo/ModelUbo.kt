package me.hydos.rosella.model.ubo

import org.joml.Matrix4f


class ModelUbo {
	companion object {
		const val MAT4f_SIZE = 16 * java.lang.Float.BYTES
		const val SIZEOF = 2 * MAT4f_SIZE
	}

	var model: Matrix4f = Matrix4f()
	var proj: Matrix4f = Matrix4f()
}