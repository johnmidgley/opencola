import 'dart:async';
import 'package:opencola_flutter/app_state.dart';
import 'package:opencola_flutter/ui/base/base_viewmodel.dart';

class MainViewModel extends BaseViewModel {
  int _tabIndex = 0;
  int get tabIndex => _tabIndex;

  @override
  FutureOr<void> init() {}

  void onTabSelected(int index) {
    if (_tabIndex != index) {
      _tabIndex = index;
      notifyListeners();
    }
  }

  Future<void> onResume() async {
    await AppState.instance.didResume();
  }
}
