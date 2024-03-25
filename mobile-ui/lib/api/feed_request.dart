import 'dart:async';
// import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:opencola_flutter/app_state.dart';
import 'package:opencola_flutter/model/feed.dart';
import 'package:opencola_flutter/utils/utils.dart';

class FeedRequest {
  Future<FeedBlob?> get({
    required String context,
    required String personaId,
    required String queryString,
    String? pagingToken,
  }) async
  {
    try {
      String urlString = '/feed?context=$context&personaId=$personaId&q=$queryString';
      if (pagingToken != null) {
        urlString += '&pagingToken=$pagingToken';
      }
      final Response<dynamic> response = await AppState.instance.api.dio.get(urlString);
      return _handleFeedResponse(response);
    } catch (e) {
      if (e is DioException) {
        log('FeedRequest [${e.response?.statusCode.toString()}]: ${e.toString()}');
      } else {
        log(e.toString());
      }
      return null;
    }
  }

  FeedBlob? _handleFeedResponse(Response<dynamic> response) {
    final int statusCode = response.statusCode ?? 0;
    log('  >>  _handleFeedResponse:statusCode = $statusCode');
    // log(const JsonEncoder.withIndent('  ').convert(response.data));
    return FeedBlob.fromJson(response.data);
  }
}
