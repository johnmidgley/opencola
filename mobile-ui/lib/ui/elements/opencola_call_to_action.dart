import 'package:flutter/material.dart';
import 'package:opencola_flutter/ui/style/app_colors.dart';

class OpenColaCallToAction extends StatelessWidget {
  const OpenColaCallToAction({
    this.backgroundColor = AppColors.ocRed,
    required this.caption,
    this.captionColor = Colors.white,
    this.disabledBackgroundColor = AppColors.disabledBackground,
    this.enabled = true,
    required this.onTap,
    this.suffixIcon,
    super.key,
  });

  final Color backgroundColor;
  final String caption;
  final Color captionColor;
  final Color disabledBackgroundColor;
  final bool enabled;
  final VoidCallback onTap;
  final Widget? suffixIcon;

  @override
  Widget build(BuildContext context) =>
    Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(6.0),
        color: enabled ? backgroundColor : disabledBackgroundColor,
      ),
      child: TextButton(
        onPressed: enabled ? onTap : null,
        child: Row(
          children: <Widget>[
            const Spacer(),
            Text(
              caption,
              style: TextStyle(
                color: captionColor,
                fontSize: 20.0,
              ),
            ),
            if (suffixIcon != null) ...<Widget>[
              const SizedBox(width: 8.0),
              suffixIcon!,
            ],
            const Spacer(),
          ],
        ),
      ),
    );
}
