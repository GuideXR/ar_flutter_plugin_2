package com.uhg0.ar_flutter_plugin_2

import com.google.android.filament.*
import io.github.sceneview.node.Node
import java.nio.ByteBuffer
import java.nio.ByteOrder
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.colorOf
import android.content.Context
import io.github.sceneview.geometries.Cube
import io.github.sceneview.node.GeometryNode
import io.github.sceneview.math.Size


/**
 * Helper functions for creating Filament primitives and materials
 * Used for procedural geometry creation (cubes, etc.)
 */

/**
 * Creates a transparent material instance using Filament's Material.Builder
 * DO NOT use MaterialLoader.createColorInstance - it will NEVER produce transparency correctly
 * @param engine Filament Engine instance
 * @param color RGB color as Int (0xRRGGBB)
 * @param opacity Opacity value (0.0 to 1.0)
 * @return MaterialInstance with transparent blending
 */
fun createTransparentMaterial(
    engine: Engine,
    context: Context,
    color: Int,
    opacity: Float
): MaterialInstance {

    val r = ((color shr 16) and 0xFF) / 255f
    val g = ((color shr 8) and 0xFF) / 255f
    val b = (color and 0xFF) / 255f

    val materialLoader = MaterialLoader(engine, context)

    // This material supports rgba directly and *will* render with proper transparency
    return materialLoader.createColorInstance(
        color = colorOf(r, g, b, opacity),
        metallic = 0.0f,
        roughness = 0.5f
    )
}




/**
 * Creates cube geometry using Filament's low-level APIs
 * @param engine Filament Engine instance
 * @param width Cube width (X dimension)
 * @param height Cube height (Y dimension)
 * @param depth Cube depth (Z dimension)
 * @param materialInstance Material instance to apply
 * @return Renderable entity ID
 */
fun createCubeRenderable(
    engine: Engine,
    width: Float,
    height: Float,
    depth: Float,
    materialInstance: MaterialInstance
): Int {
    val entityManager = EntityManager.get()
    val renderableManager = engine.renderableManager
    val entity = entityManager.create()
    
    // Cube vertices (24 vertices - 4 per face for proper normals)
    // We need 24 vertices because each face needs its own vertices with correct normals
    val halfWidth = width / 2f
    val halfHeight = height / 2f
    val halfDepth = depth / 2f
    
    // Positions: 24 vertices (4 per face × 6 faces)
    val vertices = floatArrayOf(
        // Front face (Z+)
        -halfWidth, -halfHeight,  halfDepth,  // 0
         halfWidth, -halfHeight,  halfDepth,  // 1
         halfWidth,  halfHeight,  halfDepth,  // 2
        -halfWidth,  halfHeight,  halfDepth,  // 3
        // Back face (Z-)
         halfWidth, -halfHeight, -halfDepth,  // 4
        -halfWidth, -halfHeight, -halfDepth,  // 5
        -halfWidth,  halfHeight, -halfDepth,  // 6
         halfWidth,  halfHeight, -halfDepth,  // 7
        // Left face (X-)
        -halfWidth, -halfHeight, -halfDepth,  // 8
        -halfWidth, -halfHeight,  halfDepth,  // 9
        -halfWidth,  halfHeight,  halfDepth,  // 10
        -halfWidth,  halfHeight, -halfDepth,  // 11
        // Right face (X+)
         halfWidth, -halfHeight,  halfDepth,  // 12
         halfWidth, -halfHeight, -halfDepth,  // 13
         halfWidth,  halfHeight, -halfDepth,  // 14
         halfWidth,  halfHeight,  halfDepth,  // 15
        // Top face (Y+)
        -halfWidth,  halfHeight,  halfDepth,  // 16
         halfWidth,  halfHeight,  halfDepth,  // 17
         halfWidth,  halfHeight, -halfDepth,  // 18
        -halfWidth,  halfHeight, -halfDepth,  // 19
        // Bottom face (Y-)
        -halfWidth, -halfHeight, -halfDepth,  // 20
         halfWidth, -halfHeight, -halfDepth,  // 21
         halfWidth, -halfHeight,  halfDepth,  // 22
        -halfWidth, -halfHeight,  halfDepth,  // 23
    )
    
    // Cube indices (12 triangles = 36 indices)
    val indices = shortArrayOf(
        // Front face
        0, 1, 2,  2, 3, 0,
        // Back face
        4, 5, 6,  6, 7, 4,
        // Left face
        8, 9, 10,  10, 11, 8,
        // Right face
        12, 13, 14,  14, 15, 12,
        // Top face
        16, 17, 18,  18, 19, 16,
        // Bottom face
        20, 21, 22,  22, 23, 20,
    )
    
    // Normals for each vertex (24 normals - one per vertex)
    val normals = floatArrayOf(
        // Front face (Z+)
         0f,  0f,  1f,  // 0
         0f,  0f,  1f,  // 1
         0f,  0f,  1f,  // 2
         0f,  0f,  1f,  // 3
        // Back face (Z-)
         0f,  0f, -1f,  // 4
         0f,  0f, -1f,  // 5
         0f,  0f, -1f,  // 6
         0f,  0f, -1f,  // 7
        // Left face (X-)
        -1f,  0f,  0f,  // 8
        -1f,  0f,  0f,  // 9
        -1f,  0f,  0f,  // 10
        -1f,  0f,  0f,  // 11
        // Right face (X+)
         1f,  0f,  0f,  // 12
         1f,  0f,  0f,  // 13
         1f,  0f,  0f,  // 14
         1f,  0f,  0f,  // 15
        // Top face (Y+)
         0f,  1f,  0f,  // 16
         0f,  1f,  0f,  // 17
         0f,  1f,  0f,  // 18
         0f,  1f,  0f,  // 19
        // Bottom face (Y-)
         0f, -1f,  0f,  // 20
         0f, -1f,  0f,  // 21
         0f, -1f,  0f,  // 22
         0f, -1f,  0f,  // 23
    )
    
    // UVs for each vertex (24 UVs - required for PBR materials)
    val uvs = floatArrayOf(
        // Front face
        0f, 0f,  // 0
        1f, 0f,  // 1
        1f, 1f,  // 2
        0f, 1f,  // 3
        // Back face
        0f, 0f,  // 4
        1f, 0f,  // 5
        1f, 1f,  // 6
        0f, 1f,  // 7
        // Left face
        0f, 0f,  // 8
        1f, 0f,  // 9
        1f, 1f,  // 10
        0f, 1f,  // 11
        // Right face
        0f, 0f,  // 12
        1f, 0f,  // 13
        1f, 1f,  // 14
        0f, 1f,  // 15
        // Top face
        0f, 0f,  // 16
        1f, 0f,  // 17
        1f, 1f,  // 18
        0f, 1f,  // 19
        // Bottom face
        0f, 0f,  // 20
        1f, 0f,  // 21
        1f, 1f,  // 22
        0f, 1f,  // 23
    )
    
    // Create vertex buffer with 3 buffers: position, tangents, UV
    // MaterialLoader.createColorInstance requires TANGENTS attribute for PBR materials
    val vertexBuffer = VertexBuffer.Builder()
        .vertexCount(24) // 24 vertices (4 per face × 6 faces)
        .bufferCount(3) // Position (0), Tangents (1), UV (2)
        .attribute(
            VertexBuffer.VertexAttribute.POSITION,
            0,
            VertexBuffer.AttributeType.FLOAT3,
            0,
            0
        )
        .attribute(
            VertexBuffer.VertexAttribute.TANGENTS,
            1,
            VertexBuffer.AttributeType.FLOAT4,
            0,
            0
        )
        .normalized(VertexBuffer.VertexAttribute.TANGENTS)
        .attribute(
            VertexBuffer.VertexAttribute.UV0,
            2,
            VertexBuffer.AttributeType.FLOAT2,
            0,
            0
        )
        .build(engine)
    
    // Upload vertex data (buffer 0)
    val vertexBufferData = ByteBuffer
        .allocateDirect(vertices.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .put(vertices)
    vertexBuffer.setBufferAt(
        engine,
        0,
        vertexBufferData.rewind()
    )
    
    // Generate tangents (Float4: tangent.x, tangent.y, tangent.z, handedness)
    // For a cube, we can compute simple tangents based on face orientation
    val tangents = FloatArray(24 * 4) // 24 vertices × 4 components
    for (i in 0 until 24) {
        val normalIndex = i * 3
        val nx = normals[normalIndex]
        val ny = normals[normalIndex + 1]
        val nz = normals[normalIndex + 2]
        
        // Compute tangent: perpendicular to normal, pointing in U direction
        // For each face, tangent points along the U axis direction
        val tangentIndex = i * 4
        when {
            // Front/Back faces: tangent along X axis
            kotlin.math.abs(nz) > 0.5f -> {
                tangents[tangentIndex] = 1f
                tangents[tangentIndex + 1] = 0f
                tangents[tangentIndex + 2] = 0f
            }
            // Left/Right faces: tangent along Z axis
            kotlin.math.abs(nx) > 0.5f -> {
                tangents[tangentIndex] = 0f
                tangents[tangentIndex + 1] = 0f
                tangents[tangentIndex + 2] = if (nx > 0) -1f else 1f
            }
            // Top/Bottom faces: tangent along X axis
            else -> {
                tangents[tangentIndex] = 1f
                tangents[tangentIndex + 1] = 0f
                tangents[tangentIndex + 2] = 0f
            }
        }
        // Handedness (w component): typically 1.0 for right-handed coordinate system
        tangents[tangentIndex + 3] = 1f
    }
    
    // Upload tangent data (buffer 1)
    val tangentBufferData = ByteBuffer
        .allocateDirect(tangents.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .put(tangents)
    vertexBuffer.setBufferAt(
        engine,
        1,
        tangentBufferData.rewind()
    )
    
    // Upload UV data (buffer 2)
    val uvBufferData = ByteBuffer
        .allocateDirect(uvs.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .put(uvs)
    vertexBuffer.setBufferAt(
        engine,
        2,
        uvBufferData.rewind()
    )
    
    // Create index buffer
    // Convert short indices to int for UINT type
    val intIndices = IntArray(indices.size) { indices[it].toInt() }
    val indexBuffer = IndexBuffer.Builder()
        .indexCount(intIndices.size)
        .bufferType(IndexBuffer.Builder.IndexType.UINT)
        .build(engine)
    
    val indexBufferData = ByteBuffer
        .allocateDirect(intIndices.size * 4)
        .order(ByteOrder.nativeOrder())
        .asIntBuffer()
        .put(intIndices)
    indexBuffer.setBuffer(engine, indexBufferData.rewind())
    
    // Create renderable
    // Box constructor: Box(center: FloatArray, halfExtent: FloatArray)
    // Center is at origin (0, 0, 0), halfExtent is (halfWidth, halfHeight, halfDepth)
    val center = floatArrayOf(0f, 0f, 0f)
    val halfExtent = floatArrayOf(halfWidth, halfHeight, halfDepth)
    val box = Box(center, halfExtent)
    
    RenderableManager.Builder(1)
        .boundingBox(box)
        .material(0, materialInstance)
        .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertexBuffer, indexBuffer, 0, intIndices.size)
        .culling(false) // Disable culling for transparency
        .castShadows(false)
        .receiveShadows(false)
        .build(engine, entity)
    
    return entity
}

/**
 * Destroys a renderable entity and its associated resources
 */
fun destroyRenderable(engine: Engine, entity: Int) {
    val entityManager = EntityManager.get()
    engine.destroyEntity(entity)
}

/**
 * Creates a SceneView Cube Node using SceneView's built-in Cube geometry API
 * This is much simpler than low-level Filament and integrates better with SceneView
 * @param engine Filament Engine instance
 * @param context Android Context for MaterialLoader
 * @param width Cube width (X dimension)
 * @param height Cube height (Y dimension)
 * @param depth Cube depth (Z dimension)
 * @param color RGB color as Int (0xRRGGBB)
 * @param opacity Opacity value (0.0 to 1.0)
 * @param position Position as Float3 (x, y, z)
 * @return GeometryNode with Cube geometry attached
 */
fun createSceneViewCubeNode(
    engine: Engine,
    context: Context,
    width: Float,
    height: Float,
    depth: Float,
    color: Int,
    opacity: Float,
    position: io.github.sceneview.math.Position
): GeometryNode {
    // Create SceneView Cube geometry using Builder pattern
    // According to API: https://sceneview.github.io/api/sceneview-android/sceneview/io.github.sceneview.geometries/-cube/-builder/index.html
    val cube = Cube.Builder()
        .size(Size(width, height, depth))
        .build(engine)
    
    // Create material using MaterialLoader
    val materialLoader = MaterialLoader(engine, context)
    val r = ((color shr 16) and 0xFF) / 255f
    val g = ((color shr 8) and 0xFF) / 255f
    val b = (color and 0xFF) / 255f
    
    val materialInstance = materialLoader.createColorInstance(
        color = colorOf(r, g, b, opacity),
        metallic = 0.0f,
        roughness = 0.5f
    )
    
    // Create GeometryNode with Cube geometry
    val cubeNode = GeometryNode(
        engine = engine,
        geometry = cube,
        materialInstance = materialInstance
    )
    
    // Set position
    cubeNode.position = position
    
    return cubeNode
}



