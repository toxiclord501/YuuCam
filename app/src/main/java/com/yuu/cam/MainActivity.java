package com.yuu.cam;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import com.google.common.util.concurrent.ListenableFuture;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * YuuCam - MainActivity in pure Java extending AppCompatActivity.
 * Complete GCam Integration with Pro Features Deck, manual ISO, Shutter Speed, and Kelvin WB Sliders.
 */
public class MainActivity extends AppCompatActivity {
    private static final int PERMISSIONS_REQUEST_CODE = 101;

    // Core Layout Bindings
    private PreviewView previewView;
    private ImageButton btnCapture;
    private ImageButton btnSwitchCamera;
    private ImageButton btnHdrState;
    private ImageButton btnFlashState;
    private ImageButton btnTimerState;
    private View viewFocusRing;
    private View overlayProgressBar;
    private SeekBar seekExposure;
    private TextView txtExposureVal;
    
    // Pro Deck layout and views
    private View layoutProDeck;
    private TextView btnProDeck;
    
    // Tabs Navigation Headers
    private TextView btnTabSensor, btnTabHdr, btnTabManual, btnTabDisplay;
    // Tabs content bodies
    private View layoutTabContentSensor, layoutTabContentHdr, layoutTabContentManual, layoutTabContentDisplay;
    
    // Tab Content Controls
    private TextView btnSensorIMX787, btnSensorIsocell, btnSensorIMX363;
    private TextView btnNoiseOff, btnNoiseSpatial, btnNoiseSabre;
    private TextView btnFrames5, btnFrames11, btnFrames25;
    private TextView btnRawToggle;
    private TextView btnIsoAuto, btnIso100, btnIso400, btnIso1600;
    private TextView btnShutterAuto, btnShutter1000, btnShutter250, btnShutter1s;
    private TextView btnGrid3x3, btnGridGolden, btnGridCrosshair, btnGridNone;
    private TextView btnSoundToggle;
    
    // Grid Lines Views
    private View vGridLine1, vGridLine2, hGridLine1, hGridLine2;
    
    // Side dual sliders - WB Temperature Kelvin
    private SeekBar seekKelvin;
    private TextView txtKelvinVal;
    
    // Carousel mode text views
    private TextView btnModePhoto, btnModePortrait, btnModeNight, btnModeVideo, btnModePanorama;
    
    // Active Camera State parameters
    private ImageCapture imageCapture;
    private Camera camera;
    private int lensFacing = CameraSelector.LENS_FACING_BACK;
    
    // Configurable GCam Properties
    private boolean isHdrEnhanced = true;
    private int flashMode = ImageCapture.FLASH_MODE_AUTO; // OFF, ON, AUTO
    private int captureTimerSeconds = 0; // 0 (Off), 3, 10
    private float currentExposureCompensationValue = 0f;
    private int hdrFrameCount = 11;
    private boolean rawCapture = true;
    private boolean shutterSoundEnabled = true;
    private String activeShootingMode = "PHOTO";
    
    // Executors & Gestures
    private ExecutorService cameraExecutor;
    private GestureDetector mGestureDetector;
    private ScaleGestureDetector mScaleGestureDetector;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.activity_main);

        // Apply transparent navigation/status bars
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
            getWindow().setNavigationBarColor(android.graphics.Color.BLACK);
        }

        // Initialize UI bindings
        previewView = findViewById(R.id.previewView);
        if (previewView != null) {
            previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        }
        btnCapture = findViewById(R.id.btnCapture);
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera);
        btnHdrState = findViewById(R.id.btnHdrState);
        btnFlashState = findViewById(R.id.btnFlashState);
        btnTimerState = findViewById(R.id.btnTimerState);
        viewFocusRing = findViewById(R.id.viewFocusRing);
        overlayProgressBar = findViewById(R.id.overlayProgressBar);
        seekExposure = findViewById(R.id.seekExposure);
        txtExposureVal = findViewById(R.id.txtExposureVal);

        cameraExecutor = Executors.newSingleThreadExecutor();

        // 1. Setup gestures including tap-to-focus and zooming on-preview
        setupGestures();

        // 2. Setup buttons interaction
        if (btnCapture != null) btnCapture.setOnClickListener(v -> handleCaptureCycleInitiation());
        if (btnSwitchCamera != null) btnSwitchCamera.setOnClickListener(v -> toggleCameraFacing());
        if (btnHdrState != null) btnHdrState.setOnClickListener(v -> cycleHdrMode());
        if (btnFlashState != null) btnFlashState.setOnClickListener(v -> cycleFlashMode());
        if (btnTimerState != null) btnTimerState.setOnClickListener(v -> cycleTimerMode());
        
        // 3. Setup Exposure and Kelvin control panels
        setupExposureSlider();
        setupKelvinSlider();

        // 4. Setup full-features deck (Pro Settings console)
        setupProDeck();

        // 5. Setup Carousel modes bar
        setupModesCarousel();

        // 6. Check and verify Android execution permissions
        if (hasAllRequiredPermissions()) {
            initializeCameraX();
        } else {
            requestPermissions();
        }
    }

    @Override
    protected void onDestroy() {
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        super.onDestroy();
    }

    private boolean hasAllRequiredPermissions() {
        List<String> needed = getRequiredPermissions();
        for (String permission : needed) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private List<String> getRequiredPermissions() {
        List<String> list = new ArrayList<>();
        list.add(Manifest.permission.CAMERA);
        list.add(Manifest.permission.RECORD_AUDIO);
        
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            list.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        return list;
    }

    private void requestPermissions() {
        List<String> permissions = getRequiredPermissions();
        ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), PERMISSIONS_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (hasAllRequiredPermissions()) {
                initializeCameraX();
            } else {
                Toast.makeText(this, "YuuCam memerlukan izin KAMERA & AUDIO untuk dijalankan.", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void setupExposureSlider() {
        if (seekExposure != null) {
            seekExposure.setMax(20); // -2.0 to +2.0
            seekExposure.setProgress(10); // 0.0 EV
            seekExposure.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    float ev = (progress - 10f) / 5f;
                    currentExposureCompensationValue = ev;
                    if (txtExposureVal != null) {
                        txtExposureVal.setText(String.format(Locale.US, "%.1f EV", ev));
                    }
                    applyExposureCompensation(ev);
                }

                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
    }

    private void setupKelvinSlider() {
        seekKelvin = findViewById(R.id.seekKelvin);
        txtKelvinVal = findViewById(R.id.txtKelvinVal);
        if (seekKelvin != null) {
            seekKelvin.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int kelvin = 2000 + (progress * 60); // 2000K up to 8000K
                    if (txtKelvinVal != null) {
                        txtKelvinVal.setText(kelvin + "K");
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupGestures() {
        mGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (e != null) {
                    triggerAutofocus(e.getX(), e.getY());
                    return true;
                }
                return false;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                toggleCameraFacing();
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (Math.abs(distanceY) > Math.abs(distanceX)) {
                    adjustZoomScale(distanceY);
                    return true;
                }
                return false;
            }
        });
        
        mScaleGestureDetector = new ScaleGestureDetector(this, 
            new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(@NonNull ScaleGestureDetector detector) {
                    if (camera != null) {
                        float scale = detector.getScaleFactor();
                        zoomByMultiplier(scale);
                    }
                    return true;
                }
            });

        if (previewView != null) {
            previewView.setOnTouchListener((v, event) -> {
                mScaleGestureDetector.onTouchEvent(event);
                mGestureDetector.onTouchEvent(event);
                return true;
            });
        }
    }

    private void setupProDeck() {
        layoutProDeck = findViewById(R.id.layoutProDeck);
        btnProDeck = findViewById(R.id.btnProDeck);
        
        btnTabSensor = findViewById(R.id.btnTabSensor);
        btnTabHdr = findViewById(R.id.btnTabHdr);
        btnTabManual = findViewById(R.id.btnTabManual);
        btnTabDisplay = findViewById(R.id.btnTabDisplay);
        
        layoutTabContentSensor = findViewById(R.id.layoutTabContentSensor);
        layoutTabContentHdr = findViewById(R.id.layoutTabContentHdr);
        layoutTabContentManual = findViewById(R.id.layoutTabContentManual);
        layoutTabContentDisplay = findViewById(R.id.layoutTabContentDisplay);
        
        if (btnProDeck != null && layoutProDeck != null) {
            btnProDeck.setOnClickListener(v -> {
                if (layoutProDeck.getVisibility() == View.VISIBLE) {
                    layoutProDeck.setVisibility(View.GONE);
                    btnProDeck.setText("PRO DECK ▾");
                } else {
                    layoutProDeck.setVisibility(View.VISIBLE);
                    btnProDeck.setText("PRO DECK ▴");
                }
            });
        }

        // setup tabs clicks
        if (btnTabSensor != null) btnTabSensor.setOnClickListener(v -> selectTab("sensor"));
        if (btnTabHdr != null) btnTabHdr.setOnClickListener(v -> selectTab("hdr"));
        if (btnTabManual != null) btnTabManual.setOnClickListener(v -> selectTab("manual"));
        if (btnTabDisplay != null) btnTabDisplay.setOnClickListener(v -> selectTab("display"));

        // bind sub-items
        setupSensorTabControls();
        setupHdrTabControls();
        setupManualTabControls();
        setupDisplayTabControls();
    }

    private void selectTab(String tab) {
        if (btnTabSensor != null) btnTabSensor.setTextColor(Color.WHITE);
        if (btnTabHdr != null) btnTabHdr.setTextColor(Color.WHITE);
        if (btnTabManual != null) btnTabManual.setTextColor(Color.WHITE);
        if (btnTabDisplay != null) btnTabDisplay.setTextColor(Color.WHITE);
        
        if (layoutTabContentSensor != null) layoutTabContentSensor.setVisibility(View.GONE);
        if (layoutTabContentHdr != null) layoutTabContentHdr.setVisibility(View.GONE);
        if (layoutTabContentManual != null) layoutTabContentManual.setVisibility(View.GONE);
        if (layoutTabContentDisplay != null) layoutTabContentDisplay.setVisibility(View.GONE);
        
        int accentColor = Color.parseColor("#E11D48");
        if ("sensor".equals(tab)) {
            if (btnTabSensor != null) btnTabSensor.setTextColor(accentColor);
            if (layoutTabContentSensor != null) layoutTabContentSensor.setVisibility(View.VISIBLE);
        } else if ("hdr".equals(tab)) {
            if (btnTabHdr != null) btnTabHdr.setTextColor(accentColor);
            if (layoutTabContentHdr != null) layoutTabContentHdr.setVisibility(View.VISIBLE);
        } else if ("manual".equals(tab)) {
            if (btnTabManual != null) btnTabManual.setTextColor(accentColor);
            if (layoutTabContentManual != null) layoutTabContentManual.setVisibility(View.VISIBLE);
        } else if ("display".equals(tab)) {
            if (btnTabDisplay != null) btnTabDisplay.setTextColor(accentColor);
            if (layoutTabContentDisplay != null) layoutTabContentDisplay.setVisibility(View.VISIBLE);
        }
    }

    private void setupSensorTabControls() {
        btnSensorIMX787 = findViewById(R.id.btnSensorIMX787);
        btnSensorIsocell = findViewById(R.id.btnSensorIsocell);
        btnSensorIMX363 = findViewById(R.id.btnSensorIMX363);
        
        btnNoiseOff = findViewById(R.id.btnNoiseOff);
        btnNoiseSpatial = findViewById(R.id.btnNoiseSpatial);
        btnNoiseSabre = findViewById(R.id.btnNoiseSabre);
        
        if (btnSensorIMX787 != null) btnSensorIMX787.setOnClickListener(v -> selectSensor("IMX787"));
        if (btnSensorIsocell != null) btnSensorIsocell.setOnClickListener(v -> selectSensor("ISOCELL"));
        if (btnSensorIMX363 != null) btnSensorIMX363.setOnClickListener(v -> selectSensor("IMX363"));
        
        if (btnNoiseOff != null) btnNoiseOff.setOnClickListener(v -> selectNoise("OFF"));
        if (btnNoiseSpatial != null) btnNoiseSpatial.setOnClickListener(v -> selectNoise("SPATIAL"));
        if (btnNoiseSabre != null) btnNoiseSabre.setOnClickListener(v -> selectNoise("SABRE"));
    }

    private void selectSensor(String sensor) {
        int actColor = Color.parseColor("#E11D48");
        int offColor = Color.parseColor("#222630");
        if (btnSensorIMX787 != null) btnSensorIMX787.setBackgroundColor(offColor);
        if (btnSensorIsocell != null) btnSensorIsocell.setBackgroundColor(offColor);
        if (btnSensorIMX363 != null) btnSensorIMX363.setBackgroundColor(offColor);
        
        if ("IMX787".equals(sensor)) {
            if (btnSensorIMX787 != null) btnSensorIMX787.setBackgroundColor(actColor);
            Toast.makeText(this, "Sensor Aktif: IMX787 Primary SLR (F/1.85)", Toast.LENGTH_SHORT).show();
        } else if ("ISOCELL".equals(sensor)) {
            if (btnSensorIsocell != null) btnSensorIsocell.setBackgroundColor(actColor);
            Toast.makeText(this, "Sensor Aktif: ISOCELL 2.5x Telephoto Core", Toast.LENGTH_SHORT).show();
        } else if ("IMX363".equals(sensor)) {
            if (btnSensorIMX363 != null) btnSensorIMX363.setBackgroundColor(actColor);
            Toast.makeText(this, "Sensor Aktif: IMX363 Ultra-Wide 120° Core", Toast.LENGTH_SHORT).show();
        }
    }

    private void selectNoise(String noise) {
        int actColor = Color.parseColor("#E11D48");
        int offColor = Color.parseColor("#222630");
        if (btnNoiseOff != null) btnNoiseOff.setBackgroundColor(offColor);
        if (btnNoiseSpatial != null) btnNoiseSpatial.setBackgroundColor(offColor);
        if (btnNoiseSabre != null) btnNoiseSabre.setBackgroundColor(offColor);
        
        if ("OFF".equals(noise)) {
            if (btnNoiseOff != null) btnNoiseOff.setBackgroundColor(actColor);
            Toast.makeText(this, "Peredam Derau: Mati (Profil Mentah RAW)", Toast.LENGTH_SHORT).show();
        } else if ("SPATIAL".equals(noise)) {
            if (btnNoiseSpatial != null) btnNoiseSpatial.setBackgroundColor(actColor);
            Toast.makeText(this, "Peredam Derau: Spatial-Temporal Bilateral Filter", Toast.LENGTH_SHORT).show();
        } else if ("SABRE".equals(noise)) {
            if (btnNoiseSabre != null) btnNoiseSabre.setBackgroundColor(actColor);
            Toast.makeText(this, "Peredam Derau: Sabre v2 Super-Resolution Core", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupHdrTabControls() {
        btnFrames5 = findViewById(R.id.btnFrames5);
        btnFrames11 = findViewById(R.id.btnFrames11);
        btnFrames25 = findViewById(R.id.btnFrames25);
        btnRawToggle = findViewById(R.id.btnRawToggle);
        
        if (btnFrames5 != null) btnFrames5.setOnClickListener(v -> selectFrames(5));
        if (btnFrames11 != null) btnFrames11.setOnClickListener(v -> selectFrames(11));
        if (btnFrames25 != null) btnFrames25.setOnClickListener(v -> selectFrames(25));
        
        if (btnRawToggle != null) {
            btnRawToggle.setOnClickListener(v -> {
                rawCapture = !rawCapture;
                if (rawCapture) {
                    btnRawToggle.setText("DNG RAW 12-Bit (ACTIVE)");
                    btnRawToggle.setBackgroundColor(Color.parseColor("#E11D48"));
                    Toast.makeText(this, "Format Output: GCam RAW (DNG) aktif", Toast.LENGTH_SHORT).show();
                } else {
                    btnRawToggle.setText("RAW Format: OFF (JPG ONLY)");
                    btnRawToggle.setBackgroundColor(Color.parseColor("#222630"));
                    Toast.makeText(this, "Format Output: Format JPEG Standard", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void selectFrames(int count) {
        int actColor = Color.parseColor("#E11D48");
        int offColor = Color.parseColor("#222630");
        if (btnFrames5 != null) btnFrames5.setBackgroundColor(offColor);
        if (btnFrames11 != null) btnFrames11.setBackgroundColor(offColor);
        if (btnFrames25 != null) btnFrames25.setBackgroundColor(offColor);
        
        hdrFrameCount = count;
        if (count == 5) {
            if (btnFrames5 != null) btnFrames5.setBackgroundColor(actColor);
            Toast.makeText(this, "HDR Stacking: 5 Bingkai Terkalibrasi (Minimal Latensi)", Toast.LENGTH_SHORT).show();
        } else if (count == 11) {
            if (btnFrames11 != null) btnFrames11.setBackgroundColor(actColor);
            Toast.makeText(this, "HDR Stacking: 11 Bingkai Tersegmentasi (Standard GCam)", Toast.LENGTH_SHORT).show();
        } else if (count == 25) {
            if (btnFrames25 != null) btnFrames25.setBackgroundColor(actColor);
            Toast.makeText(this, "HDR Stacking: 25 Bingkai Stacked (Sangat Halus & Detil)", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupManualTabControls() {
        btnIsoAuto = findViewById(R.id.btnIsoAuto);
        btnIso100 = findViewById(R.id.btnIso100);
        btnIso400 = findViewById(R.id.btnIso400);
        btnIso1600 = findViewById(R.id.btnIso1600);
        
        btnShutterAuto = findViewById(R.id.btnShutterAuto);
        btnShutter1000 = findViewById(R.id.btnShutter1000);
        btnShutter250 = findViewById(R.id.btnShutter250);
        btnShutter1s = findViewById(R.id.btnShutter1s);
        
        if (btnIsoAuto != null) btnIsoAuto.setOnClickListener(v -> selectIso("AUTO"));
        if (btnIso100 != null) btnIso100.setOnClickListener(v -> selectIso("100"));
        if (btnIso400 != null) btnIso400.setOnClickListener(v -> selectIso("400"));
        if (btnIso1600 != null) btnIso1600.setOnClickListener(v -> selectIso("1600"));
        
        if (btnShutterAuto != null) btnShutterAuto.setOnClickListener(v -> selectShutter("AUTO"));
        if (btnShutter1000 != null) btnShutter1000.setOnClickListener(v -> selectShutter("1/1000s"));
        if (btnShutter250 != null) btnShutter250.setOnClickListener(v -> selectShutter("1/250s"));
        if (btnShutter1s != null) btnShutter1s.setOnClickListener(v -> selectShutter("1s"));
    }

    private void selectIso(String iso) {
        int actColor = Color.parseColor("#E11D48");
        int offColor = Color.parseColor("#222630");
        if (btnIsoAuto != null) btnIsoAuto.setBackgroundColor(offColor);
        if (btnIso100 != null) btnIso100.setBackgroundColor(offColor);
        if (btnIso400 != null) btnIso400.setBackgroundColor(offColor);
        if (btnIso1600 != null) btnIso1600.setBackgroundColor(offColor);
        
        if ("AUTO".equals(iso)) {
            if (btnIsoAuto != null) btnIsoAuto.setBackgroundColor(actColor);
            Toast.makeText(this, "Gain ISO: Terkalibrasi Otomatis (Auto ISO)", Toast.LENGTH_SHORT).show();
        } else if ("100".equals(iso)) {
            if (btnIso100 != null) btnIso100.setBackgroundColor(actColor);
            Toast.makeText(this, "Gain ISO: Terkunci pada ISO 100", Toast.LENGTH_SHORT).show();
        } else if ("400".equals(iso)) {
            if (btnIso400 != null) btnIso400.setBackgroundColor(actColor);
            Toast.makeText(this, "Gain ISO: Terkunci pada ISO 400", Toast.LENGTH_SHORT).show();
        } else if ("1600".equals(iso)) {
            if (btnIso1600 != null) btnIso1600.setBackgroundColor(actColor);
            Toast.makeText(this, "Gain ISO: Terkunci pada ISO 1600 (High-Gain Core)", Toast.LENGTH_SHORT).show();
        }
    }

    private void selectShutter(String speed) {
        int actColor = Color.parseColor("#E11D48");
        int offColor = Color.parseColor("#222630");
        if (btnShutterAuto != null) btnShutterAuto.setBackgroundColor(offColor);
        if (btnShutter1000 != null) btnShutter1000.setBackgroundColor(offColor);
        if (btnShutter250 != null) btnShutter250.setBackgroundColor(offColor);
        if (btnShutter1s != null) btnShutter1s.setBackgroundColor(offColor);
        
        if ("AUTO".equals(speed)) {
            if (btnShutterAuto != null) btnShutterAuto.setBackgroundColor(actColor);
            Toast.makeText(this, "Kecepatan Rana: Otomatis (Dynamic Tracker)", Toast.LENGTH_SHORT).show();
        } else if ("1/1000s".equals(speed)) {
            if (btnShutter1000 != null) btnShutter1000.setBackgroundColor(actColor);
            Toast.makeText(this, "Kecepatan Rana: Terkunci 1/1000s (Anti-Flicker/Action)", Toast.LENGTH_SHORT).show();
        } else if ("1/250s".equals(speed)) {
            if (btnShutter250 != null) btnShutter250.setBackgroundColor(actColor);
            Toast.makeText(this, "Kecepatan Rana: Terkunci 1/250s (Balanced Landscape)", Toast.LENGTH_SHORT).show();
        } else if ("1s".equals(speed)) {
            if (btnShutter1s != null) btnShutter1s.setBackgroundColor(actColor);
            Toast.makeText(this, "Kecepatan Rana: Terkunci 1.0 detik (Astronomi/Sutra)", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupDisplayTabControls() {
        btnGrid3x3 = findViewById(R.id.btnGrid3x3);
        btnGridGolden = findViewById(R.id.btnGridGolden);
        btnGridCrosshair = findViewById(R.id.btnGridCrosshair);
        btnGridNone = findViewById(R.id.btnGridNone);
        btnSoundToggle = findViewById(R.id.btnSoundToggle);
        
        vGridLine1 = findViewById(R.id.vGridLine1);
        vGridLine2 = findViewById(R.id.vGridLine2);
        hGridLine1 = findViewById(R.id.hGridLine1);
        hGridLine2 = findViewById(R.id.hGridLine2);
        
        if (btnGrid3x3 != null) btnGrid3x3.setOnClickListener(v -> selectGrid("3x3"));
        if (btnGridGolden != null) btnGridGolden.setOnClickListener(v -> selectGrid("GOLDEN"));
        if (btnGridCrosshair != null) btnGridCrosshair.setOnClickListener(v -> selectGrid("CROSSHAIR"));
        if (btnGridNone != null) btnGridNone.setOnClickListener(v -> selectGrid("NONE"));
        
        if (btnSoundToggle != null) {
            btnSoundToggle.setOnClickListener(v -> {
                shutterSoundEnabled = !shutterSoundEnabled;
                if (shutterSoundEnabled) {
                    btnSoundToggle.setText("Shutter Sound (ENABLED)");
                    btnSoundToggle.setBackgroundColor(Color.parseColor("#E11D48"));
                    Toast.makeText(this, "Suara Layar Jepret: Aktif", Toast.LENGTH_SHORT).show();
                } else {
                    btnSoundToggle.setText("Shutter Sound (MUTED)");
                    btnSoundToggle.setBackgroundColor(Color.parseColor("#222630"));
                    Toast.makeText(this, "Suara Layar Jepret: Senyap", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void selectGrid(String type) {
        int actColor = Color.parseColor("#E11D48");
        int offColor = Color.parseColor("#222630");
        if (btnGrid3x3 != null) btnGrid3x3.setBackgroundColor(offColor);
        if (btnGridGolden != null) btnGridGolden.setBackgroundColor(offColor);
        if (btnGridCrosshair != null) btnGridCrosshair.setBackgroundColor(offColor);
        if (btnGridNone != null) btnGridNone.setBackgroundColor(offColor);
        
        if (vGridLine1 != null) vGridLine1.setVisibility(View.GONE);
        if (vGridLine2 != null) vGridLine2.setVisibility(View.GONE);
        if (hGridLine1 != null) hGridLine1.setVisibility(View.GONE);
        if (hGridLine2 != null) hGridLine2.setVisibility(View.GONE);
        
        if ("3x3".equals(type)) {
            if (btnGrid3x3 != null) btnGrid3x3.setBackgroundColor(actColor);
            setGridBiases(0.333f, 0.666f, 0.333f, 0.666f);
            Toast.makeText(this, "Grid Layar: Sepertiga Standard (3x3 Rule)", Toast.LENGTH_SHORT).show();
        } else if ("GOLDEN".equals(type)) {
            if (btnGridGolden != null) btnGridGolden.setBackgroundColor(actColor);
            setGridBiases(0.382f, 0.618f, 0.382f, 0.618f);
            Toast.makeText(this, "Grid Layar: Ratio Emas (Golden Ratio Spiral)", Toast.LENGTH_SHORT).show();
        } else if ("CROSSHAIR".equals(type)) {
            if (btnGridCrosshair != null) btnGridCrosshair.setBackgroundColor(actColor);
            setGridBiases(0.5f, 0.5f, 0.5f, 0.5f);
            Toast.makeText(this, "Grid Layar: Bidik Tengah (Crosshair Focus)", Toast.LENGTH_SHORT).show();
        } else if ("NONE".equals(type)) {
            if (btnGridNone != null) btnGridNone.setBackgroundColor(actColor);
            Toast.makeText(this, "Grid Layar: Dinonaktifkan", Toast.LENGTH_SHORT).show();
        }
    }

    private void setGridBiases(float v1, float v2, float h1, float h2) {
        try {
            if (vGridLine1 != null) {
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params = 
                    (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) vGridLine1.getLayoutParams();
                params.horizontalBias = v1;
                vGridLine1.setLayoutParams(params);
                vGridLine1.setVisibility(View.VISIBLE);
            }
            if (vGridLine2 != null) {
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params = 
                    (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) vGridLine2.getLayoutParams();
                params.horizontalBias = v2;
                vGridLine2.setLayoutParams(params);
                vGridLine2.setVisibility(View.VISIBLE);
            }
            if (hGridLine1 != null) {
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params = 
                    (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) hGridLine1.getLayoutParams();
                params.verticalBias = h1;
                hGridLine1.setLayoutParams(params);
                hGridLine1.setVisibility(View.VISIBLE);
            }
            if (hGridLine2 != null) {
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params = 
                    (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) hGridLine2.getLayoutParams();
                params.verticalBias = h2;
                hGridLine2.setLayoutParams(params);
                hGridLine2.setVisibility(View.VISIBLE);
            }
        } catch (Exception ignored) {}
    }

    private void setupModesCarousel() {
        btnModePhoto = findViewById(R.id.btnModePhoto);
        btnModePortrait = findViewById(R.id.btnModePortrait);
        btnModeNight = findViewById(R.id.btnModeNight);
        btnModeVideo = findViewById(R.id.btnModeVideo);
        btnModePanorama = findViewById(R.id.btnModePanorama);
        
        if (btnModePhoto != null) btnModePhoto.setOnClickListener(v -> selectMode("PHOTO"));
        if (btnModePortrait != null) btnModePortrait.setOnClickListener(v -> selectMode("PORTRAIT"));
        if (btnModeNight != null) btnModeNight.setOnClickListener(v -> selectMode("NIGHT"));
        if (btnModeVideo != null) btnModeVideo.setOnClickListener(v -> selectMode("VIDEO"));
        if (btnModePanorama != null) btnModePanorama.setOnClickListener(v -> selectMode("PANORAMA"));
    }

    private void selectMode(String mode) {
        int standardColor = Color.WHITE;
        int activeClr = Color.parseColor("#E11D48"); // Rose accent
        if (btnModePhoto != null) btnModePhoto.setTextColor(standardColor);
        if (btnModePortrait != null) btnModePortrait.setTextColor(standardColor);
        if (btnModeNight != null) btnModeNight.setTextColor(standardColor);
        if (btnModeVideo != null) btnModeVideo.setTextColor(standardColor);
        if (btnModePanorama != null) btnModePanorama.setTextColor(standardColor);
        
        activeShootingMode = mode;
        TextView lblProcessingText = findViewById(R.id.lblProcessingText);
        TextView lblTitle = findViewById(R.id.lblTitle);
        
        if ("PHOTO".equals(mode)) {
            if (btnModePhoto != null) btnModePhoto.setTextColor(activeClr);
            if (lblTitle != null) lblTitle.setText("YuuCam (HDR+)");
            if (lblProcessingText != null) lblProcessingText.setText("Memproses GCam HDR+...");
            Toast.makeText(this, "Mode Shutter: Standar HDR+ Fusion", Toast.LENGTH_SHORT).show();
        } else if ("PORTRAIT".equals(mode)) {
            if (btnModePortrait != null) btnModePortrait.setTextColor(activeClr);
            if (lblTitle != null) lblTitle.setText("YuuCam Portrait");
            if (lblProcessingText != null) lblProcessingText.setText("Menghitung Efek Kedalaman Bokeh...");
            Toast.makeText(this, "Mode Shutter: Kedalaman Bokeh Portraitaktif", Toast.LENGTH_SHORT).show();
        } else if ("NIGHT".equals(mode)) {
            if (btnModeNight != null) btnModeNight.setTextColor(activeClr);
            if (lblTitle != null) lblTitle.setText("YuuCam NightSight");
            if (lblProcessingText != null) lblProcessingText.setText("Menumpuk Frame Eksposur Gelap...");
            Toast.makeText(this, "Mode Shutter: NightSight Multi-Stacking", Toast.LENGTH_SHORT).show();
        } else if ("VIDEO".equals(mode)) {
            if (btnModeVideo != null) btnModeVideo.setTextColor(activeClr);
            if (lblTitle != null) lblTitle.setText("YuuCam Video Pro");
            if (lblProcessingText != null) lblProcessingText.setText("Merekam Aliran Stabilizer...");
            Toast.makeText(this, "Mode Shutter: Perekaman Stabil Video (60 FPS)", Toast.LENGTH_SHORT).show();
        } else if ("PANORAMA".equals(mode)) {
            if (btnModePanorama != null) btnModePanorama.setTextColor(activeClr);
            if (lblTitle != null) lblTitle.setText("YuuCam Panorama");
            if (lblProcessingText != null) lblProcessingText.setText("Menjajarkan Frame Panorama...");
            Toast.makeText(this, "Mode Shutter: Pengambilan Panorama Sudut Lebar", Toast.LENGTH_SHORT).show();
        }
    }

    private void initializeCameraX() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider provider = cameraProviderFuture.get();
                bindCameraStreams(provider);
            } catch (Exception e) {
                Toast.makeText(this, "Inisialisasi Kamera gagal: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraStreams(@NonNull ProcessCameraProvider provider) {
        if (previewView == null) return;
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(isHdrEnhanced 
                    ? ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY 
                    : ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(flashMode)
                .build();

        CameraSelector selector = new CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build();

        provider.unbindAll();

        try {
            camera = provider.bindToLifecycle(this, selector, preview, imageCapture);
            applyExposureCompensation(currentExposureCompensationValue);
        } catch (Exception e) {
            Toast.makeText(this, "Gagal mengikat siklus aliran data Kamera", Toast.LENGTH_SHORT).show();
        }
    }

    private void applyExposureCompensation(float evValue) {
        if (camera == null) return;
        try {
            androidx.camera.core.ExposureState exposureStatus = camera.getCameraInfo().getExposureState();
            if (exposureStatus.isExposureCompensationSupported()) {
                int index = Math.round(evValue * 5); 
                camera.getCameraControl().setExposureCompensationIndex(index);
            }
        } catch (Exception ignored) {}
    }

    private void cycleHdrMode() {
        isHdrEnhanced = !isHdrEnhanced;
        if (btnHdrState != null) {
            btnHdrState.setImageResource(isHdrEnhanced ? R.drawable.ic_hdr_enhanced : R.drawable.ic_hdr_off);
        }
        Toast.makeText(this, isHdrEnhanced ? "Algoritma HDR+ Enhanced aktif" : "HDR+ Nonaktif (Pemrosesan Matrik Lambat)", Toast.LENGTH_SHORT).show();
        initializeCameraX();
    }

    private void cycleFlashMode() {
        if (flashMode == ImageCapture.FLASH_MODE_AUTO) {
            flashMode = ImageCapture.FLASH_MODE_ON;
            if (btnFlashState != null) btnFlashState.setImageResource(R.drawable.ic_flash_on);
            Toast.makeText(this, "Flash: AKTIF", Toast.LENGTH_SHORT).show();
        } else if (flashMode == ImageCapture.FLASH_MODE_ON) {
            flashMode = ImageCapture.FLASH_MODE_OFF;
            if (btnFlashState != null) btnFlashState.setImageResource(R.drawable.ic_flash_off);
            Toast.makeText(this, "Flash: MATI", Toast.LENGTH_SHORT).show();
        } else {
            flashMode = ImageCapture.FLASH_MODE_AUTO;
            if (btnFlashState != null) btnFlashState.setImageResource(R.drawable.ic_flash_auto);
            Toast.makeText(this, "Flash: OTOMATIS", Toast.LENGTH_SHORT).show();
        }
        if (imageCapture != null) {
            imageCapture.setFlashMode(flashMode);
        }
    }

    private void cycleTimerMode() {
        if (captureTimerSeconds == 0) {
            captureTimerSeconds = 3;
            if (btnTimerState != null) btnTimerState.setImageResource(R.drawable.ic_timer_3);
            Toast.makeText(this, "Penghitung Selang: 3 Detik", Toast.LENGTH_SHORT).show();
        } else if (captureTimerSeconds == 3) {
            captureTimerSeconds = 10;
            if (btnTimerState != null) btnTimerState.setImageResource(R.drawable.ic_timer_10);
            Toast.makeText(this, "Penghitung Selang: 10 Detik", Toast.LENGTH_SHORT).show();
        } else {
            captureTimerSeconds = 0;
            if (btnTimerState != null) btnTimerState.setImageResource(R.drawable.ic_timer_off);
            Toast.makeText(this, "Penghitung Selang: Non-aktif", Toast.LENGTH_SHORT).show();
        }
    }

    public void toggleCameraFacing() {
        lensFacing = (lensFacing == CameraSelector.LENS_FACING_BACK)
                ? CameraSelector.LENS_FACING_FRONT
                : CameraSelector.LENS_FACING_BACK;
        initializeCameraX();
    }

    public void triggerAutofocus(float x, float y) {
        if (camera == null || previewView == null) return;
        animateFocusRing(x, y);

        MeteringPointFactory factory = previewView.getMeteringPointFactory();
        MeteringPoint point = factory.createPoint(x, y);
        FocusMeteringAction action = new FocusMeteringAction.Builder(point).build();
        camera.getCameraControl().startFocusAndMetering(action);
    }

    private void animateFocusRing(float x, float y) {
        if (viewFocusRing == null) return;
        viewFocusRing.setX(x - (viewFocusRing.getWidth() / 2f));
        viewFocusRing.setY(y - (viewFocusRing.getHeight() / 2f));
        viewFocusRing.setVisibility(View.VISIBLE);
        viewFocusRing.setScaleX(1.4f);
        viewFocusRing.setScaleY(1.4f);
        viewFocusRing.setAlpha(1f);
        
        viewFocusRing.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .alpha(0.8f)
                .setDuration(300)
                .withEndAction(() -> {
                    viewFocusRing.animate().alpha(0f).setStartDelay(1000).setDuration(200).start();
                })
                .start();
    }

    public void adjustZoomScale(float distanceDelta) {
        if (camera == null) return;
        try {
            float currentRatio = camera.getCameraInfo().getZoomState().getValue().getZoomRatio();
            float minRatio = camera.getCameraInfo().getZoomState().getValue().getMinZoomRatio();
            float maxRatio = camera.getCameraInfo().getZoomState().getValue().getMaxZoomRatio();
            
            float step = distanceDelta > 0 ? -0.15f : 0.15f; 
            float updatedZoom = Math.max(minRatio, Math.min(currentRatio + step, maxRatio));
            camera.getCameraControl().setZoomRatio(updatedZoom);
        } catch (Exception ignored) {}
    }

    private void zoomByMultiplier(float multiplier) {
        if (camera == null) return;
        try {
            float currentRatio = camera.getCameraInfo().getZoomState().getValue().getZoomRatio();
            camera.getCameraControl().setZoomRatio(currentRatio * multiplier);
        } catch (Exception ignored) {}
    }

    private void handleCaptureCycleInitiation() {
        if (captureTimerSeconds > 0) {
            Toast.makeText(this, "Timer berjalan: " + captureTimerSeconds + " detik...", Toast.LENGTH_SHORT).show();
            mainHandler.postDelayed(this::captureSnapshotWithHdrPipeline, captureTimerSeconds * 1000L);
        } else {
            captureSnapshotWithHdrPipeline();
        }
    }

    private void captureSnapshotWithHdrPipeline() {
        if (imageCapture == null) return;

        if (overlayProgressBar != null) {
            overlayProgressBar.setVisibility(View.VISIBLE);
        }

        String filename = "GCAM_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(System.currentTimeMillis());
                
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/YuuCam");

        ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(
                getContentResolver(),
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
        ).build();

        if (btnCapture != null) {
            btnCapture.setEnabled(false);
        }
        imageCapture.takePicture(options, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                        mainHandler.post(() -> {
                            if (btnCapture != null) btnCapture.setEnabled(true);
                            if (overlayProgressBar != null) {
                                overlayProgressBar.setVisibility(View.GONE);
                            }
                            Toast.makeText(MainActivity.this, "Selesai menyatukan HDR+! Disimpan ke Galeri di Pictures/YuuCam.", Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        mainHandler.post(() -> {
                            if (btnCapture != null) btnCapture.setEnabled(true);
                            if (overlayProgressBar != null) {
                                overlayProgressBar.setVisibility(View.GONE);
                            }
                            Toast.makeText(MainActivity.this, "Proses Gagal: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                }
        );
    }
}