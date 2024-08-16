package com.prtech.amrvoice2;

import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BtService extends Service {
    private static final boolean D = true;
    /* access modifiers changed from: private */
    public static final UUID MY_UUID_SECURE = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    private static final String NAME_SECURE = "BluetoothBTSecure";
    public static final int STATE_CONNECTED = 3;
    public static final int STATE_CONNECTING = 2;
    public static final int STATE_LISTEN = 1;
    public static final int STATE_NONE = 0;
    private static final String TAG = "BtService";
    /* access modifiers changed from: private */
    public final BluetoothAdapter mAdapter = BluetoothAdapter.getDefaultAdapter();
    /* access modifiers changed from: private */
    public ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    /* access modifiers changed from: private */
    public final Handler mHandler;
    private AcceptThread mSecureAcceptThread;
    /* access modifiers changed from: private */
    public int mState = 0;

    public BtService(Context context, Handler handler) {
        this.mHandler = handler;
    }

    private synchronized void setState(int state) {
        Log.d(TAG, "setState() " + this.mState + " -> " + state);
        this.mState = state;
        this.mHandler.obtainMessage(1, state, -1).sendToTarget();
    }

    public synchronized int getState() {
        return this.mState;
    }

    public synchronized void start() {
        Log.d(TAG, "start");
        if (this.mConnectThread != null) {
            this.mConnectThread.cancel();
            this.mConnectThread = null;
        }
        if (this.mConnectedThread != null) {
            this.mConnectedThread.cancel();
            this.mConnectedThread = null;
        }
        setState(1);
        if (this.mSecureAcceptThread == null) {
            this.mSecureAcceptThread = new AcceptThread(D);
            this.mSecureAcceptThread.start();
        }
    }

    public synchronized void connect(BluetoothDevice device, boolean secure) {
        Log.d(TAG, "connect to: " + device);
        if (this.mState == 2 && this.mConnectThread != null) {
            this.mConnectThread.cancel();
            this.mConnectThread = null;
        }
        if (this.mConnectedThread != null) {
            this.mConnectedThread.cancel();
            this.mConnectedThread = null;
        }
        this.mConnectThread = new ConnectThread(device, secure);
        this.mConnectThread.start();
        setState(2);
    }

    @SuppressLint("MissingPermission")
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device, String socketType) {
        Log.d(TAG, "connected, Socket Type:" + socketType);
        if (this.mConnectThread != null) {
            this.mConnectThread.cancel();
            this.mConnectThread = null;
        }
        if (this.mConnectedThread != null) {
            this.mConnectedThread.cancel();
            this.mConnectedThread = null;
        }
        if (this.mSecureAcceptThread != null) {
            this.mSecureAcceptThread.cancel();
            this.mSecureAcceptThread = null;
        }
        this.mConnectedThread = new ConnectedThread(socket, socketType);
        this.mConnectedThread.start();
        Message msg = this.mHandler.obtainMessage(4);
        Bundle bundle = new Bundle();
        bundle.putString(VoiceControlActivity.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        this.mHandler.sendMessage(msg);
        setState(3);
    }

    public synchronized void stop() {
        Log.d(TAG, "stop");
        if (this.mConnectThread != null) {
            this.mConnectThread.cancel();
            this.mConnectThread = null;
        }
        if (this.mConnectedThread != null) {
            this.mConnectedThread.cancel();
            this.mConnectedThread = null;
        }
        if (this.mSecureAcceptThread != null) {
            this.mSecureAcceptThread.cancel();
            this.mSecureAcceptThread = null;
        }
        setState(0);
    }

    public void write(byte[] out) {
        synchronized (this) {
            if (this.mState == 3) {
                ConnectedThread r = this.mConnectedThread;
                r.write(out);
            }
        }
    }

    /* access modifiers changed from: private */
    public void connectionFailed() {
        Message msg = this.mHandler.obtainMessage(5);
        Bundle bundle = new Bundle();
        bundle.putString(VoiceControlActivity.TOAST, "Unable to connect device");
        msg.setData(bundle);
        this.mHandler.sendMessage(msg);
        start();
    }

    /* access modifiers changed from: private */
    public void connectionLost() {
        Message msg = this.mHandler.obtainMessage(5);
        Bundle bundle = new Bundle();
        bundle.putString(VoiceControlActivity.TOAST, "Device connection was lost");
        msg.setData(bundle);
        this.mHandler.sendMessage(msg);
        start();
    }

    private class AcceptThread extends Thread {
        private String mSocketType = "Secure";
        private final BluetoothServerSocket mmServerSocket;

        @SuppressLint("MissingPermission")
        public AcceptThread(boolean secure) {
            BluetoothServerSocket tmp = null;
            if (secure) {
                try {
                    tmp = BtService.this.mAdapter.listenUsingRfcommWithServiceRecord(BtService.NAME_SECURE, BtService.MY_UUID_SECURE);
                } catch (IOException e) {
                    Log.e(BtService.TAG, "Socket Type: " + this.mSocketType + "listen() failed", e);
                }
            }
            this.mmServerSocket = tmp;
        }

        public void run() {
            Log.d(BtService.TAG, "Socket Type: " + this.mSocketType + "BEGIN mAcceptThread" + this);
            setName("AcceptThread" + this.mSocketType);
            while (BtService.this.mState != 3) {
                try {
                    BluetoothSocket socket = this.mmServerSocket.accept();
                    if (socket != null) {
                        synchronized (BtService.this) {
                            switch (BtService.this.mState) {
                                case 0:
                                case 3:
                                    try {
                                        socket.close();
                                        break;
                                    } catch (IOException e) {
                                        Log.e(BtService.TAG, "Could not close unwanted socket", e);
                                        break;
                                    }
                                case 1:
                                case 2:
                                    BtService.this.connected(socket, socket.getRemoteDevice(), this.mSocketType);
                                    break;
                            }
                        }
                    }
                } catch (IOException e2) {
                    Log.e(BtService.TAG, "Socket Type: " + this.mSocketType + "accept() failed", e2);
                }
            }
            Log.i(BtService.TAG, "END mAcceptThread, socket Type: " + this.mSocketType);
            return;
        }

        public void cancel() {
            Log.d(BtService.TAG, "Socket Type" + this.mSocketType + "cancel " + this);
            try {
                this.mmServerSocket.close();
            } catch (IOException e) {
                Log.e(BtService.TAG, "Socket Type" + this.mSocketType + "close() of server failed", e);
            }
        }
    }

    private class ConnectThread extends Thread {
        private String mSocketType = "Secure";
        private final BluetoothDevice mmDevice;
        private final BluetoothSocket mmSocket;

        @SuppressLint("MissingPermission")
        public ConnectThread(BluetoothDevice device, boolean secure) {
            this.mmDevice = device;
            BluetoothSocket tmp = null;
            if (secure) {
                try {
                    tmp = device.createRfcommSocketToServiceRecord(BtService.MY_UUID_SECURE);
                } catch (IOException e) {
                    Log.e(BtService.TAG, "Socket Type: " + this.mSocketType + "create() failed", e);
                }
            }
            this.mmSocket = tmp;
        }

        @SuppressLint("MissingPermission")
        public void run() {
            Log.i(BtService.TAG, "BEGIN mConnectThread SocketType:" + this.mSocketType);
            setName("ConnectThread" + this.mSocketType);
            BtService.this.mAdapter.cancelDiscovery();
            try {
                this.mmSocket.connect();
                synchronized (BtService.this) {
                    BtService.this.mConnectThread = null;
                }
                BtService.this.connected(this.mmSocket, this.mmDevice, this.mSocketType);
            } catch (IOException e) {
                try {
                    this.mmSocket.close();
                } catch (IOException e2) {
                    Log.e(BtService.TAG, "unable to close() " + this.mSocketType + " socket during connection failure", e2);
                }
                BtService.this.connectionFailed();
            }
        }

        public void cancel() {
            try {
                this.mmSocket.close();
            } catch (IOException e) {
                Log.e(BtService.TAG, "close() of connect " + this.mSocketType + " socket failed", e);
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private final BluetoothSocket mmSocket;

        public ConnectedThread(BluetoothSocket socket, String socketType) {
            Log.d(BtService.TAG, "create ConnectedThread: " + socketType);
            this.mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(BtService.TAG, "temp sockets not created", e);
            }
            this.mmInStream = tmpIn;
            this.mmOutStream = tmpOut;
        }

        public void run() {
            Log.i(BtService.TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            while (true) {
                try {
                    BtService.this.mHandler.obtainMessage(2, this.mmInStream.read(buffer), -1, buffer).sendToTarget();
                } catch (IOException e) {
                    Log.e(BtService.TAG, "disconnected", e);
                    BtService.this.connectionLost();
                    BtService.this.start();
                    return;
                }
            }
        }

        public void write(byte[] buffer) {
            try {
                this.mmOutStream.write(buffer);
                BtService.this.mHandler.obtainMessage(3, -1, -1, buffer).sendToTarget();
            } catch (IOException e) {
                Log.e(BtService.TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                this.mmSocket.close();
            } catch (IOException e) {
                Log.e(BtService.TAG, "close() of connect socket failed", e);
            }
        }
    }

    public IBinder onBind(Intent arg0) {
        return null;
    }
}
