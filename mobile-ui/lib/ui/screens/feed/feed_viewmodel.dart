import 'dart:async';
import 'package:collection/collection.dart';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:opencola_flutter/api/entity_request.dart';
import 'package:opencola_flutter/app_state.dart';
import 'package:opencola_flutter/model/feed.dart' as feed;
import 'package:opencola_flutter/model/feed.dart';
import 'package:opencola_flutter/model/personas.dart';
import 'package:opencola_flutter/ui/base/base_navigation.dart';
import 'package:opencola_flutter/ui/base/base_viewmodel.dart';
import 'package:opencola_flutter/ui/screens/feed/pagination_scroll_controller.dart';
import 'package:opencola_flutter/utils/utils.dart';

class FeedViewModel extends BaseViewModel {

  PaginationScrollController paginationScrollController = PaginationScrollController();
  final TextEditingController searchController = TextEditingController();
  bool didOpenURL = false;

  final Map<String, String> _showAction = <String, String>{};
  final Map<String, Persona> _personas = <String, Persona>{};
  List<Entity> _entityList = <feed.Entity>[];

  @override
  FutureOr<void> init() async {
    paginationScrollController.init(
      loadAction: () => _getNextFeedBlob(),
    );
    busy = true;
    notifyListeners();
    _clearState();
    await _fetchFeed();
    Future<void>.delayed(Duration(milliseconds: minimumDelayMS), () {
      busy = false;
      notifyListeners();
    });
  }

  void _clearState() {
    AppState.instance.feed.clear();
    _personas.clear();
    _showAction.clear();
    for (TextEditingController controller in _textEditors.values) {
      controller.dispose();
    }
    _textEditors.clear();
    paginationScrollController.reset();
  }

  Future<void> _fetchFeed() async {
    await AppState.instance.feed.getNext(searchController.text);
    _entityList = AppState.instance.feed.entityList();
    for (feed.Entity entity in _entityList) {
      final String entityId = entity.entityId ?? '';
      if (entityId.isNotEmpty) {
        if (_showAction.containsKey(entityId) == false) {
          _showAction[entityId] = '';
        }
        if (_personas.containsKey(entityId) == false) {
          final Persona persona = AppState.instance.personaForId(entity.personaId ?? '') ?? Persona();
          _personas[entityId] = persona;
        }
      }
    }
  }

  Future<bool> _getNextFeedBlob() async {
    if (AppState.instance.feed.canGetMore()) {
      await _fetchFeed();
      notifyListeners();
      return false;
    } else {
      return true;
    }
  }

  Future<void> onAppResumed() async {
    // Don't re-fetch if simply returning from URL view
    if (didOpenURL) {
      didOpenURL = false;
      return;
    }
    // If intent is set, let MainView (parent) handle it
    if (AppState.instance.intentURL != null) {
      return;
    }
    busy = true;
    notifyListeners();
    await _fetchFeed();
    Future<void>.delayed(Duration(milliseconds: minimumDelayMS), () {
      busy = false;
      notifyListeners();
    });
  }

  Future<void> onSearch(String query) async {
    _clearState();
    await _fetchFeed();
    notifyListeners();
    AppState.instance.appBarNotifier.onChange();
  }

  Future<void> refresh() async {
    _clearState();
    await _fetchFeed();
    notifyListeners();
  }

  Future<void> navigateToPeers() async {
    final Map<String, dynamic> args = <String, dynamic>{ NavigationKeys.selectedTab: 1 };
    navigateTo(NavigationTargets.home, true, args);
  }

  Future<void> navigateToSettings() async {
    navigateTo(NavigationTargets.settings, false, null);
  }

  bool isSelected(String personaName) => AppState.instance.isSelected(personaName: personaName);

  String currentSelection() => AppState.instance.selectedPersonaName();

  bool isSelectedForCard(int index, String personaName) {
    final String? entityId = _getEntityId(index);
    if (entityId == null) {
      return false;
    }
    return _personas[entityId]?.name == personaName;
  }

  String currentPersonaSelectionForCard(int index) {
    final String? entityId = _getEntityId(index);
    if (entityId == null) {
      return '';
    }
    return _personas[entityId]?.name ?? '';
  }

  void didSelectPersonaForCard(int index, String choice) async {
    final Persona persona = AppState.instance.personaForName(choice) ?? Persona();
    final String? entityId = _getEntityId(index);
    if (entityId != null) {
      _personas[entityId] = persona;
    }
    notifyListeners();
  }

  bool showDeleteForCard(int index) {
    if (index >= _personas.length) {
      return false;
    }
    final Entity? entity = _getEntity(index);
    final String? personaId = _personas[entity?.entityId]?.id;
    return entity?.isSaved(personaId) ?? false;
  }

  Future<void> didSelectPersona(String choice) async {
    final List<String> names = AppState.instance.personaNames();
    final int index = names.indexWhere((String element) => element == choice );

    if (index >= (names.length-1)) {
      navigateTo(NavigationTargets.personas, false, null);  // "Manage..." Personas
    } else {
      if (index == 0) {
        AppState.instance.selectedPersona = null;  // "All"
      } else if (index < (names.length-1)) {
        final List<Persona> personasList = AppState.instance.personasList();
        final Persona? persona = personasList.firstWhereOrNull((Persona element) => element.name == names[index]);
        AppState.instance.selectedPersona = persona;
      }
      AppState.instance.appBarNotifier.onChange();
      _clearState();
      await _fetchFeed();
    }
    notifyListeners();
  }

  int get numCards => _entityList.length;
  String uriForCard(int x) => _getEntitySummary(x)?.uri ?? '';
  String titleForCard(int x) => _getEntitySummary(x)?.name ?? '...';
  String imageUriForCard(int x) =>
    _getEntitySummary(x)?.imageUri ??
    _getEntitySummary(x)?.postedBy?.imageUri ?? '';
  String subtitleForCard(int x) => Uri.parse(_getEntitySummary(x)?.uri ?? '').host;
  String descriptionForCard(int x) => _getEntitySummary(x)?.description ?? '...';
  int getSaveCount(int x) => _getActionCount(x, 'bubble');
  int getLikeCount(int x) => _getActionCount(x, 'like');
  int getTagCount(int x) => _getActionCount(x, 'tag');
  int getAttachmentCount(int x) => _getActionCount(x, 'attach');
  int getCommentCount(int x) => _getActionCount(x, 'comment');

  String postedByNameForCard(int x) {
    final Entity? entity = _getEntity(x);
    final PostedBy? postedBy = entity?.summary?.postedBy;
    final bool isPersona = postedBy?.isPersona ?? false;
    final String name = postedBy?.name ?? '';
    return isPersona ? 'You ($name)' : name;
  }

  String photoForPersona(int x) {
    final Entity? entity = _getEntity(x);
    return entity?.summary?.postedBy?.imageUri ?? '';
  }

  String postedByDateForCard(int x) {
    final Entity? entity = _getEntity(x);
    final String? postedById = entity?.summary?.postedBy?.id;
    final List<Activity> activities = activitiesForCard(x, actionType: 'bubble');
    for (feed.Activity activity in activities) {
      if (activity.authorityId == postedById) {
        return epochToDateString(activity.epochSecond);
      }
    }
    return '';
  }

  String actionName(int x) {
    String name = actionForCard(x);
    if (name == 'attach') {
      name = 'attachment';
    } else if (name == 'bubble') {
      name = 'save';
    }
    return '${name.toTitleCase()}s:';
  }

  String uriForAttachment(int card, int x) {
    if (x < 0) {
      return '';
    }
    final List<Activity> activities = activitiesForCard(card, actionType: 'attach');
    final String? id = activities[x].actions?.first.id;
    final String baseUrl = 'https://${AppState.instance.api.ipAddress}';
    final String uri = id == null ? '' : '$baseUrl/data/$id';
    return uri;
  }

  void attach(int card) async {
    final ImagePicker picker = ImagePicker();
    final XFile? image = await picker.pickImage(source: ImageSource.gallery);
    if (image != null) {
      final feed.Entity? entity = _getEntity(card);
      final String? context = AppState.instance.feed.context;
      final String? personaId = _personas[entity?.entityId]?.id;
      if (entity != null && context != null && personaId != null) {
        final Entity? result = await EntityRequest().uploadAttachment(
          entity: entity,
          context: context,
          personaId: personaId,
          imagePath: image.path,
        );
        if (result != null) {
          AppState.instance.feed.updateEntity(result.toJson());
        }
      }
      notifyListeners();
    }
  }

  bool _authorityIdMatch(int card, int row) {
    // Check if personaId is a match
    final Entity? entity = _getEntity(card);
    final String? personaId = _personas[entity?.entityId]?.id;
    final String? authorityId = activitiesForCard(card)[row].authorityId;
    if ((personaId != null) && (authorityId != null) && (personaId == authorityId)) {
      return true;
    }
    return false;
  }

  bool canDeleteAttachment(int card, int row) {
    if (actionForCard(card) == 'attach') {
      return _authorityIdMatch(card, row);
    }
    return false;
  }

  void deleteAttachment(int card, int row) async {
    final Entity? entity = _getEntity(card);
    final String? context = AppState.instance.feed.context;
    final String? personaId = _personas[entity?.entityId]?.id;
    final String? entityId = entity?.entityId;
    final feed.Action? attachment = _attachmentForCard(card, row);
    final String? attachmentId = attachment?.id;
    if (entityId != null && context != null && personaId != null && attachmentId != null) {
      final bool success = await EntityRequest().deleteAttachment(
        entityId: entityId,
        context: context,
        personaId: personaId,
        attachmentId: attachmentId,
      );
      if (success) {
        _clearState();
        await _fetchFeed();
        // Don't close Attachment view unless empty
        if (getAttachmentCount(card) > 0) {
          _showAction[entityId] = 'attach';
        }
      }
    }
    notifyListeners();
  }

  feed.Action? _attachmentForCard(int card, int x) {
    if (x < 0) {
      return null;
    }
    final List<Activity> activities = activitiesForCard(card, actionType: 'attach');
    return activities[x].actions?.first;
  }

  String actionForCard(int x) {
    final String? entityId = _getEntityId(x);
    String? action;
    if (entityId != null) {
      action = _showAction[entityId];
    }
    return action ?? '';
  }

  bool shouldShowActionForCard(String action, int x) => actionForCard(x) == action;

  void didSelectActionForCard(String action, int x) {
    final String? entityId = _getEntityId(x);
    if (entityId == null) {
      return;
    }
    if (actionForCard(x) == action) {
      _showAction[entityId] = '';
    } else {
      _showAction[entityId] = action;
    }
    notifyListeners();
  }

  String? _getEntityId(int x) {
    final feed.Entity? entity = _getEntity(x);
    return entity?.entityId;
  }

  void _clearActionForCard(int card) {
    final String? entityId = _getEntityId(card);
    if (entityId != null) {
      _showAction[entityId] = '';
    }
  }

  void updatePost(int card) async {
    log('  TODO: updatePost($card)');
    _clearActionForCard(card);
    notifyListeners();
  }

  void cancelEditingPost(int card) {
    _clearActionForCard(card);
    notifyListeners();
  }

  void deletePost(int card) async {
    final Entity? entity = _getEntity(card);
    final String? context = AppState.instance.feed.context;
    final String? personaId = _personas[entity?.entityId]?.id;
    final String? entityId = entity?.entityId;
    if (entityId != null && context != null && personaId != null) {
      final bool success = await EntityRequest().deletePost(context, personaId, entityId);
      if (success) {
        _clearState();
        await _fetchFeed();
      }
    }
    _clearActionForCard(card);
    notifyListeners();
  }

  String _tagId(int card) => _getEntityId(card) ?? card.toString();

  bool isEditingTag(int card) => _textEditors.containsKey(_tagId(card));

  void startEditingTag(int card) {
    textEditorForTag(card);
    notifyListeners();
  }

  void updateTag(int card) async {
    final String id = _tagId(card);
    final TextEditingController? controller = _textEditors[id];
    final String text = controller?.text ?? '';
    final feed.Entity? entity = _getEntity(card);
    final String? context = AppState.instance.feed.context;
    final String? personaId = _personas[entity?.entityId]?.id;
    if (entity != null && context != null && personaId != null) {
      final feed.Entity? result = await EntityRequest().updateTag(entity, context, personaId, text);
      if (result != null) {
        AppState.instance.feed.updateEntity(result.toJson());
      }
    }
    _textEditors.remove(id);
    notifyListeners();
  }

  void cancelEditingTag(int card) {
    _textEditors.remove(_tagId(card));
    notifyListeners();
  }

  TextEditingController textEditorForTag(int card) {
    final String id = _tagId(card);
    TextEditingController? controller = _textEditors[id];
    if (controller == null) {
      controller = TextEditingController();
      _textEditors[id] = controller;
    }
    controller.text = _tagString(card);
    return controller;
  }

  String _tagString(int card) {
    String tags = '';
    tagsForCard(card).forEach((String element) {
      tags += '$element ';
    });
    return tags;
  }

  final Map<String, TextEditingController> _textEditors = <String, TextEditingController>{};
  final Map<int, bool> _createNewComment = <int, bool>{};

  bool canEditComment(int card, int row) {
    if (actionForCard(card) == 'comment' && !isEditing(card, row)) {
      return _authorityIdMatch(card, row);
    }
    return false;
  }

  bool isNewComment(int card) => _createNewComment[card] ?? false;

  void createNewComment(int card) {
    _createNewComment[card] = true;
    startEditingComment(card, -1);
  }

  void startEditingComment(int card, int row) {
    textEditorForComment(card, row);
    final String? entityId = _getEntityId(card);
    if (entityId != null) {
      _showAction[entityId] = 'comment';
    }
    notifyListeners();
  }

  TextEditingController textEditorForComment(int card, int row) {
    final String id = _commentId(card, row);
    TextEditingController? controller = _textEditors[id];
    if (controller == null) {
      controller = TextEditingController();
      _textEditors[id] = controller;
    }
    return controller;
  }

  String _commentId(int card, int row) {
    if (row < 0) {
      return '$card+$row';
    }
    final List<feed.Activity> activities = activitiesForCard(card);
    final feed.Activity activity = activities[row];
    return activity.actions?.first.id ?? '';
  }

  bool isEditing(int card, int row) => _textEditors.containsKey(_commentId(card, row));

  void updateComment(int card, int row) async {
    final String id = _commentId(card, row);
    final TextEditingController? controller = _textEditors[id];
    final String text = controller?.text ?? '';

    final feed.Entity? entity = _getEntity(card);
    final String? context = AppState.instance.feed.context;
    final String? personaId = _personas[entity?.entityId]?.id;
    if (entity != null && context != null && personaId != null) {
      final String? commentId = row >= 0 ? id : null;
      final feed.Entity? result = await EntityRequest().updateComment(entity, context, personaId, commentId, text);
      if (result != null) {
        AppState.instance.feed.updateEntity(result.toJson());
      }
    }
    _textEditors.remove(id);
    _createNewComment.remove(card);
    notifyListeners();
  }

  void cancelComment(int card, int row) {
    _textEditors.remove(_commentId(card, row));
    _createNewComment.remove(card);
    notifyListeners();
  }

  void deleteComment(int card, int row) async {
    final String id = _commentId(card, row);
    final feed.Entity? entity = _getEntity(card);
    final String? context = AppState.instance.feed.context;
    final String? personaId = _personas[entity?.entityId]?.id;
    if (entity != null && context != null && personaId != null) {
      final bool success = await EntityRequest().deleteComment(context, personaId, id);
      if (success) {
        await _fetchFeed();
      }
    }
    _textEditors.remove(id);
    _createNewComment.remove(card);
    notifyListeners();
  }

  String activityValueForCard(int card, int x, {String? actionType}) {
    if (x < 0) {
      return '';
    }
    final List<Activity> activities = activitiesForCard(card, actionType: actionType);
    return activities[x].actions?.first.value ?? '...';
  }

  feed.Activity _action(feed.Activity activity, feed.Action action, bool useValue) =>
    feed.Activity(
      authorityId: activity.authorityId,
      authorityName: activity.authorityName,
      epochSecond: activity.epochSecond,
      actions: <feed.Action>[
        feed.Action(
          value: useValue ? action.value : null,
          id: action.id,
          type: action.type,
        ),
      ],
    );

  List<feed.Activity> activitiesForCard(int x, {String? actionType}) {
    // Get selected persona for card
    final Entity? entity = _getEntity(x);
    final String? personaId = _personas[entity?.entityId]?.id;

    final String type = actionType ?? actionForCard(x);
    final bool reversed = (type == 'attach');
    final List<feed.Activity> all = <feed.Activity>[];
    if (reversed) {
      all.addAll(_getActivities(x)?.reversed.where((feed.Activity item) => item.authorityId == personaId).toList() ?? <feed.Activity>[]);
      all.addAll(_getActivities(x)?.reversed.where((feed.Activity item) => item.authorityId != personaId).toList() ?? <feed.Activity>[]);
    } else {
      all.addAll(_getActivities(x) ?? <feed.Activity>[]);
    }

    final bool useValue = (type == 'tag' || type == 'comment' || type == 'attach');
    final Set<String> uniqueActionIds = <String>{};
    final List<feed.Activity> activitiesList = <feed.Activity>[];

    for (feed.Activity activity in all) {
      final List<feed.Action>? actions = activity.actions?.reversed.toList();
      actions?.forEach((feed.Action action) {
        if (action.type == type) {
          if (action.id == null || (action.id != null && !uniqueActionIds.contains(action.id))) {
            uniqueActionIds.add(action.id ?? '');
            activitiesList.add(_action(activity, action, useValue));
          }
        }
      });
    }
    return activitiesList;
  }

  List<String> tagsForCard(int x) {
    final List<String> tags = <String>[];
    final List<feed.Activity>? activities = _getActivities(x);
    activities?.forEach((feed.Activity activity) {
      final List<feed.Action>? actions = activity.actions;
      actions?.forEach((feed.Action action) {
        if (action.type == 'tag') {
          if (action.value != null) {
            tags.add(action.value!);
          }
        }
      });
    });
    return tags;
  }

  void bubble(int x) async {
    final feed.Entity? entity = _getEntity(x);
    final String? context = AppState.instance.feed.context;
    final String? personaId = _personas[entity?.entityId]?.id;
    if (entity == null || context == null || personaId == null) {
      return;
    }
    final feed.Entity? result = await EntityRequest().bubble(entity, context, personaId);
    if (result != null) {
      AppState.instance.feed.updateEntity(result.toJson());
      notifyListeners();
    }
  }

  bool isSaved(int x) {
    if (x >= _personas.length) {
      return false;
    }
    final Entity? entity = _getEntity(x);
    final String personaId = _personas[entity?.entityId]?.id ?? '';
    return entity?.isSaved(personaId) ?? false;
  }

  bool isLiked(int x) {
    if (x >= _personas.length) {
      return false;
    }
    final Entity? entity = _getEntity(x);
    final String personaId = _personas[entity?.entityId]?.id ?? '';
    return entity?.isLiked(personaId) ?? false;
  }

  void toggleLike(int index) async {
    final feed.Entity? entity = _getEntity(index);
    final String? context = AppState.instance.feed.context;
    final String? personaId = _personas[entity?.entityId]?.id;
    if (entity == null || context == null || personaId == null) {
      return;
    }
    final feed.Entity? result = await EntityRequest().toggleLike(entity, context, personaId);
    if (result != null) {
      AppState.instance.feed.updateEntity(result.toJson());
      notifyListeners();
    }
  }

  bool hasTags(int x) {
    if (x >= _personas.length) {
      return false;
    }
    final Entity? entity = _getEntity(x);
    final String personaId = _personas[entity?.entityId]?.id ?? '';
    return entity?.hasTags(personaId) ?? false;
  }

  bool hasAttachments(int x) {
    if (x >= _personas.length) {
      return false;
    }
    final Entity? entity = _getEntity(x);
    final String personaId = _personas[entity?.entityId]?.id ?? '';
    return entity?.hasAttachments(personaId) ?? false;
  }

  bool hasComments(int x) {
    if (x >= _personas.length) {
      return false;
    }
    final Entity? entity = _getEntity(x);
    final String personaId = _personas[entity?.entityId]?.id ?? '';
    return entity?.hasComments(personaId) ?? false;
  }

  feed.Entity? _getEntity(int index) {
    final List<Entity> entityList = AppState.instance.feed.entityList();
    if (index < entityList.length) {
      return entityList[index];
    }
    return null;
  }

  feed.EntitySummary? _getEntitySummary(int x) {
    final feed.Entity? entity = _getEntity(x);
    return entity?.summary;
  }

  List<feed.Activity>? _getActivities(int index) {
    final feed.Entity? entity = _getEntity(index);
    return entity?.activities;
  }

  int _getActionCount(int x, String type) {
    int count = 0;
    final List<feed.Activity>? activities = _getActivities(x);
    final Set<String> actionIds = <String>{};
    activities?.forEach((feed.Activity activity) {
      final List<feed.Action>? actions = activity.actions;
      actions?.forEach((feed.Action action) {
        if (action.type == type) {
          if (action.id == null) {
            count += 1;
          } else if (action.id != null && !actionIds.contains(action.id)) {
            actionIds.add(action.id!);
            count += 1;
          }
        }
      });
    });
    return count;
  }

  @override
  void dispose() {
    searchController.dispose();
    paginationScrollController.dispose();
    for (TextEditingController controller in _textEditors.values) {
      controller.dispose();
    }
    super.dispose();
  }
}
