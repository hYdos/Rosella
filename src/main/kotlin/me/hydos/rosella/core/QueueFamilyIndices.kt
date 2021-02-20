package me.hydos.rosella.core

class QueueFamilyIndices {
	var graphicsFamily: Int? = null
	val isComplete: Boolean
		get() = graphicsFamily != null
}
