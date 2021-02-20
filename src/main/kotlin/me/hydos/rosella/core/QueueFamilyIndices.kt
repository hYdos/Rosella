package me.hydos.rosella.core

import java.util.stream.IntStream

class QueueFamilyIndices {
	var graphicsFamily: Int? = null
	val presentFamily: Int? = null
	val isComplete: Boolean
		get() = graphicsFamily != null && presentFamily != null

	fun unique(): IntArray {
		return IntStream.of(graphicsFamily!!, presentFamily!!).distinct().toArray()
	}

	fun array(): IntArray {
		return intArrayOf(graphicsFamily!!, presentFamily!!)
	}
}
