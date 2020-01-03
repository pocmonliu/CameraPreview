package com.ys.yoosir.camerapreview;

import android.content.Context;
import android.view.Gravity;
import android.widget.Toast;

/**
 * @author stone
 * @date 2018/10/10 18:44
 */

public class ToastUtil {

    public static Context context;

    public static void showToast(String content){
        Toast.makeText(context, content, Toast.LENGTH_SHORT).show();
    }

    public static void showCenterToast(String content){
        Toast toast = Toast.makeText(context, content, Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();
    }
}
