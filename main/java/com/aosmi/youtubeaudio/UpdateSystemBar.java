package com.aosmi.youtubeaudio;
import android.app.Activity;
import android.widget.LinearLayout;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class UpdateSystemBar {
    public static void Update(Activity activity){
        activity.getWindow().setNavigationBarColor(ContextCompat.getColor(activity,R.color.gray));
        activity.getWindow().setStatusBarColor(ContextCompat.getColor(activity,R.color.gray));

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView());
        if (controller != null) {
            activity.getWindow().setNavigationBarContrastEnforced(false);
            activity.getWindow().setStatusBarContrastEnforced(false);
        }

    }

    public static void updateTopPanelMargin(Activity activity){
        LinearLayout topPanel = activity.findViewById(R.id.topPanel);
        if (topPanel == null) return;
        int extraPadding = (int) (5 * activity.getResources().getDisplayMetrics().density);
        int finalTopMargin = activity.isInMultiWindowMode() ? 0 : extraPadding;

        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) topPanel.getLayoutParams();
        params.topMargin = finalTopMargin;
        topPanel.setLayoutParams(params);
    }
}