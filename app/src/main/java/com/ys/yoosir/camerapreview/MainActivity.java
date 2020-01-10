package com.ys.yoosir.camerapreview;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import com.aimall.core.ImoErrorCode;
import com.aimall.core.ImoSDK;
import com.aimall.core.define.ImoImageFormat;
import com.aimall.core.define.ImoImageOrientation;
import com.aimall.sdk.faceattribute.ImoFaceAttribute;
import com.aimall.sdk.faceattribute.bean.ImoAttributeInfo;
import com.aimall.sdk.facegender.bean.ImoGender;
import com.aimall.sdk.trackerdetector.ImoFaceTrackerDetector;
import com.aimall.sdk.trackerdetector.bean.ImoFaceInfo;
import com.aimall.sdk.trackerdetector.utils.PointUtils;

public class MainActivity extends FragmentActivity {
    private static final String TAG = "ImoModel";

    private static final int REQUEST_CAMERA_CODE = 0x100;
    private static final int MSG_UPDATE_UI = 1;           //更新界面

    private static final int CAMERA_PREVIEW_WIDTH = 1280;
    private static final int CAMERA_PREVIEW_HEIGHT = 720;
    private static final int CAMERA_DISPLAY_ORIENTATION = 90;
    private Camera mCamera;
    private HandlerThread mCameraHandlerThread;
    private Handler mCameraHandler;

    private TextView tv_face_attr;
    private SurfaceView surface_view;
    private SurfaceHolder mSurfaceHolder;

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

        mCameraHandlerThread = new HandlerThread("camera-thread");
        mCameraHandlerThread.start();
        mCameraHandler = new Handler(mCameraHandlerThread.getLooper());

        mImoModel = new ImoModel(this);
        mImoModel.init();
        mImoModel.registerFaceTrackerDetectorListener(mIFaceTrackerDetectorListener);
    }

    /**
     * 把摄像头的图像显示到SurfaceView
     */
    private void openSurfaceView() {
        // 获得 SurfaceHolder 对象
        mSurfaceHolder = surface_view.getHolder();
        Log.d(TAG, "surface_view: width=" + surface_view.getWidth() + ", height=" + surface_view.getHeight());
        // 设置 Surface 格式
        // 参数： PixelFormat中定义的 int 值 ,详细参见 PixelFormat.java
        mSurfaceHolder.setFormat(PixelFormat.TRANSPARENT);

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
        mSurfaceHolder.addCallback(mSurfaceCallback);
    }

    private SurfaceHolder.Callback mSurfaceCallback = new SurfaceHolder.Callback() {

        /**
         *  在 Surface 首次创建时被立即调用：活得叫焦点时。一般在这里开启画图的线程
         * @param surfaceHolder 持有当前 Surface 的 SurfaceHolder 对象
         */
        @Override
        public void surfaceCreated(final SurfaceHolder surfaceHolder) {
            Log.e(TAG, "===>surfaceCreated()");

			if(mCamera == null) {
				 mCameraHandler.post(new Runnable() {
                	@Override
                	public void run() {
                    	openCamera(surfaceHolder);
                	}
            	 });
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
            if(mCamera != null){
                mCamera.setPreviewCallback(null);
                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
            }
        }
    };

    private void openCamera(SurfaceHolder surfaceHolder) {
        try {
            // Camera,open() 默认返回的后置摄像头信息
            mCamera = Camera.open();//打开硬件摄像头，这里导包得时候一定要注意是android.hardware.Camera
            //此处也可以设置摄像头参数
            Log.d(TAG, "Camera.open()");

//            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);//得到窗口管理器
//            Display display  = wm.getDefaultDisplay();//得到当前屏幕
//            Log.d(TAG, "display.getWidth() = " + display.getWidth() + ", display.getHeight() = " + display.getHeight());
            Camera.Parameters parameters = mCamera.getParameters();//得到摄像头的参数

//            List<Size> pictureSizes = parameters.getSupportedPictureSizes();
//            int length = pictureSizes.size();
//            Log.e(TAG, "SupportedPictureSizes, length=" + length);
//            for (int i = 0; i < length; i++) {
//                Log.e(TAG,"SupportedPictureSizes : " + pictureSizes.get(i).width + "x" + pictureSizes.get(i).height);
//            }
//            List<Size> previewSizes = parameters.getSupportedPreviewSizes();
//            length = previewSizes.size();
//            Log.e(TAG, "\nSupportedPictureSizes, length=" + length);
//            for (int i = 0; i < length; i++) {
//                Log.e(TAG,"SupportedPreviewSizes : " + previewSizes.get(i).width + "x" + previewSizes.get(i).height);
//            }

            parameters.setPictureFormat(ImageFormat.JPEG);//设置照片的格式PixelFormat.RGB_888
            parameters.setJpegQuality(100);//设置照片的质量
            parameters.setPreviewSize(CAMERA_PREVIEW_WIDTH, CAMERA_PREVIEW_HEIGHT);
            mCamera.setParameters(parameters);

            //设置角度，此处 CameraId 我默认 为 0 （后置）
            //CameraId 也可以 通过 参考 Camera.open() 源码 方法获取
            //setCameraDisplayOrientation(this, 0, mCamera);

            mCamera.setDisplayOrientation(CAMERA_DISPLAY_ORIENTATION);
            mCamera.setPreviewDisplay(surfaceHolder);//通过SurfaceView显示取景画面
            mCamera.startPreview();//开始预览
            mCamera.setPreviewCallback(new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] data, Camera camera) {
                    if(mCamera != null && data != null) {
                        Camera.Parameters parameters = camera.getParameters();
                        Log.v(TAG, "=>current thread name=" + Thread.currentThread().getName()
                                + ", PreviewFormat=" + parameters.getPreviewFormat()
                                + ", PreviewSize="+ parameters.getPreviewSize().width + "*" + parameters.getPreviewSize().height
                                + ", PreviewFrameLength="+ data.length);

                        if(mImoModel != null) {
                            mImoModel.updateFrame(data, CAMERA_PREVIEW_WIDTH, CAMERA_PREVIEW_HEIGHT);
                        }
                    }
                }
            });
        } catch (IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    private ImoModel.IFaceTrackerDetectorListener mIFaceTrackerDetectorListener = new ImoModel.IFaceTrackerDetectorListener() {
        @Override
        public void onFaceAttr(ImoAttributeInfo[] attributeInfos) {
            if(attributeInfos != null && attributeInfos.length > 0) {
                for (int i = 0; i < attributeInfos.length; i++) {
                    String attribute = "=>face size=" + attributeInfos.length + ", face id=" + i + ", age: " + attributeInfos[i].getAge() + ", gender: " + attributeInfos[i].getGender().getImoGender();
                    Log.e(TAG, attribute);
                }
            } else {
                Log.e(TAG, "未检测到人脸");
            }
//            mMainHandler.obtainMessage(MSG_UPDATE_UI, attr).sendToTarget();
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if(mMainHandler != null) {
            mMainHandler.removeCallbacksAndMessages(null);
        }

        if(mCameraHandlerThread != null) {
            mCameraHandlerThread.quit();
            mCameraHandlerThread = null;
        }

        if(mImoModel != null) {
            mImoModel.destroy();
            mImoModel = null;
        }
    }
}
