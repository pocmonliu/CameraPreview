package com.ys.yoosir.camerapreview;

import android.content.Context;
import android.hardware.Camera;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.content.ContextCompat;
import android.util.Log;

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
