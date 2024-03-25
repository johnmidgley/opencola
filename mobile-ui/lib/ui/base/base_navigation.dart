import 'package:flutter/widgets.dart';
import 'package:opencola_flutter/ui/screens/login/login_view.dart';
import 'package:opencola_flutter/ui/screens/main/main_view.dart';
import 'package:opencola_flutter/ui/screens/personas/personas_view.dart';
import 'package:opencola_flutter/ui/screens/settings/settings_view.dart';

enum NavigationTargets {
  home,
  settings,
  login,
  personas,
}

typedef OnNavigationCallback = Future<dynamic> Function(NavigationTargets, bool, Map<String, dynamic>?);

abstract class NavigationKeys {
  static const String selectedTab = 'selectedTab';
}

Widget generateScreenWidget(NavigationTargets page, Map<String, dynamic>? arguments) {
  switch (page) {
    case NavigationTargets.home:
      final int? selectedTab = arguments?[NavigationKeys.selectedTab];
      return MainView(selectedTab: selectedTab);
    case NavigationTargets.settings:
      return SettingsView();
    case NavigationTargets.login:
      return LoginView();
    case NavigationTargets.personas:
      return PersonasView();
  }
}
