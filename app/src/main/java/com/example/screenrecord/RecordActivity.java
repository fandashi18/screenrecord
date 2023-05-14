package com.example.screenrecord;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.Button;
import android.widget.EditText;

import java.io.File;
import java.io.IOException;

public class RecordActivity extends AppCompatActivity {
    private static final String TAG = "RecordActivity";
    private final int WRITE_EXT_REQUEST_CODE = 0;
    private final int RECORD_AUDIO_REQUEST_CODE = 1;
    private final int GET_MEDIA_PROJ_REQUEST_CODE = 2;
    private int width = 0, height = 0;
    private MediaProjection mediaProjection;
    private MediaRecorder mediaRecorder;
    private HandlerThread workThread;
    private Handler workHandler;
    private VirtualDisplay virtualDisplay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestPermsIfNeed();
        initEditTexts();
        initButtons();
    }

    private void initEditTexts() {
        EditText widthEt = findViewById(R.id.sr_width);
        EditText heightEt = findViewById(R.id.sr_height);
        EditText fpsEt = findViewById(R.id.sr_fps);
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        WindowMetrics metrics = windowManager.getCurrentWindowMetrics();
        widthEt.setText(String.valueOf(metrics.getBounds().width()));
        heightEt.setText(String.valueOf(metrics.getBounds().height()));
        fpsEt.setText("60");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSrForegroundService();
    }


    private void requestPermsIfNeed() {
        Log.d(TAG, "requestPermsIfNeed: ");
        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO}, WRITE_EXT_REQUEST_CODE | RECORD_AUDIO_REQUEST_CODE);
    }

    private void initButtons() {
        Button startBtn = findViewById(R.id.sc_start);
        Button stopBtn = findViewById(R.id.sc_stop);
        Button pauseBtn = findViewById(R.id.sc_pause);

        stopBtn.setEnabled(false);
        pauseBtn.setEnabled(false);

        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "onClick: start");
                startSrForegroundService();
                obtainMediaProjection();
                startBtn.setEnabled(false);
                stopBtn.setEnabled(true);
                pauseBtn.setEnabled(true);
            }
        });

        pauseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: pause");
                if (virtualDisplay.getSurface() != null) {
                    mediaRecorder.pause();
                    virtualDisplay.setSurface(null);
                    pauseBtn.setText("RESUME");
                } else {
                    mediaRecorder.resume();
                    virtualDisplay.setSurface(mediaRecorder.getSurface());
                    pauseBtn.setText("PAUSE");
                }
            }
        });

        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "onClick: stop");
                stopSrForegroundService();
                workThread.getLooper().quit();
                mediaRecorder.stop();
                mediaRecorder.release();
                virtualDisplay.release();
                startBtn.setEnabled(true);
                stopBtn.setEnabled(false);
                pauseBtn.setEnabled(false);
            }
        });
    }

    private void obtainMediaProjection() {
        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Intent intent = mediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(intent, GET_MEDIA_PROJ_REQUEST_CODE);
    }

    private void stopSrForegroundService() {
        Intent service = new Intent(this, MyService.class);
        service.putExtra("recording", false);
        stopService(service);
    }

    private void startSrForegroundService() {
        Intent service = new Intent(this, MyService.class);
        service.putExtra("recording", true);
        startForegroundService(service);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == GET_MEDIA_PROJ_REQUEST_CODE) {
            Log.d(TAG, "onActivityResult: GET_MEDIA_PROJ_REQUEST_CODE");
            MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
            initRecordStatus();
            startRecord();
        }
    }

    private void initRecordStatus() {
        Log.d(TAG, "initRecordStatus: ");
        workThread = new HandlerThread("recoding-thread");
        workThread.start();
        workHandler = new Handler(workThread.getLooper());

        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);

        File outFile = new File("/storage/emulated/0/Download/test.mp4");
        if (outFile.exists()) {
            outFile.delete();
        }
        try {
            outFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        mediaRecorder.setOutputFile("/storage/emulated/0/Download/test.mp4");

        EditText widthEt = findViewById(R.id.sr_width);
        EditText heightEt = findViewById(R.id.sr_height);
        width = Integer.valueOf(widthEt.getText().toString());
        height = Integer.valueOf(heightEt.getText().toString());
        mediaRecorder.setVideoSize(width, height);

        EditText fpsEt = findViewById(R.id.sr_fps);
        mediaRecorder.setVideoFrameRate(Integer.valueOf(fpsEt.getText().toString()));

        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startRecord() {
        Log.d(TAG, "startRecord: ");
        mediaRecorder.start();

        virtualDisplay = mediaProjection.createVirtualDisplay("", width, height, 200, 0, mediaRecorder.getSurface(), new VirtualDisplay.Callback() {
            @Override
            public void onPaused() {
                super.onPaused();
                Log.d(TAG, "onPaused: ");
            }

            @Override
            public void onResumed() {
                super.onResumed();
                Log.d(TAG, "onResumed: ");
            }

            @Override
            public void onStopped() {
                super.onStopped();
                Log.d(TAG, "onStopped: ");
            }
        }, workHandler);
    }
}