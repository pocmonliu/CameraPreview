package com.ys.yoosir.camerapreview;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.aimall.core.ImoSDK;
import com.aimall.core.define.ImoImageFormat;
import com.aimall.core.define.ImoImageOrientation;
import com.aimall.sdk.faceattribute.ImoFaceAttribute;
import com.aimall.sdk.faceattribute.bean.ImoAttributeInfo;
import com.aimall.sdk.facedetector.ImoFaceDetector;
import com.aimall.sdk.facedetector.bean.ImoFaceDetectInfo;
import com.aimall.sdk.facegender.bean.ImoGender;
import com.aimall.sdk.trackerdetector.ImoFaceTrackerDetector;
import com.aimall.sdk.trackerdetector.bean.ImoFaceInfo;
import com.aimall.sdk.trackerdetector.utils.PointUtils;

import javax.net.ssl.SNIHostName;

public class MainActivity extends Activity/* extends AppCompatActivity */{

    private static final String TAG = "MyCameraPreView";

    private static final int MSG_UPDATE_UI = 1;           //更新界面

    private Camera camera;
    private boolean isPreview = false;
    private  SurfaceView mSurfaceView;
    private TextView tv_info;

    private HandlerThread mHandlerThread;
    private Handler handler;

    private int num = 0;
    private ImoFaceAttribute imoFaceAttribute;
    private boolean detectionRunning = false;
    private HandlerThread faceThread;
    private Handler faceAttrHandler;
    private Object cacheLock = new Object();

    private MainHandler mHandler;

    private ImoFaceTrackerDetector mImoFaceTrackerDetector;

    public void updateInfo(String text) {
        tv_info.setText(text);
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
                        activity.updateInfo((String) msg.obj);
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

        mSurfaceView = findViewById(R.id.surface_view);
        tv_info = findViewById(R.id.tv_info);

        ToastUtil.context = MainActivity.this;

        mHandler = new MainHandler(this);

        mHandlerThread = new HandlerThread("camera-thread");
        mHandlerThread.start();
        handler = new Handler(mHandlerThread.getLooper());

        faceThread = new HandlerThread("Work Thread");
        faceThread.start();
        faceAttrHandler = new Handler(faceThread.getLooper());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_CODE);
                }
                return;
            }

            ImoSDK.init("925E2350380866C8", null, new ImoSDK.OnInitListener() {
                @Override
                public void onInitSuccess() {
                    Log.e(TAG, "ImoSDK初始化成功！");

                    mImoFaceTrackerDetector = new ImoFaceTrackerDetector();
                    mImoFaceTrackerDetector.init();
                }

                @Override
                public void onInitError(int i) {
                    Log.d(TAG, "errorCode: " + i);
                    if(i == 0) {
                        Log.e(TAG, "ImoSDK初始化失败！");
                    }
                }
            });

            imoFaceAttribute = new ImoFaceAttribute();
            imoFaceAttribute.init();

            openSurfaceView();
        }
    }

    private void openCamera(SurfaceHolder surfaceHolder) {
        try {
            // Camera,open() 默认返回的后置摄像头信息
            camera = Camera.open();//打开硬件摄像头，这里导包得时候一定要注意是android.hardware.Camera
            //此处也可以设置摄像头参数
            Log.d(TAG, "Camera.open()");

            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);//得到窗口管理器
            Display display  = wm.getDefaultDisplay();//得到当前屏幕
            Log.d(TAG, "display.getWidth() = " + display.getWidth() + ", display.getHeight() = " + display.getHeight());
            Camera.Parameters parameters = camera.getParameters();//得到摄像头的参数

            List<Size> pictureSizes = parameters.getSupportedPictureSizes();
            int length = pictureSizes.size();
            Log.e(TAG, "SupportedPictureSizes, length=" + length);
            for (int i = 0; i < length; i++) {
                Log.e(TAG,"SupportedPictureSizes : " + pictureSizes.get(i).width + "x" + pictureSizes.get(i).height);
            }

            Log.d(TAG, "\n");

            List<Size> previewSizes = parameters.getSupportedPreviewSizes();
            length = previewSizes.size();
            Log.e(TAG, "SupportedPictureSizes, length=" + length);
            for (int i = 0; i < length; i++) {
                Log.e(TAG,"SupportedPreviewSizes : " + previewSizes.get(i).width + "x" + previewSizes.get(i).height);
            }

            parameters.setPictureFormat(ImageFormat.JPEG);//设置照片的格式PixelFormat.RGB_888
            parameters.setJpegQuality(60);//设置照片的质量
//               parameters.setPictureSize(display.getHeight(), display.getWidth());//设置照片的大小，默认是和     屏幕一样大
            //设置大小和方向等参数
//                 parameters.setPictureSize(display.getHeight(), display.getHeight());
//               parameters.setPictureSize(320, 240);
//               mSurfaceView.setLayoutParams(new RelativeLayout.LayoutParams( 720, 480));
            Log.d(TAG, "surfaceview_width=" + mSurfaceView.getWidth() + ", surfaceview_height=" + mSurfaceView.getHeight());

            //自动递归选择最优摄像头支持分辨率
            int width = -1;
            int height = -1;
            for(Size size : previewSizes){
                if(size.width > width){
                    width = size.width;
                    height = size.height;
                }
            }

            Log.d(TAG, "width=" + width + ", height=" + height);

            parameters.setPreviewSize(1280, 720);

            camera.setParameters(parameters);

            //设置角度，此处 CameraId 我默认 为 0 （后置）
            //CameraId 也可以 通过 参考 Camera.open() 源码 方法获取
            //setCameraDisplayOrientation(MainActivity.this,0,camera);

            camera.setPreviewDisplay(surfaceHolder);//通过SurfaceView显示取景画面
            camera.setDisplayOrientation(90);
            camera.startPreview();//开始预览
            camera.setPreviewCallback(new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] data, Camera camera) {
                    Log.d(TAG, "=>" + camera.getParameters().getPreviewFormat()
                            + ", "+ camera.getParameters().getPreviewSize().width + "*" + camera.getParameters().getPreviewSize().height
                            + ", "+ data.length + ", name=" + Thread.currentThread().getName());

                    if(mImoFaceTrackerDetector != null) {
                        Log.d(TAG, "111111");
                        updateFrame(data, 1280, 720, 90);
                    }
                }
            });
            isPreview = true;//设置是否预览参数为真
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        }
    }

    private String facesInfo = "";

    public List<ClientFaceInfo> updateFrame(final byte[] data, final int width, final int height, final int imageRoate) {
        mImoFaceTrackerDetector.setModel(ImoFaceTrackerDetector.Model.IMO_FACE_TRACKER_DETECTOR_MODEL_VIDEO);
        List<ImoFaceInfo> faceInfos = mImoFaceTrackerDetector.execBytes(data, width, height, ImoImageFormat.IMO_IMAGE_NV21, ImoImageOrientation.IMO_IMAGE_LEFT);
        Log.e(TAG, "===>faceInfos.size=" + faceInfos.size());
        if(faceInfos != null) {
            Log.d(TAG, "222222");
            final List<ImoFaceInfo> paramFaceInfos = new ArrayList<>();
            for (ImoFaceInfo faceInfo : faceInfos) {
                paramFaceInfos.add(faceInfo.clone());
            }
            Log.e(TAG, "===>paramFaceInfos.size=" + paramFaceInfos.size());

            if(!detectionRunning) {
                Log.d(TAG, "333333");
                faceAttrHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        detectionRunning = true;
                        float[][] pointss = PointUtils.getPointFromImoFaceInfo(paramFaceInfos);
                        ImoAttributeInfo[] attributeInfos = imoFaceAttribute.execBytes(data, width, height, ImoImageFormat.IMO_IMAGE_NV21, pointss);

                        String infos = "";
                        if(paramFaceInfos != null && paramFaceInfos.size() > 0) {

                            if(attributeInfos != null && attributeInfos.length > 0) {
                                for (ImoAttributeInfo info: attributeInfos) {
                                    infos += "age: " + info.getAge() + ", gender: " + info.getGender().getImoGender() + "\n";
                                }
                            }
                        } else {
                            infos = "未检测到人脸";
                        }

                        if(!TextUtils.isEmpty(infos) && !infos.equalsIgnoreCase(facesInfo)) {
                            facesInfo = infos;
                            //ToastUtil.showCenterToast(facesInfo);
                            Log.e(TAG, "facesInfo=>" + facesInfo);
                            mHandler.obtainMessage(MSG_UPDATE_UI, facesInfo).sendToTarget();
                        }

                        Log.d(TAG, "444444");
//                        List<ClientFaceInfo> clientFaceInfos = FaceAttrUtils.generateClientFaceInfos(paramFaceInfos, attributeInfos);
//                        Log.e(TAG, "===>clientFaceInfos.size=" + clientFaceInfos.size());
                        //做连续帧特殊逻辑
//                        if (clientFaceInfos != null) {
//                            for (ClientFaceInfo userAttrInfo : clientFaceInfos) {
//                                doAvgResult(data, width, height, imageRoate, userAttrInfo);
//                            }
//                        }

//                        synchronized (cacheLock) {
//                            cacheClientFaceInfos = clientFaceInfos;
//                        }
                        detectionRunning = false;
                    }
                });
            }
        }

        return new ArrayList<>();
    }

    private static final int LIMIT_COUNT = 21;

    private Map<Integer, UserGenderBean> userGenderScore = new HashMap<>();

    private class UserGenderBean {
        int count;
        int menTime;
        List<Integer> age = new ArrayList<>();
    }

    private List<ClientFaceInfo> cacheClientFaceInfos;

//    private OnPerfectResultCallBack onPerfectResultCallBack;

    private void doAvgResult(byte[] data, int width, int height, int imageRoate, ClientFaceInfo faceInfo) {
        ImoFaceInfo imoFaceInfo = faceInfo.getImoFaceInfo();
        UserGenderBean userGenderBean = userGenderScore.get(imoFaceInfo.getId());
        // gender开启时优化连续帧结果
        if (faceInfo.getGender() != null) {
            Log.e(TAG, "===>faceInfo.getGender() != null");
            if (userGenderBean == null) {
                userGenderBean = new UserGenderBean();
                userGenderScore.put(imoFaceInfo.getId(), userGenderBean);
            }
            if (userGenderBean.count <= LIMIT_COUNT) {
                userGenderBean.count++;
                userGenderBean.age.add(faceInfo.getAge());
                if (faceInfo.getGender().getImoGender() == ImoGender.IMO_MALE) {
                    userGenderBean.menTime++;
                }
            }
        }
        if (userGenderBean != null) {
            Log.e(TAG, "===>userGenderBean != null");
            if (userGenderBean.count - userGenderBean.menTime > userGenderBean.menTime) {
                faceInfo.getGender().setImoGender(ImoGender.IMO_FEMALE);
            } else {
                faceInfo.getGender().setImoGender(ImoGender.IMO_MALE);
            }
            int count = 0;
            int max = userGenderBean.age.get(0);
            int min = max;
            for (Integer integer : userGenderBean.age) {
                count += integer;
                max = integer > max ? integer : max;
                min = integer < min ? integer : min;
            }
            //去掉最大，最小值
            if (userGenderBean.age.size() > 2) {
                count -= min;
                count -= max;
                faceInfo.setAge(count / (userGenderBean.age.size() - 2));
            } else {
                faceInfo.setAge(count / userGenderBean.age.size());
            }
        }

        //如果次数大于LIMIT_COUNT则表示结果稳定
        if (/*onPerfectResultCallBack != null && */userGenderBean != null && userGenderBean.count == LIMIT_COUNT) {
            //onPerfectResultCallBack.perfectCallBack(data, width, height, imageRoate, faceInfo);
            String str = faceInfo.getAge() + "性别" + FaceAttrUtils.getGenderString(faceInfo.getGender().getImoGender());
            ToastUtil.showCenterToast(str);
            mHandler.obtainMessage(MSG_UPDATE_UI, str).sendToTarget();
        }

    }

    private void delAttrResultCache(final byte[] data, final int width, final int height, final int imageRoate, List<ClientFaceInfo> faceInfos) {


        //用户丢失后回调
//        if (cacheClientFaceInfos != null && onPerfectResultCallBack != null) {
//            for (ClientFaceInfo cacheFaceInfo : cacheClientFaceInfos) {
//                boolean isRemove = true;
//                for (ClientFaceInfo faceInfo : faceInfos) {
//                    if (faceInfo.getImoFaceInfo().getId() == cacheFaceInfo.getImoFaceInfo().getId()) {
//                        isRemove = false;
//                        break;
//                    }
//                }
//                if (isRemove) {
//                    UserGenderBean userGenderBean = userGenderScore.get(cacheFaceInfo.getImoFaceInfo().getId());
//                    if (userGenderBean.count < LIMIT_COUNT) {
//                        onPerfectResultCallBack.perfectCallBack(data, width, height, imageRoate, cacheFaceInfo);
//                    }
//                    userGenderScore.remove(cacheFaceInfo.getImoFaceInfo().getId());
//                }
//            }
//        }

    }

    /**
     * 把摄像头的图像显示到SurfaceView
     */
    private void openSurfaceView() {
        // 获得 SurfaceHolder 对象
        SurfaceHolder mSurfaceHolder = mSurfaceView.getHolder();

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

    private static final int REQUEST_CAMERA_CODE = 0x100;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                recreate();
            } else {
                finish();
            }
        }
    }

    private SurfaceHolder.Callback mSurfaceCallback = new SurfaceHolder.Callback() {

        /**
         *  在 Surface 首次创建时被立即调用：活得叫焦点时。一般在这里开启画图的线程
         * @param surfaceHolder 持有当前 Surface 的 SurfaceHolder 对象
         */
        @Override
        public void surfaceCreated(final SurfaceHolder surfaceHolder) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    ToastUtil.showCenterToast("开启摄像头！");
                    openCamera(surfaceHolder);
                }
            });
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

        }

        /**
         *  在 Surface 被销毁时立即调用：失去焦点时。一般在这里将画图的线程停止销毁
         * @param surfaceHolder 持有当前 Surface 的 SurfaceHolder 对象
         */
        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
            if(camera != null){
                if(isPreview){//正在预览
                    camera.stopPreview();
                    camera.release();
                }
            }
        }
    };

    /**
     * 设置 摄像头的角度
     *
     * @param activity 上下文
     * @param cameraId 摄像头ID（假如手机有N个摄像头，cameraId 的值 就是 0 ~ N-1）
     * @param camera   摄像头对象
     */
    public static void setCameraDisplayOrientation(Activity activity,
                                                   int cameraId, android.hardware.Camera camera) {

        Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        //获取摄像头信息
        android.hardware.Camera.getCameraInfo(cameraId, info);
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
        mImoFaceTrackerDetector.destroy();
        imoFaceAttribute.destroy();
        mHandlerThread.quit();
    }
}
