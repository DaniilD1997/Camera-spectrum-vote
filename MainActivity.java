package com.example.camera30;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final int MY_REQUEST_CODE_FOR_CAMERA = 1;
    private static Camera camera1;
    public static final String SCALE = "scale";
    public static final String OUTPUT_DIRECTORY = "VoiceRecorder";
    public static final String OUTPUT_FILENAME = "recorder.mp3";
    private static final int MY_PERMISSIONS_REQUEST_CODE = 0;
    int scale = 8;
    private VoiceRecorder recorder;
    private byte backi = 0;
    private int CAMERA_ID = 0;
    private SurfaceHolder holder1;
    public SurfaceView sv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button button = findViewById(R.id.button);
        sv = findViewById(R.id.sv);
        button.setOnClickListener(this);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        holder1 = sv.getHolder();
        holder1.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        GraphView graphView = findViewById(R.id.graphView);
                graphView.setGraphColor(Color.rgb(218, 165, 32));
                graphView.setCanvasColor(Color.rgb(255,255,255));

                recorder = VoiceRecorder.getInstance();
                if (recorder.isRecording()) {
                    recorder.startPlotting(graphView);

                }
                if (savedInstanceState != null) {
                    scale = savedInstanceState.getInt(SCALE);
                    graphView.setWaveLengthPX(scale);
                    if (!recorder.isRecording()) {
                        List samples = recorder.getSamples();
                        graphView.showFullGraph(samples);
                    }
                }
        requestPermissions();
        String filepath = Environment.getExternalStorageDirectory().getPath();
        File file = new File(filepath, OUTPUT_DIRECTORY);
            file.mkdirs();
        recorder.setOutputFilePath(file.getAbsoluteFile() + "/" + OUTPUT_FILENAME);
        recorder.startRecording();
        recorder.startPlotting(graphView);
    }


            @Override
            protected void onSaveInstanceState(Bundle outState) {
                outState.putInt(SCALE, scale);
                super.onSaveInstanceState(outState);

            }

            @Override
            public void onBackPressed() {
                super.onBackPressed();

                if (recorder.isRecording()) {
                    recorder.stopRecording();
                }
                if (recorder != null) {
                    recorder.release();
                }
            }
            public void requestPermissions(){
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO,Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.CAMERA},
                        MY_PERMISSIONS_REQUEST_CODE);
            }

    @Override
    protected void onResume() {
        super.onResume();
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, MY_REQUEST_CODE_FOR_CAMERA);}
        camera1 = Camera.open(CAMERA_ID);
        setCameraDisplayOrientation(CAMERA_ID);
        HolderCallback holderCallback = new HolderCallback();
        holderCallback.surfaceCreated(holder1);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (camera1 != null) {
            camera1.release();
            camera1 = null;

        }}

    public static class HolderCallback implements SurfaceHolder.Callback {
        @Override
        public void surfaceCreated(SurfaceHolder holder1) {
            try {
                MainActivity.camera1.setPreviewDisplay(holder1);
                MainActivity.camera1.startPreview();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder1, int format, int width, int height) {
            try {
                Camera.Parameters parameters = camera1.getParameters();
                parameters.setPreviewSize(width, height);
                camera1.setParameters(parameters);
                MainActivity.camera1.setPreviewDisplay(holder1);
                MainActivity.camera1.startPreview();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
                }
    }
    public void setCameraDisplayOrientation(int cameraId) {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = -90;
                break;
            case Surface.ROTATION_180:
                degrees = -180;
                break;
            case Surface.ROTATION_270:
                degrees = -270;
                break;
        }
        int result = 0;
        android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        if (info.facing == android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK) {
            result = ((360 - degrees) + info.orientation);
        }
        result = result % 180;
        camera1.setDisplayOrientation(result);

    }

    public void setCameraDisplayOrientation1(int cameraId) {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
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
        int result = 90;
        android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = ((360 - degrees) - info.orientation);
            result += 360;
        }
        result = result % 180;
        camera1.setDisplayOrientation(result);
    }

    public void onClick(View v) {
        if (backi == 1){
            onPause();
            backi--;
            CAMERA_ID = 0;
            camera1 = Camera.open(CAMERA_ID);
            setCameraDisplayOrientation(CAMERA_ID);
        } else{
            onPause();
            backi++;
            CAMERA_ID = 1;
            camera1 = Camera.open(CAMERA_ID);
            setCameraDisplayOrientation1(CAMERA_ID);
        }
        HolderCallback holderCallback = new HolderCallback();
        holderCallback.surfaceCreated(holder1);
    }
}


