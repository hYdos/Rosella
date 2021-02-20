package me.hydos.rosella.core

import java.util.stream.IntStream


private class QueueFamilyIndices {
	private val graphicsFamily: Int? = null
	private val presentFamily: Int? = null
	private val isComplete: Boolean
		get() = graphicsFamily != null && presentFamily != null

	fun unique(): IntArray {
		return IntStream.of(graphicsFamily!!, presentFamily!!).distinct().toArray()
	}

	fun array(): IntArray {
		return intArrayOf(graphicsFamily!!, presentFamily!!)
	}
}