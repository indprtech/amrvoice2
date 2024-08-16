package com.prtech.amrvoice2;
import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

@RequiresApi(api = Build.VERSION_CODES.S)
public class VoiceControlActivity extends AppCompatActivity {
    public static final String DEVICE_NAME = "device_name";
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_TOAST = 5;
    public static final int MESSAGE_WRITE = 3;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_ENABLE_BT = 3;
    private static final String TAG = "BluetoothBT";
    public static final String TOAST = "toast";
    private static final int VOICE_RECOGNITION_REQUEST_CODE = 1001;
    private BtService mBTService = null;
    private BluetoothAdapter mBluetoothAdapter = null;
    private String mConnectedDeviceName = null;

    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean bluetoothConnectGranted = result.getOrDefault(Manifest.permission.BLUETOOTH_CONNECT, false);
                Boolean bluetoothScanGranted = result.getOrDefault(Manifest.permission.BLUETOOTH_SCAN, false);

                if (bluetoothConnectGranted != null && bluetoothConnectGranted &&
                        bluetoothScanGranted != null && bluetoothScanGranted) {
                    initializeBluetooth();
                } else {
                    Toast.makeText(this, "Bluetooth permissions are required.", Toast.LENGTH_SHORT).show();
                }
            });

    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BtService.STATE_CONNECTED:
                            Log.i(TAG, "Connected to " + mConnectedDeviceName);
                            break;
                        case BtService.STATE_CONNECTING:
                            Log.i(TAG, "Connecting...");
                            break;
                        case BtService.STATE_LISTEN:
                        case BtService.STATE_NONE:
                            Log.i(TAG, "Not connected");
                            break;
                    }
                    break;
                case MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    Log.d(TAG, "Received: " + readMessage);
                    break;
                case MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    String writeMessage = new String(writeBuf);
                    Log.d(TAG, "Sent: " + writeMessage);
                    break;
                case MESSAGE_DEVICE_NAME:
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    Toast.makeText(getApplicationContext(), "Connected to " + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST), Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    private StringBuffer mOutStringBuffer;
    public String msg;
    public String voiceData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.voice);
       // setRequestedOrientation(1); // Consider changing to Android's constant, like ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkAndRequestPermissions();
        } else {
            initializeBluetooth();
        }

        setupBT();
        checkVoiceRecognition();

        Button connectDeviceButton = findViewById(R.id.connect_device_button);
        connectDeviceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Open DeviceListActivity when the button is clicked
                Intent intent = new Intent(VoiceControlActivity.this, DeviceListActivity.class);
                startActivityForResult(intent, REQUEST_CONNECT_DEVICE_SECURE);
            }
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private void checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionsLauncher.launch(new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
            });
        } else {
            initializeBluetooth();
        }
    }

    @SuppressLint("MissingPermission")
    private void initializeBluetooth() {
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void onStart() {
        super.onStart();
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else if (mBTService == null) {
            setupBT();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mBTService != null && mBTService.getState() == BtService.STATE_NONE) {
            mBTService.start();
        }
    }

    private void setupBT() {
        Log.d(TAG, "setupBT()");
        mBTService = new BtService(this, mHandler);
        mOutStringBuffer = new StringBuffer();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBluetoothAdapter.disable();
        if (mBTService != null) {
            mBTService.stop();
        }
    }

    private void sendMessage(String message) {
        if (mBTService.getState() == BtService.STATE_CONNECTED && !message.isEmpty()) {
            mBTService.write(message.getBytes());
            mOutStringBuffer.setLength(0);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE_SECURE:
                if (resultCode == RESULT_OK) {
                    connectDevice(data, true);
                }
                break;
            case REQUEST_CONNECT_DEVICE_INSECURE:
                if (resultCode == RESULT_OK) {
                    connectDevice(data, false);
                }
                break;
            case REQUEST_ENABLE_BT:
                if (resultCode == RESULT_OK) {
                    setupBT();
                } else {
                    Log.d(TAG, "BT not enabled");
                    finish();
                }
                break;
            case VOICE_RECOGNITION_REQUEST_CODE:
                if (resultCode == RESULT_OK && data != null) {
                    voiceData = data.getStringArrayListExtra("android.speech.extra.RESULTS").get(0);
                    showToastMessage(voiceData);
                    msg = String.format("*%s#", voiceData);
                    if (mConnectedDeviceName != null) {
                        sendMessage(msg);
                    }
                } else {
                    handleVoiceRecognitionErrors(resultCode);
                }
                break;
        }
    }

    @SuppressLint("MissingPermission")
    private void connectDevice(Intent data, boolean secure) {
        String address = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        }
        if (address != null) {
            mBTService.connect(mBluetoothAdapter.getRemoteDevice(address), secure);
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    private void checkVoiceRecognition() {
        if (getPackageManager().queryIntentActivities(new Intent("android.speech.action.RECOGNIZE_SPEECH"), 0).isEmpty()) {
            Toast.makeText(this, "Voice recognizer not present. Please install Google Voice.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    public void speak(View view) {
        if (mConnectedDeviceName != null) {
            Intent intent = new Intent("android.speech.action.RECOGNIZE_SPEECH");
            intent.putExtra("calling_package", getClass().getPackage().getName());
            intent.putExtra("android.speech.extra.PROMPT", "VC the Arudino");
            intent.putExtra("android.speech.extra.LANGUAGE_MODEL", "web_search");
            intent.putExtra("android.speech.extra.MAX_RESULTS", 1);
            startActivityForResult(intent, VOICE_RECOGNITION_REQUEST_CODE);
        } else {
            showToastMessage("First Connect a Bluetooth Device!");
        }
    }

    private void handleVoiceRecognitionErrors(int resultCode) {
        switch (resultCode) {
            case RecognizerIntent.RESULT_AUDIO_ERROR:
                showToastMessage("Audio Error");
                break;
            case RecognizerIntent.RESULT_CLIENT_ERROR:
                showToastMessage("Client Error");
                break;
            case RecognizerIntent.RESULT_NETWORK_ERROR:
                showToastMessage("Network Error");
                break;
            case RecognizerIntent.RESULT_NO_MATCH:
                showToastMessage("No Match");
                break;
            case RecognizerIntent.RESULT_SERVER_ERROR:
                showToastMessage("Server Error");
                break;
            default:
                showToastMessage("Unknown error occurred");
                break;
        }
    }

    private void showToastMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
