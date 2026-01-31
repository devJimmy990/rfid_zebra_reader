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
  static StreamController<RfidStatus>? _statusController;
  static Timer? _statusPollingTimer;
  static RfidStatus? _lastStatus;

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

  /// Get stream of RFID status updates
  /// Automatically polls status until connected or error occurs
  static Stream<RfidStatus> get statusStream {
    if (_statusController == null || _statusController!.isClosed) {
      _statusController = StreamController<RfidStatus>.broadcast(
        onListen: _startStatusPolling,
        onCancel: _stopStatusPolling,
      );
    }
    return _statusController!.stream;
  }

  /// Start polling status from native
  static void _startStatusPolling() {
    _logger.debug('Starting status polling', source: 'StatusPolling');
    _pollStatus(); // Initial poll
    _statusPollingTimer?.cancel();
    _statusPollingTimer = Timer.periodic(
      const Duration(milliseconds: 500),
      (_) => _pollStatus(),
    );
  }

  /// Stop polling status
  static void _stopStatusPolling() {
    _logger.debug('Stopping status polling', source: 'StatusPolling');
    _statusPollingTimer?.cancel();
    _statusPollingTimer = null;
  }

  /// Poll status and emit to stream
  static Future<void> _pollStatus() async {
    try {
      final status = await _getStatusInternal();

      // Only emit if status changed or if it's an important state
      if (_shouldEmitStatus(status)) {
        _lastStatus = status;
        _statusController?.add(status);

        // Stop polling if we reached a stable state (ready or error)
        if (status.isReady || (status.hasError && !status.isReconnecting)) {
          _stopStatusPolling();
        }
      }
    } catch (e) {
      _logger.error('Status polling error', source: 'StatusPolling', error: e);
    }
  }

  /// Check if we should emit this status update
  static bool _shouldEmitStatus(RfidStatus newStatus) {
    if (_lastStatus == null) return true;

    // Emit if any important field changed
    return _lastStatus!.permissionsGranted != newStatus.permissionsGranted ||
        _lastStatus!.sdkInitialized != newStatus.sdkInitialized ||
        _lastStatus!.readerConnected != newStatus.readerConnected ||
        _lastStatus!.isReconnecting != newStatus.isReconnecting ||
        _lastStatus!.error != newStatus.error;
  }

  /// Initialize the RFID reader
  /// Automatically handles: permissions → SDK init → reader connection
  /// Returns immediately and status updates come through [statusStream]
  static Future<void> initialize() async {
    try {
      _logger.info('Initializing RFID reader...', source: 'initialize');

      // Start status polling to track initialization progress
      _startStatusPolling();

      // Trigger native initialization (fire and forget)
      _methodChannel.invokeMethod('initialize').catchError((error) {
        _logger.error('Initialize error', source: 'initialize', error: error);
      });
    } catch (e, stack) {
      _logger.error('Initialize failed',
          source: 'initialize', error: e, stackTrace: stack);

      // Emit error status
      _statusController?.add(RfidStatus(
        permissionsGranted: false,
        sdkInitialized: false,
        readerConnected: false,
        error: 'Exception: $e',
      ));
    }
  }

  /// Get current status (one-time fetch)
  static Future<RfidStatus> getStatus() async {
    return await _getStatusInternal();
  }

  /// Internal method to get status from native
  static Future<RfidStatus> _getStatusInternal() async {
    try {
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
      _stopStatusPolling();
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

  /// Dispose resources (call when app is closing)
  static void dispose() {
    _stopStatusPolling();
    _statusController?.close();
    _statusController = null;
    _lastStatus = null;
  }
}
