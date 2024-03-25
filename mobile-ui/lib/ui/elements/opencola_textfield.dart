import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:opencola_flutter/ui/style/app_colors.dart';

class OpenColaTextField extends StatelessWidget {
  const OpenColaTextField({
    this.autocorrect = false,
    this.autofocus = false,
    this.contentPadding = const EdgeInsets.symmetric(horizontal: 20),
    this.isDense = false,
    this.borderless = false,
    required this.controller,
    this.errorMessage,
    this.focusNode,
    required this.label,
    this.hint,
    this.inputFormatters,
    this.keyboardType = TextInputType.name,
    this.onTap,
    this.readOnly = false,
    this.suffixIcon,
    this.style,
    super.key,
  });

  final bool autocorrect;
  final bool autofocus;
  final EdgeInsets contentPadding;
  final bool isDense;
  final bool borderless;
  final TextEditingController controller;
  final String? errorMessage;
  final FocusNode? focusNode;
  final String label;
  final String? hint;
  final List<TextInputFormatter>? inputFormatters;
  final TextInputType keyboardType;
  final VoidCallback? onTap;
  final bool readOnly;
  final Widget? suffixIcon;
  final TextStyle? style;

  @override
  Widget build(BuildContext context) =>
    Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        if (label.isNotEmpty) ...<Widget>[
          Text(
            label.toUpperCase(),
            style: GoogleFonts.ibmPlexSans(
              fontSize: 16.0,
              fontWeight: FontWeight.w500,
              color: AppColors.labelText,
            ),
          ),
          const SizedBox(height: 16.0),
        ],
        TextField(
          autocorrect: autocorrect,
          autofocus: autofocus,
          controller: controller,
          decoration: InputDecoration(
            border: OutlineInputBorder(
              borderSide: borderless ? BorderSide.none : const BorderSide(
                color: AppColors.textOutline,
              ),
            ),
            contentPadding: contentPadding,
            isDense: isDense,
            errorBorder: const OutlineInputBorder(
              borderSide: BorderSide(
                color: AppColors.warningText,
              ),
            ),
            errorText: errorMessage,
            hintText: hint,
            hintStyle: GoogleFonts.ibmPlexSans(
              color: AppColors.labelText.withOpacity(0.5),
              fontSize: 14.0,
            ),
            suffixIcon: suffixIcon,
          ),
          focusNode: focusNode,
          inputFormatters: inputFormatters,
          keyboardType: keyboardType,
          onTap: onTap,
          readOnly: readOnly,
          textCapitalization: TextCapitalization.none,
          style: style,
        ),
      ],
    );
}

class OpenColaSecureTextField extends StatefulWidget {
  const OpenColaSecureTextField({
    required this.controller,
    this.errorMessage,
    required this.label,
    this.hint,
    super.key,
  });

  final TextEditingController controller;
  final String? errorMessage;
  final String label;
  final String? hint;

  @override
  State<OpenColaSecureTextField> createState() => _OpenColaSecureTextFieldState();
}

class _OpenColaSecureTextFieldState extends State<OpenColaSecureTextField> {
  bool _obscureText = true;

  @override
  Widget build(BuildContext context) =>
    Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        if (widget.label.isNotEmpty) ...<Widget>[
          Text(
            widget.label.toUpperCase(),
            style: GoogleFonts.ibmPlexSans(
              fontSize: 16.0,
              fontWeight: FontWeight.w500,
              color: AppColors.labelText,
            ),
          ),
          const SizedBox(height: 16.0),
        ],
        TextField(
          autocorrect: false,
          controller: widget.controller,
          decoration: InputDecoration(
            border: const OutlineInputBorder(
              borderSide: BorderSide(
                color: AppColors.textOutline,
              ),
            ),
            contentPadding: const EdgeInsets.symmetric(horizontal: 20.0),
            errorBorder: const OutlineInputBorder(
              borderSide: BorderSide(
                color: AppColors.warningText,
              ),
            ),
            errorText: widget.errorMessage,
            hintText: widget.hint,
            hintStyle: GoogleFonts.ibmPlexSans(
              color: AppColors.labelText.withOpacity(0.5),
              fontSize: 14.0,
            ),
            suffixIcon: GestureDetector(
              child: _obscureText ? const Icon(Icons.visibility_off) : const Icon(Icons.visibility),
              onTap: () { setState(() {_obscureText = !_obscureText;}); },
            ),
          ),
          obscureText: _obscureText,
          textCapitalization: TextCapitalization.none,
        ),
      ],
    );
}
