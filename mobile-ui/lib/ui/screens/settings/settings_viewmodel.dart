import 'dart:async';
import 'package:opencola_flutter/app_state.dart';
import 'package:opencola_flutter/ui/base/base_navigation.dart';
import 'package:opencola_flutter/ui/base/base_viewmodel.dart';
import 'package:opencola_flutter/utils/utils.dart';

class SettingsViewModel extends BaseViewModel {

  @override
  FutureOr<void> init() { }

  Future<void> onAppResumed() async {
    if (AppState.instance.intentURL != null) {
      final Map<String, dynamic> args = <String, dynamic>{ NavigationKeys.selectedTab: 0 };
      await navigateTo(NavigationTargets.home, true, args);
      return;
    }
  }

  Future<void> onLogoutPressed() async {
    busy = true;
    notifyListeners();
    Future<void>.delayed(Duration(milliseconds: minimumDelayMS), () {
      busy = true;
      notifyListeners();
      AppState.instance.api.setToken('');
      AppState.instance.api.setPassword('');
      navigateTo(NavigationTargets.login, true, null);
    });
  }
}
