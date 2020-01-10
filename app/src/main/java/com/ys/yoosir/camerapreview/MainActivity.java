package com.ys.yoosir.camerapreview;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import com.aimall.sdk.faceattribute.bean.ImoAttributeInfo;

import java.lang.ref.WeakReference;

public class MainActivity extends FragmentActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int REQUEST_CAMERA_CODE = 0x100;
    private static final int MSG_UPDATE_UI = 1;           //更新界面

    private TextView tv_face_attr;
    private SurfaceView surface_view;

    private MainHandler mMainHandler;
    private ImoModel mImoModel;

    public void updateFaceAttr(String text) {
        tv_face_attr.setText(text);
    }

    private static class MainHandler extends Handler {

        private final WeakReference<MainActivity> mActivity;

        MainHandler(MainActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            MainActivity activity = mActivity.get();
            if (activity != null) {
                switch (msg.what) {
                    case MSG_UPDATE_UI:
                        activity.updateFaceAttr((String) msg.obj);
                        break;
                    default:
                        break;
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.e(TAG, "===>onCreate()");

        // 无title
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        // 全屏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);

        surface_view = findViewById(R.id.surface_view);
        tv_face_attr = findViewById(R.id.tv_face_attr);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_CODE);
            Log.e(TAG, "===>111111");
            return;
        } else {
            Log.e(TAG, "===>444444");
            init();
        }
        Log.e(TAG, "===>555555");
        openSurfaceView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.e(TAG, "===>onResume()");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.e(TAG, "===>onPause()");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "===>222222");
                recreate();
            } else {
                Log.e(TAG, "===>333333");
                finish();
            }
        }
    }

    private void init() {
        mMainHandler = new MainHandler(this);

        mImoModel = new ImoModel(this);
        mImoModel.init();
        mImoModel.registerFaceTrackerDetectorListener(mIFaceTrackerDetectorListener);
    }

    /**
     * 把摄像头的图像显示到SurfaceView
     */
    private void openSurfaceView() {
        // 获得 SurfaceHolder 对象
        SurfaceHolder surfaceHolder = surface_view.getHolder();
        Log.d(TAG, "surface_view: width=" + surface_view.getWidth() + ", height=" + surface_view.getHeight());
        // 设置 Surface 格式
        // 参数： PixelFormat中定义的 int 值 ,详细参见 PixelFormat.java
        surfaceHolder.setFormat(PixelFormat.TRANSPARENT);

        // 如果需要，保持屏幕常亮
        // mSurfaceHolder.setKeepScreenOn(true);

        // 设置 Surface 的分辨率
        // mSurfaceHolder.setFixedSize(width,height);

        // 设置 Surface 类型
        // 参数：
        //        SURFACE_TYPE_NORMAL       : 用 RAM 缓存原生数据的普通 Surface
        //        SURFACE_TYPE_HARDWARE     : 适用于 DMA(Direct memory access )引擎和硬件加速的Surface
        //        SURFACE_TYPE_GPU          : 适用于 GPU 加速的 Surface
        //        SURFACE_TYPE_PUSH_BUFFERS ：表明该 Surface 不包含原生数据，Surface用到的数据由其他对象提供
        // 在 Camera 图像预览中就使用 SURFACE_TYPE_PUSH_BUFFERS 类型的 Surface，有 Camera 负责提供给预览 Surface 数据，这样图像预览会比较流
        //mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        // 添加 Surface 的 callback 接口
        surfaceHolder.addCallback(mSurfaceCallback);
    }

    private SurfaceHolder.Callback mSurfaceCallback = new SurfaceHolder.Callback() {

        /**
         *  在 Surface 首次创建时被立即调用：活得叫焦点时。一般在这里开启画图的线程
         * @param surfaceHolder 持有当前 Surface 的 SurfaceHolder 对象
         */
        @Override
        public void surfaceCreated(final SurfaceHolder surfaceHolder) {
            Log.e(TAG, "===>surfaceCreated()");
            if(mImoModel != null) {
                mImoModel.openCamera(surfaceHolder);
            }
        }

        /**
         *  在 Surface 格式 和 大小发生变化时会立即调用，可以在这个方法中更新 Surface
         * @param surfaceHolder   持有当前 Surface 的 SurfaceHolder 对象
         * @param format          surface 的新格式
         * @param width           surface 的新宽度
         * @param height          surface 的新高度
         */
        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
            Log.e(TAG, "===>surfaceChanged(), width=" + width + ", height=" + height);
        }

        /**
         *  在 Surface 被销毁时立即调用：失去焦点时。一般在这里将画图的线程停止销毁
         * @param surfaceHolder 持有当前 Surface 的 SurfaceHolder 对象
         */
        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
            Log.e(TAG, "===>surfaceDestroyed()");
            if(mImoModel != null) {
                mImoModel.closeCamera();
            }
        }
    };

    private ImoModel.IFaceTrackerDetectorListener mIFaceTrackerDetectorListener = new ImoModel.IFaceTrackerDetectorListener() {
        @Override
        public void onFaceAttr(ImoAttributeInfo[] attributeInfos) {
            String attr = "";
            if(attributeInfos != null && attributeInfos.length > 0) {
                for (int i = 0; i < attributeInfos.length; i++) {
                    int age = attributeInfos[i].getAge();
                    int gender = attributeInfos[i].getGender().getImoGender().ordinal();
                    String sex = "未知";
                    switch (gender) {
                        case 1:
                            sex = "女";
                            break;
                        case 2:
                            sex = "男";
                            break;
                    }
                    attr += "=>face size=" + attributeInfos.length + ", face id=" + i + ", age: " + age + ", gender: " + sex + "\n";
                }
            } else {
                attr = "未检测到人脸";
                mMainHandler.obtainMessage(MSG_UPDATE_UI, "").sendToTarget();
            }
            Log.e(TAG, attr);
            mMainHandler.obtainMessage(MSG_UPDATE_UI, attr).sendToTarget();
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if(mMainHandler != null) {
            mMainHandler.removeCallbacksAndMessages(null);
        }

        if(mImoModel != null) {
            mImoModel.destroy();
            mImoModel = null;
        }
    }
}
