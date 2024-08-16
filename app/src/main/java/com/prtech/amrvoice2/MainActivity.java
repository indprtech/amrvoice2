package com.prtech.amrvoice2;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.prtech.amrvoice2.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;

    // Request Bluetooth permissions
    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                @SuppressLint("InlinedApi") Boolean bluetoothConnectGranted = result.getOrDefault(Manifest.permission.BLUETOOTH_CONNECT, false);
                @SuppressLint("InlinedApi") Boolean bluetoothScanGranted = result.getOrDefault(Manifest.permission.BLUETOOTH_SCAN, false);

                if (bluetoothConnectGranted != null && bluetoothConnectGranted &&
                        bluetoothScanGranted != null && bluetoothScanGranted) {
                    Toast.makeText(this, "Bluetooth permissions granted.", Toast.LENGTH_SHORT).show();
                    initializeBluetooth();
                } else {
                    Toast.makeText(this, "Bluetooth permissions are required.", Toast.LENGTH_SHORT).show();
                    showPermissionsRequiredDialog();
                }
            });

    @SuppressLint({"InlinedApi", "MissingPermission"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        com.prtech.amrvoice2.databinding.ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (this.bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available on this device.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        checkAndRequestPermissions();

        setTimeout(() -> {
            Toast.makeText(this, "Timeout reached, proceeding...", Toast.LENGTH_SHORT).show();
        }, 1000);
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private void checkAndRequestPermissions() {
        // Verbose check for Bluetooth Connect permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Requesting BLUETOOTH_CONNECT permission...", Toast.LENGTH_SHORT).show();
            requestPermissionsLauncher.launch(new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN});
            return;
        }

        // Verbose check for Bluetooth Scan permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Requesting BLUETOOTH_SCAN permission...", Toast.LENGTH_SHORT).show();
            requestPermissionsLauncher.launch(new String[]{Manifest.permission.BLUETOOTH_SCAN});
            return;
        }

        // If permissions are already granted
        Toast.makeText(this, "All required permissions are granted.", Toast.LENGTH_SHORT).show();
        initializeBluetooth();
    }

    @SuppressLint("MissingPermission")
    private void initializeBluetooth() {
        // Check if Bluetooth is enabled
        if (!this.bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Bluetooth is not enabled. Enabling Bluetooth...", Toast.LENGTH_SHORT).show();
            this.bluetoothAdapter.enable();

            // Delay a bit to give time for Bluetooth to enable
            setTimeout(this::navigateToDeviceListActivity, 3000); // Adjust delay as needed
        } else {
            Toast.makeText(this, "Bluetooth is already enabled.", Toast.LENGTH_SHORT).show();
            navigateToDeviceListActivity();
        }
    }

    private void navigateToDeviceListActivity() {
        Intent intent = new Intent(MainActivity.this, VoiceControlActivity.class);
        startActivity(intent);
        finish();
    }

    private void showPermissionsRequiredDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Bluetooth Permissions Required")
                .setMessage("This app requires Bluetooth permissions to function properly. Please grant the necessary permissions.")
                .setPositiveButton("OK", (dialog, which) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        checkAndRequestPermissions();
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    Toast.makeText(this, "Permissions denied. Exiting app...", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .create()
                .show();
    }

    public static void setTimeout(Runnable runnable, int delay) {
        new Thread(() -> {
            try {
                Thread.sleep(delay);
                runnable.run();
            } catch (Exception e) {
                System.err.println(e.toString());
            }
        }).start();
    }
}
