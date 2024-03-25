import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:opencola_flutter/ui/base/base_view.dart';
import 'package:opencola_flutter/ui/elements/app_lifecycle_observer.dart';
import 'package:opencola_flutter/ui/elements/feed_card.dart';
import 'package:opencola_flutter/ui/elements/opencola_app_bar.dart';
import 'package:opencola_flutter/ui/screens/feed/feed_viewmodel.dart';
import 'package:opencola_flutter/ui/screens/feed/new_post_bottom_sheet.dart';
import 'package:opencola_flutter/ui/style/app_assets.dart';

class FeedView extends StatelessWidget {
  FeedView({super.key});

  final FeedViewModel viewModel = FeedViewModel();

  @override
  Widget build(BuildContext context) =>
    BaseView<FeedViewModel>(
      viewModel: viewModel,
      appBar:
        OpenColaAppBar(
          titleString: 'Feed',
          searchController: viewModel.searchController,
          isSelected: viewModel.isSelected,
          currentSelection: viewModel.currentSelection,
          didSelectPersona: viewModel.didSelectPersona,
          onSearch: viewModel.onSearch,
          allowAll: true,
          actionList: <Widget>[
            GestureDetector(
              onTap: () async => <Future<Set<Set<Future<void>>>>>{
                showModalBottomSheet<bool>(
                  context: context,
                  isScrollControlled: true,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(24.0),
                  ),
                  builder: (BuildContext context) => const NewPostBottomSheet(),
                ).then((bool? shouldRefresh) => <Set<Future<void>>>{
                    if (shouldRefresh == true) <Future<void>>{
                      viewModel.refresh(),
                    },
                  },
                ),
              },
              child: const ImageIcon(AssetImage(AppAssets.newPost)),
            ),
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 6),
              child: ImageIcon(AssetImage(AppAssets.divider), size: 10),
            ),
            GestureDetector(
              onTap: () async => <void>{ viewModel.navigateToPeers() },
              child: const ImageIcon(AssetImage(AppAssets.peers)),
            ),
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 6),
              child: ImageIcon(AssetImage(AppAssets.divider), size: 10),
            ),
            GestureDetector(
              onTap: () async => <void>{ viewModel.navigateToSettings() },
              child: const Icon(Icons.settings),
            ),
            const SizedBox(width: 12.0),
          ],
        ),
      builder: (BuildContext context, FeedViewModel viewModel, Widget? child) =>
        AppLifecycleObserver(
          onAppResumed: () async => await viewModel.onAppResumed(),
          child: RefreshIndicator(
            onRefresh: () =>
              Future<void>.delayed(
                const Duration(seconds: 1),
                () async { viewModel.refresh(); },
              ),
            child: SingleChildScrollView(
              controller: viewModel.paginationScrollController.scrollController,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                mainAxisSize: MainAxisSize.min,
                children: <Widget>[
                  if (viewModel.searchController.text.isNotEmpty) ...<Widget>[
                    Padding(
                      padding: const EdgeInsets.only(top: 12, bottom: 4),
                      child: Text(
                        '${viewModel.numCards} results for \'${viewModel.searchController.text}\'',
                        textAlign: TextAlign.center,
                        style: GoogleFonts.ibmPlexSans(
                          color: Colors.black,
                          fontSize: 18.0,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                    ),
                  ],
                  if (viewModel.numCards > 0) ...<Widget>[
                    ListView.builder(
                      scrollDirection: Axis.vertical,
                      shrinkWrap: true,
                      physics: const ScrollPhysics(),
                      itemCount: viewModel.numCards,
                      itemBuilder: (BuildContext context, int index) =>
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
                          child: FeedCard(
                            viewModel: viewModel,
                            index: index,
                          ),
                        ),
                    ),
                  ] else ...<Widget>[
                    const Padding(
                      padding: EdgeInsets.only(top: 16),
                      child: EmptyFeedCard(),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ),
    );
}
