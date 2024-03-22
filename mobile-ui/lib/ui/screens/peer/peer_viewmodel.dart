import 'dart:async';
import 'package:collection/collection.dart';
import 'package:flutter/material.dart';
import 'package:opencola_flutter/app_state.dart';
import 'package:opencola_flutter/model/peer.dart';
import 'package:opencola_flutter/model/personas.dart';
import 'package:opencola_flutter/ui/base/base_navigation.dart';
import 'package:opencola_flutter/ui/base/base_viewmodel.dart';
import 'package:opencola_flutter/utils/utils.dart';

class PeerViewModel extends BaseViewModel {

  final TextEditingController searchController = TextEditingController();

  @override
  FutureOr<void> init() async {
    busy = true;
    notifyListeners();
    AppState.instance.selectedPersona ??= AppState.instance.personas?.items?.first;
    await AppState.instance.fetchPeers();
    Future<void>.delayed(Duration(milliseconds: minimumDelayMS), () {
      busy = false;
      notifyListeners();
    });
  }

  void onAppResumed() async {
    if (AppState.instance.intentURL != null) {
      return;
    }
    busy = true;
    notifyListeners();
    AppState.instance.selectedPersona ??= AppState.instance.personas?.items?.first;
    await AppState.instance.fetchPeers();
    Future<void>.delayed(Duration(milliseconds: minimumDelayMS), () {
      busy = false;
      notifyListeners();
    });
  }

  Future<void> refresh() async {
    await AppState.instance.fetchPeers();
    notifyListeners();
  }

  Future<void> onSearch(String query) async {
    notifyListeners();
    AppState.instance.appBarNotifier.onChange();
  }

  bool isSelected(String personaName) => AppState.instance.isSelected(personaName: personaName);

  String currentSelection() => AppState.instance.selectedPersonaName();

  Future<void> didSelectPersona(String choice) async {
    final List<String> list = AppState.instance.personaNames();
    final int index = list.indexWhere((String element) => element == choice );

    if (index >= (list.length-1)) {
      navigateTo(NavigationTargets.personas, false, null);
    } else {
      if (index == 0) {
        AppState.instance.selectedPersona = null;
      } else if (index < (list.length-1)) {
        final List<Persona> personasList = AppState.instance.personasList();
        final Persona? persona = personasList.firstWhereOrNull((Persona element) => element.name == list[index]);
        AppState.instance.selectedPersona = persona;
      }
      AppState.instance.appBarNotifier.onChange();
      await AppState.instance.fetchPeers();
    }
    notifyListeners();
  }

  Future<void> navigateToFeed() async {
    final Map<String, dynamic> args = <String, dynamic>{ NavigationKeys.selectedTab: 0 };
    navigateTo(NavigationTargets.home, true, args);
  }

  Future<void> navigateToSettings() async {
    navigateTo(NavigationTargets.settings, false, null);
  }

  int get numPeers => AppState.instance.peerList?.peers?.length ?? 0;
  String nameForPeer(int x) => _getPeer(x)?.name ?? '...';
  String idForPeer(int x) => _getPeer(x)?.id ?? '...';
  String publicKeyForPeer(int x) => _getPeer(x)?.publicKey ?? '...';
  String linkForPeer(int x) => _getPeer(x)?.address ?? '...';
  String photoForPeer(int x) => _getPeer(x)?.imageUri ?? '...';

  Peer? _getPeer(int x) {
    final PeerList? peerList = AppState.instance.peerList;
    if (peerList != null) {
      final int numPeers = peerList.peers?.length ?? 0;
      if (x < numPeers) {
        return peerList.peers![x];
      }
    }
    return null;
  }

  @override
  void dispose() {
    searchController.dispose();
    super.dispose();
  }
}
