import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:opencola_flutter/app_state.dart';
import 'package:opencola_flutter/ui/base/base_view.dart';
import 'package:opencola_flutter/ui/elements/app_lifecycle_observer.dart';
import 'package:opencola_flutter/ui/elements/opencola_call_to_action.dart';
import 'package:opencola_flutter/ui/screens/settings/settings_viewmodel.dart';
import 'package:opencola_flutter/ui/style/app_colors.dart';

class SettingsView extends StatelessWidget {
  SettingsView({super.key});

  final SettingsViewModel viewModel = SettingsViewModel();

  @override
  Widget build(BuildContext context) =>
    BaseView<SettingsViewModel>(
      viewModel: viewModel,
      appBar: AppBar(
        backgroundColor: AppColors.ocRed,
        foregroundColor: Colors.white,
        centerTitle: true,
        titleSpacing: 0,
        title: Container(
          decoration: BoxDecoration( border: Border.all(color: Colors.transparent, width: 0)),
          child: Text(
            'Settings',
            style: GoogleFonts.ibmPlexSans(
              color: Colors.white,
              fontSize: 24.0,
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
      ),
      builder: (BuildContext context, SettingsViewModel viewModel, Widget? child) =>
        AppLifecycleObserver(
          onAppResumed: () async => await viewModel.onAppResumed(),
          child: _settings(viewModel),
        ),
    );

  Container _settings(SettingsViewModel viewModel) =>
    Container(
      color: Colors.white,
      padding: const EdgeInsets.all(24.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          const SizedBox(height: 36.0),
          Padding(
            padding: const EdgeInsets.only(left: 24),
            child: Text(
              'SERVER:',
              style: GoogleFonts.ibmPlexSans(
                color: Colors.black,
                fontSize: 28.0,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
          const SizedBox(height: 12.0),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 32),
            child: Text(
              AppState.instance.api.ipAddress,
              style: GoogleFonts.ibmPlexSans(
                color: Colors.black,
                fontSize: 20.0,
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
          const SizedBox(height: 72.0),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: Container(
              decoration: BoxDecoration(
                border: Border.all(color: Colors.red, width: 3),
                borderRadius: BorderRadius.circular(32.0),
              ),
              child: OpenColaCallToAction(
                backgroundColor: Colors.transparent,
                captionColor: AppColors.ocRed,
                caption: 'Logout',
                onTap: () async => viewModel.onLogoutPressed(),
              ),
            ),
          ),
        ],
      ),
    );
}
