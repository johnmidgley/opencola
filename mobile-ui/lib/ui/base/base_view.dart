import 'package:flutter/material.dart';
import 'package:opencola_flutter/ui/base/base_navigation.dart';
import 'package:opencola_flutter/ui/base/base_viewmodel.dart';
import 'package:opencola_flutter/ui/style/app_colors.dart';

class BaseView<T extends BaseViewModel> extends StatefulWidget {
  const BaseView({
    required this.appBar,
    this.backgroundColor = AppColors.scaffoldBackground,
    required this.builder,
    this.hasScrollableBody = true,
    required this.viewModel,
    this.child,
    super.key,
  });

  final AppBar appBar;
  final Color backgroundColor;
  final Widget Function(BuildContext, T, Widget?) builder;
  final bool hasScrollableBody;
  final T viewModel;
  final Widget? child;

  @override
  State<BaseView<T>> createState() => _BaseViewState<T>();
}

class _BaseViewState<T extends BaseViewModel> extends State<BaseView<T>> {
  @override
  void initState() {
    super.initState();
    // ignore: discarded_futures
    widget.viewModel.init();
    widget.viewModel.navigateTo = (NavigationTargets page, bool clearStack, Map<String, dynamic>? arguments) async {
      if (!mounted) {
        return Future<dynamic>.value(null);
      }
      return Navigator.of(context).pushAndRemoveUntil(
        MaterialPageRoute<dynamic>(
            builder: (BuildContext context) => generateScreenWidget(page, arguments),
            settings: RouteSettings(arguments: arguments),
          ),
        (Route<dynamic> route) => !clearStack,
      );
    };
    widget.viewModel.addListener(_viewStateListener);
  }

  void _viewStateListener() {
    setState(() {});
  }

  @override
  Widget build(BuildContext context) =>
   Scaffold(
      appBar: widget.appBar,
      backgroundColor: widget.backgroundColor,
      body: Stack(
        children: <Widget>[
          Positioned.fill(
            child: CustomScrollView(
              controller: PrimaryScrollController.of(context),
              slivers: <Widget>[
                SliverFillRemaining(
                  hasScrollBody: widget.hasScrollableBody,
                  child: widget.builder(
                    context,
                    widget.viewModel,
                    widget.child,
                  ),
                ),
              ],
            ),
          ),
          if (widget.viewModel.busy) ...<Widget>[
            Positioned.fill(
              child: Container(color: AppColors.scaffoldBackground),
            ),
            Align(
              alignment: Alignment.center,
              child: Container(
                width: 100,
                height: 100,
                padding: const EdgeInsets.all(2.0),
                child: const CircularProgressIndicator(
                  color: AppColors.ocRed,
                  strokeWidth: 10,
                ),
              ),
            ),
          ],
        ],
      ),
    );

  @override
  void dispose() {
    widget.viewModel.removeListener(_viewStateListener);
    widget.viewModel.dispose();
    super.dispose();
  }
}
