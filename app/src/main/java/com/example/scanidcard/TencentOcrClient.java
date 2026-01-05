package com.example.scanidcard;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 调用腾讯云 OCR（身份证识别）。
 * Endpoint: ocr.tencentcloudapi.com
 * Action: IDCardOCR
 * Version: 2018-11-19
 */
public class TencentOcrClient {

    private static final String HOST = "ocr.tencentcloudapi.com";
    private static final String ENDPOINT = "https://" + HOST;
    private static final String SERVICE = "ocr";
    private static final String ACTION_ID_CARD_OCR = "IDCardOCR";
    private static final String VERSION = "2018-11-19";
    private static final String REGION = "ap-guangzhou"; // 可按需修改
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient http = new OkHttpClient();
    private final String secretId;
    private final String secretKey;

    public TencentOcrClient(String secretId, String secretKey) {
        this.secretId = secretId;
        this.secretKey = secretKey;
    }

    public String idCardOcr(String jsonBody) throws Exception {
        long ts = System.currentTimeMillis() / 1000L;

        TencentV3Signer signer = new TencentV3Signer(secretId, secretKey, SERVICE, HOST);
        String authorization = signer.buildAuthorization(jsonBody, ts);

        Request req = new Request.Builder()
                .url(ENDPOINT)
                .post(RequestBody.create(jsonBody, JSON))
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .addHeader("Host", HOST)
                .addHeader("Authorization", authorization)
                .addHeader("X-TC-Action", ACTION_ID_CARD_OCR)
                .addHeader("X-TC-Version", VERSION)
                .addHeader("X-TC-Timestamp", String.valueOf(ts))
                .addHeader("X-TC-Region", REGION)
                .build();

        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                String body = resp.body() != null ? resp.body().string() : "";
                throw new IOException("HTTP " + resp.code() + ": " + body);
            }
            return resp.body() != null ? resp.body().string() : "";
        }
    }

    /**
     * 将腾讯云返回的 JSON 提取出关键字段，并格式化展示。
     */
    public static String prettyResultFromResponse(String respJson) throws Exception {
        if (respJson == null || respJson.trim().isEmpty()) return "空响应";
        JSONObject root = new JSONObject(respJson);

        if (!root.has("Response")) return respJson;
        JSONObject r = root.getJSONObject("Response");

        StringBuilder sb = new StringBuilder();
        if (r.has("RequestId")) sb.append("RequestId: ").append(r.getString("RequestId")).append("\n\n");

        // 正面字段（Name/Sex/Nation/Birth/Address/IdNum）
        appendIfPresent(sb, r, "Name", "姓名");
        appendIfPresent(sb, r, "Sex", "性别");
        appendIfPresent(sb, r, "Nation", "民族");
        appendIfPresent(sb, r, "Birth", "出生");
        appendIfPresent(sb, r, "Address", "住址");
        appendIfPresent(sb, r, "IdNum", "身份证号");

        // 反面字段（Authority/ValidDate）
        appendIfPresent(sb, r, "Authority", "签发机关");
        appendIfPresent(sb, r, "ValidDate", "有效期限");

        // 若字段都没有，直接返回原文（可能是错误）
        if (sb.toString().trim().isEmpty()) return respJson;

        // 若有错误码/信息
        if (r.has("Error")) {
            JSONObject err = r.getJSONObject("Error");
            sb.append("\n错误：").append(err.optString("Code")).append(" - ").append(err.optString("Message"));
        }
        return sb.toString();
    }

    private static void appendIfPresent(StringBuilder sb, JSONObject obj, String key, String label) {
        if (obj.has(key) && !obj.isNull(key)) {
            String v = obj.optString(key, "");
            if (v != null && !v.trim().isEmpty()) {
                sb.append(label).append("：").append(v).append("\n");
            }
        }
    }
}
