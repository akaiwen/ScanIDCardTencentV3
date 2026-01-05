package com.example.scanidcard;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.example.scanidcard.databinding.ActivityMainBinding;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 实验内容：身份证识别（腾讯云 OCR），按照 PPT 流程：
 * 1) 摄像头拍照 -> 2) 保存图片 -> 3) Base64 -> 4) V3(TC3)签名 -> 5) POST -> 6) Handler 解析 JSON
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    private Uri currentPhotoUri;
    private File currentPhotoFile;
    private String currentCardSide = "FRONT"; // FRONT / BACK

    private final ExecutorService ioPool = Executors.newSingleThreadExecutor();

    private final Handler uiHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            if (msg.what == 1) {
                binding.tvResult.setText((String) msg.obj);
            } else {
                binding.tvResult.setText("识别失败：\n" + msg.obj);
            }
        }
    };

    private ActivityResultLauncher<Uri> takePictureLauncher;
    private ActivityResultLauncher<String> requestCameraPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupLaunchers();
        setupClicks();

        binding.tvResult.setText("请先拍摄身份证正面/反面，然后点击【开始识别】。\n\n" +
                "注意：需要在 gradle.properties 或 local.properties 配置腾讯云 SecretId/SecretKey。");
    }

    private void setupLaunchers() {
        takePictureLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
            if (Boolean.TRUE.equals(success) && currentPhotoUri != null) {
                binding.ivPreview.setImageURI(currentPhotoUri);
                Toast.makeText(this, "拍照成功（" + currentCardSide + "）", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "拍照取消/失败", Toast.LENGTH_SHORT).show();
            }
        });

        requestCameraPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (Boolean.TRUE.equals(granted)) {
                launchCamera();
            } else {
                Toast.makeText(this, "需要相机权限才能拍照", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setupClicks() {
        binding.btnCaptureFront.setOnClickListener(v -> {
            currentCardSide = "FRONT";
            ensureCameraAndLaunch();
        });
        binding.btnCaptureBack.setOnClickListener(v -> {
            currentCardSide = "BACK";
            ensureCameraAndLaunch();
        });
        binding.btnRecognize.setOnClickListener(v -> {
            if (currentPhotoFile == null || !currentPhotoFile.exists()) {
                Toast.makeText(this, "请先拍照", Toast.LENGTH_SHORT).show();
                return;
            }
            if (BuildConfig.TENCENT_SECRET_ID == null || BuildConfig.TENCENT_SECRET_ID.trim().isEmpty()
                    || BuildConfig.TENCENT_SECRET_KEY == null || BuildConfig.TENCENT_SECRET_KEY.trim().isEmpty()) {
                Toast.makeText(this, "未配置 SecretId/SecretKey（见 README）", Toast.LENGTH_LONG).show();
                return;
            }
            doOcr(currentPhotoFile, currentCardSide);
        });
    }

    private void ensureCameraAndLaunch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchCamera() {
        try {
            currentPhotoFile = createImageFile();
            currentPhotoUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider",
                    currentPhotoFile);
            takePictureLauncher.launch(currentPhotoUri);
        } catch (Exception e) {
            Toast.makeText(this, "创建文件失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private File createImageFile() throws Exception {
        File dir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "idcard");
        if (!dir.exists()) dir.mkdirs();
        String name = "idcard_" + System.currentTimeMillis() + ".jpg";
        return new File(dir, name);
    }

    private void doOcr(File imageFile, String cardSide) {
        binding.tvResult.setText("识别中...");

        ioPool.execute(() -> {
            try {
                String base64 = imageFileToBase64(imageFile, 1280);

                JSONObject payload = new JSONObject();
                payload.put("ImageBase64", base64);
                payload.put("CardSide", cardSide);

                TencentOcrClient client = new TencentOcrClient(
                        BuildConfig.TENCENT_SECRET_ID,
                        BuildConfig.TENCENT_SECRET_KEY
                );

                String resp = client.idCardOcr(payload.toString());

                // handler 解析 json（PPT要求）
                Message m = Message.obtain();
                m.what = 1;
                m.obj = TencentOcrClient.prettyResultFromResponse(resp);
                uiHandler.sendMessage(m);

            } catch (Exception e) {
                Message m = Message.obtain();
                m.what = 0;
                m.obj = e.toString();
                uiHandler.sendMessage(m);
            }
        });
    }

    /**
     * 将图片读取为 Bitmap，并按最大边缩放，再 JPEG 压缩成 Base64。
     */
    private static String imageFileToBase64(File file, int maxSidePx) throws Exception {
        Bitmap bitmap;
        try (InputStream is = new FileInputStream(file)) {
            bitmap = BitmapFactory.decodeStream(is);
        }
        if (bitmap == null) throw new IllegalStateException("无法解码图片");

        Bitmap scaled = scaleDown(bitmap, maxSidePx);
        if (scaled != bitmap) bitmap.recycle();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos);
        scaled.recycle();

        byte[] data = baos.toByteArray();
        // Android 的 Base64
        return android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP);
    }

    private static Bitmap scaleDown(Bitmap src, int maxSide) {
        int w = src.getWidth();
        int h = src.getHeight();
        int max = Math.max(w, h);
        if (max <= maxSide) return src;

        float ratio = (float) maxSide / (float) max;
        int nw = Math.round(w * ratio);
        int nh = Math.round(h * ratio);
        return Bitmap.createScaledBitmap(src, nw, nh, true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioPool.shutdownNow();
    }
}
