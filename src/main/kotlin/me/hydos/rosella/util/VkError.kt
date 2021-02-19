package me.hydos.rosella.util

import org.lwjgl.vulkan.VK10

fun Int.ok(): Int {
	if (this != VK10.VK_SUCCESS) {
		throw RuntimeException("crab")
	}

	return this
}
