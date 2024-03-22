import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:opencola_flutter/app_state.dart';
import 'package:opencola_flutter/ui/style/app_assets.dart';
import 'package:opencola_flutter/ui/style/app_colors.dart';

class OpenColaAppBar extends AppBar implements PreferredSizeWidget {

  OpenColaAppBar({
    super.key,
    required this.titleString,
    required this.actionList,
    required this.searchController,
    required this.currentSelection,
    required this.isSelected,
    required this.didSelectPersona,
    required this.onSearch,
    required this.allowAll,
  });

  final String titleString;
  final List<Widget>? actionList;
  final TextEditingController searchController;
  final String Function() currentSelection;
  final bool Function(String) isSelected;
  final Future<void> Function(String) didSelectPersona;
  final Future<void> Function(String) onSearch;
  final bool allowAll;

  @override
  OpenColaAppBarState createState() => OpenColaAppBarState();

  @override
  Size get preferredSize => const Size.fromHeight(kToolbarHeight + 40);
}

class OpenColaAppBarState extends State<OpenColaAppBar> {
  @override
  void initState() {
    AppState.instance.appBarNotifier.addListener(() => mounted ? setState(() {}) : null);
    super.initState();
  }

  @override
  Widget build(BuildContext context) =>
    AppBar(
      backgroundColor: AppColors.ocRed,
      centerTitle: true,
      titleSpacing: 0,
      title: Container(
        decoration: BoxDecoration( border: Border.all(color: Colors.transparent, width: 0)),
        child: Text(
          widget.titleString,
          style: GoogleFonts.ibmPlexSans(
            color: Colors.white,
            fontSize: 24.0,
            fontWeight: FontWeight.w600,
          ),
        ),
      ),
      leading: const Padding(
        padding: EdgeInsets.only(left: 12),
        child: ImageIcon(AssetImage(AppAssets.pullTab), color: Colors.white),
      ),
      actionsIconTheme: const IconThemeData(size: 24, color: Colors.white, opticalSize: 10),
      actions: widget.actionList,
      bottom: PreferredSize(
        preferredSize: const Size.fromHeight(25),
        child: Container(
          color: AppColors.ocRed,
          height: 40.0,
          padding: const EdgeInsets.symmetric(vertical: 4),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              const SizedBox(width: 8.0),
              PopupMenuButton<String>(
                itemBuilder: (BuildContext context) => AppState.instance.personaNames()
                  .map(
                    (String persona) => PopupMenuItem<String>(
                      height: 0,
                      padding: const EdgeInsets.symmetric(horizontal: 0, vertical: 4),
                      value: persona,
                      enabled: (widget.allowAll || persona != 'All'),
                        child: Row(
                        children: <Widget>[
                          Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 5),
                            child: Icon(
                              Icons.check,
                              color: widget.isSelected(persona) ? Colors.black : Colors.transparent,
                            ),
                          ),
                          Text(
                            persona,
                            style: TextStyle(
                              color: (widget.allowAll || persona != 'All')
                                ? Colors.black
                                : Colors.black.withAlpha(64),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ).toList(),
                onSelected: widget.didSelectPersona,
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
                          hintText: widget.currentSelection(),
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
              const SizedBox(width: 8.0),
              Expanded( child:
                TextField(
                  controller: widget.searchController,
                  onSubmitted: (String value) async => widget.onSearch(value),
                  textInputAction: TextInputAction.search,
                  decoration: InputDecoration(
                    filled: true,
                    fillColor: Colors.white,
                    suffixIcon: GestureDetector(
                      onTap: () async {
                        widget.searchController.clear();
                        widget.onSearch('');
                      },
                      child: Icon(
                        Icons.clear,
                        color: widget.searchController.text.isEmpty ? Colors.transparent : Colors.red,
                      ),
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 8.0),
            ],
          ),
        ),
      ),
    );
}
