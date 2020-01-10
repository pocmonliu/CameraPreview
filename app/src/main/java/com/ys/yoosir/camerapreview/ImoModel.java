package com.ys.yoosir.camerapreview;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.SurfaceHolder;

import com.aimall.core.ImoErrorCode;
import com.aimall.core.ImoSDK;
import com.aimall.core.define.ImoImageFormat;
import com.aimall.core.define.ImoImageOrientation;
import com.aimall.sdk.faceattribute.ImoFaceAttribute;
import com.aimall.sdk.faceattribute.bean.ImoAttributeInfo;
import com.aimall.sdk.trackerdetector.ImoFaceTrackerDetector;
import com.aimall.sdk.trackerdetector.bean.ImoFaceInfo;
import com.aimall.sdk.trackerdetector.utils.PointUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * author: ivanliu
 * time: 2020/1/8 18:28
 * desc:
 */
public class ImoModel {
	private static final String TAG = ImoModel.class.getSimpleName();

	private Context mContext;

	private static final int CAMERA_PREVIEW_WIDTH = 1280;
	private static final int CAMERA_PREVIEW_HEIGHT = 720;
	private static final int CAMERA_DISPLAY_ORIENTATION = 90;

	private Camera mCamera;
	private HandlerThread mCameraHandlerThread;
	private Handler mCameraHandler;
	private boolean isPreviewing = false;

	private HandlerThread mImoHandlerThread;
	private Handler mImoHandler;

	private ImoFaceTrackerDetector mImoFaceTrackerDetector;
	private ImoFaceAttribute mImoFaceAttribute;
	private boolean mDetectionRunning = false;

	private IFaceTrackerDetectorListener mIFaceTrackerDetectorListener;

	public ImoModel(Context context) {
		mContext = context;
	}

	public void init() {
		mCameraHandlerThread = new HandlerThread("camera-thread");
		mCameraHandlerThread.start();
		mCameraHandler = new Handler(mCameraHandlerThread.getLooper());

		mImoHandlerThread = new HandlerThread("imo-thread");
		mImoHandlerThread.start();
		mImoHandler = new Handler(mImoHandlerThread.getLooper());

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
	 * 打开摄像头
	 * @param surfaceHolder
	 */
	public void openCamera(final SurfaceHolder surfaceHolder) {
		if(mCamera != null) {
			Log.w(TAG, "camera is opened !");
			return;
		}

		mCameraHandler.post(new Runnable() {
			@Override
			public void run() {
				try {
					// Camera,open() 默认返回的后置摄像头信息
					mCamera = Camera.open();//打开硬件摄像头，这里导包得时候一定要注意是android.hardware.Camera
					//此处也可以设置摄像头参数
					Log.d(TAG, "Camera.open()");

//					WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);//得到窗口管理器
//					Display display  = wm.getDefaultDisplay();//得到当前屏幕
//					Log.d(TAG, "display.getWidth() = " + display.getWidth() + ", display.getHeight() = " + display.getHeight());

					Camera.Parameters parameters = mCamera.getParameters();//得到摄像头的参数
//
//					List<Size> pictureSizes = parameters.getSupportedPictureSizes();
//					int length = pictureSizes.size();
//					Log.e(TAG, "SupportedPictureSizes, length=" + length);
//					for (int i = 0; i < length; i++) {
//						Log.e(TAG,"SupportedPictureSizes : " + pictureSizes.get(i).width + "x" + pictureSizes.get(i).height);
//					}
//					List<Size> previewSizes = parameters.getSupportedPreviewSizes();
//					length = previewSizes.size();
//					Log.e(TAG, "\nSupportedPictureSizes, length=" + length);
//					for (int i = 0; i < length; i++) {
//						Log.e(TAG,"SupportedPreviewSizes : " + previewSizes.get(i).width + "x" + previewSizes.get(i).height);
//					}

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
					isPreviewing = true;
					mCamera.setPreviewCallback(new Camera.PreviewCallback() {
						@Override
						public void onPreviewFrame(byte[] data, Camera camera) {
							if(isPreviewing && data != null) {
								Camera.Parameters parameters = camera.getParameters();
								Log.v(TAG, "=>current thread name=" + Thread.currentThread().getName()
										+ ", PreviewFormat=" + parameters.getPreviewFormat()
										+ ", PreviewSize="+ parameters.getPreviewSize().width + "*" + parameters.getPreviewSize().height
										+ ", PreviewFrameLength="+ data.length);

								updateFrame(data, CAMERA_PREVIEW_WIDTH, CAMERA_PREVIEW_HEIGHT);
							}
						}
					});
				} catch (IOException e) {
					Log.e(TAG, Log.getStackTraceString(e));
				}
			}
		});
	}

	/**
	 * 关闭摄像头
	 */
	public void closeCamera() {
		mCameraHandler.post(new Runnable() {
			@Override
			public void run() {
				if(mCamera != null){
					isPreviewing = false;
					mCamera.setPreviewCallback(null);
					mCamera.stopPreview();
					mCamera.release();
					mCamera = null;
				}
			}
		});
	}

	private void updateFrame(final byte[] data, final int width, final int height, final ImoImageFormat format, final List<ImoFaceInfo> imoFaceInfos) {
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
						if(mIFaceTrackerDetectorListener != null) {
							mIFaceTrackerDetectorListener.onFaceAttr(attributeInfos);
						}

						mDetectionRunning = false;
					}
				});
			}
		}
	}

	public void updateFrame(final byte[] data, final int width, final int height) {
		if(mImoFaceTrackerDetector != null && mImoFaceAttribute != null) {
			ImoImageFormat format = ImoImageFormat.IMO_IMAGE_NV21;
			ImoImageOrientation orientation = ImoImageOrientation.IMO_IMAGE_LEFT;
			mImoFaceTrackerDetector.setModel(ImoFaceTrackerDetector.Model.IMO_FACE_TRACKER_DETECTOR_MODEL_VIDEO);
			List<ImoFaceInfo> faceInfos = mImoFaceTrackerDetector.execBytes(data, width, height, format, orientation);

			updateFrame(data, width, height, format, faceInfos);
		}
	}

	public void destroy() {
		ImoSDK.destroy();

		if(mImoFaceTrackerDetector != null) {
			mImoFaceTrackerDetector.destroy();
			mImoFaceTrackerDetector = null;
		}

		if(mImoFaceAttribute != null) {
			mImoFaceAttribute.destroy();
			mImoFaceAttribute = null;
		}

		if(mImoHandlerThread != null) {
			mImoHandlerThread.quit();
			mImoHandlerThread = null;
		}

		if(mCameraHandlerThread != null) {
			mCameraHandlerThread.quit();
			mCameraHandlerThread = null;
		}
	}

	public void registerFaceTrackerDetectorListener(IFaceTrackerDetectorListener listener) {
		mIFaceTrackerDetectorListener = listener;
	}

	public void unregisterFaceTrackerDetectorListener() {
		mIFaceTrackerDetectorListener = null;
	}

	public interface IFaceTrackerDetectorListener {
		void onFaceAttr(ImoAttributeInfo[] attributeInfos);
	}
}
