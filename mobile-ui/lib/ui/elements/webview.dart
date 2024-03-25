import 'package:flutter/material.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:opencola_flutter/app_state.dart';
import 'package:opencola_flutter/ui/style/app_colors.dart';

class WebView extends StatefulWidget {
  const WebView({
    required this.urlString,
    this.title,
    super.key,
  });
  final String urlString;
  final String? title;

  @override
  State<WebView> createState() => _WebViewState();
}

class _WebViewState extends State<WebView> {
  @override
  void initState() {
    super.initState();
  }

  @override
  Widget build(BuildContext context) =>
    Scaffold(
      appBar: AppBar(
        title: Text(widget.title ?? ''),
        backgroundColor: AppColors.ocRed,
        foregroundColor: Colors.white,
      ),
      body: InAppWebView(
        initialUrlRequest: URLRequest(
          url: WebUri(widget.urlString),
          headers: <String, String>{ 'cookie' : AppState.instance.api.authToken },
          httpShouldHandleCookies: true,
        ),
        initialSettings: InAppWebViewSettings(
          useShouldOverrideUrlLoading: true,
        ),
        shouldOverrideUrlLoading: (InAppWebViewController controller, NavigationAction navigationAction) async =>
          NavigationActionPolicy.ALLOW,
        onReceivedServerTrustAuthRequest: (InAppWebViewController controller, URLAuthenticationChallenge challenge) async =>
          ServerTrustAuthResponse(action: ServerTrustAuthResponseAction.PROCEED),
        onReceivedHttpAuthRequest: (InAppWebViewController controller, URLAuthenticationChallenge challenge) async =>
          HttpAuthResponse(action: HttpAuthResponseAction.PROCEED),
      ),
    );
}
