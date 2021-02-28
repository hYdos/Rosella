package me.hydos.rosella.model

import me.hydos.rosella.resource.Resource
import org.joml.Vector2f
import org.joml.Vector2fc
import org.joml.Vector3f
import org.joml.Vector3fc
import org.lwjgl.assimp.*
import org.lwjgl.assimp.Assimp.aiGetErrorString
import org.lwjgl.assimp.Assimp.aiImportFileEx
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.util.*
import java.util.Objects.requireNonNull
import java.util.logging.Logger

object ModelLoader {
	fun loadModel(resource: Resource, flags: Int): SimpleModel {
		aiImportFileEx(resource.identifier.file, flags, createResourceIO(resource.readAllBytes(true))).use { scene ->
			val logger: Logger = Logger.getLogger(ModelLoader::class.java.simpleName)
			logger.info("Loading model " + resource.identifier)

			if (scene?.mRootNode() == null) {
				throw RuntimeException("Could not load model " + aiGetErrorString())
			}

			val model = SimpleModel()
			val startTime = System.nanoTime()
			processNode(scene.mRootNode()!!, scene, model)
			logger.info("mdl loaded in " + (System.nanoTime() - startTime) / 1e6 + "ms")
			return model
		}
	}

	private fun createResourceIO(data: ByteBuffer): AIFileIO {
		val fileIo = AIFileIO.create()

		fileIo.set({ _, _, _ ->
			AIFile.create().apply {
				ReadProc { _: Long, pBuffer: Long, size: Long, count: Long ->
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
		}, { _, _ ->
		}, MemoryUtil.NULL)

		return fileIo
	}

	private fun processNode(node: AINode, scene: AIScene, model: SimpleModel) {
		if (node.mMeshes() != null) {
			processNodeMeshes(scene, node, model)
		}
		if (node.mChildren() != null) {
			val children = node.mChildren()
			for (i in 0 until node.mNumChildren()) {
				processNode(AINode.create(children!![i]), scene, model)
			}
		}
	}

	private fun processNodeMeshes(scene: AIScene, node: AINode, model: SimpleModel) {
		val pMeshes = scene.mMeshes()
		val meshIndices = node.mMeshes()
		for (i in 0 until meshIndices!!.capacity()) {
			val mesh = AIMesh.create(pMeshes!![meshIndices[i]])
			processMesh(scene, mesh, model)
		}
	}

	private fun processMesh(scene: AIScene, mesh: AIMesh, model: SimpleModel) {
		processPositions(mesh, model.positions)
		processTexCoords(mesh, model.texCoords)
		processIndices(mesh, model.indices)
	}

	private fun processPositions(mesh: AIMesh, positions: MutableList<Vector3fc>) {
		val vertices: AIVector3D.Buffer = requireNonNull(mesh.mVertices())
		for (i in 0 until vertices.capacity()) {
			val position = vertices[i]
			positions.add(Vector3f(position.x(), position.y(), position.z()))
		}
	}

	private fun processTexCoords(mesh: AIMesh, texCoords: MutableList<Vector2fc>) {
		val aiTexCoords: AIVector3D.Buffer = mesh.mTextureCoords(0)!!
		for (i in 0 until aiTexCoords.capacity()) {
			val coords = aiTexCoords[i]
			texCoords.add(Vector2f(coords.x(), coords.y()))
		}
	}

	private fun processIndices(mesh: AIMesh, indices: MutableList<Int>) {
		val aiFaces = mesh.mFaces()
		for (i in 0 until mesh.mNumFaces()) {
			val face = aiFaces[i]
			val pIndices = face.mIndices()
			for (j in 0 until face.mNumIndices()) {
				indices.add(pIndices[j])
			}
		}
	}

	class SimpleModel {
		val positions: MutableList<Vector3fc> = ArrayList()
		val texCoords: MutableList<Vector2fc> = ArrayList()
		val indices: MutableList<Int> = ArrayList()
	}
}
