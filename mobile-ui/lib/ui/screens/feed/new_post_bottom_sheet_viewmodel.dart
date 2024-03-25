import 'dart:async';
import 'package:flutter/material.dart';
import 'package:opencola_flutter/api/new_post_request.dart';
import 'package:opencola_flutter/app_state.dart';
import 'package:opencola_flutter/model/personas.dart';
import 'package:opencola_flutter/ui/base/base_viewmodel.dart';
import 'package:opencola_flutter/utils/utils.dart';

class NewPostBottomSheetViewModel extends BaseViewModel {

  final TextEditingController urlController = TextEditingController();
  final FocusNode focusNode = FocusNode();
  double keyboardHeight = 0;
  Persona? persona;

  @override
  FutureOr<void> init() async {
    focusNode.addListener(_focusChanged);
    persona = AppState.instance.selectedPersona;

    // Call notify after a delay, giving time for autofocused keyboard to appear
    Future<void>.delayed(const Duration(milliseconds: 800), () {
      notifyListeners();
    });
  }

  void setKeyboardHeight(double height) {
    if (height > keyboardHeight) {
      keyboardHeight = height;
    }
  }

  String? urlText;
  String get hint => AppState.instance.intentURL != null ? '' : 'enter url here';

  void _focusChanged() {
    // Always keep keyboard showing
    if (focusNode.hasFocus == false) {
      focusNode.requestFocus();
    }
    notifyListeners();
  }


  Future<void> save() async {
    final String context = AppState.instance.feed.context ?? '';
    final String? personaId = persona?.id ?? AppState.instance.personasList().first.id;
    if (personaId != null) {
      await NewPostRequest().newPost(context, personaId, urlController.text);
      AppState.instance.intentURL = null;
      notifyListeners();
    }
  }

  void cancel() {
    log('  ++  CustomTimeFilterBottomSheetViewModel.cancel');
  }

  bool isSelected(String personaName) => (persona?.name == personaName);

  void didSelectPersona(String choice) async {
    persona = AppState.instance.personaForName(choice) ?? Persona();
    notifyListeners();
  }

  String currentPersonaSelection() => persona?.name ?? (AppState.instance.personasList().first.name ?? '');

  @override
  void dispose() {
    urlController.dispose();
    super.dispose();
  }
}
