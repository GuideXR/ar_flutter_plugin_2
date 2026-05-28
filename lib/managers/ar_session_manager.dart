import 'dart:math' show sqrt;

import 'package:ar_flutter_plugin_2/datatypes/config_planedetection.dart';
import 'package:ar_flutter_plugin_2/models/ar_anchor.dart';
import 'package:ar_flutter_plugin_2/models/ar_hittest_result.dart';
import 'package:ar_flutter_plugin_2/utils/json_converters.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:vector_math/vector_math_64.dart' hide Colors;

// Type definitions to enforce a consistent use of the API
typedef ARHitResultHandler = void Function(List<ARHitTestResult> hits);
typedef ARPlaneResultHandler = void Function(int planeCount);
typedef ErrorHandler = void Function(String error);

/// Manages the session configuration, parameters and events of an [ARView]
class ARSessionManager {
  /// Platform channel used for communication from and to [ARSessionManager]
  late MethodChannel _channel;

  /// Debugging status flag. If true, all platform calls are printed. Defaults to false.
  final bool debug;

  /// Context of the [ARView] widget that this manager is attributed to
  final BuildContext buildContext;

  /// Determines the types of planes ARCore and ARKit should show
  final PlaneDetectionConfig planeDetectionConfig;

  /// Receives hit results from user taps with tracked planes or feature points
  late ARHitResultHandler onPlaneOrPointTap;

  /// Receives total number of Planes when a plane is detected and added to the view
  ARPlaneResultHandler? onPlaneDetected;

  /// Callback that is triggered once error is triggered
  ErrorHandler? onError;

  ARSessionManager(int id, this.buildContext, this.planeDetectionConfig,
      {this.debug = false}) {
    _channel = MethodChannel('arsession_$id');
    _channel.setMethodCallHandler(_platformCallHandler);
    if (debug) {
      print("ARSessionManager initialized");
    }
  }

  /// Returns the camera pose in Matrix4 format with respect to the world coordinate system of the [ARView]
  Future<Matrix4?> getCameraPose() async {
    try {
      final serializedCameraPose =
          await _channel.invokeMethod<List<dynamic>>('getCameraPose', {});
      return MatrixConverter().fromJson(serializedCameraPose!);
    } catch (e) {
      print('Error caught: ' + e.toString());
      return null;
    }
  }

  /// Returns the given anchor pose in Matrix4 format with respect to the world coordinate system of the [ARView]
  Future<Matrix4?> getPose(ARAnchor anchor) async {
    try {
      if (anchor.name.isEmpty) {
        throw Exception("Anchor can not be resolved. Anchor name is empty.");
      }
      final serializedCameraPose =
          await _channel.invokeMethod<List<dynamic>>('getAnchorPose', {
        "anchorId": anchor.name,
      });
      return MatrixConverter().fromJson(serializedCameraPose!);
    } catch (e) {
      print('Error caught: ' + e.toString());
      return null;
    }
  }

  /// Returns the distance in meters between @anchor1 and @anchor2.
  Future<double?> getDistanceBetweenAnchors(
      ARAnchor anchor1, ARAnchor anchor2) async {
    var anchor1Pose = await getPose(anchor1);
    var anchor2Pose = await getPose(anchor2);
    var anchor1Translation = anchor1Pose?.getTranslation();
    var anchor2Translation = anchor2Pose?.getTranslation();
    if (anchor1Translation != null && anchor2Translation != null) {
      return getDistanceBetweenVectors(anchor1Translation, anchor2Translation);
    } else {
      return null;
    }
  }

  /// Returns the distance in meters between @anchor and device's camera.
  Future<double?> getDistanceFromAnchor(ARAnchor anchor) async {
    Matrix4? cameraPose = await getCameraPose();
    Matrix4? anchorPose = await getPose(anchor);
    Vector3? cameraTranslation = cameraPose?.getTranslation();
    Vector3? anchorTranslation = anchorPose?.getTranslation();
    if (anchorTranslation != null && cameraTranslation != null) {
      return getDistanceBetweenVectors(anchorTranslation, cameraTranslation);
    } else {
      return null;
    }
  }

  /// Returns the distance in meters between @vector1 and @vector2.
  double getDistanceBetweenVectors(Vector3 vector1, Vector3 vector2) {
    num dx = vector1.x - vector2.x;
    num dy = vector1.y - vector2.y;
    num dz = vector1.z - vector2.z;
    double distance = sqrt(dx * dx + dy * dy + dz * dz);
    return distance;
  }

  //Disable Camera
  void disableCamera() {
    _channel.invokeMethod<void>('disableCamera');
  }

  //Enable Camera
  void enableCamera() {
    _channel.invokeMethod<void>('enableCamera');
  }

  //Show or hide planes
  void showPlanes(bool showPlanes) {
    _channel.invokeMethod<void>('showPlanes', {
      "showPlanes": showPlanes,
    });
  }

  Future<void> _platformCallHandler(MethodCall call) {
    if (debug) {
      print('_platformCallHandler call ${call.method} ${call.arguments}');
    }
    try {
      switch (call.method) {
        case 'onError':
          if (onError != null) {
            onError!(call.arguments[0]);
            print(call.arguments);
          } else {
            ScaffoldMessenger.of(buildContext).showSnackBar(SnackBar(
                content: Text(call.arguments[0]),
                action: SnackBarAction(
                    label: 'HIDE',
                    onPressed: ScaffoldMessenger.of(buildContext)
                        .hideCurrentSnackBar)));
          }
          break;
        case 'onPlaneOrPointTap':
          if (onPlaneOrPointTap != null) {
            final rawHitTestResults = call.arguments as List<dynamic>;
            final serializedHitTestResults = rawHitTestResults
                .map(
                    (hitTestResult) => Map<String, dynamic>.from(hitTestResult))
                .toList();
            final hitTestResults = serializedHitTestResults.map((e) {
              return ARHitTestResult.fromJson(e);
            }).toList();
            onPlaneOrPointTap(hitTestResults);
          }
          break;
        case 'onPlaneDetected':
          if (onPlaneDetected != null) {
            final planeCountResult = call.arguments as int;
            onPlaneDetected?.call(planeCountResult);
          }
          break;
        case 'dispose':
          // No-op: native side is already tearing down; don't echo back.
          break;
        case 'getCameraIntrinsics':
          if (onPlaneDetected != null) {
            final result = call.arguments as Map;
            onPlaneDetected?.call(result['width'] as int);
          }
          break;
        default:
          if (debug) {
            print('Unimplemented method ${call.method} ');
          }
      }
    } catch (e) {
      print('Error caught: ' + e.toString());
    }
    return Future.value();
  }

  /// Function to initialize the platform-specific AR view. Can be used to initially set or update session settings.
  /// [customPlaneTexturePath] refers to flutter assets from the app that is calling this function, NOT to assets within this plugin. Make sure
  /// the assets are correctly registered in the pubspec.yaml of the parent app (e.g. the ./example app in this plugin's repo)
  Future<void> onInitialize({
    bool showAnimatedGuide = true,
    bool showFeaturePoints = false,
    bool showPlanes = true,
    String? customPlaneTexturePath,
    bool showWorldOrigin = false,
    bool handleTaps = true,
    bool handlePans = false, // nodes are not draggable by default
    bool handleRotation = false, // nodes can not be rotated by default
  }) async {
    try {
      await _channel.invokeMethod<void>('init', {
        'showAnimatedGuide': showAnimatedGuide,
        'showFeaturePoints': showFeaturePoints,
        'planeDetectionConfig': planeDetectionConfig.index,
        'showPlanes': showPlanes,
        'customPlaneTexturePath': customPlaneTexturePath,
        'showWorldOrigin': showWorldOrigin,
        'handleTaps': handleTaps,
        'handlePans': handlePans,
        'handleRotation': handleRotation,
      });
    } on MissingPluginException catch (e) {
      // Native channel not yet registered — safe to ignore; ARCore will
      // initialise on its own once the platform view binding is complete.
      if (debug) print('ARSessionManager init: channel not ready yet — $e');
    } catch (e) {
      if (debug) print('ARSessionManager init error: $e');
    }
  }

  /// Dispose the AR view on the platforms to pause the scenes and disconnect the platform handlers.
  /// You should call this before removing the AR view to prevent out of memory erros
  dispose() async {
    try {
      await _channel.invokeMethod<void>("dispose");
    } catch (e) {
      print(e);
    }
  }

  /// Returns a future ImageProvider that contains a screenshot of the current AR Scene
  Future<ImageProvider> snapshot() async {
    final result = await _channel.invokeMethod<Uint8List>('snapshot');
    return MemoryImage(result!);
  }

  /// Returns the camera intrinsics from the AR session as a Map (fx, fy, cx, cy, width, height)
  Future<Map<String, dynamic>> getCameraIntrinsics() async {
    final result = await _channel.invokeMethod<Map>('getCameraIntrinsics');
    return Map<String, dynamic>.from(result!);
  }

  /// Returns a future ImageProvider that contains a raw camera image (JPEG) from the current AR frame
  Future<ImageProvider> captureRawImage() async {
    final result = await _channel.invokeMethod<Uint8List>('captureRawImage');
    return MemoryImage(result!);
  }

  // ========== 2-Point Bounding Box Methods ==========

  /// Performs a raycast from the center of the screen to detect planes
  Future<Map<String, dynamic>?> getCenterRaycast() async {
    try {
      final result = await _channel.invokeMethod('getCenterRaycast');
      if (result == null) return null;
      // Properly convert the result to Map<String, dynamic>
      if (result is Map) {
        final converted = Map<String, dynamic>.from(result);
        // Also convert nested position map if it exists
        if (converted['position'] is Map) {
          converted['position'] =
              Map<String, dynamic>.from(converted['position'] as Map);
        }
        return converted;
      }
      return null;
    } catch (e) {
      print('Error getting center raycast: $e');
      return null;
    }
  }

  /// Gets the current camera position and rotation
  Future<Map<String, dynamic>?> getCameraPosition() async {
    try {
      final result = await _channel.invokeMethod('getCameraPosition');
      if (result == null) return null;
      // Properly convert the result to Map<String, dynamic>
      if (result is Map) {
        final converted = Map<String, dynamic>.from(result);
        // Also convert nested position and rotation maps if they exist
        if (converted['position'] is Map) {
          converted['position'] =
              Map<String, dynamic>.from(converted['position'] as Map);
        }
        if (converted['rotation'] is Map) {
          converted['rotation'] =
              Map<String, dynamic>.from(converted['rotation'] as Map);
        }
        return converted;
      }
      return null;
    } catch (e) {
      print('Error getting camera position: $e');
      return null;
    }
  }

  /// Draws a line between two points
  Future<bool> drawLine({
    required Vector3 start,
    required Vector3 end,
    int color = 0xFFFFFF,
    bool dotted = false,
  }) async {
    try {
      await _channel.invokeMethod('drawLine', {
        'start': {'x': start.x, 'y': start.y, 'z': start.z},
        'end': {'x': end.x, 'y': end.y, 'z': end.z},
        'color': color,
        'dotted': dotted,
      });
      return true;
    } catch (e) {
      print('Error drawing line: $e');
      return false;
    }
  }

  /// Clears all drawn lines
  Future<bool> clearLines() async {
    try {
      await _channel.invokeMethod('clearLines');
      return true;
    } catch (e) {
      print('Error clearing lines: $e');
      return false;
    }
  }

  /// Updates the bounding box wireframe with 8 corners
  Future<void> updateBoundingBox(List<Vector3> corners,
      {Color color = Colors.white}) async {
    try {
      final cornersList = corners
          .map((c) => {
                'x': c.x,
                'y': c.y,
                'z': c.z,
              })
          .toList();

      await _channel.invokeMethod('updateBoundingBox', {
        'corners': cornersList,
        'color': color.value,
      });
    } catch (e) {
      print("Error updating bounding box: $e");
    }
  }

  /// Updates the length line (persistent node)
  Future<void> updateLengthLine({
    required Vector3 start,
    required Vector3 end,
    Color color = Colors.white,
  }) async {
    try {
      await _channel.invokeMethod('updateLengthLine', {
        'start': {'x': start.x, 'y': start.y, 'z': start.z},
        'end': {'x': end.x, 'y': end.y, 'z': end.z},
        'color': color.value,
      });
    } catch (e) {
      print("Error updating length line: $e");
    }
  }

  /// Adds a ground point marker (native node)
  Future<void> addGroundPoint(Vector3 position,
      {Color color = Colors.white}) async {
    try {
      await _channel.invokeMethod('addGroundPoint', {
        'position': {'x': position.x, 'y': position.y, 'z': position.z},
        'color': color.value,
      });
    } catch (e) {
      print("Error adding ground point: $e");
    }
  }
}
