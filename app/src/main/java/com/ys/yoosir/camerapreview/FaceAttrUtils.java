package com.ys.yoosir.camerapreview;


import com.aimall.sdk.faceattribute.bean.ImoAttributeInfo;
import com.aimall.sdk.facebeauty.bean.ImoBeautyInfo;
import com.aimall.sdk.faceemotion.bean.ImoEmotion;
import com.aimall.sdk.facegender.bean.ImoGender;
import com.aimall.sdk.trackerdetector.bean.ImoFaceInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * ================================================
 * 作    者：aguai（吴红斌）Github地址：https://github.com/aguai1
 * 版    本：1.0
 * 创建日期：2018/7/9
 * 描    述：
 * ================================================
 */
public class FaceAttrUtils {

    public static List<ClientFaceInfo> generateClientFaceInfos(List<ImoFaceInfo> imoFaceInfos,
															   ImoAttributeInfo[] attributeInfos) {
        List<ClientFaceInfo> clientFaceInfos = new ArrayList<>();
        for (int i = 0; i < imoFaceInfos.size(); ++i) {
            ClientFaceInfo clientFaceInfo = new ClientFaceInfo();
            clientFaceInfo.setImoFaceInfo(imoFaceInfos.get(i));
            if(null != attributeInfos && null != attributeInfos[i]) {
                clientFaceInfo.setAge(attributeInfos[i].getAge());
                clientFaceInfo.setGender(attributeInfos[i].getGender());
                clientFaceInfo.setEmotion(attributeInfos[i].getEmotion());
                clientFaceInfo.setBeauty(attributeInfos[i].getBeauty());
            }
            clientFaceInfos.add(clientFaceInfo);
        }
        return clientFaceInfos;
    }

    public static String getGenderString(ImoGender gender) {
        String genderStr = "未知";
        if(gender != null) {
            switch (gender) {
                case IMO_MALE:
                    genderStr = "男";
                    break;
                case IMO_FEMALE:
                    genderStr = "女";
                    break;
            }
        }
        return genderStr;
    }

    public static String getEmotionString(ImoEmotion emotion) {
        final String[] emotions = new String[]{
                "正常",
                "开心",
                "难过",
                "惊喜",
                "害怕",
                "厌恶",
                "愤怒",
                "鄙视"
        };
        String emotionString = "" + emotion + "-unknow";
        if (emotion != null && emotion.ordinal() >= 0 && emotion.ordinal() < emotions.length) {
            emotionString = "" + emotion.ordinal() + "-" + emotions[emotion.ordinal()];
        }
        return emotionString;
    }

    public static String getBeautyString(ImoBeautyInfo beauty) {
        String beautyStr = "";
        if(beauty != null) {
            beautyStr = beauty.getMale() + "/" + beauty.getFemale();
        }
        return beautyStr;
    }
}
