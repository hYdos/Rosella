package me.hydos.rosella.util

import org.lwjgl.util.shaderc.Shaderc

enum class ShaderType(val kind: Int) {
	VERTEX_SHADER(Shaderc.shaderc_glsl_vertex_shader), FRAGMENT_SHADER(Shaderc.shaderc_glsl_fragment_shader);
}