package me.hydos.rosella.resource

import org.lwjgl.assimp.AIFile
import org.lwjgl.assimp.AIFileIO
import org.lwjgl.assimp.AIScene
import org.lwjgl.assimp.Assimp
import org.lwjgl.system.MemoryUtil

fun loadScene(resource: Resource, flags: Int): AIScene? {
	val identifier = resource.identifier

	val context = identifier.path.run { substring(0, lastIndexOf('/') + 1) }
	val name = identifier.path.run { substring(lastIndexOf('/') + 1) }

	return Assimp.aiImportFileEx(name, flags, AIFileIO.create().apply {
		OpenProc { _, nFileName, _ ->
			val fileName = MemoryUtil.memASCII(nFileName)
			val data = (resource.loader.loadResource(Identifier(identifier.namespace, context + fileName))
					?: return@OpenProc 0).readAllBytes(true)

			AIFile.create().apply {
				ReadProc { _, pBuffer, size, count ->
					val max = data.remaining().toLong().coerceAtMost(size * count)
					MemoryUtil.memCopy(MemoryUtil.memAddress(data), pBuffer, max)
					max
				}

				SeekProc { _, offset, origin ->
					when (origin) {
						Assimp.aiOrigin_CUR -> {
							data.position(data.position() + offset.toInt())
						}
						Assimp.aiOrigin_SET -> {
							data.position(offset.toInt())
						}
						Assimp.aiOrigin_END -> {
							data.position(data.limit() + offset.toInt())
						}
					}

					0
				}

				FileSizeProc {
					data.limit().toLong()
				}
			}.address()
		}
		CloseProc { _, _ ->
		}
	})
}
