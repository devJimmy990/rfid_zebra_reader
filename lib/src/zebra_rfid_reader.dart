import 'dart:async';

import 'package:flutter/services.dart';
import 'package:zebra_rfid_reader/src/app_logger.dart';
import 'package:zebra_rfid_reader/src/models/rfid_event.dart';

class ZebraRfidReader {
  static const _methodChannel = MethodChannel('zebra_rfid_reader');
  static const _eventChannel = EventChannel('zebra_rfid_reader/events');
  static final _logger = AppLogger();

  static Stream<RfidEvent>? _eventStream;

  /// Get stream of RFID events with full status logging
  static Stream<RfidEvent> get eventStream {
    try {
      _logger.debug('Initializing event stream', source: 'ZebraRfidReader');

      _eventStream ??= _eventChannel
          .receiveBroadcastStream()
          .map((dynamic eventRaw) {
            final Map<String, dynamic> event = Map<String, dynamic>.from(
              eventRaw,
            );

            _logger.debug(
              'Event received: ${event['type']}',
              source: 'EventStream',
            );

            if (event['status'] != null) {
              _logger.debug(
                'Native status:\n${event['status']}',
                source: 'EventStream',
              );
            }

            return RfidEvent.fromMap(event);
          })
          .handleError((error) {
            _logger.error(
              'Event stream error',
              source: 'EventStream',
              error: error,
            );
            throw error;
          });

      return _eventStream!;
    } catch (e, stack) {
      _logger.critical(
        'Failed to create event stream',
        source: 'ZebraRfidReader',
        error: e,
        stackTrace: stack,
      );
      rethrow;
    }
  }

  /// Initialize SDK - MUST be called first
  static Future<String> initialize() async {
    try {
      _logger.info('Initializing SDK...', source: 'initialize');

      final result = await _methodChannel.invokeMethod<Map<dynamic, dynamic>>(
        'initialize',
      );

      if (result == null) {
        throw PlatformException(
          code: 'NO_RESULT',
          message: 'No response from native',
        );
      }

      final String message = result['message'] as String? ?? 'Initialized';
      final String nativeStatus = result['status'] as String? ?? 'No status';

      _logger.info(
        'Initialize result: $message\nNative status:\n$nativeStatus',
        source: 'initialize',
      );

      return message;
    } catch (e, stack) {
      String nativeStatus = 'No status available';
      if (e is PlatformException && e.details is Map) {
        final details = e.details as Map<dynamic, dynamic>;
        nativeStatus = details['status'] as String? ?? nativeStatus;
      }

      _logger.error(
        'Failed to initialize SDK\nNative status:\n$nativeStatus',
        source: 'initialize',
        error: e,
        stackTrace: stack,
      );
      rethrow;
    }
  }

  /// Get all available readers
  static Future<List<Map<String, String>>> getAllAvailableReaders() async {
    try {
      _logger.info(
        'Getting available readers...',
        source: 'getAllAvailableReaders',
      );

      final result = await _methodChannel.invokeMethod<Map<dynamic, dynamic>>(
        'getAllAvailableReaders',
      );

      if (result == null) {
        throw PlatformException(
          code: 'NO_RESULT',
          message: 'No response from native',
        );
      }

      final String nativeStatus = result['status'] as String? ?? 'No status';
      _logger.debug(
        'Native status:\n$nativeStatus',
        source: 'getAllAvailableReaders',
      );

      final List<dynamic>? readersList = result['readers'] as List<dynamic>?;

      if (readersList == null) {
        _logger.warning(
          'No readers list in response',
          source: 'getAllAvailableReaders',
        );
        return [];
      }

      final readers = readersList
          .map((e) => Map<String, String>.from(e as Map))
          .toList();

      _logger.info(
        'Found ${readers.length} readers',
        source: 'getAllAvailableReaders',
      );

      return readers;
    } catch (e, stack) {
      String nativeStatus = 'No status available';
      if (e is PlatformException && e.details is Map) {
        final details = e.details as Map<dynamic, dynamic>;
        nativeStatus = details['status'] as String? ?? nativeStatus;
        _logger.error(
          'Failed to get readers\nNative status:\n$nativeStatus',
          source: 'getAllAvailableReaders',
          error: e,
        );
      } else {
        _logger.error(
          'Failed to get readers',
          source: 'getAllAvailableReaders',
          error: e,
          stackTrace: stack,
        );
      }
      rethrow;
    }
  }

  /// Check if reader is connected
  static Future<bool> isConnected() async {
    try {
      _logger.info('Checking reader connection...', source: 'isConnected');

      final result = await _methodChannel.invokeMethod<Map<dynamic, dynamic>>(
        'isReaderConnected',
      );

      if (result == null) return false;

      final bool connected = result['connected'] as bool? ?? false;
      final String nativeStatus = result['status'] as String? ?? 'No status';

      _logger.debug(
        'Reader connected: $connected\nNative status:\n$nativeStatus',
        source: 'isConnected',
      );

      return connected;
    } catch (e, stack) {
      if (e is PlatformException && e.details is Map) {
        final details = e.details as Map;
        final String nativeStatus = details['status'] as String? ?? 'No status';
        _logger.error(
          'Connection check failed\nNative status:\n$nativeStatus',
          source: 'isConnected',
          error: e,
        );
      } else {
        _logger.error(
          'Failed to check reader connection',
          source: 'isConnected',
          error: e,
          stackTrace: stack,
        );
      }
      rethrow;
    }
  }

  /// Connect to RFID reader
  /// [readerName] optional - if null, auto-selects first available reader
  static Future<String> connect({String? readerName}) async {
    try {
      _logger.info(
        'Connecting to reader${readerName != null ? ": $readerName" : " (auto-select)"}...',
        source: 'connect',
      );

      final result = await _methodChannel.invokeMethod<Map<dynamic, dynamic>>(
        'connectReader',
        {'readerName': readerName},
      );

      if (result == null) {
        throw PlatformException(
          code: 'NO_RESULT',
          message: 'No response from native',
        );
      }

      final String message = result['message'] as String? ?? 'Connected';
      final String nativeStatus = result['status'] as String? ?? 'No status';

      _logger.info(
        'Connect result: $message\nNative status:\n$nativeStatus',
        source: 'connect',
      );

      return message;
    } catch (e, stack) {
      String nativeStatus = 'No status available';
      if (e is PlatformException && e.details is Map) {
        final details = e.details as Map<dynamic, dynamic>;
        nativeStatus = details['status'] as String? ?? nativeStatus;
      }

      _logger.error(
        'Failed to connect to reader\nNative status:\n$nativeStatus',
        source: 'connect',
        error: e,
        stackTrace: stack,
      );
      rethrow;
    }
  }

  /// Disconnect from RFID reader
  static Future<String> disconnect() async {
    try {
      _logger.info('Disconnecting from reader...', source: 'disconnect');

      final result = await _methodChannel.invokeMethod<Map<dynamic, dynamic>>(
        'disconnectReader',
      );

      final String message = result?['message'] as String? ?? 'Disconnected';
      final String nativeStatus = result?['status'] as String? ?? 'No status';

      _logger.info(
        'Disconnect result: $message\nNative status:\n$nativeStatus',
        source: 'disconnect',
      );

      return message;
    } catch (e, stack) {
      String nativeStatus = 'No status';
      if (e is PlatformException && e.details is Map) {
        nativeStatus = (e.details as Map)['status'] as String? ?? nativeStatus;
      }

      _logger.error(
        'Failed to disconnect\nNative status:\n$nativeStatus',
        source: 'disconnect',
        error: e,
        stackTrace: stack,
      );
      rethrow;
    }
  }

  /// Start RFID inventory
  static Future<String> startInventory() async {
    try {
      _logger.info('Starting inventory...', source: 'startInventory');

      final result = await _methodChannel.invokeMethod<Map<dynamic, dynamic>>(
        'startInventory',
      );

      final String message =
          result?['message'] as String? ?? 'Inventory started';
      final String nativeStatus = result?['status'] as String? ?? 'No status';

      _logger.info(
        'Start inventory result: $message\nNative status:\n$nativeStatus',
        source: 'startInventory',
      );

      return message;
    } catch (e, stack) {
      String nativeStatus = 'No status';
      if (e is PlatformException && e.details is Map) {
        nativeStatus = (e.details as Map)['status'] as String? ?? nativeStatus;
      }

      _logger.error(
        'Failed to start inventory\nNative status:\n$nativeStatus',
        source: 'startInventory',
        error: e,
        stackTrace: stack,
      );
      rethrow;
    }
  }

  /// Stop RFID inventory
  static Future<String> stopInventory() async {
    try {
      _logger.info('Stopping inventory...', source: 'stopInventory');

      final result = await _methodChannel.invokeMethod<Map<dynamic, dynamic>>(
        'stopInventory',
      );

      final String message =
          result?['message'] as String? ?? 'Inventory stopped';
      final String nativeStatus = result?['status'] as String? ?? 'No status';

      _logger.info(
        'Stop inventory result: $message\nNative status:\n$nativeStatus',
        source: 'stopInventory',
      );

      return message;
    } catch (e, stack) {
      String nativeStatus = 'No status';
      if (e is PlatformException && e.details is Map) {
        nativeStatus = (e.details as Map)['status'] as String? ?? nativeStatus;
      }

      _logger.error(
        'Failed to stop inventory\nNative status:\n$nativeStatus',
        source: 'stopInventory',
        error: e,
        stackTrace: stack,
      );
      rethrow;
    }
  }

  /// Set antenna power level (0 to maxPower)
  static Future<String> setAntennaPower(int powerLevel) async {
    try {
      _logger.info(
        'Setting antenna power to $powerLevel...',
        source: 'setAntennaPower',
      );

      final result = await _methodChannel.invokeMethod<Map<dynamic, dynamic>>(
        'setAntennaPower',
        {'powerLevel': powerLevel},
      );

      final String message = result?['message'] as String? ?? 'Power set';
      final String nativeStatus = result?['status'] as String? ?? 'No status';

      _logger.info(
        'Set power result: $message\nNative status:\n$nativeStatus',
        source: 'setAntennaPower',
      );

      return message;
    } catch (e, stack) {
      String nativeStatus = 'No status';
      if (e is PlatformException && e.details is Map) {
        nativeStatus = (e.details as Map)['status'] as String? ?? nativeStatus;
      }

      _logger.error(
        'Failed to set antenna power\nNative status:\n$nativeStatus',
        source: 'setAntennaPower',
        error: e,
        stackTrace: stack,
      );
      rethrow;
    }
  }

  /// Get antenna power level
  static Future<Map<String, dynamic>> getAntennaPower() async {
    try {
      _logger.debug('Getting antenna power...', source: 'getAntennaPower');

      final result = await _methodChannel.invokeMethod<Map<dynamic, dynamic>>(
        'getAntennaPower',
      );

      if (result == null) {
        throw PlatformException(
          code: 'NO_RESULT',
          message: 'No response from native',
        );
      }

      final int currentPower = result['currentPower'] as int? ?? 0;
      final int maxPower = result['maxPower'] as int? ?? 270;
      final String nativeStatus = result['status'] as String? ?? 'No status';

      _logger.debug(
        'Antenna power: $currentPower / $maxPower\nNative status:\n$nativeStatus',
        source: 'getAntennaPower',
      );

      return {
        'currentPower': currentPower,
        'maxPower': maxPower,
        'nativeStatus': nativeStatus,
      };
    } catch (e, stack) {
      String nativeStatus = 'No status';
      if (e is PlatformException && e.details is Map) {
        nativeStatus = (e.details as Map)['status'] as String? ?? nativeStatus;
      }

      _logger.error(
        'Failed to get antenna power\nNative status:\n$nativeStatus',
        source: 'getAntennaPower',
        error: e,
        stackTrace: stack,
      );
      rethrow;
    }
  }

  /// Get platform version
  static Future<String> getPlatformVersion() async {
    try {
      final result = await _methodChannel.invokeMethod<Map<dynamic, dynamic>>(
        'getPlatformVersion',
      );

      final String version = result?['version'] as String? ?? 'Unknown';

      _logger.debug('Platform version: $version', source: 'getPlatformVersion');

      return version;
    } catch (e, stack) {
      _logger.error(
        'Failed to get platform version',
        source: 'getPlatformVersion',
        error: e,
        stackTrace: stack,
      );
      rethrow;
    }
  }
}
