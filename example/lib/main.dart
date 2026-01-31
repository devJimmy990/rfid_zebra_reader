import 'dart:async';

import 'package:flutter/material.dart';
import 'package:rfid_zebra_reader/rfid_zebra_reader.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();

  // Just initialize - status updates will come through stream
  ZebraRfidReader.initialize();

  runApp(const MaterialApp(home: RfidReaderPage()));
}

class RfidReaderPage extends StatefulWidget {
  const RfidReaderPage({super.key});

  @override
  State<RfidReaderPage> createState() => _RfidReaderPageState();
}

class _RfidReaderPageState extends State<RfidReaderPage> {
  // Status - now managed by stream
  RfidStatus? _status;
  bool _isScanning = false;

  // Tags
  final List<RfidTag> _scannedTags = [];
  final Set<String> _uniqueTagIds = {};

  // Power
  int _powerLevel = 270;
  int _maxPower = 270;

  // Event subscriptions
  StreamSubscription<RfidEvent>? _eventSubscription;
  StreamSubscription<RfidStatus>? _statusSubscription;

  @override
  void initState() {
    super.initState();
    _setupEventListener();
    _setupStatusListener();
  }

  void _setupStatusListener() {
    _statusSubscription = ZebraRfidReader.statusStream.listen(
      (status) {
        setState(() {
          _status = status;
          _maxPower = status.maxPower;

          // Update power level when first connected
          if (status.isReady && _powerLevel == 270) {
            _powerLevel = status.maxPower;
          }
        });

        // Show user feedback for important state changes
        if (status.isReady && (_status == null || !_status!.isReady)) {
          _showSnackBar('Connected to ${status.readerName}');
          AppLogger().info('Ready: ${status.readerName}', source: 'UI');
        } else if (status.hasError) {
          _showSnackBar(status.error!);
          AppLogger().error('Status error: ${status.error}', source: 'UI');
        }
      },
      onError: (error) {
        _showSnackBar('Status stream error: $error');
        AppLogger().error('Status stream error', error: error);
      },
    );
  }

  void _setupEventListener() {
    _eventSubscription = ZebraRfidReader.eventStream.listen(
      _handleRfidEvent,
      onError: (error) {
        _showSnackBar('Event error: $error');
        AppLogger().error('Event stream error', error: error);
      },
    );
  }

  void _handleRfidEvent(RfidEvent event) {
    switch (event.type) {
      case RfidEventType.connected:
        _showSnackBar('Connected to ${event.readerName}');
        break;

      case RfidEventType.disconnected:
        setState(() {
          _isScanning = false;
        });
        _showSnackBar('Disconnected');
        break;

      case RfidEventType.tagRead:
        final tags = event.tags;
        if (tags != null) {
          setState(() {
            for (final tag in tags) {
              if (!_uniqueTagIds.contains(tag.tagId)) {
                _uniqueTagIds.add(tag.tagId);
                _scannedTags.insert(0, tag);
              }
            }
          });
        }
        break;

      case RfidEventType.trigger:
        final pressed = event.triggerPressed ?? false;
        if (pressed && _status?.isReady == true && !_isScanning) {
          _startScanning();
        } else if (!pressed && _isScanning) {
          _stopScanning();
        }
        break;

      case RfidEventType.error:
        _showSnackBar('Error: ${event.errorMessage}');
        break;

      case RfidEventType.readerAppeared:
        _showSnackBar('Reader detected: ${event.readerName}');
        break;

      case RfidEventType.readerDisappeared:
        _showSnackBar('Reader removed');
        break;

      default:
        break;
    }
  }

  Future<void> _startScanning() async {
    if (_status?.isReady != true) {
      _showSnackBar('Reader not ready');
      return;
    }

    final success = await ZebraRfidReader.startInventory();
    if (success) {
      setState(() => _isScanning = true);
    } else {
      _showSnackBar('Failed to start scanning');
    }
  }

  Future<void> _stopScanning() async {
    final success = await ZebraRfidReader.stopInventory();
    if (success) {
      setState(() => _isScanning = false);
    }
  }

  Future<void> _setPower(int power) async {
    final success = await ZebraRfidReader.setAntennaPower(power);
    if (success) {
      setState(() => _powerLevel = power);
      _showSnackBar('Power set to $power');
    } else {
      _showSnackBar('Failed to set power');
    }
  }

  void _clearTags() {
    setState(() {
      _scannedTags.clear();
      _uniqueTagIds.clear();
    });
  }

  Future<void> _retryInitialization() async {
    _showSnackBar('Re-initializing...');
    AppLogger().info('Manual re-initialization triggered', source: 'UI');
    await ZebraRfidReader.initialize();
  }

  void _showSnackBar(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message), duration: const Duration(seconds: 2)),
    );
  }

  @override
  void dispose() {
    _eventSubscription?.cancel();
    _statusSubscription?.cancel();
    ZebraRfidReader.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('Zebra RFID Reader'),
      actions: [
        IconButton(
          icon: const Icon(Icons.delete_sweep),
          onPressed: _clearTags,
          tooltip: 'Clear tags',
        ),
        IconButton(
          icon: const Icon(Icons.article),
          onPressed: () => Navigator.push(
            context,
            MaterialPageRoute(builder: (_) => const LogViewerScreen()),
          ),
          tooltip: 'View logs',
        ),
      ],
    ),
    body: Column(
      children: [
        // Status Card
        _buildStatusCard(),

        // Control Buttons
        if (_status?.isReady == true) _buildControlButtons(),

        // Power Slider
        if (_status?.isReady == true) _buildPowerSlider(),

        const Divider(),

        // Tags Header
        _buildTagsHeader(),

        // Tags List
        Expanded(child: _buildTagsList()),
      ],
    ),
  );

  Widget _buildStatusCard() {
    final status = _status;
    final isInitializing = status == null || status.isInitializing;

    Color statusColor;
    IconData statusIcon;

    if (status == null) {
      statusColor = Colors.orange;
      statusIcon = Icons.hourglass_empty;
    } else if (status.hasError) {
      statusColor = Colors.red;
      statusIcon = Icons.error;
    } else if (status.isReconnecting) {
      statusColor = Colors.orange;
      statusIcon = Icons.sync;
    } else if (status.isReady) {
      statusColor = Colors.green;
      statusIcon = Icons.check_circle;
    } else if (!status.permissionsGranted) {
      statusColor = Colors.red;
      statusIcon = Icons.lock;
    } else if (!status.sdkInitialized) {
      statusColor = Colors.orange;
      statusIcon = Icons.settings;
    } else if (!status.readerConnected) {
      statusColor = Colors.orange;
      statusIcon = Icons.bluetooth_searching;
    } else {
      statusColor = Colors.grey;
      statusIcon = Icons.bluetooth_disabled;
    }

    return Card(
      margin: const EdgeInsets.all(16),
      color: statusColor.withValues(alpha: .1),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            Row(
              children: [
                Icon(statusIcon, color: statusColor, size: 32),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        status?.statusMessage ?? 'Initializing...',
                        style: const TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        'Tags: ${_uniqueTagIds.length} | Scanning: ${_isScanning ? "Yes" : "No"}',
                        style: TextStyle(fontSize: 14, color: Colors.grey[600]),
                      ),
                      if (status != null && status.readerName != null)
                        Padding(
                          padding: const EdgeInsets.only(top: 4),
                          child: Text(
                            'Reader: ${status.readerName}',
                            style: TextStyle(
                              fontSize: 12,
                              color: Colors.grey[600],
                              fontStyle: FontStyle.italic,
                            ),
                          ),
                        ),
                    ],
                  ),
                ),
                if (isInitializing || (status.isReconnecting == true))
                  const SizedBox(
                    width: 24,
                    height: 24,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  ),
              ],
            ),
            const SizedBox(height: 12),
            // Status indicators
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                _buildStatusIndicator(
                  'Permissions',
                  status?.permissionsGranted ?? false,
                ),
                _buildStatusIndicator('SDK', status?.sdkInitialized ?? false),
                _buildStatusIndicator(
                  'Connected',
                  status?.readerConnected ?? false,
                ),
              ],
            ),
            // Retry button if error
            if (status?.hasError == true)
              Padding(
                padding: const EdgeInsets.only(top: 12),
                child: ElevatedButton.icon(
                  onPressed: _retryInitialization,
                  icon: const Icon(Icons.refresh),
                  label: const Text('Retry'),
                ),
              ),
            // Debug info
            if (status != null)
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: Text(
                  'State: ${status.shortStatus}',
                  style: TextStyle(
                    fontSize: 10,
                    color: Colors.grey[500],
                    fontFamily: 'monospace',
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildStatusIndicator(String label, bool active) => Column(
    children: [
      Icon(
        active ? Icons.check_circle : Icons.cancel,
        color: active ? Colors.green : Colors.grey,
        size: 20,
      ),
      const SizedBox(height: 4),
      Text(
        label,
        style: TextStyle(
          fontSize: 12,
          color: active ? Colors.green : Colors.grey,
        ),
      ),
    ],
  );

  Widget _buildControlButtons() => Padding(
    padding: const EdgeInsets.symmetric(horizontal: 16),
    child: Row(
      children: [
        Expanded(
          child: ElevatedButton.icon(
            onPressed: !_isScanning ? _startScanning : null,
            icon: const Icon(Icons.play_arrow),
            label: const Text('Start'),
            style: ElevatedButton.styleFrom(
              backgroundColor: Colors.green,
              foregroundColor: Colors.white,
              padding: const EdgeInsets.all(16),
            ),
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: ElevatedButton.icon(
            onPressed: _isScanning ? _stopScanning : null,
            icon: const Icon(Icons.stop),
            label: const Text('Stop'),
            style: ElevatedButton.styleFrom(
              backgroundColor: Colors.red,
              foregroundColor: Colors.white,
              padding: const EdgeInsets.all(16),
            ),
          ),
        ),
      ],
    ),
  );

  Widget _buildPowerSlider() => Padding(
    padding: const EdgeInsets.all(16),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Antenna Power: $_powerLevel / $_maxPower',
          style: const TextStyle(fontWeight: FontWeight.bold),
        ),
        Slider(
          value: _powerLevel.toDouble(),
          min: 100,
          max: _maxPower.toDouble(),
          divisions: (_maxPower - 100) ~/ 10,
          label: _powerLevel.toString(),
          onChanged: (value) {
            setState(() => _powerLevel = value.toInt());
          },
          onChangeEnd: (value) => _setPower(value.toInt()),
        ),
      ],
    ),
  );

  Widget _buildTagsHeader() => Padding(
    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
    child: Row(
      children: [
        const Text(
          'Scanned Tags',
          style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
        ),
        const Spacer(),
        Text(
          '${_uniqueTagIds.length} unique',
          style: const TextStyle(color: Colors.grey),
        ),
      ],
    ),
  );

  Widget _buildTagsList() {
    if (_scannedTags.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.nfc, size: 64, color: Colors.grey[400]),
            const SizedBox(height: 16),
            Text(
              _status?.isReady == true
                  ? 'Press "Start" or trigger to scan'
                  : 'Waiting for reader connection...',
              style: TextStyle(fontSize: 16, color: Colors.grey[600]),
            ),
          ],
        ),
      );
    }

    return ListView.builder(
      itemCount: _scannedTags.length,
      itemBuilder: (context, index) {
        final tag = _scannedTags[index];
        return Card(
          margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
          child: ListTile(
            leading: CircleAvatar(
              backgroundColor: Colors.blue,
              child: Text(
                '${index + 1}',
                style: const TextStyle(color: Colors.white),
              ),
            ),
            title: Text(
              tag.tagId,
              style: const TextStyle(
                fontFamily: 'monospace',
                fontSize: 12,
                fontWeight: FontWeight.bold,
              ),
            ),
            subtitle: Text(
              'RSSI: ${tag.rssi} dBm | Antenna: ${tag.antennaId} | Reads: ${tag.count}',
              style: const TextStyle(fontSize: 11),
            ),
            trailing: Icon(Icons.nfc, color: Colors.blue[300]),
          ),
        );
      },
    );
  }
}
