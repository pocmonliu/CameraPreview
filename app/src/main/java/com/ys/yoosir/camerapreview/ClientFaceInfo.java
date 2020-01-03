package com.ys.yoosir.camerapreview;

import com.aimall.sdk.facebeauty.bean.ImoBeautyInfo;
import com.aimall.sdk.faceemotion.bean.ImoEmotionInfo;
import com.aimall.sdk.facegender.bean.ImoGenderInfo;
import com.aimall.sdk.trackerdetector.bean.ImoFaceInfo;

public class ClientFaceInfo {

    private ImoFaceInfo imoFaceInfo;
    private int age;
    private ImoGenderInfo gender;
    private ImoEmotionInfo emotion;
    private ImoBeautyInfo beauty;
    private Object tag;

    public ImoFaceInfo getImoFaceInfo() {
        return imoFaceInfo;
    }

    public void setImoFaceInfo(ImoFaceInfo imoFaceInfo) {
        this.imoFaceInfo = imoFaceInfo;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public ImoGenderInfo getGender() {
        return gender;
    }

    public void setGender(ImoGenderInfo gender) {
        this.gender = gender;
    }

    public ImoEmotionInfo getEmotion() {
        return emotion;
    }

    public void setEmotion(ImoEmotionInfo emotion) {
        this.emotion = emotion;
    }

    public ImoBeautyInfo getBeauty() {
        return beauty;
    }

    public void setBeauty(ImoBeautyInfo beauty) {
        this.beauty = beauty;
    }

    public Object getTag() {
        return tag;
    }

    public void setTag(Object tag) {
        this.tag = tag;
    }
}
