package me.hydos.rosella.render.model

import me.hydos.rosella.render.resource.Identifier
import org.joml.Vector2f
import org.joml.Vector3f
import java.awt.Shape
import java.awt.geom.AffineTransform
import java.awt.geom.PathIterator
import kotlin.math.max

class ShapeRenderObject(
	private val shape: Shape,
	matId: Identifier,
	z: Float,
	colour: Vector3f,
	scaleX: Float,
	scaleZ: Float,
	translateX: Float,
	translateZ: Float
) : GuiRenderObject(matId, z, colour, scaleX, scaleZ, translateX, translateZ) {

	override fun loadModelInfo() {
		val iterator = shape.getPathIterator(AffineTransform())
		val buffer = FloatArray(6)

		var location = 0f to 0f
		val i = shape.bounds2D.run {
			max(width - x, height - y).toFloat()
		} / 5

		while (!iterator.isDone) {
			when (iterator.currentSegment(buffer)) {
				PathIterator.SEG_MOVETO -> {
					location = buffer[0] / i to buffer[1] / i
				}
				PathIterator.SEG_LINETO -> {
					val point = buffer[0] / i to buffer[1] / i
					vertices.add(Vertex(Vector3f(location.first + 0.01f, location.second + 0.01f, 0f), colour, Vector2f(0f, 0f)))
					vertices.add(Vertex(Vector3f(location.first, location.second, 0f), colour, Vector2f(0f, 0f)))
					vertices.add(Vertex(Vector3f(point.first, point.second, 0f), colour, Vector2f(0f, 0f)))
					indices.add(vertices.size - 3)
					indices.add(vertices.size - 2)
					indices.add(vertices.size - 1)
					println("SEG_LINETO $point -> $location")
					location = point
				}
				PathIterator.SEG_QUADTO -> {
					val a = buffer[0] / i to buffer[1] / i
					val b = buffer[2] / i to buffer[3] / i
					println("SEG_QUADTO")
				}
				PathIterator.SEG_CUBICTO -> {
					val a = buffer[0] / i to buffer[1] / i
					val b = buffer[2] / i to buffer[3] / i
					val c = buffer[4] / i to buffer[5] / i
					println("SEG_CUBICTO")
				}
				PathIterator.SEG_CLOSE -> {
				}
				else -> {
					error("Unknown path segment type")
				}
			}

			iterator.next()
		}
	}
}
