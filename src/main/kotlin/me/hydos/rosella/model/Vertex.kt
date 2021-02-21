package me.hydos.rosella.model

import org.joml.Vector2fc
import org.joml.Vector3fc
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkVertexInputAttributeDescription
import org.lwjgl.vulkan.VkVertexInputBindingDescription


class Vertex(val pos: Vector2fc, val color: Vector3fc) {
	companion object {
		const val SIZEOF = (2 + 3) * java.lang.Float.BYTES
		private const val OFFSETOF_POS = 0
		private const val OFFSETOF_COLOR = 2 * java.lang.Float.BYTES

		internal val bindingDescription: VkVertexInputBindingDescription.Buffer
			get() {
				val bindingDescription = VkVertexInputBindingDescription.callocStack(1)
				bindingDescription.binding(0)
				bindingDescription.stride(SIZEOF)
				bindingDescription.inputRate(VK_VERTEX_INPUT_RATE_VERTEX)
				return bindingDescription
			}

		internal val attributeDescriptions: VkVertexInputAttributeDescription.Buffer
			get() {
				val attributeDescriptions = VkVertexInputAttributeDescription.callocStack(2)

				val posDescription = attributeDescriptions[0]
				posDescription.binding(0)
				posDescription.location(0)
				posDescription.format(VK_FORMAT_R32G32_SFLOAT)
				posDescription.offset(OFFSETOF_POS)

				val colorDescription = attributeDescriptions[1]
				colorDescription.binding(0)
				colorDescription.location(1)
				colorDescription.format(VK_FORMAT_R32G32B32_SFLOAT)
				colorDescription.offset(OFFSETOF_COLOR)
				return attributeDescriptions.rewind()
			}
	}
}