package me.hydos.rosella.util

import org.lwjgl.vulkan.VK10

private val map = mutableMapOf<Int, String>().apply {
	this[VK10.VK_ERROR_UNKNOWN] = "VK_ERROR_UNKNOWN"
}

fun Int.ok(): Int {
	if (this != VK10.VK_SUCCESS) {
		throw RuntimeException(map[this] ?: toString(16))
	}

	return this
}
