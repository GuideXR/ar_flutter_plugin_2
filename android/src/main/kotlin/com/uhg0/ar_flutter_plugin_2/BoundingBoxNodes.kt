package com.uhg0.ar_flutter_plugin_2

import android.content.Context
import com.google.android.filament.Engine
import io.github.sceneview.math.Position
import io.github.sceneview.math.lookTowards
import io.github.sceneview.node.Node
import kotlin.math.sqrt

/**
 * A node representing a line between two points.
 * Implemented using a scaled cube.
 */
class LineNode(
    val context: Context,
    engine: Engine,
    var start: Position = Position(0f, 0f, 0f),
    var end: Position = Position(0f, 1f, 0f),
    val thickness: Float = 0.005f,
    val color: Int = 0xFFFFFF
) : Node(engine) {

    private var geometryNode: Node? = null

    init {
        updateGeometry()
    }

    fun updatePoints(newStart: Position, newEnd: Position) {
        start = newStart
        end = newEnd
        updateGeometry()
    }

    private fun updateGeometry() {
        // Remove old node if exists
        geometryNode?.let { 
            removeChildNode(it)
            it.destroy() 
        }

        val direction = end - start
        val length = sqrt(direction.x * direction.x + direction.y * direction.y + direction.z * direction.z)
        
        if (length < 0.001f) return // Too small to render

        val midpoint = Position(
            (start.x + end.x) / 2f,
            (start.y + end.y) / 2f,
            (start.z + end.z) / 2f
        )

        // Create a cube scaled to be a line
        // We align along Z axis for length
        geometryNode = createSceneViewCubeNode(
            engine,
            context,
            thickness,
            thickness,
            length,
            color,
            1.0f,
            Position(0f, 0f, 0f) // Local position 0
        )

        addChildNode(geometryNode!!)
        
        // Position at midpoint
        this.position = midpoint
        
        // Look at end point
        lookTowards(end)
    }
}

/**
 * A node representing a point on the ground (e.g., corner of bounding box).
 */
class GroundPointNode(
    context: Context,
    engine: Engine,
    color: Int = 0xFFFFFFFF.toInt()
) : Node(engine) {
    init {
        // Create a small flat square/cube
        val dot = createSceneViewCubeNode(
            engine,
            context,
            0.05f, 0.01f, 0.05f, 
            color,
            1.0f,
            Position(0f, 0f, 0f)
        )
        addChildNode(dot)
    }
}

/**
 * A node representing a complete 3D bounding box wireframe.
 * Manages 12 LineNodes.
 */
class BoundingBoxNode(
    val context: Context,
    engine: Engine,
    val color: Int = 0xFFFFFF
) : Node(engine) {
    // 12 lines
    private val lines = ArrayList<LineNode>()
    
    init {
        for (i in 0 until 12) {
            val line = LineNode(context, engine, color = color)
            lines.add(line)
            addChildNode(line)
        }
    }

    /**
     * Updates the bounding box geometry based on 8 corner points.
     * Order: 0-3 (Base), 4-7 (Top)
     */
    fun update(corners: List<Position>) {
        if (corners.size != 8) return
        
        // Base (0-1, 1-2, 2-3, 3-0)
        lines[0].updatePoints(corners[0], corners[1])
        lines[1].updatePoints(corners[1], corners[2])
        lines[2].updatePoints(corners[2], corners[3])
        lines[3].updatePoints(corners[3], corners[0])
        
        // Top (4-5, 5-6, 6-7, 7-4)
        lines[4].updatePoints(corners[4], corners[5])
        lines[5].updatePoints(corners[5], corners[6])
        lines[6].updatePoints(corners[6], corners[7])
        lines[7].updatePoints(corners[7], corners[4])
        
        // Vertical (0-4, 1-5, 2-6, 3-7)
        lines[8].updatePoints(corners[0], corners[4])
        lines[9].updatePoints(corners[1], corners[5])
        lines[10].updatePoints(corners[2], corners[6])
        lines[11].updatePoints(corners[3], corners[7])
    }
}
