import 'dart:async';
// import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:opencola_flutter/app_state.dart';
import 'package:opencola_flutter/model/peer.dart';
import 'package:opencola_flutter/utils/utils.dart';

class PeersRequest {
  Future<PeerList?> get(String id) async {
    try {
      final Response<dynamic> response = await AppState.instance.api.dio.get('/peers?personaId=$id');
      return _handlePeerResponse(response);
    } catch (e) {
      if (e is DioException) {
        log('PeerRequest [${e.response?.statusCode.toString()}]: ${e.toString()}');
      } else {
        log(e.toString());
      }
      return null;
    }
  }

  PeerList? _handlePeerResponse(Response<dynamic> response) {
    final int statusCode = response.statusCode ?? 0;
    log('  >>  _handlePeerResponse:statusCode = $statusCode');
    // log(const JsonEncoder.withIndent('  ').convert(response.data));
    final List<dynamic> results = response.data['results'];
    return PeerList.fromJson(results);
  }
}
