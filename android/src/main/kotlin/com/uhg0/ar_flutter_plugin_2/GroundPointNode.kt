package com.uhg0.ar_flutter_plugin_2

import android.content.Context
import com.google.android.filament.*
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.colorOf
import io.github.sceneview.node.Node
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A node that renders a small white dot using LINES primitive (lightweight)
 * Creates a small cross pattern to represent a point marker
 */
class GroundPointNode(
    val context: Context,
    engine: Engine,
    private val color: Int = 0xFFFFFF
) : Node(engine) {
    private var vertexBuffer: VertexBuffer? = null
    private var indexBuffer: IndexBuffer? = null
    private var materialInstance: MaterialInstance? = null
    private var renderableEntity: Int = 0
    private val pointSize = 0.05f // 5cm size for the point marker (larger for better visibility)

    init {
        setupBuffers()
        createMaterial()
        updatePosition() // Initialize the cross pattern
    }

    private fun setupBuffers() {
        // Create a simple cross pattern (2 lines forming a + shape) - horizontal and vertical only
        // 2 lines = 4 vertices
        vertexBuffer = VertexBuffer.Builder()
            .vertexCount(4)
            .bufferCount(1)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 12)
            .build(engine)

        // Indices for 2 lines (horizontal and vertical cross) - LINES primitive only, no cylinders
        val indices = shortArrayOf(
            0, 1, // Horizontal line left to right (X axis)
            2, 3  // Vertical line front to back (Z axis)
        )
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
            roughness = 0.3f
        )
    }

    // Update the point position (called when node is positioned)
    fun updatePosition() {
        // Create simple cross pattern (horizontal + vertical lines) - FLAT on ground (XZ plane)
        // Both lines are in the XZ plane (Y=0) so they appear flat when viewed from above
        // This is a LINES primitive, NOT a cylinder - just two perpendicular lines
        val halfSize = pointSize / 2f
        val floatData = floatArrayOf(
            // Horizontal line (along X axis) - flat on ground
            -halfSize, 0f, 0f,
            halfSize, 0f, 0f,
            // Vertical line (along Z axis) - flat on ground, perpendicular to X line
            0f, 0f, -halfSize,
            0f, 0f, halfSize
        )

        val vertexData = ByteBuffer.allocateDirect(floatData.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(floatData)

        vertexBuffer!!.setBufferAt(engine, 0, vertexData.rewind())

        if (renderableEntity == 0) {
            // Create renderable using LINES primitive - this is NOT a cylinder
            RenderableManager.Builder(1)
                .boundingBox(Box(0f, 0f, 0f, pointSize, 0.01f, pointSize)) // Very thin bounding box
                .geometry(0, RenderableManager.PrimitiveType.LINES, vertexBuffer!!, indexBuffer!!)
                .material(0, materialInstance!!)
                .culling(false)
                .build(engine, this.entity)
            renderableEntity = this.entity
        }
    }

    override fun destroy() {
        super.destroy()
        vertexBuffer?.let { engine.destroyVertexBuffer(it) }
        indexBuffer?.let { engine.destroyIndexBuffer(it) }
        materialInstance?.let { engine.destroyMaterialInstance(it) }
    }
}
