package me.hydos.rosella.core.device

import org.lwjgl.vulkan.VkQueue

class Queues {
	lateinit var graphicsQueue: VkQueue
	lateinit var presentQueue: VkQueue
}