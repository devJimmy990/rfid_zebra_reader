import 'package:zebra_rfid_reader/src/models/rfid_tag.dart';

/// RFID Event types
enum RfidEventType {
  tagRead,
  trigger,
  connected,
  disconnected,
  readerAppeared,
  readerDisappeared,
  initialized,
  error,
  unknown,
}

/// RFID Event class
class RfidEvent {
  final RfidEventType type;
  final dynamic data;

  RfidEvent({required this.type, this.data});

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
      case 'initialized':
        eventType = RfidEventType.initialized;
        break;
      case 'error':
        eventType = RfidEventType.error;
        break;
      default:
        eventType = RfidEventType.unknown;
    }

    return RfidEvent(type: eventType, data: map);
  }

  /// Get tags from tagRead event
  List<RfidTag>? get tags {
    if (type == RfidEventType.tagRead && data is Map) {
      final tagsList = (data as Map)['tags'] as List?;
      return tagsList
          ?.map((t) => RfidTag.fromJson(Map<String, dynamic>.from(t)))
          .toList();
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
      return (data as Map)['reader'] as String? ??
          (data as Map)['name'] as String?;
    }
    return null;
  }

  @override
  String toString() {
    return 'RfidEvent{type: $type, data: $data}';
  }
}
