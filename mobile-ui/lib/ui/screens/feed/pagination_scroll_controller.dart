import 'package:flutter/material.dart';

class PaginationScrollController {
  late ScrollController scrollController;
  bool isLoading = false;
  bool stopLoading = false;
  int currentPage = 1;
  double boundaryOffset = 0.5;
  late Function loadAction;

  void init({Function? initAction, required Function loadAction}) {
    if (initAction != null) {
      initAction();
    }
    this.loadAction = loadAction;
    scrollController = ScrollController()..addListener(scrollListener);
  }

  void reset() {
    isLoading = false;
    stopLoading = false;
    currentPage = 1;
    if (scrollController.hasClients) {
      scrollController.jumpTo(0);
    }
  }

  void scrollListener() {
    if (!stopLoading) {
      if (scrollController.offset >= (scrollController.position.maxScrollExtent * boundaryOffset) && !isLoading) {
        // Load more data
        isLoading = true;
        loadAction().then((bool shouldStop) {
          isLoading = false;
          currentPage++;
          boundaryOffset = 1 - (1 / (currentPage * 2));
          if (shouldStop == true) {
            stopLoading = true;
          }
        });
      }
    }
  }

  void dispose() {
    scrollController.removeListener(scrollListener);
    scrollController.dispose();
  }
}
