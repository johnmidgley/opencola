import 'package:flutter/material.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:markdown_editable_textinput/format_markdown.dart';
import 'package:markdown_editable_textinput/markdown_text_input.dart';
import 'package:opencola_flutter/app_state.dart';
import 'package:opencola_flutter/ui/elements/opencola_textfield.dart';
import 'package:opencola_flutter/ui/elements/webview.dart';
import 'package:opencola_flutter/ui/screens/feed/feed_viewmodel.dart';
import 'package:opencola_flutter/ui/style/app_assets.dart';
import 'package:opencola_flutter/ui/style/app_colors.dart';
import 'package:opencola_flutter/utils/utils.dart';

class FeedCard extends StatelessWidget {
  const FeedCard({
    required this.viewModel,
    required this.index,
    super.key,
  });

  final FeedViewModel viewModel;
  final int index;

  @override
  Widget build(BuildContext context) =>
    Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(8.0),
        boxShadow: <BoxShadow>[
          BoxShadow(
            color: Colors.grey.shade300,
            spreadRadius: 1,
            blurRadius: 4,
            offset: const Offset(0, 8),
          ),
        ],
        color: Colors.white,
      ),
      padding: const EdgeInsets.symmetric(
        horizontal: 10.0,
        vertical: 10.0,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[

          //
          // POSTED BY
          //
          Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: <Widget>[
              ClipRRect(
                borderRadius: BorderRadius.circular(15.0),
                child: SizedBox(
                  height: 55,
                  width: 55,
                  child: Image.network(viewModel.photoForPersona(index), fit: BoxFit.cover),
                ),
              ),
              const SizedBox(width: 16),
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Text(
                    viewModel.postedByNameForCard(index),
                    style: GoogleFonts.ibmPlexSans(
                      color: Colors.black,
                      fontSize: 18.0,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                  Text(
                    viewModel.postedByDateForCard(index),
                    style: GoogleFonts.ibmPlexSans(
                      color: Colors.grey.shade500,
                      fontSize: 13.0,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 12),

          //
          // TITLE
          //
          InkWell(
            onTap: () =>
              <void>{ _launchURL(context, viewModel.uriForCard(index)), viewModel.didOpenURL = true },
            child: Text(
              viewModel.titleForCard(index),
              style: GoogleFonts.ibmPlexSans(
                color: AppColors.linkText,
                fontSize: 20.0,
                fontWeight: FontWeight.w500,
              ),
            ),
          ),

          //
          // SUBTITLE
          //
          Text(
            viewModel.subtitleForCard(index),
            style: GoogleFonts.ibmPlexSans(
              color: Colors.grey.shade500,
              fontSize: 16.0,
              fontWeight: FontWeight.w500,
            ),
          ),
          const SizedBox(height: 2.0),

          //
          // TAGS
          //
          if (viewModel.tagsForCard(index).isNotEmpty) ...<Widget>[
            const SizedBox(height: 4.0),
            Wrap(
              alignment: WrapAlignment.start,
              crossAxisAlignment: WrapCrossAlignment.center,
              direction: Axis.horizontal,
              children: <Widget>[
                for (int x=0; x<viewModel.tagsForCard(index).length; ++x) ...<Widget>[
                  Container(
                    padding: const EdgeInsets.symmetric(vertical: 3, horizontal: 3),
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10),
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(32.0),
                        color: AppColors.tagBackground,
                      ),
                      child: Text(
                        viewModel.tagsForCard(index)[x],
                        style: GoogleFonts.ibmPlexSans(
                          color: Colors.black,
                          fontSize: 12.0,
                          fontWeight: FontWeight.w400,
                        ),
                      ),
                    ),
                  ),
                ],
              ],
            ),
            const SizedBox(height: 6.0),
          ],
          Container(
            color: AppColors.scaffoldBackground,
            padding: const EdgeInsets.all(8),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.center,
              mainAxisSize: MainAxisSize.max,
              children: <Widget>[

                //
                // IMAGE
                //
                GestureDetector(
                  onTap: () =>
                    <void>{ _launchURL(context, viewModel.uriForCard(index)), viewModel.didOpenURL = true },
                  child: Padding(
                    padding: const EdgeInsets.all(8),
                    child: Wrap(
                      alignment: WrapAlignment.center,
                      children: <Widget>[
                        Image.network(
                          viewModel.imageUriForCard(index),
                          fit: BoxFit.scaleDown,
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 6.0),

                //
                // DESCRIPTION
                //
                Text(
                  viewModel.descriptionForCard(index),
                  style: GoogleFonts.ibmPlexSans(
                    color: Colors.black,
                    fontSize: 15.0,
                    fontWeight: FontWeight.w500,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 6.0),

          if (viewModel.getAttachmentCount(index) > 0) ...<Widget>[
            Padding(
              padding: const EdgeInsets.all(8),
              child: Wrap(
                alignment: WrapAlignment.center,
                children: <Widget>[
                  for (int i=0; i<viewModel.getAttachmentCount(index); i++) ...<Widget>[
                    Padding(
                      padding: const EdgeInsets.symmetric(vertical: 10),
                      child: GestureDetector(
                        onTap: () => <void>{
                          _launchURL(
                            context,
                            viewModel.uriForAttachment(index, i),
                            title: viewModel.activityValueForCard(index, i, actionType: 'attach'),
                          ),
                          viewModel.didOpenURL = true,
                        },
                        child: Image.network(
                          viewModel.uriForAttachment(index, i),
                          fit: BoxFit.scaleDown,
                          headers: <String, String>{ 'cookie' : AppState.instance.api.authToken },
                          errorBuilder: (BuildContext context, Object error, StackTrace? stackTrace) =>
                            Padding(
                              padding: const EdgeInsets.only(top: 20, bottom: 10, left: 10, right: 10),
                              child: _attachDetail(context, row: i, fontSize: 20, showTrash: false),
                            ),
                        ),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ],

          const SizedBox(height: 6.0),
          Wrap(
            alignment: WrapAlignment.start,
            spacing: 5,
            runAlignment: WrapAlignment.start,
            runSpacing: 5,
            crossAxisAlignment: WrapCrossAlignment.center,
            direction: Axis.horizontal,
            children: <Widget>[

              //
              // PERSONA SELECTOR
              //
              if (AppState.instance.selectedPersona == null) ...<Widget>[
                _personaSelector(),
                const SizedBox(width: 6),
              ],

              //
              // SAVE
              //
              _pill(
                count: viewModel.getSaveCount(index).toString(),
                action: 'bubble',
                asset: AppAssets.bubble,
                toggleAction: (int index) => viewModel.bubble(index),
                isSelected: (int index) => viewModel.isSaved(index),
                toggleShow: (int index, String action) => viewModel.didSelectActionForCard('bubble', index),
                showOrHide: (int index, String action) => viewModel.shouldShowActionForCard('bubble', index),
              ),

              //
              // LIKE
              //
              _pill(
                count: viewModel.getLikeCount(index).toString(),
                action: 'like',
                asset: AppAssets.like,
                toggleAction: (int index) => viewModel.toggleLike(index),
                isSelected: (int index) => viewModel.isLiked(index),
                toggleShow: (int index, String action) => viewModel.didSelectActionForCard('like', index),
                showOrHide: (int index, String action) => viewModel.shouldShowActionForCard('like', index),
              ),

              //
              // TAG
              //
              _pill(
                count: viewModel.getTagCount(index).toString(),
                action: 'tag',
                asset: AppAssets.tag,
                toggleAction: (int index) => viewModel.startEditingTag(index),
                isSelected: (int index) => viewModel.hasTags(index),
                toggleShow: (int index, String action) => viewModel.didSelectActionForCard('tag', index),
                showOrHide: (int index, String action) => viewModel.shouldShowActionForCard('tag', index),
              ),

              //
              // ATTACH
              //
              _pill(
                count: viewModel.getAttachmentCount(index).toString(),
                action: 'attach',
                asset: AppAssets.attach,
                toggleAction: (int index) => viewModel.attach(index),
                isSelected: (int index) => viewModel.hasAttachments(index),
                toggleShow: (int index, String action) => viewModel.didSelectActionForCard('attach', index),
                showOrHide: (int index, String action) => viewModel.shouldShowActionForCard('attach', index),
              ),

              //
              // COMMENT
              //
              _pill(
                count: viewModel.getCommentCount(index).toString(),
                action: 'comment',
                asset: AppAssets.comment,
                isSelected: (int index) => viewModel.hasComments(index),
                toggleAction: (int index) => viewModel.createNewComment(index),
                toggleShow: (int index, String action) => viewModel.didSelectActionForCard('comment', index),
                showOrHide: (int index, String action) => viewModel.shouldShowActionForCard('comment', index),
              ),

              //
              // EDIT
              //
              const SizedBox(width: 8),
              GestureDetector(
                onTap: () { viewModel.didSelectActionForCard('edit', index); },
                child: Image.asset(AppAssets.edit, width: 22, height: 22),
              ),
              const SizedBox(width: 6),
            ],
          ),
          if (viewModel.isNewComment(index)) ...<Widget>[   // Check for new comment creation
            _commentDetail(row: -1, context: context),
            const SizedBox(height: 6),
          ],
          if (viewModel.isEditingTag(index)) ...<Widget>[   // Check for tag editing
            _tagEditor(),
            const SizedBox(height: 6),
          ] else if (viewModel.shouldShowActionForCard('edit', index)) ...<Widget>[  // Check for post editing
            const SizedBox(height: 6),
            _postEditor(),
            const SizedBox(height: 6),
          ] else ...<Widget>[
            if (viewModel.actionForCard(index).isNotEmpty) ...<Widget>[
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.max,
                children: <Widget>[
                  const SizedBox(height: 10),
                  Text(
                  viewModel.actionName(index),
                    style: GoogleFonts.ibmPlexSans(
                      color: Colors.black,
                      fontSize: 18.0,
                      fontWeight: FontWeight.w400,
                    ),
                  ),
                  const SizedBox(height: 2),
                  for (int row = 0; row < viewModel.activitiesForCard(index).length; row++) ...<Widget>[
                    Wrap(
                      children: <Widget>[
                        const SizedBox(width: 4),
                        Text(
                          viewModel.activitiesForCard(index)[row].authorityName ?? '',
                          style: GoogleFonts.ibmPlexSans(
                            color: Colors.grey.shade800,
                            fontSize: 16.0,
                            fontWeight: FontWeight.w400,
                          ),
                        ),
                        const SizedBox(width: 10),
                        Text(
                          epochToDateString(viewModel.activitiesForCard(index)[row].epochSecond),
                          style: GoogleFonts.ibmPlexSans(
                            color: Colors.grey.shade800,
                            fontSize: 16.0,
                            fontWeight: FontWeight.w400,
                          ),
                        ),
                        const SizedBox(width: 10),
                        if ((viewModel.activityValueForCard(index, row)).isNotEmpty) ...<Widget>[
                          if (viewModel.actionForCard(index) == 'tag') ...<Widget>[
                            _tagDetail(row: row),
                          ] else if (viewModel.actionForCard(index) == 'attach') ...<Widget>[
                            _attachDetail(context, row: row, showTrash: viewModel.canDeleteAttachment(index, row)),
                          ] else if (viewModel.canEditComment(index, row)) ...<Widget>[
                            const SizedBox(width: 4),
                            GestureDetector(
                              onTap: () { viewModel.startEditingComment(index, row); },
                              child: Image.asset(AppAssets.edit, width: 18, height: 18),
                            ),
                          ],
                        ],
                      ],
                    ),
                    const SizedBox(height: 6),
                    if (viewModel.activityValueForCard(index, row).isNotEmpty) ...<Widget>[
                      if (viewModel.actionForCard(index) == 'comment') ...<Widget>[
                        _commentDetail(row: row, context: context),
                        const SizedBox(height: 6),
                      ],
                    ],
                  ],
                ],
              ),
            ],
          ],
        ],
      ),
    );

  Widget _pill({
    required String count,
    required String action,
    required String asset,
    required final bool Function(int index) isSelected,
    required final void Function(int index) toggleAction,
    required final void Function(int index, String action) toggleShow,
    required final bool Function(int index, String action) showOrHide,
  }) =>
    Container(
      padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 2.5),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(32.0),
        border: Border.all(color: Colors.black, width: 1.0),
      ),
      child: IntrinsicHeight(
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            GestureDetector(
              onTap: () { toggleAction(index); },
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.center,
                mainAxisSize: MainAxisSize.min,
                children: <Widget>[
                  const SizedBox(width: 4),
                  isSelected(index)
                    ? Image.asset(asset, width: 18, height: 18, fit: BoxFit.fitWidth, color: AppColors.ocRed)
                    : Image.asset(asset, width: 18, height: 18, fit: BoxFit.fitWidth),
                  const SizedBox(width: 4),
                  Text(
                    count,
                    style: GoogleFonts.ibmPlexSans(
                      color: Colors.black,
                      fontSize: 20.0,
                      height: 1.1,
                      fontWeight: FontWeight.w400,
                    ),
                  ),
                  const SizedBox(width: 8),
                ],
              ),
            ),
            const VerticalDivider(width: 1, color: Colors.black, indent: 0, endIndent: 0),
            GestureDetector(
              onTap: () { toggleShow(index, action); },
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.center,
                mainAxisSize: MainAxisSize.min,
                children: <Widget>[
                  const SizedBox(width: 4),
                  Image.asset(showOrHide(index, action) ? AppAssets.hide : AppAssets.show, width: 18, height: 18),
                  const SizedBox(width: 2),
                ],
              ),
            ),
          ],
        ),
      ),
    );

  Widget _tagDetail({required int row}) =>
    Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 2),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(32.0),
        color: AppColors.tagBackground,
      ),
      child: Text(
        viewModel.activityValueForCard(index, row),
        style: GoogleFonts.ibmPlexSans(
          color: Colors.grey.shade800,
          fontSize: 12.0,
          fontWeight: FontWeight.w500,
        ),
      ),
    );

  Widget _attachDetail(BuildContext context, {required int row, double? fontSize = 14.0, bool showTrash = true }) =>
    InkWell(
      onTap: () => <void>{
        _launchURL(
          context,
          viewModel.uriForAttachment(index, row),
          title: viewModel.activityValueForCard(index, row, actionType: 'attach'),
        ),
        viewModel.didOpenURL = true,
      },
      child: Wrap(
        children: <Widget>[
          Text(
            viewModel.activityValueForCard(index, row, actionType: 'attach'),
            style: GoogleFonts.ibmPlexSans(
              color: AppColors.linkText,
              fontSize: fontSize,
              fontWeight: FontWeight.w400,
            ),
          ),
          if (showTrash) ...<Widget>[
            const SizedBox(width: 4),
            GestureDetector(
              onTap: () { viewModel.deleteAttachment(index, row); },
              child: Image.asset(AppAssets.delete, width: 18, height: 18),
            ),
          ],
        ],
      ),
    );

  Widget _tagEditor() =>
    Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        OpenColaTextField(
          controller: viewModel.textEditorForTag(index),
          label: '',
          style: GoogleFonts.ibmPlexSans(
            color: Colors.black,
            fontSize: 20.0,
            fontWeight: FontWeight.w500,
          ),
        ),
        _buttonRow(
          onSave: () => viewModel.updateTag(index),
          onCancel: () => viewModel.cancelEditingTag(index),
          onDelete: () => <void>{ },
          showDelete: false,
        ),
      ],
      // ),
    );

  Widget _postEditor() =>
    Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        _buttonRow(
          onSave: () => viewModel.updatePost(index),
          onCancel: () => viewModel.cancelEditingPost(index),
          onDelete: () => viewModel.deletePost(index),
          showDelete: viewModel.showDeleteForCard(index),
        ),
      ],
      // ),
    );

  Widget _commentDetail({required int row, required BuildContext context}) =>
    Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 12),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(8.0),
        color: AppColors.scaffoldBackground,
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          if (row < 0 || viewModel.isEditing(index, row)) ...<Widget>[
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                mainAxisSize: MainAxisSize.min,
                children: <Widget>[
                  MarkdownTextInput(
                    (String value) => <void>{ },
                    viewModel.activityValueForCard(index, row),
                    maxLines: 5,
                    actions: MarkdownType.values,
                    controller: viewModel.textEditorForComment(index, row),
                    textStyle: const TextStyle(fontSize: 16),
                  ),
                  _buttonRow(
                    onSave: () => viewModel.updateComment(index, row),
                    onCancel: () => viewModel.cancelComment(index, row),
                    onDelete: () => viewModel.deleteComment(index, row),
                    showDelete: (row >= 0),
                  ),
                ],
              ),
            ),
          ] else ... <Widget>[
            MarkdownBody(
              data: (viewModel.activityValueForCard(index, row)),
              softLineBreak: true,
              onTapLink: (String text, String? href, String title) =>
                <void>{ _launchURL(context, href ?? ''), viewModel.didOpenURL = true },
              styleSheet: MarkdownStyleSheet.fromTheme(Theme.of(context)),
            ),
            const Spacer(),
          ],
        ],
      ),
    );

  Widget _personaSelector() =>
    Container(
      height: 48,
      padding: const EdgeInsets.only(top: 8, bottom: 8),
      child: PopupMenuButton<String>(
        itemBuilder: (BuildContext context) => AppState.instance.personaNames(true)
          .map(
            (String persona) => PopupMenuItem<String>(
              height: 36,
              padding: EdgeInsets.zero,
              value: persona,
              enabled: true,
                child: Row(
                children: <Widget>[
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 5),
                    child: Icon(
                      Icons.check,
                      color: viewModel.isSelectedForCard(index, persona) ? Colors.black : Colors.transparent,
                    ),
                  ),
                  Text(
                    persona,
                    style: const TextStyle(
                      color: Colors.black,
                    ),
                  ),
                ],
              ),
            ),
          ).toList(),
        onSelected: (String value) {
          viewModel.didSelectPersonaForCard(index, value);
        },
        child: IgnorePointer(
          ignoring: true,
          child: ConstrainedBox(
            constraints: const BoxConstraints(minWidth: 48),
            child: IntrinsicWidth(
              child: TextField(
                textAlignVertical: TextAlignVertical.center,
                decoration: InputDecoration(
                  border: const OutlineInputBorder(
                    borderSide: BorderSide( color: Colors.transparent, width: 0),
                  ),
                  filled: true,
                  fillColor: Colors.white,
                  contentPadding: const EdgeInsets.only(left: 6, right: 4),
                  hintText: viewModel.currentPersonaSelectionForCard(index),
                  suffixIcon: Icon(
                    Icons.keyboard_arrow_down,
                    color: Colors.black.withAlpha(192),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );

  void _launchURL(BuildContext context, String urlString, {String? title}) async {
    if (!urlString.startsWith('http')) {
      urlString = 'http://$urlString';
    }
    Navigator.push(context, MaterialPageRoute<dynamic>(
      builder: (BuildContext context) =>
        WebView(urlString: urlString, title: title),
      ),
    );
  }

  Widget _buttonRow({
    final void Function()? onSave,
    final void Function()? onCancel,
    final void Function()? onDelete,
    final bool showDelete = true,
  }) =>
    Container(
      padding: const EdgeInsets.symmetric(vertical: 10),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: <Widget>[
          GestureDetector(
            onTap: () => <void>{ onSave != null ? onSave() : null },
            child: Container(
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(16.0),
                border: Border.all(color: Colors.black, width: 1.0),
              ),
              padding: const EdgeInsets.symmetric(
                horizontal: 12.0,
                vertical: 2.0,
              ),
              child: Text(
                'Save',
                style: GoogleFonts.ibmPlexSans(
                  color: Colors.black,
                  fontSize: 16.0,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ),
          ),
          const SizedBox(width: 6.0),
          GestureDetector(
            onTap: () => <void>{ onCancel != null ? onCancel() : null },
            child: Container(
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(16.0),
                border: Border.all(color: Colors.black, width: 1.0),
              ),
              padding: const EdgeInsets.symmetric(
                horizontal: 12.0,
                vertical: 2.0,
              ),
              child: Text(
                'Cancel',
                style: GoogleFonts.ibmPlexSans(
                  color: Colors.black,
                  fontSize: 16.0,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ),
          ),
          const Spacer(),
          if (showDelete) ...<Widget>[
            GestureDetector(
              onTap: () => <void>{ onDelete != null ? onDelete() : null },
              child: Container(
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(16.0),
                  border: Border.all(color: Colors.black, width: 1.0),
                ),
                padding: const EdgeInsets.symmetric(
                  horizontal: 12.0,
                  vertical: 2.0,
                ),
                child: Text(
                  'Delete',
                  style: GoogleFonts.ibmPlexSans(
                    color: Colors.red,
                    fontSize: 16.0,
                    fontWeight: FontWeight.w500,
                  ),
                ),
              ),
            ),
          ],
        ],
      ),
    );
}

class EmptyFeedCard extends StatelessWidget {
  const EmptyFeedCard({super.key});

  @override
  Widget build(BuildContext context) =>
    Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(8.0),
        color: Colors.white,
      ),
      padding: const EdgeInsets.symmetric(
        horizontal: 10.0,
        vertical: 24.0,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.max,
        children: <Widget>[
          Padding(
            padding: const EdgeInsets.only(left: 16, bottom: 16),
            child: Text(
              'Snap!  Your feed is empty!',
              style: GoogleFonts.ibmPlexSans(
                color: Colors.black,
                fontSize: 22.0,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
          Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: <Widget>[
              Image.asset(AppAssets.nola, height: 120),
              const SizedBox(width: 16.0),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.center,
                  mainAxisSize: MainAxisSize.max,
                  children: <Widget>[
                    const SizedBox(height: 24.0),
                    RichText(
                      text: TextSpan(
                        children: <InlineSpan>[
                          TextSpan(
                            text: 'Add posts by clicking the new post icon (',
                            style: GoogleFonts.ibmPlexSans(
                              color: Colors.black,
                              fontSize: 18.0,
                              fontWeight: FontWeight.w400,
                            ),
                          ),
                          const WidgetSpan(
                            child: ImageIcon(AssetImage(AppAssets.newPost), size: 22),
                          ),
                          TextSpan(
                            text: ') on the top right',
                            style: GoogleFonts.ibmPlexSans(
                              color: Colors.black,
                              fontSize: 18.0,
                              fontWeight: FontWeight.w400,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 24.0),
                    RichText(
                      text: TextSpan(
                        children: <InlineSpan>[
                          TextSpan(
                            text: 'Add peers by clicking the peers icon (',
                            style: GoogleFonts.ibmPlexSans(
                              color: Colors.black,
                              fontSize: 18.0,
                              fontWeight: FontWeight.w400,
                            ),
                          ),
                          const WidgetSpan(
                            child: ImageIcon(AssetImage(AppAssets.peers), size: 22),
                          ),
                          TextSpan(
                            text: ') on the top right',
                            style: GoogleFonts.ibmPlexSans(
                              color: Colors.black,
                              fontSize: 18.0,
                              fontWeight: FontWeight.w400,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
    );
}
