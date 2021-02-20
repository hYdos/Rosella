package me.hydos.rosella.util

import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.NativeResource
import org.lwjgl.util.shaderc.Shaderc.*
import java.io.IOException
import java.lang.ClassLoader.getSystemClassLoader
import java.net.URI
import java.net.URISyntaxException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Paths


fun compileShaderFile(shaderFile: String?, shaderType: ShaderType): SpirV? {
	return compileShaderAbsoluteFile(getSystemClassLoader().getResource(shaderFile).toExternalForm(), shaderType)
}

fun compileShaderAbsoluteFile(shaderFile: String, shaderType: ShaderType): SpirV? {
	try {
		val source = String(Files.readAllBytes(Paths.get(URI(shaderFile))))
		return compileShader(shaderFile, source, shaderType)
	} catch (e: IOException) {
		e.printStackTrace()
	} catch (e: URISyntaxException) {
		e.printStackTrace()
	}
	return null
}

fun compileShader(filename: String, source: String?, shaderType: ShaderType): SpirV? {
	val compiler = shaderc_compiler_initialize()
	if (compiler == NULL) {
		throw RuntimeException("Failed to create shader compiler")
	}
	val result: Long = shaderc_compile_into_spv(compiler, source, shaderType.kind, filename, "main", NULL)
	if (result == NULL) {
		throw RuntimeException("Failed to compile shader $filename into SPIR-V")
	}
	if (shaderc_result_get_compilation_status(result) != shaderc_compilation_status_success) {
		throw RuntimeException(
			"Failed to compile shader ${filename}into SPIR-V: ${shaderc_result_get_error_message(result)}"
		)
	}
	shaderc_compiler_release(compiler)
	return SpirV(result, shaderc_result_get_bytes(result))
}

class SpirV(private val handle: Long, bytecode: ByteBuffer?) : NativeResource {
	private var bytecode: ByteBuffer?
	fun bytecode(): ByteBuffer? {
		return bytecode
	}

	override fun free() {
		shaderc_result_release(handle)
		bytecode = null // Help the GC
	}

	init {
		this.bytecode = bytecode
	}
}
