import 'package:collection/collection.dart';
import 'package:opencola_flutter/model/base_model.dart';

class PeerList extends BaseModel {
  List<Peer>? peers;
  PeerList({this.peers});

  Peer? peerForId(String id) =>
    peers?.firstWhereOrNull((Peer peer) => peer.id == id);

  @override
  factory PeerList.fromJson(dynamic json) {
    final List<Peer> peers = <Peer>[];
    for (Map<String, dynamic> element in json) {
      peers.add(Peer.fromJson(element));
    }
    return PeerList(
      peers: peers,
    );
  }

  Map<String, dynamic> toJson() {
    final Map<String, dynamic> data = <String, dynamic>{};
    if (peers != null) {
      data['results'] = peers!.map((Peer v) => v.toJson()).toList();
    }
    return data;
  }
}

class Peer extends BaseModel {
  String? id;
  String? name;
  String? publicKey;
  String? address;
  String? imageUri;
  bool? isActive;

  Peer({
    this.id,
    this.name,
    this.publicKey,
    this.address,
    this.imageUri,
    this.isActive,
  });

  @override
  factory Peer.fromJson(Map<String, dynamic> json) =>
    Peer(
      id: json['id'],
      name: json['name'],
      publicKey: json['publicKey'],
      address: json['address'],
      imageUri: json['imageUri'],
      isActive: json['isActive'],
    );

  Map<String, dynamic> toJson() {
    final Map<String, dynamic> data = <String, dynamic>{};
    data['id'] = id;
    data['name'] = name;
    data['publicKey'] = publicKey;
    data['address'] = address;
    data['imageUri'] = imageUri;
    data['isActive'] = isActive;
    return data;
  }
}
