import 'dart:async';
import 'dart:io';
import 'package:collection/collection.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:opencola_flutter/api/api.dart';
import 'package:opencola_flutter/api/peers_request.dart';
import 'package:opencola_flutter/api/personas_request.dart';
import 'package:opencola_flutter/model/feed.dart' hide Entity;
import 'package:opencola_flutter/model/peer.dart';
import 'package:opencola_flutter/model/personas.dart';
import 'package:opencola_flutter/utils/utils.dart';
import 'package:objectbox/objectbox.dart';
import 'package:opencola_flutter/objectbox.g.dart';
import 'package:flutter_sharing_intent/flutter_sharing_intent.dart';
import 'package:flutter_sharing_intent/model/sharing_file.dart';

class AppState {
  // Singlegon
  AppState._();
  static AppState instance = AppState._();

  API api = API();
  Feed feed = Feed();
  Personas? personas;
  Persona? selectedPersona;
  PeerList? peerList;
  String? intentURL;
  AppBarNotifier appBarNotifier = AppBarNotifier();

  bool useCache = false;
  MethodChannel methodChannel = const MethodChannel('com.opencola');
  bool isFreshLogin = false;

  FutureOr<void> init() async {
    HttpOverrides.global = MyHttpOverrides();
    final String token = api.authToken;
    log('  >>  AppState.init: token = $token');
    if (token.isNotEmpty) {
      await fetchAll();
    }
    listen();
  }

  Future<void> listen() async {

    // For sharing images coming from outside the app while the app is in the memory
    FlutterSharingIntent.instance.getMediaStream()
    .listen((List<SharedFile> list) {
        AppState.instance.intentURL = list.map((SharedFile f) => f.value).join(',');
        log('   %%%');
        log('   %%% FlutterSharingIntent.getMediaStream: intentURL = ${AppState.instance.intentURL}');
        log('   %%%');
      }, onError: (dynamic err) {
        log('   %%% FlutterSharingIntent.getMediaStream: err = $err');
      },
    );

    // For sharing images coming from outside the app while the app is closed
    FlutterSharingIntent.instance.getInitialSharing().then((List<SharedFile> list) {
      final String value = list.map((SharedFile f) => f.value).join(',');
      log('   %%% FlutterSharingIntent.getInitialSharing: value = $value');
      if (value.isNotEmpty) {
        AppState.instance.intentURL = value;
        log('   %%% FlutterSharingIntent.getInitialSharing: setting intentURL = ${AppState.instance.intentURL}');
      }
    });
  }

  Future<void> fetchAll() async {
    await fetchPersonas();
    await fetchPeers();
  }

  Future<void> fetchPersonas() async {
    personas = await PersonasRequest().get();
  }

  List<Persona> personasList() =>
    personas?.personasList() ?? <Persona>[];

  List<String> personaNames([bool brief = false]) =>
    personas?.personaNames(brief) ?? <String>[];

  String selectedPersonaName() =>
    selectedPersona?.name ?? 'All';

  bool isSelected({required String personaName}) =>
    personaName == selectedPersonaName();

  Persona? personaForId(String personaId) =>
    personas?.personaForId(personaId);

  Persona? personaForName(String name) =>
    personas?.personaForName(name);

  Future<void> fetchPeers() async {
    String personaId = personas?.items?.first.id ?? '';
    if (selectedPersona != null) {
      final Persona? persona = personas?.items?.firstWhereOrNull((Persona element) => element.name == selectedPersona?.name);
      if (persona != null && persona.id != null) {
        personaId = persona.id!;
      }
    }
    log('  ++  AppState.fetchPeers: selectedPersona=${selectedPersona?.name}, personaId=$personaId');
    peerList = await PeersRequest().get(personaId);
  }

  Future<void> didResume() async {
    log('  ######  appLifeCycleState: resumed!');
    await fetchAll();
    useCache = false;
  }

  Future<void> onLogin() async {
    useCache = true;
    methodChannel.setMethodCallHandler((MethodCall handler) async {
      if (handler.method == 'ApplePushNotification') {
        log('  ###### Method Channel Received: ${handler.arguments}');
      }
    });
  }

  void clearUserData() {
    useCache = false;
  }
}

class MyHttpOverrides extends HttpOverrides {
  @override
  HttpClient createHttpClient(SecurityContext? context) =>
    super.createHttpClient(context)
      ..badCertificateCallback = (X509Certificate cert, String host, int port) => true;
}

class AppBarNotifier extends ChangeNotifier {
  void onChange() {
    notifyListeners();
  }
}

@Entity()
class AppStorage {
  @Id()
  int id = 0;

  String? appBlob;

  @Transient() // Ignore this property, not stored in the database.
  int? computedProperty;
}