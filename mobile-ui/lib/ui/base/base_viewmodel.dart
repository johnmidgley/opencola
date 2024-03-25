import 'package:flutter/material.dart';
import 'package:opencola_flutter/ui/base/base_navigation.dart';
import 'dart:async';

abstract class BaseViewModel extends ChangeNotifier {
  bool _busy = false;
  String? _errorMessage;

  bool get busy => _busy;
  @protected
  set busy(bool value) => _busy = value;

  String? get errorMessage => _errorMessage;
  @protected
  set errorMessage(String? value) => _errorMessage = value;

  late OnNavigationCallback navigateTo;

  FutureOr<void> init();
}
