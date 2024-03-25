import 'dart:io';
import 'package:dio/dio.dart';
import 'package:dio/io.dart';
import 'package:opencola_flutter/objectbox.g.dart';
import 'package:objectbox/objectbox.dart';
import 'package:opencola_flutter/utils/objectbox.dart';
import 'package:opencola_flutter/utils/utils.dart';

class API {
  API() {
    _readTokensFromStorage();
    _configCharlesProxy();
  }

  void _readTokensFromStorage() {
    // Read tokens from Storage
    final List<CredentialStorage> credentials = storageBox.getAll();
    if (credentials.isNotEmpty) {
      _credentials = credentials.first;
      dioOptions.baseUrl = 'https://$ipAddress';
    }
  }

  late CredentialStorage _credentials = CredentialStorage();
  final Box<CredentialStorage> storageBox = objectbox.store.box<CredentialStorage>();
  String get authToken => _credentials.authToken ?? '';
  void setToken(String? authToken) {
    _readTokensFromStorage();
    _credentials.authToken = authToken;
    storageBox.removeAll();
    storageBox.put(_credentials);
  }

  String get ipAddress => _credentials.ipAddress ?? '';
  void setIPAddress(String? ipAddress) {
    _readTokensFromStorage();
    _credentials.ipAddress = ipAddress;
    storageBox.removeAll();
    storageBox.put(_credentials);
    _configCharlesProxy();
  }

  String get password => _credentials.password ?? '';
  void setPassword(String? password) {
    _readTokensFromStorage();
    _credentials.password = password;
    storageBox.removeAll();
    storageBox.put(_credentials);
  }

  // static String get defaultURL => 'https://localhost.charlesproxy.com:7796';
  // static String get defaultURL => 'https://localhost:5796';
  // static String get defaultURL => 'https://192.168.1.95:5795';  // WiFi
  // static String get defaultURL => 'https://192.168.1.96:5796';  // Wired
  static String get defaultURL => 'https://192.168.1.96:5796';

  BaseOptions dioOptions = BaseOptions(
    baseUrl: defaultURL,
    connectTimeout: const Duration(seconds: 1000), // 20
    receiveTimeout: const Duration(seconds: 1000), // 30
    contentType: 'application/x-www-form-urlencoded',
    responseType: ResponseType.json,
  );

  late final Dio _dio = Dio(dioOptions);
  Dio get dio {
    if (authToken.isNotEmpty) {
      _dio.options.headers['cookie'] = authToken;
    }

    _dio.interceptors.clear();
    _dio.interceptors.add(
      InterceptorsWrapper(
        onResponse: (Response<dynamic> e, ResponseInterceptorHandler handler) {
          final String requestPath = e.requestOptions.path;
          final String realPath = e.realUri.pathSegments.first;
          final int numRedirects = e.redirects.length;
          // log('    ####  API.onResponse: numRedirects=$numRedirects, requestPath=$requestPath, realPath=$realPath');
          if (numRedirects > 0 && !requestPath.contains(realPath)) {
            log('    ####  API: ABORT due to redirect to <<$realPath>> for request <<$requestPath>>');
            setToken('');
            setPassword('');
          }
          return handler.next(e);
        },
        onRequest: (RequestOptions options, RequestInterceptorHandler handler) => handler.next(options),
        onError: (DioException e, ErrorInterceptorHandler handler) async {
          // Check for unreachable server
          if (e.error.toString().contains('Connection refused')) {
            log('    ####  API: Resetting token due to DioException: ${e.error.toString()}');
            setToken('');
            setPassword('');
          }
          // Check for bad token
          else if (e.response?.statusCode == 404 && !e.requestOptions.path.contains('/index.html') ) {
            log('    ####  API: Resetting token due to statusCode 404 for path ${e.requestOptions.path}');
            setToken('');
            setPassword('');
          }
          return handler.next(e);
        },
      ),
    );
    return _dio;
  }

  void _configCharlesProxy() {
    // ignore: deprecated_member_use
    (dio.httpClientAdapter as DefaultHttpClientAdapter).onHttpClientCreate = (HttpClient client) {
      if (ipAddress.toLowerCase().contains('charles')) {
        client.findProxy = (Uri uri) => 'PROXY localhost:8888;';
      }
      client.badCertificateCallback = (X509Certificate cert, String host, int port) => true;
      return;
    };
  }
}

@Entity()
class CredentialStorage {
  @Id()
  int id = 0;

  String? password;
  String? authToken;
  String? ipAddress;

  @Transient() // Ignore this property, not stored in the database.
  int? computedProperty;
}