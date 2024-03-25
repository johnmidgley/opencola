import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:dio/dio.dart';
import 'package:http_parser/http_parser.dart';
import 'package:opencola_flutter/app_state.dart';
import 'package:opencola_flutter/model/feed.dart';
import 'package:opencola_flutter/utils/utils.dart';

class EntityRequest {

  Future<Entity?> bubble(Entity entity, String context, String personaId) async {
    try {
      final Response<dynamic> response = await AppState.instance.api.dio.post(
        '/entity/${entity.entityId}?context=$context&personaId=$personaId',
        data: null,
        options: Options(contentType: Headers.jsonContentType),
      );
      return _handleBubbleResponse(response);
    } catch (e) {
      if (e is DioException) {
        log('  >>  EntityRequest.bubble [${e.response?.statusCode.toString()}]: ${e.toString()}');
      } else {
        log(e.toString());
      }
      return null;
    }
  }

  Entity? _handleBubbleResponse(Response<dynamic> response) {
    final int statusCode = response.statusCode ?? 0;
    log('  >>  _handleBubbleResponse:statusCode = $statusCode');
    // log(const JsonEncoder.withIndent('  ').convert(response.data));
    return Entity.fromJson(response.data);
  }

  Future<Entity?> toggleLike(Entity entity, String context, String personaId) async {
    try {
      final Response<dynamic> response = await AppState.instance.api.dio.post(
        '/entity/${entity.entityId}/like?context=$context&personaId=$personaId',
        data: _jsonLikeString(entity, personaId),
        options: Options(contentType: Headers.jsonContentType),
      );
      return _handleLikeResponse(response);
    } catch (e) {
      if (e is DioException) {
        log('  >>  EntityRequest.toggleLike [${e.response?.statusCode.toString()}]: ${e.toString()}');
      } else {
        log(e.toString());
      }
      return null;
    }
  }

  static String _jsonLikeString(Entity entity, String personaId) {
    final Map<String, dynamic> json = <String, dynamic>{};
    final bool isLiked = entity.isLiked(personaId);
    json['value'] = isLiked ? null : true;
    final String jsonString = jsonEncode(json);
    // log(const JsonEncoder.withIndent('  ').convert(jsonString));
    return jsonString;
  }

  Entity? _handleLikeResponse(Response<dynamic> response) {
    final int statusCode = response.statusCode ?? 0;
    log('  >>  _handleLikeResponse:statusCode = $statusCode');
    // log(const JsonEncoder.withIndent('  ').convert(response.data));
    return Entity.fromJson(response.data);
  }

  Future<Entity?> updateComment(Entity entity, String context, String personaId, String? commentId, String text) async {
    try {
      final Response<dynamic> response = await AppState.instance.api.dio.post(
        '/entity/${entity.entityId}/comment?context=$context&personaId=$personaId',
        data: <String, String?>{
          'commentId': commentId,
          'text': text,
        },
        options: Options(contentType: Headers.jsonContentType),
      );
      return _handleCommentResponse(response);
    } catch (e) {
      if (e is DioException) {
        log('  >>  EntityRequest.updateComment [${e.response?.statusCode.toString()}]: ${e.toString()}');
      } else {
        log(e.toString());
      }
      return null;
    }
  }

  Entity? _handleCommentResponse(Response<dynamic> response) {
    final int statusCode = response.statusCode ?? 0;
    log('  >>  _handleLikeResponse:statusCode = $statusCode');
    // log(const JsonEncoder.withIndent('  ').convert(response.data));
    return Entity.fromJson(response.data);
  }

  Future<bool> deleteComment(String context, String personaId, String commentId) async {
    try {
      final Response<dynamic> response = await AppState.instance.api.dio.delete(
        '/comment/$commentId?context=$context&personaId=$personaId',
      );
      return _handleDeleteCommentResponse(response);
    } catch (e) {
      if (e is DioException) {
        log('  >>  EntityRequest.deleteComment [${e.response?.statusCode.toString()}]: ${e.toString()}');
      } else {
        log(e.toString());
      }
      return false;
    }
  }

  bool _handleDeleteCommentResponse(Response<dynamic> response) {
    final int statusCode = response.statusCode ?? 0;
    log('  >>  _handleDeleteCommentResponse:statusCode = $statusCode');
    return statusCode >= 200 && statusCode < 300;
  }

  Future<Entity?> updateTag(Entity entity, String context, String personaId, String tags) async {
    try {
      final Response<dynamic> response = await AppState.instance.api.dio.post(
        '/entity/${entity.entityId}/tags?context=$context&personaId=$personaId',
        data: _jsonTagString(entity, tags),
        options: Options(contentType: Headers.jsonContentType),
      );
      return _handleTagResponse(response);
    } catch (e) {
      if (e is DioException) {
        log('  >>  EntityRequest.updateTag [${e.response?.statusCode.toString()}]: ${e.toString()}');
      } else {
        log(e.toString());
      }
      return null;
    }
  }

  static String _jsonTagString(Entity entity, String tags) {
    final Map<String, dynamic> json = <String, dynamic>{};
    json['value'] = tags;
    final String jsonString = jsonEncode(json);
    // log(const JsonEncoder.withIndent('  ').convert(jsonString));
    return jsonString;
  }

  Entity? _handleTagResponse(Response<dynamic> response) {
    final int statusCode = response.statusCode ?? 0;
    log('  >>  _handleTagResponse:statusCode = $statusCode');
    log(const JsonEncoder.withIndent('  ').convert(response.data));
    return Entity.fromJson(response.data);
  }

  Future<bool> deletePost(String context, String personaId, String entityId) async {
    try {
      final Response<dynamic> response = await AppState.instance.api.dio.delete(
        '/entity/$entityId?context=$context&personaId=$personaId',
      );
      return _handleDeletePostResponse(response);
    } catch (e) {
      if (e is DioException) {
        log('  >>  EntityRequest.deletePost [${e.response?.statusCode.toString()}]: ${e.toString()}');
      } else {
        log(e.toString());
      }
      return false;
    }
  }

  bool _handleDeletePostResponse(Response<dynamic> response) {
    final int statusCode = response.statusCode ?? 0;
    log('  >>  _handleDeletePostResponse:statusCode = $statusCode');
    return statusCode >= 200 && statusCode < 300;
  }

  Future<Entity?> uploadAttachment({
    required Entity entity,
    required String context,
    required String personaId,
    required String imagePath,
  }) async {
    try {
      final FormData data = await _dataForPath(imagePath);
      final String contentType = 'multipart/form-data; boundary=${data.boundary}';

      final Response<dynamic> response = await AppState.instance.api.dio.post(
        '/entity/${entity.entityId}/attachment?context=$context&personaId=$personaId',
        data: data,
        options: Options(contentType: contentType),
      );
      return _handleUploadAttachmentResponse(response);
    } catch (e) {
      if (e is DioException) {
        log('  >>  EntityRequest.uploadAttachment [${e.response?.statusCode.toString()}]: ${e.toString()}');
      } else {
        log(e.toString());
      }
      return null;
    }
  }

  Future<FormData> _dataForPath(String path) async {
    final File file = File(path);
    final String fileName = file.path.split('/').last;
    final String extension = fileName.split('.').last;
    final String baseName = fileName.split('.').first;
    final String shortName = '${baseName.substring(baseName.length - 8)}.$extension';
    final FormData formData = FormData.fromMap(<String, dynamic>{
        'file': await MultipartFile.fromFile(
          file.path,
          filename: shortName,
          contentType: MediaType('image', extension),
        ),
    });
    return formData;
  }

  Entity? _handleUploadAttachmentResponse(Response<dynamic> response) {
    final int statusCode = response.statusCode ?? 0;
    log('  >>  _handleUploadAttachmentResponse:statusCode = $statusCode');
    // log(const JsonEncoder.withIndent('  ').convert(response.data));
    return Entity.fromJson(response.data);
  }

  Future<bool> deleteAttachment({
    required String entityId,
    required String context,
    required String personaId,
    required String attachmentId,
  }) async {
    try {
      final Response<dynamic> response = await AppState.instance.api.dio.delete(
        '/entity/$entityId/attachment/$attachmentId?context=$context&personaId=$personaId',
      );
      return _handleDeleteAttachmentResponse(response);
    } catch (e) {
      if (e is DioException) {
        log('  >>  EntityRequest.deleteAttachment [${e.response?.statusCode.toString()}]: ${e.toString()}');
      } else {
        log(e.toString());
      }
      return false;
    }
  }

  bool _handleDeleteAttachmentResponse(Response<dynamic> response) {
    final int statusCode = response.statusCode ?? 0;
    log('  >>  _handleDeleteAttachmentResponse:statusCode = $statusCode');
    return statusCode >= 200 && statusCode < 300;
  }
}
