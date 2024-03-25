import 'package:opencola_flutter/api/feed_request.dart';
import 'package:opencola_flutter/app_state.dart';
import 'package:opencola_flutter/model/base_model.dart';
import 'package:opencola_flutter/model/personas.dart';
import 'package:opencola_flutter/utils/utils.dart';

class Feed extends BaseModel {
  final List<FeedBlob> _feed = <FeedBlob>[];

  bool get isEmpty => _feed.isEmpty;
  bool get isNotEmpty => _feed.isNotEmpty;
  String? get context => _feed.isEmpty ? null : _feed.first.context;
  void clear() => _feed.clear();

  Future<void> getNext([String? queryString]) async {
    String? pagingToken;
    if (_feed.isNotEmpty) {
      pagingToken = _feed.last.pagingToken;
    }
    final Persona? selectedPersona = AppState.instance.selectedPersona;
    final String personaId = selectedPersona?.id ?? '';
    final String context = AppState.instance.feed.context ?? '';
    log('  ++  Feed.getNext: selectedPersona=${selectedPersona?.name}, personaId=$personaId');
    final FeedBlob? feedBlob = await FeedRequest().get(context: context, personaId: personaId, queryString: queryString ?? '', pagingToken: pagingToken);
    if (feedBlob != null) {
      log('  ++  Feed.getNext: APPEND');
      _feed.add(feedBlob);
    } else {
      log('  ++  Feed.getNext: NULL');
    }
  }

  bool canGetMore() {
    if (_feed.isEmpty) {
      log('  >>  canGetMore: TRUE (_feed is empty)');
      return true;
    }
    log('  >>  canGetMore: _feed.last.pagingToken=${_feed.last.pagingToken}');
    return _feed.last.pagingToken != null;
  }

  List<Entity> entityList() {
    final List<Entity> entityList = <Entity>[];
    for (FeedBlob feedBlob in _feed) {
      feedBlob.results?.forEach((Entity entity) {
        entityList.add(entity);
      });
    }
    return entityList;
  }

  void updateEntity(Map<String, dynamic> json) {
    final Entity entity = Entity.fromJson(json);
    for (FeedBlob feedBlob in _feed) {
      final int? index = feedBlob.results?.indexWhere((Entity element) => element.entityId == entity.entityId);
      if (index != null) {
        feedBlob.updateEntity(json);
        return;
      }
    }
  }
}

class FeedBlob extends BaseModel {
  String? context;
  String? pagingToken;
  List<Entity>? results;

  FeedBlob({
    this.context,
    this.pagingToken,
    this.results,
  });

  @override
  factory FeedBlob.fromJson(Map<String, dynamic> json) {
    final List<Entity> results = <Entity>[];
    if (json['results'] != null) {
      json['results'].forEach((dynamic v) {
        results.add(Entity.fromJson(v));
      });
    }
    return FeedBlob(
      context: json['context'],
      pagingToken: json['pagingToken'],
      results: results,
    );
  }

  Map<String, dynamic> toJson() {
    final Map<String, dynamic> data = <String, dynamic>{};
    data['context'] = context;
    data['pagingToken'] = pagingToken;
    if (results != null) {
      data['results'] = results!.map((Entity v) => v.toJson()).toList();
    }
    return data;
  }

  void updateEntity(Map<String, dynamic> json) {
    final Entity entity = Entity.fromJson(json);
    final int? index = results?.indexWhere((Entity element) => element.entityId == entity.entityId);
    if (index != null) {
      results?.replaceRange(index, index+1, <Entity>[entity]);
    }
  }
}

class Entity extends BaseModel {
  String? entityId;
  String? personaId;
  EntitySummary? summary;
  List<Activity>? activities;

  Entity({
    this.entityId,
    this.personaId,
    this.summary,
    this.activities,
  });

  @override
  factory Entity.fromJson(Map<String, dynamic> json) {
    final EntitySummary? summary = json['summary'] != null ? EntitySummary.fromJson(json['summary']) : null;
    final List<Activity> activities = <Activity>[];
    if (json['activities'] != null) {
      json['activities'].forEach((dynamic v) {
        activities.add(Activity.fromJson(v));
      });
    }
    return Entity(
      entityId: json['entityId'],
      personaId: json['personaId'],
      summary: summary,
      activities: activities,
    );
  }

  Map<String, dynamic> toJson() {
    final Map<String, dynamic> data = <String, dynamic>{};
    data['entityId'] = entityId;
    data['personaId'] = personaId;
    if (summary != null) {
      data['summary'] = summary!.toJson();
    }
    if (activities != null) {
      data['activities'] = activities!.map((Activity v) => v.toJson()).toList();
    }
    return data;
  }

  // Map<String, dynamic> getPayload() {
  //   final Map<String, dynamic> json = <String, dynamic>{};
  //   json['entityId'] = entityId;
  //   json['name'] = summary?.name;
  //   json['imageUri'] = summary?.imageUri;
  //   json['description'] = summary?.description;
  //   json['tags'] = _getTags();
  //   json['comment'] = '';
  //   json['attachments'] = <void>[];
  //   return json;
  // }

  // String? _getTags() {
  //   String tags = '';
  //   activities?.forEach((Activity activity) {
  //     final List<Action>? actions = activity.actions;
  //     actions?.forEach((Action action) {
  //       if (action.type == 'tag') {
  //         final String? value = action.value;
  //         if (value != null) {
  //           final String prefix = tags.isNotEmpty ? ' ' : '';
  //           tags += (prefix + value);
  //         }
  //       }
  //     });
  //   });
  //   return tags;
  // }

  bool isLiked(String personaId) {
    String? value;
    activities?.forEach((Activity activity) {
      final List<Action>? actions = activity.actions;
      actions?.forEach((Action action) {
        if ((action.type == 'like') && (activity.authorityId == personaId)) {
          value = action.value;
        }
      });
    });
    return value == 'true';
  }

  bool _hasAction(String actionType, String personaId) {
    bool found = false;
    activities?.forEach((Activity activity) {
      final List<Action>? actions = activity.actions;
      actions?.forEach((Action action) {
        if ((action.type == actionType) && (activity.authorityId == personaId)) {
          found = true;
        }
      });
    });
    return found;
  }

  bool hasTags(String personaId) =>
    _hasAction('tag', personaId);

  bool hasAttachments(String personaId) =>
    _hasAction('attach', personaId);

  bool hasComments(String personaId) =>
    _hasAction('comment', personaId);

  bool isSaved(String? personaId) {
    if (personaId == null) {
      return false;
    }
    // Check postedBy
    final String? postedBy = summary?.postedBy?.id;
    if (postedBy == personaId) {
      return true;
    }

    // Check activities
    bool isSaved = false;
    activities?.forEach((Activity activity) {
      final List<Action>? actions = activity.actions;
      actions?.forEach((Action action) {
        if ((action.type == 'bubble') && (activity.authorityId == personaId)) {
          isSaved = true;
        }
      });
    });
    return isSaved;
  }
}

class EntitySummary extends BaseModel {
  String? name;
  String? uri;
  String? description;
  String? imageUri;
  PostedBy? postedBy;

  EntitySummary({
    this.name,
    this.uri,
    this.description,
    this.imageUri,
    this.postedBy,
  });

  @override
  factory EntitySummary.fromJson(Map<String, dynamic> json) {
    final PostedBy? postedBy = json['postedBy'] != null ? PostedBy.fromJson(json['postedBy']) : null;
    return EntitySummary(
      name: json['name'],
      uri: json['uri'],
      description: json['description'],
      imageUri: json['imageUri'],
      postedBy: postedBy,
    );
  }

  Map<String, dynamic> toJson() {
    final Map<String, dynamic> data = <String, dynamic>{};
    data['name'] = name;
    data['uri'] = uri;
    data['description'] = description;
    data['imageUri'] = imageUri;
    if (postedBy != null) {
      data['postedBy'] = postedBy!.toJson();
    }
    return data;
  }
}

class Activity extends BaseModel {
  String? authorityId;
  String? authorityName;
  String? host;
  int? epochSecond;
  List<Action>? actions;

  Activity({
    this.authorityId,
    this.authorityName,
    this.host,
    this.epochSecond,
    this.actions,
  });

  @override
  factory Activity.fromJson(Map<String, dynamic> json) {
    final List<Action> actions = <Action>[];
    if (json['actions'] != null) {
      json['actions'].forEach((dynamic v) {
        actions.add(Action.fromJson(v));
      });
    }
    return Activity(
      authorityId: json['authorityId'],
      authorityName: json['authorityName'],
      host: json['host'],
      epochSecond: json['epochSecond'],
      actions: actions,
    );
  }

  Map<String, dynamic> toJson() {
    final Map<String, dynamic> data = <String, dynamic>{};
    data['authorityId'] = authorityId;
    data['authorityName'] = authorityName;
    data['host'] = host;
    data['epochSecond'] = epochSecond;
    if (actions != null) {
      data['actions'] = actions!.map((Action v) => v.toJson()).toList();
    }
    return data;
  }
}

class Action extends BaseModel {
  String? type;
  String? id;
  String? value;

  Action({
    this.type,
    this.id,
    this.value,
  });

  @override
  factory Action.fromJson(Map<String, dynamic> json) =>
    Action(
      type: json['type'],
      id: json['id'],
      value: json['value'],
    );

  Map<String, dynamic> toJson() {
    final Map<String, dynamic> data = <String, dynamic>{};
    data['type'] = type;
    data['id'] = id;
    data['value'] = value;
    return data;
  }
}

class PostedBy extends BaseModel {
  String? id;
  String? name;
  String? imageUri;
  bool? isPersona;

  PostedBy({
    this.id,
    this.name,
    this.imageUri,
    this.isPersona,
  });

  @override
  factory PostedBy.fromJson(Map<String, dynamic> json) =>
    PostedBy(
      id: json['id'],
      name: json['name'],
      imageUri: json['imageUri'],
      isPersona: json['isPersona'],
    );

  Map<String, dynamic> toJson() {
    final Map<String, dynamic> data = <String, dynamic>{};
    data['id'] = id;
    data['name'] = name;
    data['imageUri'] = imageUri;
    data['isPersona'] = isPersona;
    return data;
  }
}
