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
 * A node that renders a wireframe box using Filament's PrimitiveType.LINES.
 * This is much more efficient than creating cylinder nodes for lines.
 */
class WireframeNode(
    val context: Context,
    engine: Engine,
    var color: Int = 0xFFFFFF // Make mutable so we can update color
) : Node(engine) {
    private var vertexBuffer: VertexBuffer? = null
    private var indexBuffer: IndexBuffer? = null
    private var materialInstance: MaterialInstance? = null
    private var renderableEntity: Int = 0

    init {
        setupBuffers()
        createMaterial()
    }

    private fun setupBuffers() {
        // 8 corners, 3 floats per corner (x, y, z)
        vertexBuffer = VertexBuffer.Builder()
            .vertexCount(8)
            .bufferCount(1)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 12)
            .build(engine)

        // Indices for a box wireframe
        // Base: 0-1, 1-2, 2-3, 3-0
        // Top: 4-5, 5-6, 6-7, 7-4
        // Vertical: 0-4, 1-5, 2-6, 3-7
        val indices = shortArrayOf(
            0, 1, 1, 2, 2, 3, 3, 0,
            4, 5, 5, 6, 6, 7, 7, 4,
            0, 4, 1, 5, 2, 6, 3, 7
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

        val floatData = FloatArray(8 * 3)
        for (i in 0 until 8) {
            floatData[i * 3] = corners[i].x
            floatData[i * 3 + 1] = corners[i].y
            floatData[i * 3 + 2] = corners[i].z
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
