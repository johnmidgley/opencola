import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:opencola_flutter/app_state.dart';
import 'package:opencola_flutter/ui/screens/login/login_view.dart';
import 'package:opencola_flutter/ui/screens/main/main_view.dart';
import 'package:opencola_flutter/utils/objectbox.dart';

Future<void> main() async {
  await init();
  runApp(const OpenColaApp());
}

Future<void> init() async {
  // This is required so ObjectBox can get the application directory to store the database in.
  WidgetsFlutterBinding.ensureInitialized();
  objectbox = await ObjectBox.create();
  SystemChrome.setPreferredOrientations(<DeviceOrientation>[DeviceOrientation.portraitUp]);
  await AppState.instance.init();
}

class OpenColaApp extends StatelessWidget {
  const OpenColaApp({super.key});

  @override
  Widget build(BuildContext context) =>
    MaterialApp(
      title: 'OpenCola',
      theme: ThemeData(primarySwatch: Colors.blue,),
      home: AppState.instance.api.authToken.isNotEmpty
          ? const MainView()
          : LoginView(),
    );
}
