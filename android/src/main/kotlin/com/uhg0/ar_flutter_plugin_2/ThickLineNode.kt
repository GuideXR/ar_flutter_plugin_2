package com.uhg0.ar_flutter_plugin_2

import android.content.Context
import com.google.android.filament.*
import io.github.sceneview.math.Position
import io.github.sceneview.node.Node
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.colorOf
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A node that renders a thick white line using multiple parallel LINES primitives
 * Lightweight approach - uses LINES primitive (not cylinders) for performance
 * Creates visual thickness by drawing 3 parallel lines very close together
 */
class ThickLineNode(
    val context: Context,
    engine: Engine,
    val color: Int = 0xFFFFFF
) : Node(engine) {
    private var vertexBuffers = mutableListOf<VertexBuffer>()
    private var indexBuffers = mutableListOf<IndexBuffer>()
    private var materialInstance: MaterialInstance? = null
    private var renderableEntity: Int = 0
    private val lineCount = 7 // Draw 7 parallel lines for better visual thickness
    private val lineSpacing = 0.004f // 4mm spacing between parallel lines for thicker, more visible appearance

    init {
        setupBuffers()
        createMaterial()
    }

    private fun setupBuffers() {
        // Create buffers for each parallel line
        for (i in 0 until lineCount) {
            val vertexBuffer = VertexBuffer.Builder()
                .vertexCount(2)
                .bufferCount(1)
                .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 12)
                .build(engine)
            vertexBuffers.add(vertexBuffer)

            val indices = shortArrayOf(0, 1)
            val indexBuffer = IndexBuffer.Builder()
                .indexCount(indices.size)
                .bufferType(IndexBuffer.Builder.IndexType.USHORT)
                .build(engine)

            val indexData = ByteBuffer.allocateDirect(indices.size * 2)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer()
                .put(indices)
            indexBuffer.setBuffer(engine, indexData.rewind())
            indexBuffers.add(indexBuffer)
        }
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
        // Calculate line direction and perpendicular vector for parallel lines
        val dx = end.x - start.x
        val dy = end.y - start.y
        val dz = end.z - start.z
        val length = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)

        if (length < 0.001f) {
            return // Too short, skip
        }

        // Calculate perpendicular vector for offsetting parallel lines
        // Use a simple perpendicular in the XZ plane
        val perpX = -dz / length
        val perpZ = dx / length

        // Update vertex buffers for each parallel line
        for (i in 0 until lineCount) {
            val offset = (i - lineCount / 2) * lineSpacing // Center the lines
            val offsetX = perpX * offset
            val offsetZ = perpZ * offset

            val floatData = floatArrayOf(
                start.x + offsetX, start.y, start.z + offsetZ,
                end.x + offsetX, end.y, end.z + offsetZ
            )

            val vertexData = ByteBuffer.allocateDirect(floatData.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(floatData)

            vertexBuffers[i].setBufferAt(engine, 0, vertexData.rewind())
        }

        // Create renderable with multiple geometries (one for each parallel line)
        if (renderableEntity == 0) {
            val builder = RenderableManager.Builder(lineCount)
                .boundingBox(Box(0f, 0f, 0f, 100f, 100f, 100f))
                .culling(false)

            // Add each parallel line as a separate geometry
            for (i in 0 until lineCount) {
                builder.geometry(i, RenderableManager.PrimitiveType.LINES, vertexBuffers[i], indexBuffers[i])
                    .material(i, materialInstance!!)
            }

            builder.build(engine, this.entity)
            renderableEntity = this.entity
        }
    }

    override fun destroy() {
        super.destroy()
        vertexBuffers.forEach { engine.destroyVertexBuffer(it) }
        indexBuffers.forEach { engine.destroyIndexBuffer(it) }
        materialInstance?.let { engine.destroyMaterialInstance(it) }
    }
}
