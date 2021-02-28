package me.hydos.rosella.resource

class CascadingResourceLoader(private val loaders: Collection<ResourceLoader>) : ResourceLoader {

	override fun loadResource(id: Identifier): Resource? {
		for (loader in loaders) {
			val resource = loader.loadResource(id)

			if (resource != null) {
				return resource
			}
		}

		return null
	}
}
