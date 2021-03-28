package me.hydos.rosella.render.resource

interface ResourceLoader {

	fun loadResource(id: Identifier): Resource?

	fun ensureResource(id: Identifier): Resource {
		return Global.loadResource(id) ?: error("Could not open $id")
	}
}