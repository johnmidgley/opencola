import 'dart:async';
import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:opencola_flutter/app_state.dart';
import 'package:opencola_flutter/model/personas.dart';
import 'package:opencola_flutter/utils/utils.dart';

class PersonasRequest {
  Future<Personas?> get() async {
    try {
      final Response<dynamic> response = await AppState.instance.api.dio.get('/personas');
      return _handleGetResponse(response);
    } catch (e) {
      if (e is DioException) {
        log('PersonasRequest.get [${e.response?.statusCode.toString()}]: ${e.toString()}');
      } else {
        log(e.toString());
      }
      return null;
    }
  }

  Personas? _handleGetResponse(Response<dynamic> response) {
    final int statusCode = response.statusCode ?? 0;
    log('  >>  _handleGetResponse:statusCode = $statusCode');
    // log(const JsonEncoder.withIndent('  ').convert(response.data));
    final List<dynamic> items = response.data['items'];
    return Personas.fromJson(items);
  }

  Future<Persona?> update(Persona persona) async {
    try {
      final String data = jsonEncode(persona.toJson());
      final Response<dynamic> response = await AppState.instance.api.dio.put(
        '/personas',
        data: data,
        options: Options(contentType: Headers.jsonContentType),
      );
      return _handleUpdateResponse(response);
    } catch (e) {
      if (e is DioException) {
        log('PersonasRequest.update [${e.response?.statusCode.toString()}]: ${e.toString()}');
      } else {
        log(e.toString());
      }
      return null;
    }
  }

  Persona? _handleUpdateResponse(Response<dynamic> response) {
    final int statusCode = response.statusCode ?? 0;
    log('  >>  _handleUpdateResponse:statusCode = $statusCode');
    // log(const JsonEncoder.withIndent('  ').convert(response.data));
    final dynamic data = response.data;
    return Persona.fromJson(data);
  }

  Future<bool> delete(String personaId) async {
    try {
      final Response<dynamic> response = await AppState.instance.api.dio.delete('/personas/$personaId');
      return _handleDeleteResponse(response);
    } catch (e) {
      if (e is DioException) {
        log('PersonasRequest.delete [${e.response?.statusCode.toString()}]: ${e.toString()}');
      } else {
        log(e.toString());
      }
      return false;
    }
  }

  bool _handleDeleteResponse(Response<dynamic> response) {
    final int statusCode = response.statusCode ?? 0;
    log('  >>  _handleDeleteResponse:statusCode = $statusCode');
    // log(const JsonEncoder.withIndent('  ').convert(response.data));
    return statusCode >= 200 && statusCode < 300;
  }
}
