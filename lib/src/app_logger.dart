import 'package:flutter/foundation.dart';

enum LogLevel { debug, info, warning, error, critical }

class LogEntry {
  final DateTime timestamp;
  final LogLevel level;
  final String message;
  final String? source;
  final dynamic error;
  final StackTrace? stackTrace;

  LogEntry({
    required this.timestamp,
    required this.level,
    required this.message,
    this.source,
    this.error,
    this.stackTrace,
  });

  String get levelText {
    switch (level) {
      case LogLevel.debug:
        return '🔍 DEBUG';
      case LogLevel.info:
        return 'ℹ️ INFO';
      case LogLevel.warning:
        return '⚠️ WARNING';
      case LogLevel.error:
        return '❌ ERROR';
      case LogLevel.critical:
        return '🔥 CRITICAL';
    }
  }

  String get formattedTime {
    return '${timestamp.hour.toString().padLeft(2, '0')}:'
        '${timestamp.minute.toString().padLeft(2, '0')}:'
        '${timestamp.second.toString().padLeft(2, '0')}';
  }

  @override
  String toString() {
    var text = '[$formattedTime] $levelText';
    if (source != null) text += ' [$source]';
    text += ': $message';
    if (error != null) text += '\nError: $error';
    if (stackTrace != null) {
      text +=
          '\nStack: ${stackTrace.toString().split('\n').take(3).join('\n')}';
    }
    return text;
  }
}

class AppLogger extends ChangeNotifier {
  static final AppLogger _instance = AppLogger._internal();
  factory AppLogger() => _instance;
  AppLogger._internal();

  final List<LogEntry> _logs = [];
  static const int maxLogs = 500;

  List<LogEntry> get logs => List.unmodifiable(_logs);

  void log(
    String message, {
    LogLevel level = LogLevel.info,
    String? source,
    dynamic error,
    StackTrace? stackTrace,
  }) {
    final entry = LogEntry(
      timestamp: DateTime.now(),
      level: level,
      message: message,
      source: source,
      error: error,
      stackTrace: stackTrace,
    );

    _logs.insert(0, entry);

    // Keep only last maxLogs entries
    if (_logs.length > maxLogs) {
      _logs.removeRange(maxLogs, _logs.length);
    }

    // Print to console for debugging
    debugPrint(entry.toString());

    notifyListeners();
  }

  void debug(String message, {String? source}) {
    log(message, level: LogLevel.debug, source: source);
  }

  void info(String message, {String? source}) {
    log(message, level: LogLevel.info, source: source);
  }

  void warning(String message, {String? source, dynamic error}) {
    log(message, level: LogLevel.warning, source: source, error: error);
  }

  void error(
    String message, {
    String? source,
    dynamic error,
    StackTrace? stackTrace,
  }) {
    log(
      message,
      level: LogLevel.error,
      source: source,
      error: error,
      stackTrace: stackTrace,
    );
  }

  void critical(
    String message, {
    String? source,
    dynamic error,
    StackTrace? stackTrace,
  }) {
    log(
      message,
      level: LogLevel.critical,
      source: source,
      error: error,
      stackTrace: stackTrace,
    );
  }

  void clear() {
    _logs.clear();
    notifyListeners();
  }

  String exportLogs() {
    return _logs.reversed.map((e) => e.toString()).join('\n\n');
  }
}
