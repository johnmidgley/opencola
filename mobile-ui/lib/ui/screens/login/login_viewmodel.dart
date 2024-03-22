import 'dart:async';
import 'package:flutter/material.dart';
import 'package:opencola_flutter/api/login_request.dart';
import 'package:opencola_flutter/api/start_request.dart';
import 'package:opencola_flutter/app_state.dart';
import 'package:opencola_flutter/ui/base/base_navigation.dart';
import 'package:opencola_flutter/ui/base/base_viewmodel.dart';
import 'package:opencola_flutter/utils/utils.dart';

class LoginViewModel extends BaseViewModel {

  final TextEditingController serverController = TextEditingController();
  final TextEditingController passwordController = TextEditingController();

  @override
  FutureOr<void> init() {
    String ipAddress = AppState.instance.api.ipAddress;
    if (ipAddress.isEmpty) {
      ipAddress = 'localhost:5796';
    }
    final String password = AppState.instance.api.password;
    serverController.text = ipAddress;
    passwordController.text = password;
  }

  void onAppResumed() { }

  Future<bool> onLoginPressed() async {
    final String ipAddress = serverController.text;
    final String password = passwordController.text;
    AppState.instance.api.setIPAddress(ipAddress);
    AppState.instance.api.setPassword(password);

    busy = true;
    errorMessage = '';
    notifyListeners();

    final StartResponse result = await StartRequest().start(password);
    if (result == StartResponse.serverFailure) {
      // Case where password isn't needed (already unlocked?)
      await _waitForServerStart(password);
      return true;
    }
    else if (result == StartResponse.success) {
      await _waitForServerStart(password);
      return true;
    }

    busy = false;
    if (result == StartResponse.badPassword) {
      errorMessage = 'Incorrect password.';
    } else if (result == StartResponse.serverUnreachable) {
      errorMessage = 'Can\'t connect to server.\nPlease start it and try again.';
    } else {
      errorMessage = 'Unexpected error.';
    }
    notifyListeners();
    return false;
  }

  Future<void> _waitForServerStart(String password) async {
    final bool result = await StartRequest().getIndex();
    if (result) {
      await _performLogin(password);
    } else {
      Future<void>.delayed(Duration(milliseconds: minimumDelayMS), () async {
        _waitForServerStart(password);
      });
    }
  }

  Future<void> _performLogin(String password) async {
    Future<Null>.delayed(Duration(milliseconds: minimumDelayMS), () async {
      final String? token = await LoginRequest().login(password);
      if (token != null) {
        await AppState.instance.fetchAll();
        final Map<String, dynamic> args = <String, dynamic>{ NavigationKeys.selectedTab: 0 };
        navigateTo(NavigationTargets.home, true, args);
      } else {
        errorMessage = 'Bad password.';
        busy = false;
        notifyListeners();
      }
    });
  }
}
