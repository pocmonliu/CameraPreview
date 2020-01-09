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

    private TextView tv_face_attr;
    private  SurfaceView surface_view;
    private Camera mCamera;
    private boolean isPreview = false;

    private HandlerThread mCameraHandlerThread;
    private Handler mCameraHandler;
    private HandlerThread mImoHandlerThread;
    private Handler mImoHandler;
    

    private MainHandler mMainHandler;
    private ImoFaceTrackerDetector mImoFaceTrackerDetector;
    private ImoFaceAttribute mImoFaceAttribute;
    private boolean mDetectionRunning = false;

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
        // 无title
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        // 全屏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);

        surface_view = findViewById(R.id.surface_view);
        tv_face_attr = findViewById(R.id.tv_face_attr);

        mMainHandler = new MainHandler(this);

        mCameraHandlerThread = new HandlerThread("camera-thread");
        mCameraHandlerThread.start();
        mCameraHandler = new Handler(mCameraHandlerThread.getLooper());

        mImoHandlerThread = new HandlerThread("imo-thread");
        mImoHandlerThread.start();
        mImoHandler = new Handler(mImoHandlerThread.getLooper());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_CODE);
        } else {
            init();
        }

        openSurfaceView();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                init();
            } else {
                finish();
            }
        }
    }

    private void init() {
        ImoSDK.init("925E2350380866C8", null, new ImoSDK.OnInitListener() {
            @Override
            public void onInitSuccess() {
                Log.d(TAG, "ImoSDK初始化成功！");
            }

            @Override
            public void onInitError(int i) {
                Log.d(TAG, "errorCode: " + i);
                if(i == 0) {
                    Log.e(TAG, "ImoSDK初始化失败！");
                }
            }
        });

        int errorCode;

        mImoFaceTrackerDetector = new ImoFaceTrackerDetector();
        errorCode = mImoFaceTrackerDetector.init();
        if(errorCode != ImoErrorCode.IMO_API_RET_SUCCESS) {
            Log.e(TAG, "imoFaceTrackerDetector.init失败！！！errorCode=" + errorCode);
        }

        mImoFaceAttribute = new ImoFaceAttribute();
        errorCode = mImoFaceAttribute.init();
        if(errorCode != ImoErrorCode.IMO_API_RET_SUCCESS) {
            Log.e(TAG, "imoFaceAttribute.init失败！！！errorCode=" + errorCode);
        }
    }

    /**
     * 把摄像头的图像显示到SurfaceView
     */
    private void openSurfaceView() {
        // 获得 SurfaceHolder 对象
        SurfaceHolder mSurfaceHolder = surface_view.getHolder();
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
                if(isPreview){//正在预览
                    mCamera.stopPreview();
                    mCamera.release();
                }
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
            parameters.setJpegQuality(60);//设置照片的质量
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
                    Log.v(TAG, "=>current thread name=" + Thread.currentThread().getName()
                            + ", PreviewFormat=" + camera.getParameters().getPreviewFormat()
                            + ", PreviewSize="+ camera.getParameters().getPreviewSize().width + "*" + camera.getParameters().getPreviewSize().height
                            + ", PreviewFrameLength="+ data.length);

                    if(data != null && mImoFaceTrackerDetector != null && mImoFaceAttribute != null) {
                        updateFrame(data, CAMERA_PREVIEW_WIDTH, CAMERA_PREVIEW_HEIGHT);
                    }
                }
            });
            isPreview = true;//设置是否预览参数为真
        } catch (IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    public void updateFrame(final byte[] data, final int width, final int height, final ImoImageFormat format, final List<ImoFaceInfo> imoFaceInfos) {
        if(imoFaceInfos != null) {
            final List<ImoFaceInfo> paramFaceInfos = new ArrayList<>();
            for (ImoFaceInfo faceInfo : imoFaceInfos) {
                paramFaceInfos.add(faceInfo.clone());
            }

            if(!mDetectionRunning) {
                mImoHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mDetectionRunning = true;

                        float[][] pointss = PointUtils.getPointFromImoFaceInfo(paramFaceInfos);
                        ImoAttributeInfo[] attributeInfos = mImoFaceAttribute.execBytes(data, width, height, format, pointss);
                        if(attributeInfos != null && attributeInfos.length > 0) {
                            for (int i = 0; i < attributeInfos.length; i++) {
                                String attribute = "=>face size=" + attributeInfos.length + ", face id=" + i + ", age: " + attributeInfos[i].getAge() + ", gender: " + attributeInfos[i].getGender().getImoGender();
                                Log.e(TAG, attribute);
                                // mMainHandler.obtainMessage(MSG_UPDATE_UI, attribute).sendToTarget();
								mIFaceTrackerDetectorListener.onFaceAttribute(attributeInfos[i].getAge(), attributeInfos[i].getGender().getImoGender());
                            }
                        } else {
                            Log.e(TAG, "未检测到人脸");
                        }

                        mDetectionRunning = false;
                    }
                });
            }
        }
    }

    public void updateFrame(final byte[] data, final int width, final int height) {
        ImoImageFormat format = ImoImageFormat.IMO_IMAGE_NV21;
        ImoImageOrientation orientation = ImoImageOrientation.IMO_IMAGE_LEFT;
        mImoFaceTrackerDetector.setModel(ImoFaceTrackerDetector.Model.IMO_FACE_TRACKER_DETECTOR_MODEL_VIDEO);
        List<ImoFaceInfo> faceInfos = mImoFaceTrackerDetector.execBytes(data, width, height, format, orientation);

        updateFrame(data, width, height, format, faceInfos);
	}

    /**
     * 设置 摄像头的角度
     *
     * @param activity 上下文
     * @param cameraId 摄像头ID（假如手机有N个摄像头，cameraId 的值 就是 0 ~ N-1）
     * @param camera   摄像头对象
     */
    public static void setCameraDisplayOrientation(Activity activity,
                                                   int cameraId, Camera camera) {

        Camera.CameraInfo info = new Camera.CameraInfo();
        //获取摄像头信息
        Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        //获取摄像头当前的角度
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        Log.d(TAG, "info.facing =   " + info.facing + ", cameraCount = " + Camera.getNumberOfCameras());
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            //前置摄像头
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360; // compensate the mirror
        } else {
            // back-facing  后置摄像头
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
		ImoSDK.destroy();
        if(mImoFaceTrackerDetector != null) {
            mImoFaceTrackerDetector.destroy();
        }
        if(mImoFaceAttribute != null) {
            mImoFaceAttribute.destroy();
        }
        if(mMainHandler != null) {
            mMainHandler.removeCallbacksAndMessages(null);
        }
        if(mCameraHandlerThread != null) {
            mCameraHandlerThread.quit();
        }
        if(mImoHandlerThread != null) {
            mImoHandlerThread.quit();
        }
    }

    private IFaceTrackerDetectorListener mIFaceTrackerDetectorListener = new IFaceTrackerDetectorListener() {
        @Override
        public void onFaceAttribute(int age, ImoGender gender) {
            String attr = "age: " + age + ", gender: " + gender;
            Log.e(TAG, attr);
//            mMainHandler.obtainMessage(MSG_UPDATE_UI, attr).sendToTarget();
        }
    };

    public interface IFaceTrackerDetectorListener {
        void onFaceAttribute(int age, ImoGender gender);
    }
}
