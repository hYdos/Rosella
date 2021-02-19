package me.hydos.mowingsim.engine;

import me.hydos.mowingsim.engine.util.VkError;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions;
import static org.lwjgl.system.MemoryStack.stackGet;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.VK10.*;

public class Rosella {

	public final VkInstance vulkanInstance;
	public long debugMessenger;
	public State state;

	public Rosella(String name, boolean enableValidationLayers) {
		state = State.STARTING;
		try (MemoryStack stack = stackPush()) {
			List<String> validationLayers = getDefaultValidationLayers();
			if (enableValidationLayers && !validationLayersSupported(validationLayers)) {
				throw new RuntimeException("Validation Layers are not available!");
			}

			VkApplicationInfo applicationInfo = VkApplicationInfo.callocStack()
					.sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
					.pApplicationName(stack.UTF8Safe(name))
					.applicationVersion(VK_MAKE_VERSION(1, 0, 0))
					.pEngineName(stack.UTF8Safe("Rosella"))
					.engineVersion(VK_MAKE_VERSION(0, 1, 0))
					.apiVersion(VK_API_VERSION_1_0);

			VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.callocStack()
					.pApplicationInfo(applicationInfo)
					.sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
					.ppEnabledExtensionNames(glfwGetRequiredInstanceExtensions());

			if (enableValidationLayers) {
				createInfo.ppEnabledLayerNames(layersAsPtrBuffer(validationLayers));

				VkDebugUtilsMessengerCreateInfoEXT debugCreateInfo = VkDebugUtilsMessengerCreateInfoEXT.callocStack(stack);
				populateDebugMessengerCreateInfo(debugCreateInfo);
				createInfo.pNext(debugCreateInfo.address());
			}

			PointerBuffer instancePtr = stack.mallocPointer(1);

			if (vkCreateInstance(createInfo, null, instancePtr) != VK_SUCCESS) {
				throw new RuntimeException("Couldn't instantiate vk instance");
			}

			this.vulkanInstance = new VkInstance(instancePtr.get(0), createInfo);

			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				vkDestroyInstance(vulkanInstance, null);
				if (vkGetInstanceProcAddr(vulkanInstance, "vkDestroyDebugUtilsMessengerEXT") != VK_NULL_HANDLE) {
					vkDestroyDebugUtilsMessengerEXT(vulkanInstance, debugMessenger, null);
				}
			}));

			if (enableValidationLayers) {
				setupDebugMessenger();
			}

			state = State.READY;
		}
	}

	private List<String> getDefaultValidationLayers() {
		List<String> validationLayers = new ArrayList<>();
		validationLayers.add("VK_LAYER_KHRONOS_validation");
		return validationLayers;
	}

	private void setupDebugMessenger() {
		try (MemoryStack stack = stackPush()) {
			VkDebugUtilsMessengerCreateInfoEXT createInfo = VkDebugUtilsMessengerCreateInfoEXT.callocStack(stack);
			populateDebugMessengerCreateInfo(createInfo);
			LongBuffer pDebugMessenger = stack.longs(VK_NULL_HANDLE);
			if (createDebugUtilsMessengerEXT(vulkanInstance, createInfo, null, pDebugMessenger) != VK_SUCCESS) {
				throw new RuntimeException("Failed to set up debug messenger");
			}

			debugMessenger = pDebugMessenger.get(0);
		}
	}


	private static int createDebugUtilsMessengerEXT(VkInstance instance, VkDebugUtilsMessengerCreateInfoEXT createInfo, VkAllocationCallbacks allocationCallbacks, LongBuffer pDebugMessenger) {
		if (vkGetInstanceProcAddr(instance, "vkCreateDebugUtilsMessengerEXT") != MemoryUtil.NULL) {
			return vkCreateDebugUtilsMessengerEXT(instance, createInfo, allocationCallbacks, pDebugMessenger);
		}
		return VK_ERROR_EXTENSION_NOT_PRESENT;
	}

	private void populateDebugMessengerCreateInfo(VkDebugUtilsMessengerCreateInfoEXT debugCreateInfo) {
		debugCreateInfo.sType(VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT)
				.messageSeverity(VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT)
				.messageType(VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT)
				.pfnUserCallback(this::debugCallback);
	}

	private int debugCallback(int severity, int messageType, long pCallbackData, long pUserData) {
		VkDebugUtilsMessengerCallbackDataEXT callbackData = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData);
		if (severity == VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) {
			System.err.println(callbackData.pMessageString());
		} else {
			System.out.println(callbackData.pMessageString());
		}
		return VK_FALSE;
	}

	private PointerBuffer layersAsPtrBuffer(List<String> validationLayers) {
		MemoryStack stack = stackGet();
		PointerBuffer buffer = stack.mallocPointer(validationLayers.size());
		for (String validationLayer : validationLayers) {
			ByteBuffer byteBuffer = stack.UTF8(validationLayer);
			buffer.put(byteBuffer);
		}
		return buffer.rewind();
	}

	private boolean validationLayersSupported(List<String> validationLayers) {
		try (MemoryStack stack = stackPush()) {
			IntBuffer layerCount = stack.ints(0);

			vkEnumerateInstanceLayerProperties(layerCount, null);
			VkLayerProperties.Buffer availableLayers = VkLayerProperties.mallocStack(layerCount.get(0), stack);
			vkEnumerateInstanceLayerProperties(layerCount, availableLayers);

			Set<String> availableLayerNames = availableLayers.stream()
					.map(VkLayerProperties::layerNameString)
					.collect(Collectors.toSet());

			return availableLayerNames.containsAll(validationLayers);
		}
	}

	public enum State {
		STARTING, READY, STOPPING, ERRORED
	}
}
