package com.clevertap.android.sdk.ads.model;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import com.clevertap.android.sdk.Constants;
import com.clevertap.android.sdk.ads.AdConstants;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

/**
 * This model class holds the data of an individual ad Unit.
 */
public class CTAdUnit implements Parcelable {

    private String adID;//Ad unit identifier

    private AdConstants.CtAdType adType;//can be (banner/image/video/carousel etc.)

    private String bgColor;

    private String orientation;

    private ArrayList<CTAdUnitContent> adContentItems;

    // Custom Key Value pairs
    private HashMap<String, String> customExtras;

    private JSONObject jsonObject;

    private String error;

    //constructors
    private CTAdUnit(JSONObject jsonObject, String adID, AdConstants.CtAdType adType,
                     String bgColor, String orientation, ArrayList<CTAdUnitContent> contentArray,
                     JSONObject kvObject, String error) {
        this.jsonObject = jsonObject;
        this.adID = adID;
        this.adType = adType;
        this.bgColor = bgColor;
        this.orientation = orientation;
        this.adContentItems = contentArray;
        this.customExtras = getKeyValues(kvObject);
        this.error = error;
    }

    /**
     * static API to convert json to AdUnit
     *
     * @param jsonObject - Ad Unit Item in Json form
     * @return - CTAdUnit
     */
    public static CTAdUnit toAdUnit(JSONObject jsonObject) {
        if (jsonObject != null) {
            //logic to convert jsonobj to item
            try {
                String adID = jsonObject.has("wzrk_id") ? jsonObject.getString("wzrk_id") : "";
                AdConstants.CtAdType adType = jsonObject.has(Constants.KEY_TYPE) ? AdConstants.CtAdType.type(jsonObject.getString(Constants.KEY_TYPE)) : null;

                String bgColor = jsonObject.has(Constants.KEY_BG) ? jsonObject.getString(Constants.KEY_BG) : "";

                String orientation = jsonObject.has("orientation") ? jsonObject.getString("orientation") : "";

                JSONArray contentArray = jsonObject.has("content") ? jsonObject.getJSONArray("content") : null;
                ArrayList<CTAdUnitContent> contentArrayList = new ArrayList<>();
                if (contentArray != null) {
                    for (int i = 0; i < contentArray.length(); i++) {
                        CTAdUnitContent adUnitContent = CTAdUnitContent.toContent(contentArray.getJSONObject(i));
                        contentArrayList.add(adUnitContent);
                    }
                }
                JSONObject customKV = null;
                //custom KV can be added to ad unit of any types, so don't
                if (jsonObject.has("custom_kv")) {
                    customKV = jsonObject.getJSONObject("custom_kv");
                }
                return new CTAdUnit(jsonObject, adID, adType, bgColor, orientation, contentArrayList, customKV, null);
            } catch (Exception e) {
                return new CTAdUnit(null, "", null, null, null, null, null,"Error Creating AdUnit from JSON : " + e.getLocalizedMessage());

            }
        }
        return null;
    }

    public String getAdID() {
        return adID;
    }

    public String getError() {
        return error;
    }

    @SuppressWarnings("unused")
    public HashMap<String, String> getCustomExtras() {
        return customExtras;
    }

    public JSONObject getJsonObject() {
        return jsonObject;
    }

    @SuppressWarnings("unused")
    public String getBgColor() {
        return bgColor;
    }

    public String getOrientation() {
        return orientation;
    }

    @SuppressWarnings("unused")
    public AdConstants.CtAdType getAdType() {
        return adType;
    }

    @SuppressWarnings("unused")
    public ArrayList<CTAdUnitContent> getAdContentItems() {
        return adContentItems;
    }

    /**
     * get the wzrk fields obj to be passed in the data for recording event.
     */
    public JSONObject getWzrkFields() {
        try {
            if (jsonObject != null) {
                Iterator<String> iterator = jsonObject.keys();
                JSONObject wzrkFieldsObj = new JSONObject();
                while (iterator.hasNext()) {
                    String keyName = iterator.next();
                    if (keyName.startsWith(Constants.WZRK_PREFIX)) {
                        wzrkFieldsObj.put(keyName, jsonObject.get(keyName));
                    }
                }
                return wzrkFieldsObj;
            }
        } catch (Exception e) {
            //no op
        }
        return null;
    }

    /**
     * populates the custom key values pairs from json
     *
     * @param kvObj- Custom Key Values
     */
    private HashMap<String, String> getKeyValues(JSONObject kvObj) {
        try {
            if (kvObj != null) {
                Iterator<String> keys = kvObj.keys();
                if (keys != null) {
                    String key, value;
                    HashMap<String, String> hashMap = null;
                    while (keys.hasNext()) {
                        key = keys.next();
                        value = kvObj.getString(key);
                        if (!TextUtils.isEmpty(key)) {
                            if (hashMap == null)
                                hashMap = new HashMap<>();
                            hashMap.put(key, value);
                        }
                    }
                    return hashMap;
                }
            }
        } catch (Exception e) {
            //no op
        }
        return null;
    }

    public static final Creator<CTAdUnit> CREATOR = new Creator<CTAdUnit>() {
        @Override
        public CTAdUnit createFromParcel(Parcel in) {
            return new CTAdUnit(in);
        }

        @Override
        public CTAdUnit[] newArray(int size) {
            return new CTAdUnit[size];
        }
    };

    @SuppressWarnings("unchecked")
    private CTAdUnit(Parcel in) {
        try {
            this.adID = in.readString();
            this.adType = (AdConstants.CtAdType) in.readValue(AdConstants.CtAdType.class.getClassLoader());
            this.bgColor = in.readString();
            this.orientation = in.readString();

            if (in.readByte() == 0x01) {
                adContentItems = new ArrayList<>();
                in.readList(adContentItems, CTAdUnitContent.class.getClassLoader());
            } else {
                adContentItems = null;
            }

            this.customExtras = in.readHashMap(null);
            this.jsonObject = in.readByte() == 0x00 ? null : new JSONObject(in.readString());
            this.error = in.readString();
        } catch (Exception e) {
            error = "Error Creating AdUnit from parcel : " + e.getLocalizedMessage();
        }
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(adID);
        parcel.writeValue(adType);
        parcel.writeString(bgColor);
        parcel.writeString(orientation);

        if (adContentItems == null) {
            parcel.writeByte((byte) (0x00));
        } else {
            parcel.writeByte((byte) (0x01));
            parcel.writeList(adContentItems);
        }

        parcel.writeMap(customExtras);
        if (jsonObject == null) {
            parcel.writeByte((byte) (0x00));
        } else {
            parcel.writeByte((byte) (0x01));
            parcel.writeString(jsonObject.toString());
        }
        parcel.writeString(error);
    }

    @Override
    public int describeContents() {
        return 0;
    }
}