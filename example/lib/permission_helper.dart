import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';

class PermissionHelper {
  /// Request all permissions needed for RFID reader
  static Future<bool> requestRfidPermissions(BuildContext context) async {
    // List of permissions to request
    final Map<Permission, PermissionStatus> statuses = await [
      Permission.bluetooth,
      Permission.bluetoothScan,
      Permission.bluetoothConnect,
      Permission.location,
    ].request();

    // Check if all permissions granted
    final bool allGranted = statuses.values.every((status) => status.isGranted);

    if (!allGranted) {
      // Show dialog if permissions denied
      if (context.mounted) {
        await _showPermissionDeniedDialog(context, statuses);
      }
      return false;
    }

    return true;
  }

  /// Check if permissions are already granted
  static Future<bool> checkRfidPermissions() async {
    final bool bluetoothGranted = await Permission.bluetooth.isGranted;
    final bool bluetoothScanGranted = await Permission.bluetoothScan.isGranted;
    final bool bluetoothConnectGranted =
        await Permission.bluetoothConnect.isGranted;
    final bool locationGranted = await Permission.location.isGranted;

    return bluetoothGranted &&
        bluetoothScanGranted &&
        bluetoothConnectGranted &&
        locationGranted;
  }

  /// Show dialog when permissions are denied
  static Future<void> _showPermissionDeniedDialog(
    BuildContext context,
    Map<Permission, PermissionStatus> statuses,
  ) async {
    final List<String> deniedPermissions = [];

    statuses.forEach((permission, status) {
      if (!status.isGranted) {
        final String name = permission.toString().split('.').last;
        deniedPermissions.add(name);
      }
    });

    await showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Permissions Required'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'The following permissions are required to use the RFID reader:',
            ),
            const SizedBox(height: 10),
            ...deniedPermissions.map((perm) => Text('• $perm')),
            const SizedBox(height: 10),
            const Text(
              'Please grant these permissions in the app settings.',
              style: TextStyle(fontWeight: FontWeight.bold),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () {
              Navigator.pop(context);
              openAppSettings();
            },
            child: const Text('Open Settings'),
          ),
        ],
      ),
    );
  }

  /// Request location permission specifically
  static Future<bool> requestLocationPermission() async {
    final status = await Permission.location.request();
    return status.isGranted;
  }

  /// Request Bluetooth permissions specifically
  static Future<bool> requestBluetoothPermissions() async {
    final Map<Permission, PermissionStatus> statuses = await [
      Permission.bluetooth,
      Permission.bluetoothScan,
      Permission.bluetoothConnect,
    ].request();

    return statuses.values.every((status) => status.isGranted);
  }
}
