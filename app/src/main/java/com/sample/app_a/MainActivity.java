package com.sample.app_a;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.security.Permission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class MainActivity extends AppCompatActivity {

    public static final String TAG = MainActivity.class.getSimpleName() + "bhaskar";
    private static final int REQUEST_ENABLE_BT = 00;
    private static final int REQUEST_BT_PERMISSIONS = 01;
    private static final int REQUEST_BT_DISCOVERABLE_MODE = 02;


    TextView connectionStatus;
    Spinner flavorSel;
    EditText remoteAppsSelection;
    Button refreshApp;
    BluetoothAdapter bluetoothAdapter;

    private BluetoothLeScanner bluetoothLeScanner =
            BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();
    private boolean mScanning;
    private Handler handler = new Handler();

    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;

    public ArrayList <BluetoothDevice>  leDeviceList = new ArrayList<>();

    BluetoothDevice targetDevice;
    String sampleDeviceMacAddress = "00:11:22:33:AA:BB";

    List<BluetoothGattService> gattServicesList; // = new ArrayList();


    PackageManager pm;

    /**
     * Name of the connected device
     */
    private String mConnectedDeviceName = null;

    /**
     * String buffer for outgoing messages
     */
    private StringBuffer mOutStringBuffer;


    /**
     * Member object for the chat services
     */
    private VendingMachineBtService mChatService = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        pm = getPackageManager();

        // Get the default adapter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if(bluetoothAdapter == null){
            Log.d(TAG, "Device doesn't support Bluetooth");
        }

        setupViews();

        // Register for broadcasts when a device is discovered.
/*        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filter);*/
    }

    // Create a BroadcastReceiver for ACTION_FOUND.
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address
            }
        }
    };

    public void setupViews(){

        connectionStatus = findViewById(R.id.textview_connection_status);
        flavorSel = findViewById(R.id.spinner_flavor_selection);
        remoteAppsSelection = findViewById(R.id.edittext_remote_app_selection);
        refreshApp = findViewById(R.id.button_refresh_app);

    }

    void checkForBluetoothPermissions(){

        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.M){
            int hasLocPerm = pm.checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION, getPackageName());
            int hasBtPerm = pm.checkPermission(Manifest.permission.BLUETOOTH, getPackageName());
            int hasBtAdminPerm = pm.checkPermission(Manifest.permission.BLUETOOTH_ADMIN, getPackageName());

            if(!((hasLocPerm==PackageManager.PERMISSION_GRANTED)&&(hasBtPerm==PackageManager.PERMISSION_GRANTED)&&(hasBtAdminPerm==PackageManager.PERMISSION_GRANTED))){
                ActivityCompat.requestPermissions(this, new String[] {
                    Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN }, REQUEST_BT_PERMISSIONS);
            }
        }

    }

    void checkForBluetoothSupport(){
        // Use this check to determine whether BLE is supported on the device. Then
        // you can selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            Toast.makeText(this, R.string.bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }
    }


    @Override
    protected void onResume() {
        super.onResume();

        //checkForBluetoothSupport();
        //checkForBluetoothPermissions();
        //scanDevices();

        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == VendingMachineBtService.STATE_NONE) {
                // Start the Bluetooth chat services
                mChatService.start();
            }
        }

    }

/*    void scanDevices(){
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        if (pairedDevices.size() > 0) {
            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address
            }
        }
    }*/


    @Override
    protected void onStart() {
        super.onStart();

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }else if(mChatService == null) {
            setupCommunication();
        }

    }

    void setupCommunication(){

        flavorSel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String text = flavorSel.getSelectedItem().toString();
                sendMessage(text);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new VendingMachineBtService(this, mHandler);

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");


    }

    /**
     * Sends a message.
     *
     * @param message A string of text to send.
     */
    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mChatService.getState() != VendingMachineBtService.STATE_CONNECTED) {
            //Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mChatService.write(send);

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
            //mOutEditText.setText(mOutStringBuffer);
        }
    }

    /**
     * The Handler that gets information back from the BluetoothChatService
     */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            //FragmentActivity activity = getActivity();
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case VendingMachineBtService.STATE_CONNECTED:
                            //setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
                            //mConversationArrayAdapter.clear();
                            connectionStatus.append(" connected to" + mConnectedDeviceName);
                            break;
                        case VendingMachineBtService.STATE_CONNECTING:
                            //setStatus(R.string.title_connecting);
                            break;
                        case VendingMachineBtService.STATE_LISTEN:
                        case VendingMachineBtService.STATE_NONE:
                            //setStatus(R.string.title_not_connected);
                            break;
                    }
                    break;
                case Constants.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    //mConversationArrayAdapter.add("Me:  " + writeMessage);
                    //remoteAppsSelection.setText(writeMessage);
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    //mConversationArrayAdapter.add(mConnectedDeviceName + ":  " + readMessage);
                    remoteAppsSelection.setText(readMessage);
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                   /* if (null != activity) {
                        Toast.makeText(getApplicationContext(), "Connected to "
                                + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    }*/
                    break;
                case Constants.MESSAGE_TOAST:
                    /*if (null != activity) {
                        Toast.makeText(getApplicationContext(), msg.getData().getString(Constants.TOAST),
                                Toast.LENGTH_SHORT).show();
                    }*/
                    break;
            }
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Bluetooth enabled", Toast.LENGTH_SHORT).show();
            } else if(resultCode == RESULT_CANCELED) {
                connectionStatus.append(" BT Disabled!!");
            }
        }

        if(requestCode == REQUEST_BT_DISCOVERABLE_MODE){
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "BT Discoverable mode enabled");
            } else if(resultCode == RESULT_CANCELED) {
                connectionStatus.append(" BT Not Discoverable!!");
            }
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(grantResults.length > 2 ){
            if((grantResults[0] == PackageManager.PERMISSION_GRANTED)&(grantResults[1] == PackageManager.PERMISSION_GRANTED)&(grantResults[2] == PackageManager.PERMISSION_GRANTED)){
                Toast.makeText(this, "All Bluetooth permissions granted", Toast.LENGTH_SHORT).show();
            }else{
                Toast.makeText(this, "Bluetooth permissions needed for app to work", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        //unregisterReceiver(receiver);
    }



}