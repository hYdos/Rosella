package me.hydos.rosella.core.swapchain

import me.hydos.rosella.core.QueueFamilyIndices
import me.hydos.rosella.core.Rosella
import me.hydos.rosella.io.Screen
import me.hydos.rosella.util.findQueueFamilies
import me.hydos.rosella.util.ok
import org.lwjgl.glfw.GLFW.glfwGetFramebufferSize
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryStack.stackGet
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.KHRSurface.*
import org.lwjgl.vulkan.KHRSwapchain.*
import org.lwjgl.vulkan.VK10.*
import java.nio.IntBuffer
import java.nio.LongBuffer


class SwapChain(
	engine: Rosella,
	device: VkDevice,
	physicalDevice: VkPhysicalDevice,
	surface: Long
) {
	var swapChain: Long = 0
	var swapChainImageViews: List<Long> = ArrayList()
	var swapChainFramebuffers: List<Long> = ArrayList()
	var swapChainImages: List<Long> = ArrayList()
	var swapChainImageFormat = 0
	var swapChainExtent: VkExtent2D? = null

	init {
		MemoryStack.stackPush().use {
			val swapChainSupport: SwapChainSupportDetails = querySwapChainSupport(physicalDevice, it, surface)

			val surfaceFormat: VkSurfaceFormatKHR = chooseSwapSurfaceFormat(swapChainSupport.formats!!)!!
			val presentMode: Int = chooseSwapPresentMode(swapChainSupport.presentModes!!)
			val extent: VkExtent2D = chooseSwapExtent(swapChainSupport.capabilities!!, engine.screen)!!

			val imageCount: IntBuffer = it.ints(swapChainSupport.capabilities!!.minImageCount() + 1)

			if (swapChainSupport.capabilities!!.maxImageCount() > 0 && imageCount[0] > swapChainSupport.capabilities!!.maxImageCount()) {
				imageCount.put(0, swapChainSupport.capabilities!!.maxImageCount())
			}

			val createInfo: VkSwapchainCreateInfoKHR = VkSwapchainCreateInfoKHR.callocStack(it)

			createInfo.sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
				.surface(surface)

			createInfo.minImageCount(imageCount[0])
				.imageFormat(surfaceFormat.format())
				.imageColorSpace(surfaceFormat.colorSpace())
				.imageExtent(extent)
				.imageArrayLayers(1)
				.imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)

			val indices: QueueFamilyIndices = findQueueFamilies(physicalDevice, engine)

			if (indices.graphicsFamily != indices.presentFamily) {
				createInfo.imageSharingMode(VK_SHARING_MODE_CONCURRENT)
					.pQueueFamilyIndices(it.ints(indices.graphicsFamily!!, indices.presentFamily!!))
			} else {
				createInfo.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
			}

			createInfo.preTransform(swapChainSupport.capabilities!!.currentTransform())
				.compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
				.presentMode(presentMode)
				.clipped(true)
				.oldSwapchain(VK_NULL_HANDLE)

			val pSwapChain: LongBuffer = it.longs(VK_NULL_HANDLE)
			vkCreateSwapchainKHR(device, createInfo, null, pSwapChain).ok()
			swapChain = pSwapChain[0]
			vkGetSwapchainImagesKHR(device, swapChain, imageCount, null)
			val pSwapchainImages: LongBuffer = it.mallocLong(imageCount[0])
			vkGetSwapchainImagesKHR(device, swapChain, imageCount, pSwapchainImages)

			swapChainImages = ArrayList(imageCount[0])

			for (i in 0 until pSwapchainImages.capacity()) {
				(swapChainImages as ArrayList<Long>).add(pSwapchainImages[i])
			}

			swapChainImageFormat = surfaceFormat.format()
			swapChainExtent = VkExtent2D.create().set(extent)
		}
	}

	private fun chooseSwapSurfaceFormat(availableFormats: VkSurfaceFormatKHR.Buffer): VkSurfaceFormatKHR? {
		return availableFormats.stream()
			.filter { availableFormat: VkSurfaceFormatKHR -> availableFormat.format() == VK_FORMAT_B8G8R8_SRGB }
			.filter { availableFormat: VkSurfaceFormatKHR -> availableFormat.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR }
			.findAny()
			.orElse(availableFormats[0])
	}

	private fun chooseSwapPresentMode(availablePresentModes: IntBuffer): Int {
		for (i in 0 until availablePresentModes.capacity()) {
			if (availablePresentModes[i] == VK_PRESENT_MODE_MAILBOX_KHR) {
				return availablePresentModes[i]
			}
		}
		return VK_PRESENT_MODE_FIFO_KHR
	}

	private fun chooseSwapExtent(capabilities: VkSurfaceCapabilitiesKHR, screen: Screen): VkExtent2D? {
		if (capabilities.currentExtent().width() != UINT32_MAX) {
			return capabilities.currentExtent()
		}

		val width = stackGet().ints(0)
		val height = stackGet().ints(0)

		glfwGetFramebufferSize(screen.windowPtr, width, height)

		val actualExtent = VkExtent2D.mallocStack().set(width[0], height[0])
		val minExtent = capabilities.minImageExtent()
		val maxExtent = capabilities.maxImageExtent()
		actualExtent.width(minExtent.width().coerceIn(maxExtent.width(), actualExtent.width()))
		actualExtent.height(minExtent.height().coerceIn(maxExtent.height(), actualExtent.height()))
		return actualExtent
	}

	companion object {
		private const val UINT32_MAX = -0x1
	}
}

fun querySwapChainSupport(device: VkPhysicalDevice, stack: MemoryStack, surface: Long): SwapChainSupportDetails {
	val details = SwapChainSupportDetails()
	details.capabilities = VkSurfaceCapabilitiesKHR.mallocStack(stack)
	vkGetPhysicalDeviceSurfaceCapabilitiesKHR(device, surface, details.capabilities!!)
	val count = stack.ints(0)
	vkGetPhysicalDeviceSurfaceFormatsKHR(device, surface, count, null)
	if (count[0] != 0) {
		details.formats = VkSurfaceFormatKHR.mallocStack(count[0], stack)
		vkGetPhysicalDeviceSurfaceFormatsKHR(device, surface, count, details.formats)
	}
	vkGetPhysicalDeviceSurfacePresentModesKHR(device, surface, count, null)
	if (count[0] != 0) {
		details.presentModes = stack.mallocInt(count[0])
		vkGetPhysicalDeviceSurfacePresentModesKHR(device, surface, count, details.presentModes)
	}
	return details
}