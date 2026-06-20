package com.limelight;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;

public class ScreenCaptureActivity extends Activity {
    private static final int REQUEST_CODE_SCREEN_CAPTURE = 1005;
    private String pcIp;
    private String token;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent != null) {
            pcIp = intent.getStringExtra("PC_IP");
            token = intent.getStringExtra("TOKEN");
        }

        if (pcIp == null) {
            finish();
            return;
        }

        MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (projectionManager != null) {
            startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CODE_SCREEN_CAPTURE);
        } else {
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
                serviceIntent.putExtra("RESULT_CODE", resultCode);
                serviceIntent.putExtra("RESULT_DATA", data);
                serviceIntent.putExtra("PC_IP", pcIp);
                serviceIntent.putExtra("TOKEN", token);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
            }
        }
        finish();
    }
}
