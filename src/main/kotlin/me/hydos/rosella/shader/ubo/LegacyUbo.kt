package me.hydos.rosella.shader.ubo

import org.joml.Matrix4f

@Deprecated("This is bad. dont use thank")
class LegacyUbo {
	var model: Matrix4f = Matrix4f()
	var view: Matrix4f = Matrix4f()
	var proj: Matrix4f = Matrix4f()
}