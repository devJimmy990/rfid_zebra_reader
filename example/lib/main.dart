import 'package:flutter/material.dart';
import 'package:zebra_rfid_reader_example/rfid_home.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Zebra RFID Reader',
      theme: ThemeData(primarySwatch: Colors.blue),
      home: const RfidReaderPage(),
    );
  }
}
