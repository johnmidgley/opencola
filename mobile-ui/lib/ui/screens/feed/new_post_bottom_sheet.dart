import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:opencola_flutter/app_state.dart';
import 'package:opencola_flutter/ui/elements/opencola_call_to_action.dart';
import 'package:opencola_flutter/ui/elements/opencola_textfield.dart';
import 'package:opencola_flutter/ui/screens/feed/feed_viewmodel.dart';
import 'package:opencola_flutter/ui/screens/feed/new_post_bottom_sheet_viewmodel.dart';
import 'package:opencola_flutter/ui/style/app_colors.dart';
import 'package:opencola_flutter/utils/utils.dart';

class NewPostBottomSheet extends StatefulWidget {
  const NewPostBottomSheet({
    this.urlText,
    this.feedViewModel,
    super.key,
  });

  final String? urlText;
  final FeedViewModel? feedViewModel;

  @override
  State<NewPostBottomSheet> createState() => _NewPostBottomSheetState();
}

class _NewPostBottomSheetState extends State<NewPostBottomSheet> {
  final NewPostBottomSheetViewModel viewModel = NewPostBottomSheetViewModel();
  String? urlText;

  @override
  void initState() {
    super.initState();
    // ignore: discarded_futures
    viewModel.init();
    viewModel.urlText = widget.urlText ?? AppState.instance.intentURL;
    viewModel.urlController.text = viewModel.urlText ?? '';
    log('   >>>  NewPostBottomSheet.initState: widget.urlText=${widget.urlText}');
    log('   >>>  NewPostBottomSheet.initState: intentURL=${AppState.instance.intentURL}');
    log('   >>>  NewPostBottomSheet.initState: viewModel.urlText=${viewModel.urlText}');
    viewModel.addListener(_onNotifyListeners);
  }

  void _onNotifyListeners() {
    viewModel.setKeyboardHeight(MediaQuery.of(context).viewInsets.bottom);
    setState(() {});
  }

  @override
  Widget build(BuildContext context) =>
    Padding(
      padding: const EdgeInsets.symmetric(
        horizontal: 32.0,
        vertical: 16.0,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          _title(),
          const SizedBox(height: 10.0),
          _body(),
          const SizedBox(height: 10.0),
          _saveButton(),
          AnimatedSize( // Animate vertical slide above keyboard
            curve: Curves.easeIn,
            duration: const Duration(milliseconds: 350),
            child: SizedBox(height: viewModel.keyboardHeight),
          ),
        ],
      ),
    );

  Widget _title() =>
    Row(
      children: <Widget>[
        Text(
          'New Post',
          style: GoogleFonts.ibmPlexSans(
            color: Colors.black,
            fontSize: 22.0,
          ),
        ),
        const Spacer(),
        IconButton(
          onPressed: () => <void>{ viewModel.cancel(), Navigator.of(context).pop(false) },
          iconSize: 56.0,
          icon: Container(
            decoration: const BoxDecoration(
              color: AppColors.bottomSheetCloseButtonBackground,
              shape: BoxShape.circle,
            ),
            child: const Center(
              child: Icon(
                Icons.close_sharp,
                color: AppColors.bottomSheetCloseButtonIcon,
                size: 32.0,
              ),
            ),
          ),
        ),
      ],
    );

  Widget _body() =>
    Flexible(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          OpenColaTextField(
            controller: viewModel.urlController,
            autofocus: true,
            focusNode: viewModel.focusNode,
            keyboardType: TextInputType.url,
            label: '',
            hint: viewModel.hint,
            style: GoogleFonts.ibmPlexSans(
              color: Colors.black,
              fontSize: 20.0,
              fontWeight: FontWeight.w500,
            ),
          ),
          const SizedBox(height: 10.0),
          _personaSelector(),
          const SizedBox(height: 10.0),
        ],
      ),
    );

  Widget _saveButton() =>
    Padding(
      padding: const EdgeInsets.symmetric(
        vertical: 16.0,
      ),
      child: OpenColaCallToAction(
        caption: 'Save',
        enabled: true,
        onTap: () async => <void>{
          await viewModel.save(),
          // ignore: use_build_context_synchronously
          Navigator.of(context).pop(true),
        },
      ),
    );

  Widget _personaSelector() =>
    Container(
      height: 50,
      padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 24),
      child: PopupMenuButton<String>(
        itemBuilder: (BuildContext context) => AppState.instance.personaNames(true)
          .map(
            (String persona) => PopupMenuItem<String>(
              height: 20,
              padding: EdgeInsets.zero,
              value: persona,
              enabled: true,
                child: Row(
                children: <Widget>[
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 5),
                    child: Icon(
                      Icons.check,
                      color: viewModel.isSelected(persona) ? Colors.black : Colors.transparent,
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
          viewModel.didSelectPersona(value);
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
                  hintText: viewModel.currentPersonaSelection(),
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

  @override
  void dispose() {
    viewModel.removeListener(_onNotifyListeners);
    viewModel.dispose();
    super.dispose();
  }
}
