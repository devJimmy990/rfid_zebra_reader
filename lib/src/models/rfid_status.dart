/// Status model for RFID reader state
class RfidStatus {
  final bool permissionsGranted;
  final bool sdkInitialized;
  final bool readerConnected;
  final String? readerName;
  final String? error;
  final bool isReconnecting;
  final int maxPower;

  RfidStatus({
    required this.permissionsGranted,
    required this.sdkInitialized,
    required this.readerConnected,
    this.readerName,
    this.error,
    this.isReconnecting = false,
    this.maxPower = 270,
  });

  factory RfidStatus.fromMap(Map<String, dynamic> map) {
    return RfidStatus(
      permissionsGranted: map['permissionsGranted'] as bool? ?? false,
      sdkInitialized: map['sdkInitialized'] as bool? ?? false,
      readerConnected: map['readerConnected'] as bool? ?? false,
      readerName: map['readerName'] as String?,
      error: map['error'] as String?,
      isReconnecting: map['isReconnecting'] as bool? ?? false,
      maxPower: map['maxPower'] as int? ?? 270,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'permissionsGranted': permissionsGranted,
      'sdkInitialized': sdkInitialized,
      'readerConnected': readerConnected,
      'readerName': readerName,
      'error': error,
      'isReconnecting': isReconnecting,
      'maxPower': maxPower,
    };
  }

  /// Check if there's an error
  bool get hasError => error != null && error!.isNotEmpty;

  /// Check if fully ready (permissions + SDK + connected)
  bool get isReady => permissionsGranted && sdkInitialized && readerConnected;

  /// Get status message for display
  String get statusMessage {
    if (hasError) return 'Error: $error';
    if (isReconnecting) return 'Reconnecting...';
    if (!permissionsGranted) return 'Permissions required';
    if (!sdkInitialized) return 'SDK not initialized';
    if (!readerConnected) return 'Reader not connected';
    return 'Connected to ${readerName ?? "reader"}';
  }

  @override
  String toString() {
    return 'RfidStatus('
        'permissions: $permissionsGranted, '
        'sdk: $sdkInitialized, '
        'connected: $readerConnected, '
        'reader: $readerName, '
        'error: $error)';
  }

  /// Create a copy with updated values
  RfidStatus copyWith({
    bool? permissionsGranted,
    bool? sdkInitialized,
    bool? readerConnected,
    String? readerName,
    String? error,
    bool? isReconnecting,
    int? maxPower,
  }) {
    return RfidStatus(
      permissionsGranted: permissionsGranted ?? this.permissionsGranted,
      sdkInitialized: sdkInitialized ?? this.sdkInitialized,
      readerConnected: readerConnected ?? this.readerConnected,
      readerName: readerName ?? this.readerName,
      error: error,
      isReconnecting: isReconnecting ?? this.isReconnecting,
      maxPower: maxPower ?? this.maxPower,
    );
  }
}
