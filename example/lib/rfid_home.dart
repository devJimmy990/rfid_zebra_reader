import 'dart:async';

import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:zebra_rfid_reader/zebra_rfid_reader.dart';

import 'permission_helper.dart';

class RfidReaderPage extends StatefulWidget {
  const RfidReaderPage({super.key});

  @override
  State<RfidReaderPage> createState() => _RfidReaderPageState();
}

class _RfidReaderPageState extends State<RfidReaderPage> {
  String _status = 'Disconnected';
  bool _isScanning = false;
  bool _isConnected = false;
  bool _permissionsGranted = false;
  final List<RfidTag> _scannedTags = [];
  final Set<String> _uniqueTagIds = {};
  StreamSubscription<RfidEvent>? _eventSubscription;
  int _powerLevel = 270;

  @override
  void initState() {
    super.initState();
    _checkAndRequestPermissions();
  }

  // Check and request permissions on app start
  Future<void> _checkAndRequestPermissions() async {
    bool granted = await PermissionHelper.checkRfidPermissions();

    if (!granted) {
      // Request permissions
      granted = await PermissionHelper.requestRfidPermissions(context);
    }

    setState(() {
      _permissionsGranted = granted;
      if (granted) {
        _status = 'Ready - Permissions granted';
        _initializeReader();
      } else {
        _status = 'Permissions denied - Cannot use RFID reader';
      }
    });
  }

  Future<void> _initializeReader() async {
    // Listen to RFID events
    _eventSubscription = ZebraRfidReader.eventStream.listen(
      _handleRfidEvent,
      onError: (error) {
        setState(() {
          _status = 'Error: $error';
        });
      },
    );

    // Check connection status
    _checkConnection();
  }

  void _handleRfidEvent(RfidEvent event) {
    setState(() {
      switch (event.type) {
        case RfidEventType.connected:
          _isConnected = true;
          _status = 'Connected to ${event.readerName ?? "reader"}';
          break;

        case RfidEventType.disconnected:
          _isConnected = false;
          _isScanning = false;
          _status = 'Disconnected';
          break;

        case RfidEventType.tagRead:
          final tags = event.tags;
          if (tags != null) {
            for (var tag in tags) {
              if (!_uniqueTagIds.contains(tag.tagId)) {
                _uniqueTagIds.add(tag.tagId);
                _scannedTags.insert(0, tag);
              }
            }
          }
          break;

        case RfidEventType.trigger:
          final pressed = event.triggerPressed ?? false;
          if (pressed) {
            _status = 'Trigger pressed - Scanning...';
            _startScanning();
          } else {
            _status = 'Trigger released';
            _stopScanning();
          }
          break;

        case RfidEventType.error:
          _status = 'Error: ${event.errorMessage}';
          break;

        case RfidEventType.readerAppeared:
          _status = 'Reader appeared: ${event.readerName}';
          _showSnackBar('Reader detected: ${event.readerName}');
          break;

        case RfidEventType.readerDisappeared:
          _status = 'Reader disappeared: ${event.readerName}';
          _showSnackBar('Reader disconnected');
          break;

        default:
          break;
      }
    });
  }

  Future<void> _checkConnection() async {
    try {
      final isConnected = await ZebraRfidReader.isConnected();
      setState(() {
        _isConnected = isConnected;
        _status = isConnected ? 'Connected' : 'Ready to connect';
      });
    } catch (e) {
      setState(() {
        _status = 'Error checking connection: $e';
      });
    }
  }

  Future<void> _connect() async {
    if (!_permissionsGranted) {
      _showSnackBar('Please grant permissions first');
      await _checkAndRequestPermissions();
      return;
    }

    try {
      setState(() {
        _status = 'Connecting...';
      });
      final result = await ZebraRfidReader.connect();
      setState(() {
        _status = result ?? 'Connected';
      });
      _checkConnection();
    } catch (e) {
      setState(() {
        _status = 'Connection error: $e';
      });
    }
  }

  Future<void> _disconnect() async {
    try {
      await ZebraRfidReader.disconnect();
      setState(() {
        _isConnected = false;
        _isScanning = false;
        _status = 'Disconnected';
      });
    } catch (e) {
      setState(() {
        _status = 'Disconnect error: $e';
      });
    }
  }

  Future<void> _startScanning() async {
    if (!_isConnected) {
      _showSnackBar('Please connect to reader first');
      return;
    }

    try {
      final result = await ZebraRfidReader.startInventory();
      setState(() {
        _isScanning = true;
        _status = result ?? 'Scanning...';
      });
    } catch (e) {
      setState(() {
        _status = 'Start scanning error: $e';
      });
    }
  }

  Future<void> _stopScanning() async {
    try {
      final result = await ZebraRfidReader.stopInventory();
      setState(() {
        _isScanning = false;
        _status = result ?? 'Scanning stopped';
      });
    } catch (e) {
      setState(() {
        _status = 'Stop scanning error: $e';
      });
    }
  }

  Future<void> _setPower(int power) async {
    try {
      final result = await ZebraRfidReader.setAntennaPower(power);
      setState(() {
        _powerLevel = power;
        _status = result ?? 'Power set to $power';
      });
      _showSnackBar('Power set to $power');
    } catch (e) {
      _showSnackBar('Error setting power: $e');
    }
  }

  void _clearTags() {
    setState(() {
      _scannedTags.clear();
      _uniqueTagIds.clear();
      _status = 'Tags cleared';
    });
  }

  void _showSnackBar(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message), duration: const Duration(seconds: 2)),
    );
  }

  @override
  void dispose() {
    _eventSubscription?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Zebra RFID Reader - TC27'),
        actions: [
          IconButton(
            icon: const Icon(Icons.delete_sweep),
            onPressed: _clearTags,
            tooltip: 'Clear tags',
          ),
          IconButton(
            icon: const Icon(Icons.settings),
            onPressed: () => openAppSettings(),
            tooltip: 'App settings',
          ),
        ],
      ),
      body: Column(
        children: [
          // Permission Warning Banner
          if (!_permissionsGranted)
            Container(
              color: Colors.red,
              width: double.infinity,
              padding: const EdgeInsets.all(12),
              child: Row(
                children: [
                  const Icon(Icons.warning, color: Colors.white),
                  const SizedBox(width: 8),
                  const Expanded(
                    child: Text(
                      'Permissions required',
                      style: TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                  ElevatedButton(
                    onPressed: _checkAndRequestPermissions,
                    child: const Text('Grant'),
                  ),
                ],
              ),
            ),

          // Status Card
          Card(
            margin: const EdgeInsets.all(16),
            color: _isConnected ? Colors.green[50] : Colors.grey[200],
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                children: [
                  Row(
                    children: [
                      Icon(
                        _isConnected ? Icons.check_circle : Icons.bluetooth,
                        color: _isConnected ? Colors.green : Colors.grey,
                        size: 32,
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              _status,
                              style: const TextStyle(
                                fontSize: 16,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              'Tags: ${_uniqueTagIds.length} | Scanning: ${_isScanning ? "Yes" : "No"}',
                              style: const TextStyle(
                                fontSize: 14,
                                color: Colors.grey,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),

          // Control Buttons
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Row(
              children: [
                Expanded(
                  child: ElevatedButton.icon(
                    onPressed: (_permissionsGranted && !_isConnected)
                        ? _connect
                        : null,
                    icon: const Icon(Icons.bluetooth_connected),
                    label: const Text('Connect'),
                    style: ElevatedButton.styleFrom(
                      padding: const EdgeInsets.all(16),
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: ElevatedButton.icon(
                    onPressed: _isConnected ? _disconnect : null,
                    icon: const Icon(Icons.bluetooth_disabled),
                    label: const Text('Disconnect'),
                    style: ElevatedButton.styleFrom(
                      padding: const EdgeInsets.all(16),
                    ),
                  ),
                ),
              ],
            ),
          ),

          const SizedBox(height: 8),

          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Row(
              children: [
                Expanded(
                  child: ElevatedButton.icon(
                    onPressed: (_isConnected && !_isScanning)
                        ? _startScanning
                        : null,
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
                    onPressed: (_isConnected && _isScanning)
                        ? _stopScanning
                        : null,
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
          ),

          // Power Control
          Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Antenna Power: $_powerLevel',
                  style: const TextStyle(fontWeight: FontWeight.bold),
                ),
                Slider(
                  value: _powerLevel.toDouble(),
                  min: 100,
                  max: 270,
                  divisions: 17,
                  label: _powerLevel.toString(),
                  onChanged: _isConnected
                      ? (value) {
                          setState(() {
                            _powerLevel = value.toInt();
                          });
                        }
                      : null,
                  onChangeEnd: _isConnected
                      ? (value) => _setPower(value.toInt())
                      : null,
                ),
              ],
            ),
          ),

          const Divider(),

          // Tags Header
          Padding(
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
          ),

          // Tags List
          Expanded(
            child: _scannedTags.isEmpty
                ? Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(Icons.nfc, size: 64, color: Colors.grey[400]),
                        const SizedBox(height: 16),
                        Text(
                          _isConnected
                              ? 'Press "Start" to scan tags'
                              : 'Connect to reader first',
                          style: TextStyle(
                            fontSize: 16,
                            color: Colors.grey[600],
                          ),
                        ),
                      ],
                    ),
                  )
                : ListView.builder(
                    itemCount: _scannedTags.length,
                    itemBuilder: (context, index) {
                      final tag = _scannedTags[index];
                      return Card(
                        margin: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 4,
                        ),
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
                  ),
          ),
        ],
      ),
    );
  }
}
