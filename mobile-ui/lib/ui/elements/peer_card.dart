import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:opencola_flutter/ui/elements/opencola_textfield.dart';
import 'package:opencola_flutter/ui/style/app_assets.dart';

class PeerCard extends StatelessWidget {
  const PeerCard({
    required this.name,
    required this.id,
    required this.publicKey,
    required this.link,
    required this.photo,
    required this.index,
    this.isEditing,
    this.onEdit,
    this.onSave,
    this.onCancel,
    this.onDelete,
    this.nameController,
    super.key,
  });

  final String name;
  final String id;
  final String publicKey;
  final String link;
  final String photo;
  final int index;
  final bool? isEditing;
  final void Function(int)? onEdit;
  final void Function(int, String?)? onSave;
  final void Function()? onCancel;
  final void Function(int)? onDelete;
  final TextEditingController? nameController;

  @override
  Widget build(BuildContext context) =>
    Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(8.0),
        color: Colors.white,
      ),
      padding: const EdgeInsets.symmetric(
        horizontal: 12.0,
        vertical: 12.0,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: <Widget>[
              ClipRRect(
                borderRadius: BorderRadius.circular(15.0),
                child: SizedBox(
                  height: 55,
                  width: 55,
                  child: Image.network(photo, fit: BoxFit.cover),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: <Widget>[
              const ImageIcon(AssetImage(AppAssets.user)),
              const SizedBox(width: 8.0),
              if (isEditing != null && isEditing!) ...<Widget>[
                Container(
                  width: MediaQuery.of(context).size.width - 90,
                  padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 2.5),
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(10.0),
                    border: Border.all(color: Colors.black, width: 0.5),
                  ),
                  child: OpenColaTextField(
                    contentPadding: EdgeInsets.zero,
                    isDense: true,
                    borderless: true,
                    controller: nameController ?? TextEditingController(),
                    label: '',
                    style: GoogleFonts.ibmPlexSans(
                      color: Colors.black,
                      fontSize: 14.0,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ),
              ] else ...<Widget>[
                Container(
                  width: MediaQuery.of(context).size.width - 90,
                  padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 2.5),
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(10.0),
                    border: Border.all(color: Colors.black, width: 0.5),
                  ),
                  child: Text(
                    name,
                    style: GoogleFonts.ibmPlexSans(
                      color: Colors.black,
                      fontSize: 14.0,
                      fontWeight: FontWeight.w400,
                    ),
                  ),
                ),
              ],
            ],
          ),
          const SizedBox(height: 6),
          Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            mainAxisSize: MainAxisSize.max,
            children: <Widget>[
              const ImageIcon(AssetImage(AppAssets.link)),
              const SizedBox(width: 8.0),
              Container(
                width: MediaQuery.of(context).size.width - 90,
                padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 2.5),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(10.0),
                  border: Border.all(color: Colors.black, width: 0.5),
                ),
                child: Text(
                  link,
                  style: GoogleFonts.ibmPlexSans(
                    color: Colors.black,
                    fontSize: 14.0,
                    fontWeight: FontWeight.w400,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 6),
          Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: <Widget>[
              const ImageIcon(AssetImage(AppAssets.photo)),
              const SizedBox(width: 8.0),
              Container(
                width: MediaQuery.of(context).size.width - 90,
                padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 2.5),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(10.0),
                  border: Border.all(color: Colors.black, width: 0.5),
                ),
                child: Text(
                  photo,
                  overflow: TextOverflow.ellipsis,
                  maxLines: 1,
                  softWrap: false,
                  style: GoogleFonts.ibmPlexSans(
                    color: Colors.black,
                    fontSize: 14.0,
                    fontWeight: FontWeight.w400,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 16.0),
          if (isEditing != null) ...<Widget>[
            _buttonRow(
              isEditing: isEditing!,
              onEdit: onEdit != null ? () => onEdit!(index) : null,
              onSave: onSave != null ? () => onSave!(index, nameController?.text) : null,
              onCancel: onCancel != null ? () => onCancel!() : null,
              onDelete: onDelete != null ?  () => onDelete!(index) : null,
            ),
          ],
          const SizedBox(height: 8.0),
        ],
      ),
    );

  Widget _buttonRow({
    required bool isEditing,
    final void Function()? onEdit,
    final void Function()? onSave,
    final void Function()? onCancel,
    final void Function()? onDelete,
  }) =>
    Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: <Widget>[
        if (isEditing) ...<Widget>[
          TextButton(
            onPressed: () => <void>{ onSave != null ? onSave() : null },
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
          TextButton(
            onPressed: () => <void>{ onCancel != null ? onCancel() : null },
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
          TextButton(
            onPressed: () => <void>{ onDelete != null ? onDelete() : null },
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
        ] else ...<Widget>[
          TextButton(
            onPressed: () => <void>{ onEdit != null ? onEdit() : null },
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
                'Edit',
                style: GoogleFonts.ibmPlexSans(
                  color: Colors.black,
                  fontSize: 16.0,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ),
          ),
        ],
      ],
    );
}

class EmptyPeerCard extends StatelessWidget {
  const EmptyPeerCard({super.key});

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
              'Snap!  You have no peers!',
              style: GoogleFonts.ibmPlexSans(
                color: Colors.black,
                fontSize: 22.0,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Image.asset(AppAssets.nola, height: 120),
              const SizedBox(width: 16.0),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.max,
                  children: <Widget>[
                    const SizedBox(height: 24.0),
                    RichText(
                      text: TextSpan(
                        children: <InlineSpan>[
                          TextSpan(
                            text: 'Add peers by clicking the add peer icon (',
                            style: GoogleFonts.ibmPlexSans(
                              color: Colors.black,
                              fontSize: 18.0,
                              fontWeight: FontWeight.w400,
                            ),
                          ),
                          const WidgetSpan(
                            child: ImageIcon(AssetImage(AppAssets.addPeer), size: 22),
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
