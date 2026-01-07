class RfidTag {
  final String tagId;
  final int rssi, antennaId, count;

  RfidTag({
    required this.rssi,
    required this.tagId,
    required this.count,
    required this.antennaId,
  });

  factory RfidTag.fromJson(Map<String, dynamic> map) {
    return RfidTag(
      rssi: map['rssi'] as int? ?? 0,
      count: map['count'] as int? ?? 0,
      tagId: map['tagId'] as String? ?? '',
      antennaId: map['antennaId'] as int? ?? 0,
    );
  }

  @override
  String toString() {
    return 'RfidTag{tagId: $tagId, rssi: $rssi, antennaId: $antennaId, count: $count}';
  }
}
