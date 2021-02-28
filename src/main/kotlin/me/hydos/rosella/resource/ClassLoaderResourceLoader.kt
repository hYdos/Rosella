package me.hydos.rosella.resource

import java.io.InputStream

class ClassLoaderResourceLoader(private val loader: ClassLoader) : ResourceLoader {

	override fun loadResource(id: Identifier): Resource? {
		val stream = loader.getResourceAsStream(id.file)

		return if (stream != null) {
			object : Resource {
				override val identifier: Identifier
					get() = id

				override fun openStream(): InputStream = stream
			}
		} else {
			null
		}
	}
}
