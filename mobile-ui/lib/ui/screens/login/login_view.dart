import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:opencola_flutter/ui/base/base_view.dart';
import 'package:opencola_flutter/ui/elements/app_lifecycle_observer.dart';
import 'package:opencola_flutter/ui/elements/opencola_call_to_action.dart';
import 'package:opencola_flutter/ui/elements/opencola_textfield.dart';
import 'package:opencola_flutter/ui/screens/login/login_viewmodel.dart';
import 'package:opencola_flutter/ui/style/app_assets.dart';
import 'package:opencola_flutter/ui/style/app_colors.dart';

class LoginView extends StatelessWidget {
  LoginView({super.key});

  final LoginViewModel viewModel = LoginViewModel();

  @override
  Widget build(BuildContext context) =>
    BaseView<LoginViewModel>(
      viewModel: viewModel,
      appBar: AppBar(
        backgroundColor: AppColors.ocRed,
        centerTitle: true,
        titleSpacing: 0,
        title: Container(
          decoration: BoxDecoration( border: Border.all(color: Colors.transparent, width: 0)),
          child: Text(
            'OpenCola',
            style: GoogleFonts.ibmPlexSans(
              color: Colors.white,
              fontSize: 24.0,
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
      ),
      builder: (BuildContext context, LoginViewModel viewModel, Widget? child) =>
        AppLifecycleObserver(
          onAppResumed: () => viewModel.onAppResumed(),
          child: Container(
            color: Colors.white,
            padding: const EdgeInsets.all(24.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: <Widget>[
                const SizedBox(height: 36.0),
                OpenColaTextField(
                  controller: viewModel.serverController,
                  hint: '192.168.1.100',
                  label: 'Server',
                  keyboardType: TextInputType.url,
                ),
                const SizedBox(height: 20.0),
                OpenColaSecureTextField(
                  controller: viewModel.passwordController,
                  hint: 'password',
                  label: 'Password',
                ),
                const SizedBox(height: 32.0),
                OpenColaCallToAction(
                  caption: 'Login',
                  onTap: () async => viewModel.onLoginPressed(),
                ),
                const SizedBox(height: 8.0),
                Text(
                  viewModel.errorMessage ?? '',
                  style: const TextStyle(
                    color: AppColors.ocRed,
                    fontSize: 16.0,
                  ),
                ),
                const SizedBox(height: 48.0),
                const Center(
                  child: ImageIcon(
                    AssetImage(AppAssets.pullTab),
                    color: AppColors.ocRed,
                    size: 200,
                  ),
                ),
              ],
            ),
          ),
        ),
    );
}
