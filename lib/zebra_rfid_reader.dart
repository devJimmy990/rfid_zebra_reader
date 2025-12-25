import 'dart:async';
import 'package:flutter/services.dart';

class ZebraRfidReader {
  static const _methodChannel = MethodChannel('zebra_rfid_reader');
  static const _eventChannel = EventChannel('zebra_rfid_reader/events');

  static Stream<RfidEvent>? _eventStream;

  /// Get stream of RFID events (tags, triggers, connection status)
  static Stream<RfidEvent> get eventStream {
    _eventStream ??= _eventChannel
        .receiveBroadcastStream()
        .map((dynamic event) => RfidEvent.fromMap(Map<String, dynamic>.from(event)));
    return _eventStream!;
  }

  /// Check if reader is connected
  static Future<String?> checkReaderConnection() async {
    try {
      return await _methodChannel.invokeMethod('checkReaderConnection');
    } catch (e) {
      rethrow;
    }
  }

  /// Connect to RFID reader
  static Future<String?> connect() async {
    try {
      return await _methodChannel.invokeMethod('connect');
    } catch (e) {
      rethrow;
    }
  }

  /// Disconnect from RFID reader
  static Future<String?> disconnect() async {
    try {
      return await _methodChannel.invokeMethod('disconnect');
    } catch (e) {
      rethrow;
    }
  }

  /// Check connection status
  static Future<bool> isConnected() async {
    try {
      final result = await _methodChannel.invokeMethod('isConnected');
      return result == true;
    } catch (e) {
      return false;
    }
  }

  /// Start RFID inventory (scanning)
  static Future<String?> startInventory() async {
    try {
      return await _methodChannel.invokeMethod('startInventory');
    } catch (e) {
      rethrow;
    }
  }

  /// Stop RFID inventory (scanning)
  static Future<String?> stopInventory() async {
    try {
      return await _methodChannel.invokeMethod('stopInventory');
    } catch (e) {
      rethrow;
    }
  }

  /// Set antenna power level (0-270)
  static Future<String?> setAntennaPower(int powerLevel) async {
    try {
      return await _methodChannel.invokeMethod('setAntennaPower', {
        'powerLevel': powerLevel,
      });
    } catch (e) {
      rethrow;
    }
  }

  /// Get platform version
  static Future<String?> getPlatformVersion() async {
    try {
      return await _methodChannel.invokeMethod('getPlatformVersion');
    } catch (e) {
      rethrow;
    }
  }
}

/// RFID Event types
enum RfidEventType {
  tagRead,
  trigger,
  connected,
  disconnected,
  readerAppeared,
  readerDisappeared,
  error,
  unknown,
}

/// RFID Event class
class RfidEvent {
  final RfidEventType type;
  final dynamic data;

  RfidEvent({
    required this.type,
    this.data,
  });

  factory RfidEvent.fromMap(Map<String, dynamic> map) {
    final typeString = map['type'] as String?;
    RfidEventType eventType;

    switch (typeString) {
      case 'tagRead':
        eventType = RfidEventType.tagRead;
        break;
      case 'trigger':
        eventType = RfidEventType.trigger;
        break;
      case 'connected':
        eventType = RfidEventType.connected;
        break;
      case 'disconnected':
        eventType = RfidEventType.disconnected;
        break;
      case 'readerAppeared':
        eventType = RfidEventType.readerAppeared;
        break;
      case 'readerDisappeared':
        eventType = RfidEventType.readerDisappeared;
        break;
      case 'error':
        eventType = RfidEventType.error;
        break;
      default:
        eventType = RfidEventType.unknown;
    }

    return RfidEvent(
      type: eventType,
      data: map,
    );
  }

  /// Get tags from tagRead event
  List<RfidTag>? get tags {
    if (type == RfidEventType.tagRead && data is Map) {
      final tagsList = (data as Map)['tags'] as List?;
      return tagsList?.map((t) => RfidTag.fromMap(Map<String, dynamic>.from(t))).toList();
    }
    return null;
  }

  /// Get trigger state from trigger event
  bool? get triggerPressed {
    if (type == RfidEventType.trigger && data is Map) {
      return (data as Map)['pressed'] as bool?;
    }
    return null;
  }

  /// Get error message
  String? get errorMessage {
    if (type == RfidEventType.error && data is Map) {
      return (data as Map)['message'] as String?;
    }
    return null;
  }

  /// Get reader name
  String? get readerName {
    if (data is Map) {
      return (data as Map)['reader'] as String? ?? (data as Map)['name'] as String?;
    }
    return null;
  }

  @override
  String toString() {
    return 'RfidEvent{type: $type, data: $data}';
  }
}

/// RFID Tag class
class RfidTag {
  final String tagId;
  final int rssi;
  final int antennaId;
  final int count;

  RfidTag({
    required this.tagId,
    required this.rssi,
    required this.antennaId,
    required this.count,
  });

  factory RfidTag.fromMap(Map<String, dynamic> map) {
    return RfidTag(
      tagId: map['tagId'] as String? ?? '',
      rssi: map['rssi'] as int? ?? 0,
      antennaId: map['antennaId'] as int? ?? 0,
      count: map['count'] as int? ?? 0,
    );
  }

  @override
  String toString() {
    return 'RfidTag{tagId: $tagId, rssi: $rssi, antennaId: $antennaId, count: $count}';
  }
}