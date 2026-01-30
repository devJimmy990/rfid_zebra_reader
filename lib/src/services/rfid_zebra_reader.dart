import 'dart:async';

import 'package:flutter/services.dart';
import 'package:rfid_zebra_reader/src/models/rfid_event.dart';
import 'package:rfid_zebra_reader/src/models/rfid_status.dart';
import 'package:rfid_zebra_reader/src/services/app_logger.dart';

class ZebraRfidReader {
  static const _methodChannel = MethodChannel('rfid_zebra_reader');
  static const _eventChannel = EventChannel('rfid_zebra_reader/events');
  static final _logger = AppLogger();

  static Stream<RfidEvent>? _eventStream;

  /// Get stream of RFID events
  static Stream<RfidEvent> get eventStream {
    _eventStream ??= _eventChannel.receiveBroadcastStream().map((event) {
      final Map<String, dynamic> eventMap = Map<String, dynamic>.from(event);
      _logger.debug('Event: ${eventMap['type']}', source: 'EventStream');
      return RfidEvent.fromMap(eventMap);
    }).handleError((error) {
      _logger.error('Event stream error', source: 'EventStream', error: error);
      throw error;
    });

    return _eventStream!;
  }

  /// Initialize the RFID reader
  /// Automatically handles: permissions → SDK init → reader connection
  /// Returns [RfidStatus] with full status information
  static Future<RfidStatus> initialize() async {
    try {
      _logger.info('Initializing RFID reader...', source: 'initialize');

      final result = await _methodChannel
          .invokeMethod<Map<dynamic, dynamic>>('initialize');

      if (result == null) {
        _logger.error('No response from native', source: 'initialize');
        return RfidStatus(
          permissionsGranted: false,
          sdkInitialized: false,
          readerConnected: false,
          error: 'No response from native',
        );
      }

      final status = RfidStatus.fromMap(Map<String, dynamic>.from(result));

      if (status.hasError) {
        _logger.warning('Initialize completed with error: ${status.error}',
            source: 'initialize');
      } else {
        _logger.info('Initialize successful: ${status.readerName}',
            source: 'initialize');
      }

      return status;
    } catch (e, stack) {
      _logger.error('Initialize failed',
          source: 'initialize', error: e, stackTrace: stack);
      return RfidStatus(
        permissionsGranted: false,
        sdkInitialized: false,
        readerConnected: false,
        error: 'Exception: $e',
      );
    }
  }

  /// Get current status
  static Future<RfidStatus> getStatus() async {
    try {
      _logger.debug('Getting status...', source: 'getStatus');

      final result =
          await _methodChannel.invokeMethod<Map<dynamic, dynamic>>('getStatus');

      if (result == null) {
        return RfidStatus(
          permissionsGranted: false,
          sdkInitialized: false,
          readerConnected: false,
          error: 'No response',
        );
      }

      return RfidStatus.fromMap(Map<String, dynamic>.from(result));
    } catch (e, stack) {
      _logger.error('Get status failed',
          source: 'getStatus', error: e, stackTrace: stack);
      return RfidStatus(
        permissionsGranted: false,
        sdkInitialized: false,
        readerConnected: false,
        error: 'Exception: $e',
      );
    }
  }

  /// Check if permissions are granted
  static Future<bool> isPermissionGranted() async {
    try {
      final result = await _methodChannel
          .invokeMethod<Map<dynamic, dynamic>>('isPermissionGranted');
      return result?['granted'] as bool? ?? false;
    } catch (e) {
      _logger.error('Permission check failed',
          source: 'isPermissionGranted', error: e);
      return false;
    }
  }

  /// Disconnect from reader (manual)
  static Future<bool> disconnect() async {
    try {
      _logger.info('Disconnecting...', source: 'disconnect');
      await _methodChannel.invokeMethod('disconnect');
      _logger.info('Disconnected', source: 'disconnect');
      return true;
    } catch (e, stack) {
      _logger.error('Disconnect failed',
          source: 'disconnect', error: e, stackTrace: stack);
      return false;
    }
  }

  /// Start scanning for RFID tags
  static Future<bool> startInventory() async {
    try {
      _logger.info('Starting inventory...', source: 'startInventory');
      await _methodChannel.invokeMethod('startInventory');
      _logger.info('Inventory started', source: 'startInventory');
      return true;
    } catch (e, stack) {
      _logger.error('Start inventory failed',
          source: 'startInventory', error: e, stackTrace: stack);
      return false;
    }
  }

  /// Stop scanning for RFID tags
  static Future<bool> stopInventory() async {
    try {
      _logger.info('Stopping inventory...', source: 'stopInventory');
      await _methodChannel.invokeMethod('stopInventory');
      _logger.info('Inventory stopped', source: 'stopInventory');
      return true;
    } catch (e, stack) {
      _logger.error('Stop inventory failed',
          source: 'stopInventory', error: e, stackTrace: stack);
      return false;
    }
  }

  /// Set antenna power level (0 to maxPower, typically 270)
  static Future<bool> setAntennaPower(int powerLevel) async {
    try {
      _logger.info('Setting power to $powerLevel...',
          source: 'setAntennaPower');
      await _methodChannel
          .invokeMethod('setAntennaPower', {'powerLevel': powerLevel});
      _logger.info('Power set to $powerLevel', source: 'setAntennaPower');
      return true;
    } catch (e, stack) {
      _logger.error('Set power failed',
          source: 'setAntennaPower', error: e, stackTrace: stack);
      return false;
    }
  }

  /// Get current antenna power level
  static Future<Map<String, int>> getAntennaPower() async {
    try {
      _logger.debug('Getting antenna power...', source: 'getAntennaPower');

      final result = await _methodChannel
          .invokeMethod<Map<dynamic, dynamic>>('getAntennaPower');

      if (result == null) {
        return {'currentPower': 0, 'maxPower': 270};
      }

      return {
        'currentPower': result['currentPower'] as int? ?? 0,
        'maxPower': result['maxPower'] as int? ?? 270,
      };
    } catch (e, stack) {
      _logger.error('Get power failed',
          source: 'getAntennaPower', error: e, stackTrace: stack);
      return {'currentPower': 0, 'maxPower': 270};
    }
  }

  /// Get platform version
  static Future<String> getPlatformVersion() async {
    try {
      final result = await _methodChannel
          .invokeMethod<Map<dynamic, dynamic>>('getPlatformVersion');
      return result?['version'] as String? ?? 'Unknown';
    } catch (e) {
      _logger.error('Get platform version failed',
          source: 'getPlatformVersion', error: e);
      return 'Unknown';
    }
  }
}
