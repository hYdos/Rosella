package me.hydos.rosella.resource

interface ResourceLoader {

	fun loadResource(id: Identifier): Resource?
}
