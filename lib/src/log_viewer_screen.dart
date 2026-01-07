import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'app_logger.dart';

class LogViewerScreen extends StatefulWidget {
  const LogViewerScreen({super.key});

  @override
  State<LogViewerScreen> createState() => _LogViewerScreenState();
}

class _LogViewerScreenState extends State<LogViewerScreen> {
  final _logger = AppLogger();
  final _scrollController = ScrollController();
  bool _autoScroll = true;
  LogLevel? _filterLevel;

  @override
  void initState() {
    super.initState();
    _logger.addListener(_onLogsChanged);
  }

  @override
  void dispose() {
    _logger.removeListener(_onLogsChanged);
    _scrollController.dispose();
    super.dispose();
  }

  void _onLogsChanged() {
    if (mounted) {
      setState(() {});

      if (_autoScroll && _scrollController.hasClients) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (_scrollController.hasClients) {
            _scrollController.animateTo(
              0,
              duration: const Duration(milliseconds: 300),
              curve: Curves.easeOut,
            );
          }
        });
      }
    }
  }

  List<LogEntry> get _filteredLogs {
    if (_filterLevel == null) {
      return _logger.logs;
    }
    return _logger.logs.where((log) => log.level == _filterLevel).toList();
  }

  Color _getLogColor(LogLevel level) {
    switch (level) {
      case LogLevel.debug:
        return Colors.grey;
      case LogLevel.info:
        return Colors.blue;
      case LogLevel.warning:
        return Colors.orange;
      case LogLevel.error:
        return Colors.red;
      case LogLevel.critical:
        return Colors.purple;
    }
  }

  void _copyLogsToClipboard() {
    final logs = _logger.exportLogs();
    Clipboard.setData(ClipboardData(text: logs));
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(const SnackBar(content: Text('Logs copied to clipboard')));
  }

  @override
  Widget build(BuildContext context) {
    final logs = _filteredLogs;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Debug Logs'),
        actions: [
          PopupMenuButton<LogLevel?>(
            icon: const Icon(Icons.filter_list),
            tooltip: 'Filter by level',
            onSelected: (level) {
              setState(() {
                _filterLevel = level;
              });
            },
            itemBuilder: (context) => [
              const PopupMenuItem(value: null, child: Text('All Logs')),
              const PopupMenuDivider(),
              PopupMenuItem(
                value: LogLevel.debug,
                child: Row(
                  children: [
                    Icon(
                      Icons.bug_report,
                      color: _getLogColor(LogLevel.debug),
                      size: 20,
                    ),
                    const SizedBox(width: 8),
                    const Text('Debug'),
                  ],
                ),
              ),
              PopupMenuItem(
                value: LogLevel.info,
                child: Row(
                  children: [
                    Icon(
                      Icons.info,
                      color: _getLogColor(LogLevel.info),
                      size: 20,
                    ),
                    const SizedBox(width: 8),
                    const Text('Info'),
                  ],
                ),
              ),
              PopupMenuItem(
                value: LogLevel.warning,
                child: Row(
                  children: [
                    Icon(
                      Icons.warning,
                      color: _getLogColor(LogLevel.warning),
                      size: 20,
                    ),
                    const SizedBox(width: 8),
                    const Text('Warning'),
                  ],
                ),
              ),
              PopupMenuItem(
                value: LogLevel.error,
                child: Row(
                  children: [
                    Icon(
                      Icons.error,
                      color: _getLogColor(LogLevel.error),
                      size: 20,
                    ),
                    const SizedBox(width: 8),
                    const Text('Error'),
                  ],
                ),
              ),
              PopupMenuItem(
                value: LogLevel.critical,
                child: Row(
                  children: [
                    Icon(
                      Icons.crisis_alert,
                      color: _getLogColor(LogLevel.critical),
                      size: 20,
                    ),
                    const SizedBox(width: 8),
                    const Text('Critical'),
                  ],
                ),
              ),
            ],
          ),
          IconButton(
            icon: Icon(
              _autoScroll
                  ? Icons.vertical_align_bottom
                  : Icons.vertical_align_top,
            ),
            onPressed: () {
              setState(() {
                _autoScroll = !_autoScroll;
              });
            },
            tooltip: _autoScroll ? 'Disable auto-scroll' : 'Enable auto-scroll',
          ),
          IconButton(
            icon: const Icon(Icons.copy),
            onPressed: _copyLogsToClipboard,
            tooltip: 'Copy logs',
          ),
          IconButton(
            icon: const Icon(Icons.delete),
            onPressed: () {
              _logger.clear();
              ScaffoldMessenger.of(
                context,
              ).showSnackBar(const SnackBar(content: Text('Logs cleared')));
            },
            tooltip: 'Clear logs',
          ),
        ],
      ),
      body: logs.isEmpty
          ? Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(
                    Icons.article_outlined,
                    size: 64,
                    color: Colors.grey[400],
                  ),
                  const SizedBox(height: 16),
                  Text(
                    _filterLevel == null
                        ? 'No logs yet'
                        : 'No ${_filterLevel.toString().split('.').last} logs',
                    style: TextStyle(color: Colors.grey[600]),
                  ),
                ],
              ),
            )
          : ListView.builder(
              controller: _scrollController,
              padding: const EdgeInsets.all(8),
              itemCount: logs.length,
              itemBuilder: (context, index) {
                final log = logs[index];
                final color = _getLogColor(log.level);

                return Card(
                  margin: const EdgeInsets.symmetric(vertical: 4),
                  color:
                      log.level == LogLevel.error ||
                          log.level == LogLevel.critical
                      ? color.withValues(alpha: .1)
                      : null,
                  child: ExpansionTile(
                    leading: Icon(_getIcon(log.level), color: color, size: 20),
                    title: Text(
                      log.message,
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight:
                            log.level == LogLevel.error ||
                                log.level == LogLevel.critical
                            ? FontWeight.bold
                            : FontWeight.normal,
                      ),
                    ),
                    subtitle: Text(
                      '${log.formattedTime}${log.source != null ? " • ${log.source}" : ""}',
                      style: const TextStyle(fontSize: 11),
                    ),
                    children: [
                      if (log.error != null || log.stackTrace != null)
                        Container(
                          width: double.infinity,
                          padding: const EdgeInsets.all(12),
                          color: Colors.grey[100],
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              if (log.error != null) ...[
                                const Text(
                                  'Error:',
                                  style: TextStyle(
                                    fontWeight: FontWeight.bold,
                                    fontSize: 12,
                                  ),
                                ),
                                const SizedBox(height: 4),
                                Text(
                                  log.error.toString(),
                                  style: const TextStyle(
                                    fontSize: 11,
                                    fontFamily: 'monospace',
                                  ),
                                ),
                              ],
                              if (log.stackTrace != null) ...[
                                const SizedBox(height: 8),
                                const Text(
                                  'Stack Trace:',
                                  style: TextStyle(
                                    fontWeight: FontWeight.bold,
                                    fontSize: 12,
                                  ),
                                ),
                                const SizedBox(height: 4),
                                Text(
                                  log.stackTrace
                                      .toString()
                                      .split('\n')
                                      .take(5)
                                      .join('\n'),
                                  style: const TextStyle(
                                    fontSize: 10,
                                    fontFamily: 'monospace',
                                  ),
                                ),
                              ],
                            ],
                          ),
                        ),
                    ],
                  ),
                );
              },
            ),
    );
  }

  IconData _getIcon(LogLevel level) {
    switch (level) {
      case LogLevel.debug:
        return Icons.bug_report;
      case LogLevel.info:
        return Icons.info;
      case LogLevel.warning:
        return Icons.warning;
      case LogLevel.error:
        return Icons.error;
      case LogLevel.critical:
        return Icons.crisis_alert;
    }
  }
}
