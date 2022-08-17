package com.lite.holistic_tracking;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.util.Log;
import android.util.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.mediapipe.components.CameraHelper;
import com.google.mediapipe.components.CameraXPreviewHelper;
import com.google.mediapipe.components.ExternalTextureConverter;
import com.google.mediapipe.components.FrameProcessor;
import com.google.mediapipe.components.PermissionHelper;
import com.google.mediapipe.formats.proto.LandmarkProto;
import com.google.mediapipe.framework.AndroidAssetUtil;
import com.google.mediapipe.framework.PacketGetter;
import com.google.mediapipe.glutil.EglManager;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.HashMap;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class holistic_activity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    // Flips the camera-preview frames vertically by default, before sending them into FrameProcessor
    // to be processed in a MediaPipe graph, and flips the processed frames back when they are
    // displayed. This maybe needed because OpenGL represents images assuming the image origin is at
    // the bottom-left corner, whereas MediaPipe in general assumes the image origin is at the
    // top-left corner.
    // NOTE: use "flipFramesVertically" in manifest metadata to override this behavior.
    private static final boolean FLIP_FRAMES_VERTICALLY = true;

    private Button backBtn;
    static {
        // Load all native libraries needed by the app.
        System.loadLibrary("mediapipe_jni");
        try {
            System.loadLibrary("opencv_java3");
        } catch (UnsatisfiedLinkError e) {
            // Some example apps (e.g. template matching) require OpenCV 4.
            System.loadLibrary("opencv_java4");
        }
    }

    // Sends camera-preview frames into a MediaPipe graph for processing, and displays the processed
    // frames onto a {@link Surface}.
    protected FrameProcessor processor;
    // Handles camera access via the {@link CameraX} Jetpack support library.
    protected CameraXPreviewHelper cameraHelper;

    // {@link SurfaceTexture} where the camera-preview frames can be accessed.
    private SurfaceTexture previewFrameTexture;
    // {@link SurfaceView} that displays the camera-preview frames processed by a MediaPipe graph.
    private SurfaceView previewDisplayView;

    // Creates and manages an {@link EGLContext}.
    private EglManager eglManager;
    // Converts the GL_TEXTURE_EXTERNAL_OES texture from Android camera into a regular texture to be
    // consumed by {@link FrameProcessor} and the underlying MediaPipe graph.
    private ExternalTextureConverter converter;

    // ApplicationInfo for retrieving metadata defined in the manifest.
    private ApplicationInfo applicationInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_holistic_activity);

        HashMap<String, float[][]> LandmarkMap = new HashMap<>();
        LandmarkMap.put("pose",null);
        LandmarkMap.put("leftHand",null);
        LandmarkMap.put("rightHand",null);
        LandmarkMap.put("face",null);

        RetrofitClient retrofitClient = new RetrofitClient();
        retrofitClient.generateClient();

        backBtn = findViewById(R.id.BackBtn);
        backBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                finish();
            }
        });
        try {
            applicationInfo =
                    getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Cannot find application info: " + e);
        }

        previewDisplayView = new SurfaceView(this);
        setupPreviewDisplayView();

        // Initialize asset manager so that MediaPipe native libraries can access the app assets, e.g.,
        // binary graphs.
        AndroidAssetUtil.initializeNativeAssetManager(this);
        eglManager = new EglManager(null);
        processor =
                new FrameProcessor(
                        this,
                        eglManager.getNativeContext(),
                        applicationInfo.metaData.getString("binaryGraphName"),
                        applicationInfo.metaData.getString("inputVideoStreamName"),
                        applicationInfo.metaData.getString("outputVideoStreamName")
                );

        processor
                .addPacketCallback("face_landmarks", (packet) -> {
                    try {
                        Log.d("ㄱ", "face");
                        byte[] landmarksRaw = PacketGetter.getProtoBytes(packet);
                        LandmarkProto.NormalizedLandmarkList poseLandmarks = LandmarkProto.NormalizedLandmarkList.parseFrom(landmarksRaw);
//                        Log.v("AAA", String.valueOf(packet));
//                        LandmarkProto.NormalizedLandmarkList poseLandmarks =
//                                PacketGetter.getProto(packet, LandmarkProto.NormalizedLandmarkList.class);
//                        Log.v(
//                                "AAA_FL",
//                                "[TS:"
//                                        + packet.getTimestamp()
//                                        + "] "
//                                        + getPoseLandmarksDebugString(poseLandmarks));
                        LandmarkMap.put("face",getPoseLandmarksDebugAry(poseLandmarks));

                        Call<JsonElement> callAPI = retrofitClient.getApi().sendLandmark(LandmarkMap);
                        callAPI.enqueue(new Callback<JsonElement>() {
                            @Override
                            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                                JsonArray DictResponseArray = response.body().getAsJsonArray();
                                Log.e("api가 계산해서 보냈어요", String.valueOf(DictResponseArray));
//                                map 값 초기화 필요
                            }
                            @Override
                            public void onFailure(Call<JsonElement> call, Throwable t) {
                                Log.e("실패군","실패다");
                            }
                        });
                    } catch (InvalidProtocolBufferException e) {
                        Log.e("AAA", "Failed to get proto.", e);
                    }

                });
        processor
                .addPacketCallback("pose_landmarks", (packet) -> {
                    try {
                        Log.d("ㄱ", "pose");
                        byte[] landmarksRaw = PacketGetter.getProtoBytes(packet);
                        LandmarkProto.NormalizedLandmarkList poseLandmarks = LandmarkProto.NormalizedLandmarkList.parseFrom(landmarksRaw);
//                        Log.v("AAA", String.valueOf(packet));
//                        LandmarkProto.NormalizedLandmarkList poseLandmarks =
//                                PacketGetter.getProto(packet, LandmarkProto.NormalizedLandmarkList.class);
//                        Log.v(
//                                "AAA_PL",
//                                "[TS:"
//                                        + packet.getTimestamp()
//                                        + "] "
//                                        + getPoseLandmarksDebugString(poseLandmarks));
                        LandmarkMap.put("pose",getPoseLandmarksDebugAry(poseLandmarks));
                    } catch (InvalidProtocolBufferException e) {
                        Log.e("AAA", "Failed to get proto.", e);
                    }

                });
        processor
                .addPacketCallback("left_hand_landmarks", (packet) -> {
                    try {
                        Log.d("ㄱ", "left");
                        byte[] landmarksRaw = PacketGetter.getProtoBytes(packet);
                        LandmarkProto.NormalizedLandmarkList poseLandmarks = LandmarkProto.NormalizedLandmarkList.parseFrom(landmarksRaw);
//                        Log.v("AAA", String.valueOf(packet));
//                        LandmarkProto.NormalizedLandmarkList poseLandmarks =
//                                PacketGetter.getProto(packet, LandmarkProto.NormalizedLandmarkList.class);
//                        Log.v(
//                                "AAA_LH",
//                                "[TS:"
//                                        + packet.getTimestamp()
//                                        + "] "
//                                        + getPoseLandmarksDebugString(poseLandmarks));
                        LandmarkMap.put("leftHand",getPoseLandmarksDebugAry(poseLandmarks));
                    } catch (InvalidProtocolBufferException e) {
                        Log.e("AAA", "Failed to get proto.", e);
                    }

                });
        processor
                .addPacketCallback("right_hand_landmarks", (packet) -> {
                    try {
                        Log.d("ㄱ", "right");
                        byte[] landmarksRaw = PacketGetter.getProtoBytes(packet);
                        LandmarkProto.NormalizedLandmarkList poseLandmarks = LandmarkProto.NormalizedLandmarkList.parseFrom(landmarksRaw);
//                        Log.v("AAA", String.valueOf(packet));
//                        LandmarkProto.NormalizedLandmarkList poseLandmarks =
//                                PacketGetter.getProto(packet, LandmarkProto.NormalizedLandmarkList.class);
//                        Log.v(
//                                "AAA_RH",
//                                "[TS:"
//                                        + packet.getTimestamp()
//                                        + "] "
//                                        + getPoseLandmarksDebugString(poseLandmarks));
                        LandmarkMap.put("rightHand",getPoseLandmarksDebugAry(poseLandmarks));
                    } catch (InvalidProtocolBufferException e) {
                        Log.e("AAA", "Failed to get proto.", e);
                    }

                });

        processor
                .getVideoSurfaceOutput()
                .setFlipY(
                        applicationInfo.metaData.getBoolean("flipFramesVertically", FLIP_FRAMES_VERTICALLY));


        PermissionHelper.checkAndRequestCameraPermissions(this);


// Flask의 REST API와의 연결 (목적 : 카메라로 인식한 좌표값 API에게 보내서 계산된 좌표값을 받아오는 코드)




    }
// 좌표값 string으로 변환해서 반환하는 코드
//    private static String getPoseLandmarksDebugString(LandmarkProto.NormalizedLandmarkList poseLandmarks) {
//        String poseLandmarkStr = "Pose landmarks: " + poseLandmarks.getLandmarkCount() + "\n";
//        int landmarkIndex = 0;
//        for (LandmarkProto.NormalizedLandmark landmark : poseLandmarks.getLandmarkList()) {
//            poseLandmarkStr +=
//                    "\tLandmark ["
//                            + landmarkIndex
//                            + "]: ("
//                            + landmark.getX()
//                            + ", "
//                            + landmark.getY()
//                            + ", "
//                            + landmark.getZ()
//                            + ")\n";
//            ++landmarkIndex;
//        }
//        return poseLandmarkStr;
//    }

    // 좌표값 숫자 배열로 변환해서 반환하는 코드
    private static float[][] getPoseLandmarksDebugAry(LandmarkProto.NormalizedLandmarkList poseLandmarks){
        float[][] poseLandmarkAry = new float[poseLandmarks.getLandmarkCount()][3];
        int landmarkIndex = 0;
        for (LandmarkProto.NormalizedLandmark landmark : poseLandmarks.getLandmarkList()) {
            poseLandmarkAry[landmarkIndex][0] = landmark.getX();
            poseLandmarkAry[landmarkIndex][1] = landmark.getY();
            poseLandmarkAry[landmarkIndex][2] = landmark.getZ();
            ++landmarkIndex;
        }
        return poseLandmarkAry;
    }

    @Override
    protected void onResume() {
        super.onResume();
        converter = new ExternalTextureConverter(eglManager.getContext());
        converter.setFlipY(
                applicationInfo.metaData.getBoolean("flipFramesVertically", FLIP_FRAMES_VERTICALLY));
        converter.setConsumer(processor);
        if (PermissionHelper.cameraPermissionsGranted(this)) {
            startCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        converter.close();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    protected void onCameraStarted(SurfaceTexture surfaceTexture) {
        previewFrameTexture = surfaceTexture;
        // Make the display view visible to start showing the preview. This triggers the
        // SurfaceHolder.Callback added to (the holder of) previewDisplayView.
        previewDisplayView.setVisibility(View.VISIBLE);
    }

    protected Size cameraTargetResolution() {
        return null; // No preference and let the camera (helper) decide.
    }

    public void startCamera() {
        cameraHelper = new CameraXPreviewHelper();
        cameraHelper.setOnCameraStartedListener(
                surfaceTexture -> {
                    onCameraStarted(surfaceTexture);
                });
        CameraHelper.CameraFacing cameraFacing =
                applicationInfo.metaData.getBoolean("cameraFacingFront", false)
                        ? CameraHelper.CameraFacing.BACK
                        : CameraHelper.CameraFacing.FRONT;
        cameraHelper.startCamera(
                this, cameraFacing, /*surfaceTexture=*/ null, cameraTargetResolution());
    }

    protected Size computeViewSize(int width, int height) {
        return new Size(width, height);
    }

    protected void onPreviewDisplaySurfaceChanged(
            SurfaceHolder holder, int format, int width, int height) {
        // (Re-)Compute the ideal size of the camera-preview display (the area that the
        // camera-preview frames get rendered onto, potentially with scaling and rotation)
        // based on the size of the SurfaceView that contains the display.
        Size viewSize = computeViewSize(width, height);
        Size displaySize = cameraHelper.computeDisplaySizeFromViewSize(viewSize);
        boolean isCameraRotated = cameraHelper.isCameraRotated();

        // Connect the converter to the camera-preview frames as its input (via
        // previewFrameTexture), and configure the output width and height as the computed
        // display size.
        converter.setSurfaceTextureAndAttachToGLContext(
                previewFrameTexture,
                isCameraRotated ? displaySize.getHeight() : displaySize.getWidth(),
                isCameraRotated ? displaySize.getWidth() : displaySize.getHeight());
    }

    private void setupPreviewDisplayView() {
        previewDisplayView.setVisibility(View.GONE);
        ViewGroup viewGroup = findViewById(R.id.preview_display_layout);
        viewGroup.addView(previewDisplayView);

        previewDisplayView
                .getHolder()
                .addCallback(
                        new SurfaceHolder.Callback() {
                            @Override
                            public void surfaceCreated(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(holder.getSurface());
                            }

                            @Override
                            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                                onPreviewDisplaySurfaceChanged(holder, format, width, height);
                            }

                            @Override
                            public void surfaceDestroyed(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(null);
                            }
                        });
    }
}