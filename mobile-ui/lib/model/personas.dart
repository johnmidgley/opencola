import 'package:collection/collection.dart';
import 'package:opencola_flutter/model/base_model.dart';

class Personas extends BaseModel {
  List<Persona>? items;
  Personas({this.items});

  List<Persona> personasList() {
    final List<Persona> list = <Persona>[];
    items?.forEach( (Persona persona) {
      list.add(persona);
    });
    return list;
  }

  Persona? personaForId(String personaId) {
    final List<Persona> list = personasList();
    return list.firstWhereOrNull((Persona element) => element.id == personaId);
  }

  Persona? personaForName(String name) {
    final List<Persona> list = personasList();
    return list.firstWhereOrNull((Persona element) => element.name == name);
  }

  List<String> personaNames([bool brief = false]) {
    final List<String> names = <String>[];
    final List<Persona> list = personasList();
    for (Persona element in list) {
      if (element.name != null) {
        names.add(element.name!);
      }
    }
    if (!brief) {
      names.insert(0, 'All');
      names.add('Manage...');
    }
    return names;
  }

  @override
  factory Personas.fromJson(dynamic json) {
    final List<Persona> items = <Persona>[];
    for (Map<String, dynamic> element in json) {
      items.add(Persona.fromJson(element));
    }
    return Personas(
      items: items,
    );
  }

  Map<String, dynamic> toJson() {
    final Map<String, dynamic> data = <String, dynamic>{};
    if (items != null) {
      data['items'] = items!.map((Persona v) => v.toJson()).toList();
    }
    return data;
  }
}

class Persona extends BaseModel {
  String? id;
  String? name;
  String? publicKey;
  String? address;
  String? imageUri;
  bool? isActive;

  Persona({
    this.id,
    this.name,
    this.publicKey,
    this.address,
    this.imageUri,
    this.isActive,
  });

  @override
  factory Persona.fromJson(Map<String, dynamic> json) =>
    Persona(
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
