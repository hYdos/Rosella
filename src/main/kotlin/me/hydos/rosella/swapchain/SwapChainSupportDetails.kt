package me.hydos.rosella.swapchain

import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR
import org.lwjgl.vulkan.VkSurfaceFormatKHR
import java.nio.IntBuffer

class SwapChainSupportDetails {
	internal var capabilities: VkSurfaceCapabilitiesKHR? = null
	internal var formats: VkSurfaceFormatKHR.Buffer? = null
	internal var presentModes: IntBuffer? = null
}