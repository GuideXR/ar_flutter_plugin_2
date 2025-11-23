package com.uhg0.ar_flutter_plugin_2

import android.content.Context
import com.google.android.filament.*
import io.github.sceneview.math.Position
import io.github.sceneview.node.Node
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.colorOf
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * A node that renders a wireframe box using Filament's PrimitiveType.LINES.
 * Supports thickness by drawing multiple parallel lines per edge.
 */
class WireframeNode(
    val context: Context,
    engine: Engine,
    var color: Int = 0xFFFFFF, // Make mutable so we can update color
    val lineCount: Int = 4 // Number of parallel lines per edge (4 for thickness)
) : Node(engine) {
    private var vertexBuffer: VertexBuffer? = null
    private var indexBuffer: IndexBuffer? = null
    private var materialInstance: MaterialInstance? = null
    private var renderableEntity: Int = 0
    private val offsetDistance = 0.0015f // 1.5mm spacing between parallel lines
    
    // 12 edges in a box wireframe
    private val EDGE_COUNT = 12

    init {
        setupBuffers()
        createMaterial()
    }

    private fun setupBuffers() {
        // Each edge has lineCount parallel lines, each line needs 2 vertices
        // Total: 12 edges * lineCount lines * 2 vertices = 24 * lineCount vertices
        val vertexCount = EDGE_COUNT * lineCount * 2
        vertexBuffer = VertexBuffer.Builder()
            .vertexCount(vertexCount)
            .bufferCount(1)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 12)
            .build(engine)

        // Each line needs 2 indices (start, end)
        // Total: 12 edges * lineCount lines * 2 indices = 24 * lineCount indices
        val indexCount = EDGE_COUNT * lineCount * 2
        val indices = ShortArray(indexCount)
        var idx = 0
        for (edge in 0 until EDGE_COUNT) {
            for (line in 0 until lineCount) {
                val baseVertex = (edge * lineCount + line) * 2
                indices[idx++] = baseVertex.toShort()
                indices[idx++] = (baseVertex + 1).toShort()
            }
        }

        indexBuffer = IndexBuffer.Builder()
            .indexCount(indices.size)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)

        val indexData = ByteBuffer.allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .put(indices)
        indexBuffer!!.setBuffer(engine, indexData.rewind())
    }

    private fun createMaterial() {
        val materialLoader = MaterialLoader(engine, context)
        // Flutter Color.value is in ARGB format (0xAARRGGBB)
        // Extract RGB components correctly (ignore alpha channel)
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f

        materialInstance = materialLoader.createColorInstance(
            color = colorOf(r, g, b, 1.0f),
            metallic = 0.0f,
            roughness = 0.1f // Lower roughness for brighter, more visible white lines
        )
    }

    fun update(corners: List<Position>, newColor: Int? = null) {
        if (corners.size != 8) return

        // Update color if provided (always update to ensure color is applied)
        if (newColor != null) {
            if (newColor != color) {
                color = newColor
                // Recreate material with new color
                materialInstance?.let { engine.destroyMaterialInstance(it) }
                createMaterial()
                // Rebuild renderable with new material
                if (renderableEntity != 0) {
                    RenderableManager.Builder(1)
                        .boundingBox(Box(0f, 0f, 0f, 100f, 100f, 100f))
                        .geometry(0, RenderableManager.PrimitiveType.LINES, vertexBuffer!!, indexBuffer!!)
                        .material(0, materialInstance!!)
                        .culling(false)
                        .build(engine, this.entity)
                }
            }
        }

        // Define 12 edges of the box
        val edges = arrayOf(
            // base square
            0 to 1, 1 to 2, 2 to 3, 3 to 0,
            // top square
            4 to 5, 5 to 6, 6 to 7, 7 to 4,
            // vertical lines
            0 to 4, 1 to 5, 2 to 6, 3 to 7
        )

        // Total vertices: 12 edges * lineCount lines * 2 vertices * 3 floats
        val floatData = FloatArray(EDGE_COUNT * lineCount * 2 * 3)
        val centerOffset = (lineCount - 1) * offsetDistance / 2f
        var vertexIdx = 0

        for ((a, b) in edges) {
            val start = corners[a]
            val end = corners[b]
            
            // Calculate direction vector
            val dx = end.x - start.x
            val dy = end.y - start.y
            val dz = end.z - start.z
            
            val len = sqrt(dx * dx + dy * dy + dz * dz)
            if (len < 0.0001f) {
                // Zero-length edge, fill with degenerate lines
                for (line in 0 until lineCount) {
                    floatData[vertexIdx++] = start.x
                    floatData[vertexIdx++] = start.y
                    floatData[vertexIdx++] = start.z
                    floatData[vertexIdx++] = end.x
                    floatData[vertexIdx++] = end.y
                    floatData[vertexIdx++] = end.z
                }
                continue
            }
            
            val ux = dx / len
            val uy = dy / len
            val uz = dz / len
            
            // Calculate perpendicular vector for offset
            val px: Float
            val py: Float
            val pz: Float
            
            if (kotlin.math.abs(uy) > 0.99f) {
                // Line is nearly vertical
                px = 0f
                py = uz * offsetDistance
                pz = -uy * offsetDistance
            } else {
                // Normal case
                px = -uz * offsetDistance
                py = 0f
                pz = ux * offsetDistance
            }
            
            // Calculate unit perpendicular vector (normalize px, py, pz)
            val perpLen = sqrt(px * px + py * py + pz * pz)
            val unitPx = if (perpLen > 0.0001f) px / perpLen else 0f
            val unitPy = if (perpLen > 0.0001f) py / perpLen else 0f
            val unitPz = if (perpLen > 0.0001f) pz / perpLen else 0f
            
            // Create parallel lines for this edge
            for (line in 0 until lineCount) {
                val offset = (line * offsetDistance) - centerOffset
                val offsetX = unitPx * offset
                val offsetY = unitPy * offset
                val offsetZ = unitPz * offset
                
                // Start vertex
                floatData[vertexIdx++] = start.x + offsetX
                floatData[vertexIdx++] = start.y + offsetY
                floatData[vertexIdx++] = start.z + offsetZ
                
                // End vertex
                floatData[vertexIdx++] = end.x + offsetX
                floatData[vertexIdx++] = end.y + offsetY
                floatData[vertexIdx++] = end.z + offsetZ
            }
        }

        val vertexData = ByteBuffer.allocateDirect(floatData.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(floatData)
        
        vertexBuffer!!.setBufferAt(engine, 0, vertexData.rewind())

        if (renderableEntity == 0) {
            RenderableManager.Builder(1)
                .boundingBox(Box(0f, 0f, 0f, 100f, 100f, 100f))
                .geometry(0, RenderableManager.PrimitiveType.LINES, vertexBuffer!!, indexBuffer!!)
                .material(0, materialInstance!!)
                .culling(false)
                .build(engine, this.entity)
            renderableEntity = this.entity
        }
    }
    
    override fun destroy() {
        super.destroy()
        engine.destroyVertexBuffer(vertexBuffer!!)
        engine.destroyIndexBuffer(indexBuffer!!)
        // Material instance is managed by loader? Or we should destroy it?
        engine.destroyMaterialInstance(materialInstance!!)
    }
}
