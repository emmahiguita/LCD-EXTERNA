package com.limelight.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.nvstream.NvConnection;

public class SettingsOverlay extends LinearLayout {

    private NvConnection conn;
    private TextView tvConnectionStatus, tvConnectedTo, tvIpAddress, tvResolution, tvQualityValue, tvResolutionValue;
    private Button btnShutdown, btnRestart, btnLogout, btnLock, btnCloseSettings;
    private SeekBar seekBarQuality;
    private Spinner spinnerResolution;

    public SettingsOverlay(Context context, NvConnection conn) {
        super(context);
        this.conn = conn;
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.overlay_settings, this, true);

        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        tvConnectedTo = findViewById(R.id.tvConnectedTo);
        tvIpAddress = findViewById(R.id.tvIpAddress);
        tvResolution = findViewById(R.id.tvResolution);
        tvQualityValue = findViewById(R.id.tvQualityValue);
        tvResolutionValue = findViewById(R.id.tvResolutionValue);

        btnShutdown = findViewById(R.id.btnShutdown);
        btnRestart = findViewById(R.id.btnRestart);
        btnLogout = findViewById(R.id.btnLogout);
        btnLock = findViewById(R.id.btnLock);
        btnCloseSettings = findViewById(R.id.btnCloseSettings);

        seekBarQuality = findViewById(R.id.seekBarQuality);
        spinnerResolution = findViewById(R.id.spinnerResolution);

        setupButtonListeners();
        setupQualitySlider();
    }

    private void setupButtonListeners() {
        btnShutdown.setOnClickListener(v -> {
            // Send shutdown command (implementation depends on system)
            // This would typically send a special key sequence or system command
        });

        btnRestart.setOnClickListener(v -> {
            // Send restart command
        });

        btnLogout.setOnClickListener(v -> {
            // Send logout command (Win+L)
        });

        btnLock.setOnClickListener(v -> {
            // Send lock command (Win+L)
        });

        btnCloseSettings.setOnClickListener(v -> {
            setOverlayVisibility(false);
        });
    }

    private void setupQualitySlider() {
        seekBarQuality.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                String[] qualityLevels = {"Baja", "Media", "Automática", "Alta", "Épica"};
                tvQualityValue.setText(qualityLevels[progress]);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Apply quality setting
            }
        });
    }

    public void updateConnectionInfo(String status, String pcName, String ipAddress) {
        tvConnectionStatus.setText(status);
        tvConnectedTo.setText(pcName);
        tvIpAddress.setText(ipAddress);
    }

    public void updatePerformanceMetrics(int fps, int latency, String signalQuality, String resolution) {
        tvResolution.setText(resolution + " RESOLUCIÓN");
    }

    public void setOverlayVisibility(boolean visible) {
        animate().cancel();
        if (visible) {
            if (getVisibility() != VISIBLE) {
                setAlpha(0f);
                setVisibility(VISIBLE);
            }
            animate().alpha(1f).setDuration(200).withEndAction(null).start();
        } else {
            if (getVisibility() == VISIBLE) {
                animate().alpha(0f).setDuration(200).withEndAction(() -> setVisibility(GONE)).start();
            }
        }
    }
}
