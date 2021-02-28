package me.hydos.rosella.resource

data class Identifier(val namespace: String, val path: String) {

	val file: String = "$namespace/$path"

	override fun toString(): String {
		return "$namespace:$path"
	}
}
