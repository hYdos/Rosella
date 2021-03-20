package me.hydos.rosella.resource

import java.io.InputStream

class ClassLoaderResourceLoader(private val loader: ClassLoader) : ResourceLoader {

	override fun loadResource(id: Identifier): Resource {
		return object : Resource {
			override val identifier: Identifier
				get() = id

			override val loader: ResourceLoader
				get() = this@ClassLoaderResourceLoader

			override fun openStream(): InputStream = this@ClassLoaderResourceLoader.loader.getResourceAsStream(id.file)!!
		}
	}
}
