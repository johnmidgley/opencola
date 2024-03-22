import 'package:flutter/material.dart';
import 'package:opencola_flutter/app_state.dart';
import 'package:opencola_flutter/ui/base/base_navigation.dart';
import 'package:opencola_flutter/ui/screens/feed/feed_view.dart';
import 'package:opencola_flutter/ui/screens/feed/new_post_bottom_sheet.dart';
import 'package:opencola_flutter/ui/screens/main/main_viewmodel.dart';
import 'package:opencola_flutter/ui/screens/peer/peer_view.dart';

class MainView extends StatefulWidget {
  const MainView({
    super.key,
    this.selectedTab,
  });

  final int? selectedTab;

  @override
  State<MainView> createState() => _MainViewState();
}

class _MainViewState extends State<MainView> with WidgetsBindingObserver {
  final List<Widget> tabs = <Widget>[];
  late MainViewModel viewModel;
  FeedView feedView = FeedView();
  PeerView peerView = PeerView();

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) async {
    super.didChangeAppLifecycleState(state);
    if (state == AppLifecycleState.resumed) {
      await viewModel.onResume();
      if (AppState.instance.intentURL != null) {
        // ignore: use_build_context_synchronously
        showNewPost(context);
      }
    }
  }

  @override
  void initState() {
    super.initState();
    tabs.add(feedView);
    tabs.add(peerView);

    viewModel = MainViewModel();
    // ignore: discarded_futures
    viewModel.init();
    viewModel.onTabSelected(widget.selectedTab ?? 0);
    viewModel.navigateTo = (NavigationTargets target, bool clearStack, Map<String, dynamic>? arguments) async =>
      Navigator.of(context).pushAndRemoveUntil(
        MaterialPageRoute<dynamic>(
            builder: (BuildContext context) => generateScreenWidget(target, arguments),
            settings: RouteSettings(arguments: arguments),
          ),
        (Route<dynamic> route) => !clearStack,
      );
    viewModel.addListener(_onViewModelUpdated);
    WidgetsBinding.instance.addObserver(this);
  }

  void _onViewModelUpdated() => setState(() {});

  @override
  Widget build(BuildContext context) =>
    Scaffold(
      body: tabs[viewModel.tabIndex],
      backgroundColor: Colors.transparent,
      extendBody: true,
    );

  Future<void> showNewPost(BuildContext context) async {
    if (viewModel.tabIndex != 0) {
      viewModel.onTabSelected(0);
      _onViewModelUpdated();
    }
    showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(24.0),
      ),
      builder: (BuildContext context) => const NewPostBottomSheet(),
    ).then((bool? shouldRefresh) => <Set<Future<void>>>{
        if (shouldRefresh == true) <Future<void>>{
          feedView.viewModel.refresh(),
        },
      },
    );
  }

  @override
  void dispose() {
    super.dispose();
    viewModel.removeListener(_onViewModelUpdated);
    viewModel.dispose();
    WidgetsBinding.instance.removeObserver(this);
  }
}
