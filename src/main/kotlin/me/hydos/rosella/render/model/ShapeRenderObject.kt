package me.hydos.rosella.render.model

import me.hydos.rosella.render.resource.Identifier
import org.joml.Vector2f
import org.joml.Vector3f
import java.awt.Shape
import java.awt.geom.AffineTransform
import java.awt.geom.CubicCurve2D
import java.awt.geom.PathIterator
import java.awt.geom.QuadCurve2D
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
		val iterator = shape.getPathIterator(AffineTransform().apply {
			val i = shape.bounds2D.run {
				max(width - x, height - y).toFloat()
			}

			setToScale(10.0 / i, -10.0 / i)
		})
		val buffer = FloatArray(6)

		var location = 0f to 0f

		while (!iterator.isDone) {
			when (iterator.currentSegment(buffer)) {
				PathIterator.SEG_MOVETO -> {
					location = buffer[0] to buffer[1]
				}
				PathIterator.SEG_LINETO -> {
					val point = buffer[0] to buffer[1]
					drawLine(location, point)
					location = point
				}
				PathIterator.SEG_QUADTO -> {
					val a = buffer[0] to buffer[1]
					val b = buffer[2] to buffer[3]
					drawLine(location, a)
					drawLine(a, b)
					val quad = QuadCurve2D.Float(location.first, location.second, a.first, a.second, b.first, b.second)
				}
				PathIterator.SEG_CUBICTO -> {
					val a = buffer[0] to buffer[1]
					val b = buffer[2] to buffer[3]
					val c = buffer[4] to buffer[5]
					drawLine(location, a)
					drawLine(a, b)
					drawLine(b, c)
					val cubic = CubicCurve2D.Float(
						location.first,
						location.second,
						a.first,
						a.second,
						b.first,
						b.second,
						c.first,
						c.second
					)
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

	private fun drawLine(from: Pair<Float, Float>, to: Pair<Float, Float>) {
		vertices.add(Vertex(Vector3f(from.first + 0.02f, from.second + 0.02f, 0f), colour, Vector2f(0f, 0f)))
		vertices.add(Vertex(Vector3f(from.first, from.second, 0f), colour, Vector2f(0f, 0f)))
		vertices.add(Vertex(Vector3f(to.first, to.second, 0f), colour, Vector2f(0f, 0f)))
		indices.add(vertices.size - 3)
		indices.add(vertices.size - 2)
		indices.add(vertices.size - 1)
	}
}
