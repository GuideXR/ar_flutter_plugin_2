package com.uhg0.ar_flutter_plugin_2

import android.content.Context
import com.google.android.filament.*
import io.github.sceneview.math.Position
import io.github.sceneview.node.Node
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.colorOf
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SimpleLineNode(
    val context: Context,
    engine: Engine,
    val color: Int = 0xFFFFFF
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
        vertexBuffer = VertexBuffer.Builder()
            .vertexCount(2)
            .bufferCount(1)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 12)
            .build(engine)

        val indices = shortArrayOf(0, 1)
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
        val floatData = floatArrayOf(
            start.x, start.y, start.z,
            end.x, end.y, end.z
        )

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
