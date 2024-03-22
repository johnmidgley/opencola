import 'dart:async';
import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:opencola_flutter/app_state.dart';
import 'package:opencola_flutter/model/feed.dart';
import 'package:opencola_flutter/utils/utils.dart';

class NewPostRequest {
  Future<Entity?> newPost(String context, String personaId, String description) async {
    try {
      final Response<dynamic> response = await AppState.instance.api.dio.post(
        '/post?context=$context&personaId=$personaId',
        data: <String, Object?>{
          'entityId': '',
          'name': '',
          'imageUri': '',
          'description': description,
          'like': null,
          'attachments': <void>[],
          'tags': '',
          'comment': '',
        },
        options: Options(contentType: Headers.jsonContentType),
      );
      return _handleNewPostResponse(response);
    } catch (e) {
      if (e is DioException) {
        log('  >>  NewPostRequest.newPost [${e.response?.statusCode.toString()}]: ${e.toString()}');
      } else {
        log(e.toString());
      }
      return null;
    }
  }

  Entity? _handleNewPostResponse(Response<dynamic> response) {
    final int statusCode = response.statusCode ?? 0;
    log('  >>  _handleNewPostResponse:statusCode = $statusCode');
    log(const JsonEncoder.withIndent('  ').convert(response.data));
    return Entity.fromJson(response.data);
  }
}
