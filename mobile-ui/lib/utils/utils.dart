import 'dart:developer' as developer;
import 'package:intl/intl.dart';

extension StringCaseExtension on String {
  String toCapitalized() => length > 0 ?'${this[0].toUpperCase()}${substring(1).toLowerCase()}':'';
  String toTitleCase() => replaceAll(RegExp(' +'), ' ').split(' ').map((String str) => str.toCapitalized()).join(' ');
}

int get minimumDelayMS => 400;

String epochToDateString(int? epoch) {
  final DateTime timeStamp = DateTime.fromMillisecondsSinceEpoch((epoch ?? 0) * 1000, isUtc: false).toLocal();
  final DateFormat formatter = DateFormat('yyyy-MM-dd hh:mm a');
  return formatter.format(timeStamp);
}

void log(String? string) {
  developer.log('${DateTime.now()}: $string');
}

// import 'dart:io';
// import 'package:flutter_inappwebview/flutter_inappwebview.dart';
// import 'package:path_provider/path_provider.dart';
// Future<void> saveAsWebArchive(String url) async {
//   log('  >>>  handleSave($url)  <<<  ');
//   final WebUri webURI = WebUri(url);
//   final URLRequest request = URLRequest(url: webURI);
//   final HeadlessInAppWebView webview = HeadlessInAppWebView(initialUrlRequest: request);
//   await webview.run();
//   final InAppWebViewController? controller = webview.webViewController;
//   final Directory tempDir = await getTemporaryDirectory();
//   final String? mht = await controller?.saveWebArchive(filePath: tempDir.path, autoname: true);
//   log('mht:$mht');
// }
