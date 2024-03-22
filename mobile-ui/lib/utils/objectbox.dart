// ignore: depend_on_referenced_packages
import 'dart:io';

// ignore: depend_on_referenced_packages
import 'package:path/path.dart' as p;
// ignore: depend_on_referenced_packages
import 'package:path_provider/path_provider.dart';
import 'package:opencola_flutter/objectbox.g.dart';  // created by `flutter pub run build_runner build`


/// Provides access to the ObjectBox Store throughout the app.
late ObjectBox objectbox;

class ObjectBox {
  /// The Store of this app.
  late final Store store;

  ObjectBox._create(this.store) {
    // Add any additional setup code, e.g. build queries.
  }

  /// Create an instance of ObjectBox to use throughout the app.
  static Future<ObjectBox> create() async {
    final Directory docsDir = await getApplicationDocumentsDirectory();
    // Future<Store> openStore() {...} is defined in the generated objectbox.g.dart
    final Store store = await openStore(directory: p.join(docsDir.path, 'obx-example'));
    return ObjectBox._create(store);
  }
}
