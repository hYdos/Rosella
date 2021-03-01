package me.hydos.rosella.memory

import me.hydos.rosella.device.Device
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.util.vma.Vma
import org.lwjgl.util.vma.VmaAllocatorCreateInfo
import org.lwjgl.util.vma.VmaVulkanFunctions
import org.lwjgl.vulkan.VkInstance

/**
 * Used for managing CPU and GPU memory. if your getting memory problems, blame this class
 */
class MemMan(val device: Device, private val instance: VkInstance) {

	var allocator: Long = 0

	init {
		stackPush().use {
			val vulkanFunctions: VmaVulkanFunctions = VmaVulkanFunctions.callocStack(it)
				.set(instance, device.device)

			val createInfo: VmaAllocatorCreateInfo = VmaAllocatorCreateInfo.callocStack(it)
				.device(device.device)
				.physicalDevice(device.physicalDevice)
				.instance(instance)
				.pVulkanFunctions(vulkanFunctions)

			val pAllocator = it.mallocPointer(1)
			Vma.vmaCreateAllocator(createInfo, pAllocator)
			this.allocator = pAllocator[0]
		}
	}
}