import 'package:flutter/material.dart';

typedef LifecycleCallback = void Function(AppLifecycleState state);

/// Call [onAppLifecycleChange] when the app life cycle changes
class AppLifecycleObserver extends StatefulWidget {
  const AppLifecycleObserver({
    super.key,
    this.child,
    this.onAppLifecycleChange,
    this.builder,
    this.onAppResumed,
    this.durationBeforeResume,
  });

  final Widget? child;
  final LifecycleCallback? onAppLifecycleChange;

  /// Called only when app is resumed after it was paused (sent to background)
  final VoidCallback? onAppResumed;

  /// Rebuild the widget when LifecycleChange change
  final WidgetBuilder? builder;

  /// The duration that we need to exceed before calling [onAppResumed]
  /// if null [onAppResumed] method will be called instantly
  final Duration? durationBeforeResume;

  @override
  AppLifecycleObserverState createState() => AppLifecycleObserverState();
}

class AppLifecycleObserverState extends State<AppLifecycleObserver> with WidgetsBindingObserver {
  AppLifecycleState? _currentAppLifecycleState;

  /// Last time the was sent to background
  DateTime? _timestampSinceLastTime;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (mounted) {
      setState(() => widget.onAppLifecycleChange?.call(state));
    }
    if (state == AppLifecycleState.resumed && _currentAppLifecycleState == AppLifecycleState.inactive) {
      if (widget.durationBeforeResume == null) {
        widget.onAppResumed?.call();
      } else {
        if (_timestampSinceLastTime != null) {
          final Duration duration = DateTime.now().difference(_timestampSinceLastTime!);
          if (duration >= widget.durationBeforeResume!) {
            widget.onAppResumed?.call();
          }
        }
      }
    }

    if (state == AppLifecycleState.paused && _currentAppLifecycleState == AppLifecycleState.inactive) {
      _timestampSinceLastTime = DateTime.now();
    }

    if (state == AppLifecycleState.resumed && _currentAppLifecycleState == AppLifecycleState.inactive) {
      _timestampSinceLastTime = null;
    }

    _currentAppLifecycleState = state;
    super.didChangeAppLifecycleState(state);
  }

  @override
  Widget build(BuildContext context) =>
    widget.builder?.call(context) ?? widget.child!;
}