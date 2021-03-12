package me.hydos.rosella.shader

import me.hydos.rosella.device.Device
import me.hydos.rosella.material.Material
import me.hydos.rosella.memory.memcpy
import me.hydos.rosella.shader.ubo.ModelUbo
import me.hydos.rosella.swapchain.SwapChain
import me.hydos.rosella.util.createBuffer
import me.hydos.rosella.util.ok
import me.hydos.rosella.util.sizeof
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import java.util.*
import java.util.function.Consumer

class ShaderPair(val vertexShader: Shader, val fragmentShader: Shader, val device: Device) {

	var uniformBuffers: MutableList<Long> = ArrayList()
	var uniformBuffersMemory: MutableList<Long> = ArrayList()

	var pushConstantBuffers: MutableList<Long> = ArrayList()
	var pushConstantBuffersMemory: MutableList<Long> = ArrayList()

	var descriptorPool: Long = 0
	var descriptorSetLayout: Long = 0
	var descriptorSets: MutableList<Long> = ArrayList()

	fun createPool(swapChain: SwapChain) {
		MemoryStack.stackPush().use { stack ->
			val poolSizes = VkDescriptorPoolSize.callocStack(2, stack)

			// Uniform Buffer Pool Size
			poolSizes[0]
				.type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
				.descriptorCount(swapChain.swapChainImages.size)

			// Texture Sampler Pool Size
			poolSizes[1]
				.type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
				.descriptorCount(swapChain.swapChainImages.size)

			val poolInfo = VkDescriptorPoolCreateInfo.callocStack(stack)
				.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
				.pPoolSizes(poolSizes)
				.maxSets(swapChain.swapChainImages.size)

			val pDescriptorPool = stack.mallocLong(1)
			vkCreateDescriptorPool(
				device.device,
				poolInfo,
				null,
				pDescriptorPool
			).ok("Failed to create descriptor pool")

			descriptorPool = pDescriptorPool[0]
		}
	}

	fun updateUniformBuffer(currentImage: Int, swapchain: SwapChain) {
		MemoryStack.stackPush().use {
			val ubo = ModelUbo()
			ubo.model.rotate((GLFW.glfwGetTime() * Math.toRadians(90.0)).toFloat(), 0.0f, 0.0f, 1.0f)
			ubo.view.lookAt(2.0f, -40.0f, 2.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f)
			ubo.proj.perspective(
				Math.toRadians(45.0).toFloat(),
				swapchain.swapChainExtent!!.width().toFloat() / swapchain.swapChainExtent!!.height().toFloat(),
				0.1f,
				1000.0f
			)
			ubo.proj.m11(ubo.proj.m11() * -1)

			val data = it.mallocPointer(1)
			vkMapMemory(
				device.device,
				uniformBuffersMemory[currentImage],
				0,
				ModelUbo.SIZEOF.toLong(),
				0,
				data
			)
			run {
				memcpy(data.getByteBuffer(0, ModelUbo.SIZEOF), ubo)
			}
			vkUnmapMemory(device.device, uniformBuffersMemory[currentImage])
		}
	}

	fun createUniformBuffers(swapchain: SwapChain, device: Device) {
		MemoryStack.stackPush().use { stack ->
			uniformBuffers = ArrayList(swapchain.swapChainImages.size)
			uniformBuffersMemory = ArrayList(swapchain.swapChainImages.size)
			val pBuffer = stack.mallocLong(1)
			val pBufferMemory = stack.mallocLong(1)
			for (i in swapchain.swapChainImages.indices) {
				createBuffer(
					ModelUbo.SIZEOF,
					VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
					VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
					pBuffer,
					pBufferMemory,
					device
				)
				uniformBuffers.add(pBuffer[0])
				uniformBuffersMemory.add(pBufferMemory[0])
			}
		}
	}

	fun createPushConstantBuffer() {
		MemoryStack.stackPush().use {
			val pBuffer = it.mallocLong(1)
			val pBufferMemory = it.mallocLong(1)
			createBuffer(
				sizeof(Vector3f::class), //TODO: unhardcode
				VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
				VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
				pBuffer,
				pBufferMemory,
				device
			)

			pushConstantBuffers.add(pBuffer[0])
			pushConstantBuffersMemory.add(pBufferMemory[0])
		}
	}

	fun createDescriptorSetLayout(vararg poolObjects: PoolObjType) {
		MemoryStack.stackPush().use {
			val bindings = VkDescriptorSetLayoutBinding.callocStack(poolObjects.size, it)

			poolObjects.forEachIndexed { i, poolObj ->
				bindings[i]
					.binding(i)
					.descriptorCount(1)
					.descriptorType(poolObj.vkType)
					.pImmutableSamplers(null)
					.stageFlags(poolObj.vkShader)
			}

			val layoutInfo = VkDescriptorSetLayoutCreateInfo.callocStack(it)
			layoutInfo.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
			layoutInfo.pBindings(bindings)
			val pDescriptorSetLayout = it.mallocLong(1)
			vkCreateDescriptorSetLayout(
				device.device,
				layoutInfo,
				null,
				pDescriptorSetLayout
			).ok("Failed to create descriptor set layout")
			descriptorSetLayout = pDescriptorSetLayout[0]
		}
	}

	fun createDescriptorSets(swapChain: SwapChain, material: Material) {
		MemoryStack.stackPush().use { stack ->
			val layouts = stack.mallocLong(swapChain.swapChainImages.size)
			for (i in 0 until layouts.capacity()) {
				layouts.put(i, descriptorSetLayout)
			}
			val allocInfo = VkDescriptorSetAllocateInfo.callocStack(stack)
				.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
				.descriptorPool(descriptorPool)
				.pSetLayouts(layouts)
			val pDescriptorSets = stack.mallocLong(swapChain.swapChainImages.size)

			vkAllocateDescriptorSets(device.device, allocInfo, pDescriptorSets)
				.ok("Failed to allocate descriptor sets")

			descriptorSets = ArrayList(pDescriptorSets.capacity())

			val bufferInfo = VkDescriptorBufferInfo.callocStack(1, stack)
				.offset(0)
				.range(ModelUbo.SIZEOF.toLong())

			val imageInfo = VkDescriptorImageInfo.callocStack(1, stack)
				.imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
				.imageView(material.textureImageView)
				.sampler(material.textureSampler)

			val descriptorWrites = VkWriteDescriptorSet.callocStack(2, stack)

			val uboDescriptorWrite = descriptorWrites[0]
				.sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
				.dstBinding(0)
				.dstArrayElement(0)
				.descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
				.descriptorCount(1)
				.pBufferInfo(bufferInfo)

			val samplerDescriptorWrite = descriptorWrites[1]
				.sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
				.dstBinding(1)
				.dstArrayElement(0)
				.descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
				.descriptorCount(1)
				.pImageInfo(imageInfo)

			for (i in 0 until pDescriptorSets.capacity()) {
				val descriptorSet = pDescriptorSets[i]
				bufferInfo.buffer(uniformBuffers[i])
				uboDescriptorWrite.dstSet(descriptorSet)
				samplerDescriptorWrite.dstSet(descriptorSet)
				vkUpdateDescriptorSets(device.device, descriptorWrites, null)
				descriptorSets.add(descriptorSet)
			}
		}
	}

	fun free(device: Device) {
		uniformBuffers.forEach(Consumer { ubo: Long? -> vkDestroyBuffer(device.device, ubo!!, null) })
		uniformBuffersMemory.forEach(Consumer { uboMemory: Long? ->
			vkFreeMemory(
				device.device,
				uboMemory!!, null
			)
		})
	}

	enum class PoolObjType(val vkType: Int, val vkShader: Int) {
		UBO(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_VERTEX_BIT),
		COMBINED_IMG_SAMPLER(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, VK_SHADER_STAGE_FRAGMENT_BIT)
	}
}