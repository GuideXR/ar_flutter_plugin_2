# Procedural Translucent Cube Implementation

## Overview

This document describes the implementation of procedural translucent cube rendering for Android using SceneView's built-in Cube geometry API. This feature allows creating and managing transparent 3D bounding boxes in AR scenes without requiring external 3D model files.

## Implementation Date

January 2025

## Features

- ✅ Create translucent cubes with custom dimensions (width, height, depth)
- ✅ Set custom color and opacity
- ✅ Position cubes at specific 3D coordinates
- ✅ Update cube position dynamically
- ✅ Update cube dimensions (requires recreation)
- ✅ Proper lifecycle management and cleanup
- ✅ Crash-safe disposal handling

## Architecture

### Flutter Side

The Flutter side communicates with the native Android implementation via a MethodChannel:

```dart
// Method channel name: 'arobjects_0'
final methodChannel = const MethodChannel('arobjects_0');

// Create a cube
await methodChannel.invokeMethod('addPrimitiveCube', {
  'width': 1.0,
  'height': 2.0,
  'depth': 1.0,
  'color': 0xFF00FF00, // Green
  'opacity': 0.3,
  'position': {'x': 0.0, 'y': 0.0, 'z': -2.0},
  'rotation': {'x': 0.0, 'y': 0.0, 'z': 0.0, 'w': 1.0},
});

// Update cube position
await methodChannel.invokeMethod('updatePrimitiveCube', {
  'width': 1.5,  // Optional: triggers recreation
  'height': 2.5, // Optional: triggers recreation
  'depth': 1.5,  // Optional: triggers recreation
  'color': 0xFF00FF00, // Optional
  'opacity': 0.3,      // Optional
  'position': {'x': 0.5, 'y': 0.5, 'z': -2.0},
});

// Remove cube
await methodChannel.invokeMethod('removePrimitiveCube');
```

### Android Native Side

#### Files Modified/Created

1. **`FilamentHelpers.kt`** (New file)
   - Location: `android/src/main/kotlin/com/uhg0/ar_flutter_plugin_2/FilamentHelpers.kt`
   - Contains helper functions for creating SceneView Cube geometry

2. **`ArView.kt`** (Modified)
   - Location: `android/src/main/kotlin/com/uhg0/ar_flutter_plugin_2/ArView.kt`
   - Added method channel handlers for cube operations

#### Key Components

##### 1. FilamentHelpers.kt

**`createSceneViewCubeNode()` Function:**

```kotlin
fun createSceneViewCubeNode(
    engine: Engine,
    context: Context,
    width: Float,
    height: Float,
    depth: Float,
    color: Int,
    opacity: Float,
    position: io.github.sceneview.math.Position
): GeometryNode
```

**Purpose:**
- Creates a SceneView Cube geometry using the Builder pattern
- Applies transparent material using MaterialLoader
- Returns a GeometryNode ready to be added to the scene

**Implementation Details:**
- Uses `Cube.Builder()` with `Size(width, height, depth)`
- Creates material using `MaterialLoader.createColorInstance()` with RGBA color
- Sets metallic and roughness parameters for PBR rendering
- Positions the node at the specified coordinates

**Why SceneView Cube API?**
- Much simpler than low-level Filament (20 lines vs 300+ lines)
- Better integration with SceneView's rendering pipeline
- Automatic resource management
- Better performance (SceneView-optimized)

##### 2. ArView.kt Method Handlers

**`handleAddPrimitiveCube()`:**
- Parses arguments (width, height, depth, color, opacity, position, rotation)
- Removes existing cube if any
- Creates new cube using `createSceneViewCubeNode()`
- Adds cube node to SceneView
- Stores reference for future updates/removal

**`handleUpdatePrimitiveCube()`:**
- Updates cube position if provided
- Recreates cube if dimensions change (SceneView Cube doesn't support dynamic resizing)
- Preserves color and opacity from update call

**`handleRemovePrimitiveCube()`:**
- Safely removes cube node from SceneView
- Handles scene destruction gracefully (prevents crashes)
- Cleans up resources

**Lifecycle Management:**
- Cube is cleaned up in `dispose()` method before scene destruction
- Prevents `IllegalStateException: Calling method on destroyed Scene` errors

## Usage Example

### Flutter/Dart Side

```dart
import 'package:flutter/services.dart';

class BoundingBoxScreen extends StatefulWidget {
  // ...
}

class _BoundingBoxScreenState extends State<BoundingBoxScreen> {
  MethodChannel? _methodChannel;

  @override
  void onARViewCreated(...) {
    if (Platform.isAndroid) {
      _methodChannel = const MethodChannel('arobjects_0');
    }
  }

  Future<void> createBoundingBox() async {
    if (_methodChannel == null) return;

    final dimensions = calculateBoundingBoxDimensions();
    
    await _methodChannel!.invokeMethod('addPrimitiveCube', {
      'width': dimensions['width'],
      'height': dimensions['height'],
      'depth': dimensions['depth'],
      'color': Colors.green.value,
      'opacity': 0.3,
      'position': {
        'x': dimensions['center'].x,
        'y': dimensions['center'].y,
        'z': dimensions['center'].z,
      },
      'rotation': {
        'x': 0.0,
        'y': 0.0,
        'z': 0.0,
        'w': 1.0,
      },
    });
  }

  Future<void> updateBoundingBox() async {
    if (_methodChannel == null) return;

    final dimensions = calculateBoundingBoxDimensions();
    
    await _methodChannel!.invokeMethod('updatePrimitiveCube', {
      'width': dimensions['width'],
      'height': dimensions['height'],
      'depth': dimensions['depth'],
      'color': Colors.green.value,
      'opacity': 0.3,
      'position': {
        'x': dimensions['center'].x,
        'y': dimensions['center'].y,
        'z': dimensions['center'].z,
      },
    });
  }

  Future<void> removeBoundingBox() async {
    if (_methodChannel == null) return;

    await _methodChannel!.invokeMethod('removePrimitiveCube');
  }
}
```

## Technical Details

### Material Transparency

The implementation uses `MaterialLoader.createColorInstance()` which supports RGBA colors directly. The opacity parameter (0.0 to 1.0) is passed as the alpha channel:

```kotlin
val materialInstance = materialLoader.createColorInstance(
    color = colorOf(r, g, b, opacity),
    metallic = 0.0f,
    roughness = 0.5f
)
```

### Cube Geometry

SceneView's Cube geometry is created using the Builder pattern:

```kotlin
val cube = Cube.Builder()
    .size(Size(width, height, depth))
    .build(engine)
```

The cube is centered at the origin by default, and positioning is handled by the GeometryNode's position property.

### Transform Management

- Position is set directly on the `GeometryNode.position` property
- Rotation is set on `GeometryNode.rotation` (Euler angles, not quaternion)
- SceneView handles transform synchronization automatically

### Resource Cleanup

**Important:** The cube node must be removed before the SceneView is destroyed to prevent crashes:

```kotlin
override fun dispose() {
    // Clean up bounding box cube before destroying scene
    boundingBoxCubeNode?.let { node ->
        try {
            if (sceneView.scene != null) {
                sceneView.removeChildNode(node)
            }
            node.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning up cube node: ${e.message}")
        }
        boundingBoxCubeNode = null
    }
    
    sceneView.destroy()
    // ... other cleanup
}
```

## Limitations

1. **Dynamic Resizing:** SceneView Cube doesn't support dynamic resizing. When dimensions change, the cube must be recreated (handled automatically in `handleUpdatePrimitiveCube()`).

2. **Material Transparency:** While `MaterialLoader.createColorInstance()` supports transparency, the exact rendering behavior may vary depending on the SceneView version and device capabilities.

3. **Platform Support:** This implementation is Android-only. iOS would require a different approach using ARKit's geometry APIs.

## Future Improvements

1. **Rotation Support:** Currently uses identity rotation. Could add support for custom rotations based on bounding box orientation.

2. **Material Customization:** Could expose more material parameters (metallic, roughness) via method channel.

3. **Multiple Cubes:** Current implementation supports one cube at a time. Could be extended to support multiple cubes with unique IDs.

4. **iOS Implementation:** Port to iOS using ARKit's geometry APIs for cross-platform consistency.

## Related Files

- `android/src/main/kotlin/com/uhg0/ar_flutter_plugin_2/FilamentHelpers.kt` - Helper functions
- `android/src/main/kotlin/com/uhg0/ar_flutter_plugin_2/ArView.kt` - Method channel handlers
- Method channel: `arobjects_0` (defined in ArView.kt)

## References

- [SceneView Cube API Documentation](https://sceneview.github.io/api/sceneview-android/sceneview/io.github.sceneview.geometries/-cube/index.html)
- [SceneView Geometry Documentation](https://sceneview.github.io/api/sceneview-android/sceneview/io.github.sceneview.geometries/-geometry/index.html)

