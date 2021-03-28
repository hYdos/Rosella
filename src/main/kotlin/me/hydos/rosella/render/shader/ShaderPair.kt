package me.hydos.rosella.render.shader

import me.hydos.rosella.Rosella
import me.hydos.rosella.render.device.Device
import me.hydos.rosella.render.model.RenderObject
import me.hydos.rosella.render.swapchain.SwapChain
import me.hydos.rosella.render.util.memory.Memory
import me.hydos.rosella.render.util.ok
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*

class ShaderPair(
	val vertexShader: Shader,
	val fragmentShader: Shader,
	val device: Device,
	val memory: Memory,
	var maxObjCount: Int,
	vararg var poolObjects: PoolObjType
) {
	var descriptorPool: Long = 0
	var descriptorSetLayout: Long = 0

	fun updateUbos(currentImage: Int, swapChain: SwapChain, engine: Rosella) {
		for (renderObject in engine.renderObjects) {
			renderObject.ubo.update(
				currentImage,
				swapChain,
				engine.camera.view,
				engine.camera.proj,
				renderObject.modelTransformMatrix
			)
		}
	}

	fun createPool(swapChain: SwapChain) {
		MemoryStack.stackPush().use { stack ->
			val poolSizes = VkDescriptorPoolSize.callocStack(poolObjects.size, stack)

			poolObjects.forEachIndexed { i, poolObj ->
				poolSizes[i]
					.type(poolObj.vkType)
					.descriptorCount(swapChain.swapChainImages.size * maxObjCount)
			}

			val poolInfo = VkDescriptorPoolCreateInfo.callocStack(stack)
				.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
				.pPoolSizes(poolSizes)
				.maxSets(swapChain.swapChainImages.size * maxObjCount)

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

	fun createDescriptorSetLayout() {
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

	fun createDescriptorSets(swapChain: SwapChain, renderObject: RenderObject) {
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

			renderObject.descriptorSets = ArrayList(pDescriptorSets.capacity())

			val bufferInfo = VkDescriptorBufferInfo.callocStack(1, stack)
				.offset(0)
				.range(renderObject.ubo.getSize().toLong())

			val imageInfo = VkDescriptorImageInfo.callocStack(1, stack)
				.imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
				.imageView(renderObject.material.textureImageView)
				.sampler(renderObject.material.textureSampler)

			val descriptorWrites = VkWriteDescriptorSet.callocStack(poolObjects.size, stack)

			for (i in 0 until pDescriptorSets.capacity()) {
				val descriptorSet = pDescriptorSets[i]
				bufferInfo.buffer(renderObject.ubo.getUniformBuffers()[i].buffer)
				poolObjects.forEachIndexed { index, poolObj ->
					val descriptorWrite = descriptorWrites[index]
						.sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
						.dstBinding(index)
						.dstArrayElement(0)
						.descriptorType(poolObj.vkType)
						.descriptorCount(1)

					when (poolObj.vkType) {
						VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER -> {
							descriptorWrite.pBufferInfo(bufferInfo)
						}

						VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER -> {
							descriptorWrite.pImageInfo(imageInfo)
						}
					}
					descriptorWrite.dstSet(descriptorSet)
				}
				vkUpdateDescriptorSets(device.device, descriptorWrites, null)
				renderObject.descriptorSets.add(descriptorSet)
			}
		}
	}

	enum class PoolObjType(val vkType: Int, val vkShader: Int) {
		UBO(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_VERTEX_BIT),
		COMBINED_IMG_SAMPLER(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, VK_SHADER_STAGE_FRAGMENT_BIT)
	}
}