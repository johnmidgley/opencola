import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:opencola_flutter/app_state.dart';
import 'package:opencola_flutter/ui/base/base_view.dart';
import 'package:opencola_flutter/ui/elements/app_lifecycle_observer.dart';
import 'package:opencola_flutter/ui/elements/opencola_app_bar.dart';
import 'package:opencola_flutter/ui/elements/peer_card.dart';
import 'package:opencola_flutter/ui/screens/peer/peer_viewmodel.dart';
import 'package:opencola_flutter/ui/style/app_assets.dart';
import 'package:opencola_flutter/utils/utils.dart';

class PeerView extends StatelessWidget {
  PeerView({super.key});

  final PeerViewModel viewModel = PeerViewModel();

  @override
  Widget build(BuildContext context) =>
    BaseView<PeerViewModel>(
      viewModel: viewModel,
      appBar:
        OpenColaAppBar(
          titleString: 'Peers',
          searchController: viewModel.searchController,
          isSelected: viewModel.isSelected,
          currentSelection: viewModel.currentSelection,
          didSelectPersona: viewModel.didSelectPersona,
          onSearch: viewModel.onSearch,
          allowAll: false,
          actionList: <Widget>[
            GestureDetector(
              onTap: () => <void>{ log(' >>> New Peer <<< ') },
              child: const ImageIcon(AssetImage(AppAssets.addPeer)),
            ),
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 6),
              child: ImageIcon(AssetImage(AppAssets.divider), size: 10),
            ),
            GestureDetector(
              onTap: () async => <void>{ viewModel.navigateToFeed()  },
              child: const ImageIcon(AssetImage(AppAssets.feed)),
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
      builder: (BuildContext context, PeerViewModel viewModel, Widget? child) =>
        AppLifecycleObserver(
          onAppResumed: () => viewModel.onAppResumed(),
          child: RefreshIndicator(
            onRefresh: () =>
              Future<void>.delayed(
                const Duration(seconds: 1),
                () async { viewModel.refresh(); },
              ),
            child: SingleChildScrollView(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                mainAxisSize: MainAxisSize.min,
                children: <Widget>[
                  Padding(
                    padding: const EdgeInsets.only(left: 16, top: 24, bottom: 8),
                    child: Text(
                      'Peers of ${AppState.instance.selectedPersona?.name}',
                      textAlign: TextAlign.center,
                      style: GoogleFonts.ibmPlexSans(
                        color: Colors.black,
                        fontSize: 25.0,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                  ),
                  if (viewModel.numPeers > 0) ...<Widget>[
                    ListView.builder(
                      scrollDirection: Axis.vertical,
                      shrinkWrap: true,
                      physics: const ScrollPhysics(),
                      itemCount: viewModel.numPeers,
                      itemBuilder: (BuildContext context, int index) =>
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
                          child: PeerCard(
                            name: viewModel.nameForPeer(index),
                            id: viewModel.idForPeer(index),
                            publicKey: viewModel.publicKeyForPeer(index),
                            link: viewModel.linkForPeer(index),
                            photo: viewModel.photoForPeer(index),
                            index: index,
                          ),
                        ),
                    ),
                  ] else ...<Widget>[
                    const Padding(
                      padding: EdgeInsets.only(top: 16),
                      child: EmptyPeerCard(),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ),
    );
}
