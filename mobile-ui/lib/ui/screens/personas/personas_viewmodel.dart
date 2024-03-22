import 'dart:async';
import 'package:collection/collection.dart';
import 'package:flutter/material.dart';
import 'package:opencola_flutter/api/personas_request.dart';
import 'package:opencola_flutter/app_state.dart';
import 'package:opencola_flutter/model/personas.dart';
import 'package:opencola_flutter/ui/base/base_navigation.dart';
import 'package:opencola_flutter/ui/base/base_viewmodel.dart';
import 'package:opencola_flutter/utils/utils.dart';

class PersonasViewModel extends BaseViewModel {

  final TextEditingController searchController = TextEditingController();
  int _isEditing = -1;

  @override
  FutureOr<void> init() async {
    busy = true;
    notifyListeners();
    await AppState.instance.fetchPersonas();
    Future<void>.delayed(Duration(milliseconds: minimumDelayMS), () {
      busy = false;
      notifyListeners();
    });
  }

  Future<void> onAppResumed() async {
    if (AppState.instance.intentURL != null) {
      final Map<String, dynamic> args = <String, dynamic>{ NavigationKeys.selectedTab: 0 };
      await navigateTo(NavigationTargets.home, true, args);
      return;
    }

    busy = true;
    notifyListeners();
    await AppState.instance.fetchPersonas();
    Future<void>.delayed(Duration(milliseconds: minimumDelayMS), () {
      busy = false;
      notifyListeners();
    });
  }

  Future<void> refresh() async {
    await AppState.instance.fetchPersonas();
    notifyListeners();
  }

  Future<void> onSearch(String query) async {
    notifyListeners();
    AppState.instance.appBarNotifier.onChange();
  }

  bool isSelected(String personaName) => personaName == 'Manage...';

  String currentSelection() => 'Manage...';

  Future<void> didSelectPersona(String choice) async {
    final List<String> list = AppState.instance.personaNames();
    final int index = list.indexWhere((String element) => element == choice );

    if (index >= (list.length-1)) {
      return;
    }

    if (index == 0) {
      AppState.instance.selectedPersona = null;
    } else if (index < (list.length-1)) {
      final List<Persona> personasList = AppState.instance.personasList();
      final Persona? persona = personasList.firstWhereOrNull((Persona element) => element.name == list[index]);
      AppState.instance.selectedPersona = persona;
    }
    final Map<String, dynamic> args = <String, dynamic>{ NavigationKeys.selectedTab: 0 };
    navigateTo(NavigationTargets.home, true, args);
  }

  Future<void> navigateToFeed() async {
    AppState.instance.selectedPersona = null;
    final Map<String, dynamic> args = <String, dynamic>{ NavigationKeys.selectedTab: 0 };
    navigateTo(NavigationTargets.home, true, args);
  }

  Future<void> navigateToSettings() async {
    navigateTo(NavigationTargets.settings, false, null);
  }

  int get numPersonas => AppState.instance.personasList().length;
  String nameForPersona(int x) => _getPersona(x)?.name ?? '...';
  String idForPersona(int x) => _getPersona(x)?.id ?? '...';
  String publicKeyForPersona(int x) => _getPersona(x)?.publicKey ?? '...';
  String linkForPersona(int x) => _getPersona(x)?.address ?? '...';
  String photoForPersona(int x) => _getPersona(x)?.imageUri ?? '...';
  TextEditingController nameController(int x) {
    final String name = _getPersona(x)?.name ?? '...';
    return TextEditingController(text: name);
  }

  bool isEditing(int index) => _isEditing == index;

  void onEdit(int index) {
    _isEditing = index;
    notifyListeners();
  }

  void onCancel() {
    _isEditing = -1;
    notifyListeners();
  }

  void onSave(int index, String? name) async {
    final Persona? persona = _getPersona(index);
    if (persona != null) {
      persona.name = name;
      await PersonasRequest().update(persona);
      await AppState.instance.fetchPersonas();
    }
    _isEditing = -1;
    notifyListeners();
  }

  void onDelete(int index) async {
    final String? personaId = _getPersona(index)?.id;
    if (personaId != null) {
      await PersonasRequest().delete(personaId);
      await AppState.instance.fetchPersonas();
    }
    _isEditing = -1;
    notifyListeners();
  }

  Persona? _getPersona(int x) {
    final List<Persona> personasList = AppState.instance.personasList();
    final int numPersonas = personasList.length;
    if (x < numPersonas) {
      return personasList[x];
    }
    return null;
  }

  @override
  void dispose() {
    searchController.dispose();
    super.dispose();
  }
}
