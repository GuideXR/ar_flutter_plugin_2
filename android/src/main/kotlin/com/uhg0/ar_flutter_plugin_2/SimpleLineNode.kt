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

class SimpleLineNode(
    val context: Context,
    engine: Engine,
    val color: Int = 0xFFFFFF,
    val lineCount: Int = 4 // Number of parallel lines to draw (4 for thickness)
) : Node(engine) {
    private var vertexBuffer: VertexBuffer? = null
    private var indexBuffer: IndexBuffer? = null
    private var materialInstance: MaterialInstance? = null
    private var renderableEntity: Int = 0
    private val offsetDistance = 0.0015f // 1.5mm spacing between parallel lines

    init {
        setupBuffers()
        createMaterial()
    }

    private fun setupBuffers() {
        // Each parallel line needs 2 vertices
        val vertexCount = lineCount * 2
        vertexBuffer = VertexBuffer.Builder()
            .vertexCount(vertexCount)
            .bufferCount(1)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 12)
            .build(engine)

        // Each line needs 2 indices (start, end)
        val indices = ShortArray(lineCount * 2)
        for (i in 0 until lineCount) {
            indices[i * 2] = (i * 2).toShort()
            indices[i * 2 + 1] = (i * 2 + 1).toShort()
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
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f

        materialInstance = materialLoader.createColorInstance(
            color = colorOf(r, g, b, 1.0f),
            metallic = 0.0f,
            roughness = 0.4f
        )
    }

    fun update(start: Position, end: Position) {
        // Calculate direction vector
        val dx = end.x - start.x
        val dy = end.y - start.y
        val dz = end.z - start.z
        
        val len = sqrt(dx * dx + dy * dy + dz * dz)
        if (len < 0.0001f) return // Skip zero-length lines
        
        val ux = dx / len
        val uy = dy / len
        val uz = dz / len
        
        // Calculate perpendicular vector for offset
        // Cross product with up vector (0, 1, 0) to get perpendicular
        val px: Float
        val py: Float
        val pz: Float
        
        if (kotlin.math.abs(uy) > 0.99f) {
            // Line is nearly vertical, use cross product with (1, 0, 0)
            px = 0f
            py = uz * offsetDistance
            pz = -uy * offsetDistance
        } else {
            // Normal case: cross product with (0, 1, 0)
            px = -uz * offsetDistance
            py = 0f
            pz = ux * offsetDistance
        }
        
        // Create parallel lines by offsetting perpendicularly
        // Calculate unit perpendicular vector (px, py, pz are already scaled by offsetDistance)
        val perpLen = sqrt(px * px + py * py + pz * pz)
        val unitPx = if (perpLen > 0.0001f) px / perpLen else 0f
        val unitPy = if (perpLen > 0.0001f) py / perpLen else 0f
        val unitPz = if (perpLen > 0.0001f) pz / perpLen else 0f
        
        val floatData = FloatArray(lineCount * 2 * 3) // 2 vertices per line, 3 floats per vertex
        val centerOffset = (lineCount - 1) * offsetDistance / 2f
        
        for (i in 0 until lineCount) {
            val offset = (i * offsetDistance) - centerOffset
            val offsetX = unitPx * offset
            val offsetY = unitPy * offset
            val offsetZ = unitPz * offset
            
            // Start vertex
            floatData[i * 6] = start.x + offsetX
            floatData[i * 6 + 1] = start.y + offsetY
            floatData[i * 6 + 2] = start.z + offsetZ
            
            // End vertex
            floatData[i * 6 + 3] = end.x + offsetX
            floatData[i * 6 + 4] = end.y + offsetY
            floatData[i * 6 + 5] = end.z + offsetZ
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
        engine.destroyMaterialInstance(materialInstance!!)
    }
}
