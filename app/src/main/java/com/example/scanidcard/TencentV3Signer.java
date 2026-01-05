package com.example.scanidcard;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 腾讯云 API v3（TC3-HMAC-SHA256）签名实现。
 *
 * PPT 对应步骤：
 * 1) 拼接规范请求串 CanonicalRequest
 * 2) 拼接待签名字符串 StringToSign
 * 3) 计算签名 Signature
 * 4) 拼接 Authorization
 */
public class TencentV3Signer {

    private static final String ALGORITHM = "TC3-HMAC-SHA256";
    private static final String SIGNED_HEADERS = "content-type;host";
    private static final String CANONICAL_URI = "/";
    private static final String CANONICAL_QUERY_STRING = "";
    private static final String TERMINATOR = "tc3_request";

    private final String secretId;
    private final String secretKey;
    private final String service;
    private final String host;

    public TencentV3Signer(String secretId, String secretKey, String service, String host) {
        this.secretId = secretId;
        this.secretKey = secretKey;
        this.service = service;
        this.host = host;
    }

    public String buildAuthorization(String payloadJson, long timestampSeconds) throws Exception {
        String date = toDate(timestampSeconds);

        // ************* 步骤 1：拼接规范请求串 *************
        String canonicalHeaders = "content-type:application/json; charset=utf-8\n" +
                "host:" + host + "\n";

        String hashedPayload = sha256Hex(payloadJson);
        String canonicalRequest = "POST\n" +
                CANONICAL_URI + "\n" +
                CANONICAL_QUERY_STRING + "\n" +
                canonicalHeaders + "\n" +
                SIGNED_HEADERS + "\n" +
                hashedPayload;

        // ************* 步骤 2：拼接待签名字符串 *************
        String credentialScope = date + "/" + service + "/" + TERMINATOR;
        String hashedCanonicalRequest = sha256Hex(canonicalRequest);
        String stringToSign = ALGORITHM + "\n" +
                timestampSeconds + "\n" +
                credentialScope + "\n" +
                hashedCanonicalRequest;

        // ************* 步骤 3：计算签名 *************
        byte[] secretDate = hmacSha256(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmacSha256(secretDate, service);
        byte[] secretSigning = hmacSha256(secretService, TERMINATOR);
        String signature = bytesToHex(hmacSha256(secretSigning, stringToSign));

        // ************* 步骤 4：拼接 Authorization *************
        return ALGORITHM + " " +
                "Credential=" + secretId + "/" + credentialScope + ", " +
                "SignedHeaders=" + SIGNED_HEADERS + ", " +
                "Signature=" + signature;
    }

    private static String toDate(long tsSeconds) {
        Date d = new Date(tsSeconds * 1000L);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(d);
    }

    private static String sha256Hex(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(digest);
    }

    private static byte[] hmacSha256(byte[] key, String msg) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec spec = new SecretKeySpec(key, "HmacSHA256");
        mac.init(spec);
        return mac.doFinal(msg.getBytes(StandardCharsets.UTF_8));
    }

    private static String bytesToHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte v : b) sb.append(String.format("%02x", v & 0xff));
        return sb.toString();
    }
}
