import 'package:rfid_zebra_reader/rfid_zebra_reader.dart';

/// RFID Event types
enum RfidEventType {
  tagRead,
  trigger,
  connected,
  disconnected,
  readerAppeared,
  readerDisappeared,
  initialized,
  reconnecting,
  inventoryStarted,
  inventoryStopped,
  ready,
  error,
  unknown,
}

/// RFID Event class
class RfidEvent {
  final RfidEventType type;
  final dynamic data;
  final String? message;

  RfidEvent({
    required this.type,
    this.data,
    this.message,
  });

  factory RfidEvent.fromMap(Map<String, dynamic> map) {
    final typeString = map['type'] as String?;
    final eventType = _parseEventType(typeString);

    return RfidEvent(
      type: eventType,
      data: map,
      message: map['message'] as String?,
    );
  }

  static RfidEventType _parseEventType(String? typeString) {
    switch (typeString) {
      case 'tagRead':
        return RfidEventType.tagRead;
      case 'trigger':
        return RfidEventType.trigger;
      case 'connected':
        return RfidEventType.connected;
      case 'disconnected':
        return RfidEventType.disconnected;
      case 'readerAppeared':
        return RfidEventType.readerAppeared;
      case 'readerDisappeared':
        return RfidEventType.readerDisappeared;
      case 'initialized':
        return RfidEventType.initialized;
      case 'reconnecting':
        return RfidEventType.reconnecting;
      case 'inventory_started':
        return RfidEventType.inventoryStarted;
      case 'inventory_stopped':
        return RfidEventType.inventoryStopped;
      case 'ready':
        return RfidEventType.ready;
      case 'error':
        return RfidEventType.error;
      default:
        return RfidEventType.unknown;
    }
  }

  /// Get tags from tagRead event
  List<RfidTag>? get tags {
    if (type != RfidEventType.tagRead || data is! Map) return null;

    final tagsList = (data as Map)['tags'] as List?;
    if (tagsList == null) return null;

    return tagsList
        .map((t) => RfidTag.fromJson(Map<String, dynamic>.from(t as Map)))
        .toList();
  }

  /// Get trigger state from trigger event
  bool? get triggerPressed {
    if (type != RfidEventType.trigger || data is! Map) return null;
    return (data as Map)['pressed'] as bool?;
  }

  /// Get error message
  String? get errorMessage {
    if (data is! Map) return null;
    return (data as Map)['message'] as String? ??
        (data as Map)['error'] as String?;
  }

  /// Get reader name
  String? get readerName {
    if (data is! Map) return null;
    return (data as Map)['readerName'] as String? ??
        (data as Map)['reader'] as String? ??
        (data as Map)['name'] as String?;
  }

  @override
  String toString() => 'RfidEvent{type: $type, message: $message}';
}
