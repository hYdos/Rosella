package me.hydos.rosella.core

import org.lwjgl.vulkan.VkQueue

class Queues {
	lateinit var graphicsQueue: VkQueue
	lateinit var presentQueue: VkQueue
}