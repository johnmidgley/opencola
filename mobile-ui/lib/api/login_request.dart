import 'dart:async';
// import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:opencola_flutter/app_state.dart';
import 'package:opencola_flutter/utils/utils.dart';

class LoginRequest {
  Future<String?> login(String password) async {
    try {
      final Response<dynamic> response = await AppState.instance.api.dio.post('/login', data: 'password=$password');
      return _handleLoginResponse(response);
    } catch (e) {
      if (e is DioException) {
        // Handle 302 redirect
        final int? statusCode = e.response?.statusCode;
        if (statusCode == 302) {
          final String? token = getTokenFromResponse(e.response);
          if (token != null) {
            AppState.instance.api.setToken(token);
            return token;
          }
        }
      } else {
        log(e.toString());
      }
      return null;
    }
  }

  String? getTokenFromResponse(Response<dynamic>? response) {

    // Check response body for "Bad password"
    final dynamic data = response?.data;
    if (data != null && (data.contains('Incorrect password') || data.contains('Bad password'))) {
      return null;
    }

    final String? cookie = response?.headers.map['set-cookie']?.first;
    final String? token = cookie?.split(';').where((String string) => string.contains('authToken')).first;
    log('getTokenFromResponse: $token');
    return token;
  }

  String? _handleLoginResponse(Response<dynamic> response) {
    final int statusCode = response.statusCode ?? 0;
    log('  >>  LoginRequest:statusCode = $statusCode');
    // log(const JsonEncoder.withIndent('  ').convert(response.data));
    return getTokenFromResponse(response);
  }
}
