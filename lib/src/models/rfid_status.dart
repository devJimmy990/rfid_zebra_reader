/// Status model for RFID reader state
class RfidStatus {
  final int maxPower;
  final String? error;
  final String? readerName;
  final bool isReconnecting;
  final bool sdkInitialized;
  final bool readerConnected;
  final bool permissionsGranted;

  RfidStatus({
    required this.sdkInitialized,
    required this.readerConnected,
    required this.permissionsGranted,
    this.error,
    this.readerName,
    this.maxPower = 270,
    this.isReconnecting = false,
  });

  factory RfidStatus.fromMap(Map<String, dynamic> map) {
    return RfidStatus(
      error: map['error'] as String?,
      readerName: map['readerName'] as String?,
      maxPower: map['maxPower'] as int? ?? 270,
      isReconnecting: map['isReconnecting'] as bool? ?? false,
      sdkInitialized: map['sdkInitialized'] as bool? ?? false,
      readerConnected: map['readerConnected'] as bool? ?? false,
      permissionsGranted: map['permissionsGranted'] as bool? ?? false,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'error': error,
      'maxPower': maxPower,
      'readerName': readerName,
      'sdkInitialized': sdkInitialized,
      'isReconnecting': isReconnecting,
      'readerConnected': readerConnected,
      'permissionsGranted': permissionsGranted,
    };
  }

  /// Check if there's an error
  bool get hasError => error != null && error!.isNotEmpty;

  /// Check if fully ready (permissions + SDK + connected)
  bool get isReady => permissionsGranted && sdkInitialized && readerConnected;

  /// Check if initialization is in progress
  bool get isInitializing =>
      !isReady && !hasError && (permissionsGranted || sdkInitialized);

  /// Get status message for display
  String get statusMessage {
    if (hasError) return 'Error: $error';
    if (isReconnecting) return 'Reconnecting...';
    if (!sdkInitialized) return 'Initializing SDK...';
    if (!permissionsGranted) return 'Permissions required';
    if (!readerConnected) return 'Connecting to reader...';
    return 'Connected to ${readerName ?? "reader"}';
  }

  /// Get short status for logs
  String get shortStatus {
    if (hasError) return 'ERROR';
    if (isReconnecting) return 'RECONNECTING';
    if (!permissionsGranted) return 'NO_PERMISSION';
    if (!sdkInitialized) return 'SDK_INIT';
    if (!readerConnected) return 'CONNECTING';
    return 'READY';
  }

  @override
  String toString() {
    return 'RfidStatus('
        'error: $error, '
        'reader: $readerName, '
        'sdk: $sdkInitialized, '
        'connected: $readerConnected, '
        'permissions: $permissionsGranted)';
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;

    return other is RfidStatus &&
        other.maxPower == maxPower &&
        other.error == error &&
        other.readerName == readerName &&
        other.isReconnecting == isReconnecting &&
        other.sdkInitialized == sdkInitialized &&
        other.readerConnected == readerConnected &&
        other.permissionsGranted == permissionsGranted;
  }

  @override
  int get hashCode {
    return Object.hash(
      maxPower,
      error,
      readerName,
      isReconnecting,
      sdkInitialized,
      readerConnected,
      permissionsGranted,
    );
  }

  /// Create a copy with updated values
  RfidStatus copyWith({
    String? error,
    int? maxPower,
    String? readerName,
    bool? isReconnecting,
    bool? sdkInitialized,
    bool? readerConnected,
    bool? permissionsGranted,
  }) {
    return RfidStatus(
      error: error ?? this.error,
      maxPower: maxPower ?? this.maxPower,
      readerName: readerName ?? this.readerName,
      sdkInitialized: sdkInitialized ?? this.sdkInitialized,
      isReconnecting: isReconnecting ?? this.isReconnecting,
      readerConnected: readerConnected ?? this.readerConnected,
      permissionsGranted: permissionsGranted ?? this.permissionsGranted,
    );
  }
}
