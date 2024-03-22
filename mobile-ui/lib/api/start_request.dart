import 'dart:async';
import 'package:dio/dio.dart';
import 'package:opencola_flutter/app_state.dart';
import 'package:opencola_flutter/utils/utils.dart';

enum StartResponse {
  success,
  badPassword,
  serverFailure,
  serverUnreachable,
  unknown,
}

class StartRequest {
  Future<StartResponse> start(String password) async {
    try {
      final Response<dynamic> response = await AppState.instance.api.dio.post('/start', data: 'password=$password');
      return _handleStartResponse(response);
    } catch (e) {
      if (e is DioException) {
        if (e.response?.statusCode == null) {
          log('  @@@@@  StartRequest.start: statusCode is null');
          return StartResponse.serverUnreachable;
        }
        final int statusCode = e.response?.statusCode ?? 0;
        log('  @@@@@  StartRequest.start [$statusCode]: ${e.toString()}');
        if (statusCode == 500) {
          return StartResponse.serverFailure;
        }
      } else {
        log('  @@@@@  e:${e.toString()}');
      }
      return StartResponse.unknown;
    }
  }

  StartResponse _handleStartResponse(Response<dynamic> response) {
    final int statusCode = response.statusCode ?? 0;
    log('  >> StartRequest.start._handleStartResponse:statusCode = $statusCode');
    if (statusCode == 500) {
      return StartResponse.serverFailure;
    }
    if (statusCode == 200) {
      final String? data = response.data;
      if (data != null && (data.contains('Incorrect password') || data.contains('Bad password'))) {
        return StartResponse.badPassword;
      }
      return StartResponse.success;
    }
    return StartResponse.unknown;
  }

  Future<bool> getIndex() async {
    try {
      final Response<dynamic> response = await AppState.instance.api.dio.get('/index.html');
      final int statusCode = response.statusCode ?? 0;
      log('StartRequest.getIndex: statusCode=$statusCode');
      return statusCode == 200;
    } catch (e) {
      if (e is DioException) {
        log('StartRequest.getIndex [${e.response?.statusCode.toString()}]: ${e.toString()}');
      } else {
        log(e.toString());
      }
      return false;
    }
  }
}
