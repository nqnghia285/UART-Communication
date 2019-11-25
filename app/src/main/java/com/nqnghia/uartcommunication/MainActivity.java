package com.nqnghia.uartcommunication;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.UartDevice;
import com.google.android.things.pio.UartDeviceCallback;

import java.io.IOException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Skeleton of an Android Things activity.
 * <p>
 * Android Things peripheral APIs are accessible through the class
 * PeripheralManagerService. For example, the snippet below will open a GPIO pin and
 * set it to HIGH:
 *
 * <pre>{@code
 * PeripheralManagerService service = new PeripheralManagerService();
 * mLedGpio = service.openGpio("BCM6");
 * mLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
 * mLedGpio.setValue(true);
 * }</pre>
 * <p>
 * For more complex peripherals, look for an existing user-space driver, or implement one if none
 * is available.
 *
 * @see <a href="https://github.com/androidthings/contrib-drivers#readme">https://github.com/androidthings/contrib-drivers#readme</a>
 */
public class MainActivity extends AppCompatActivity {
    // UART Configuration Parameters
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String UART_DEVICE_NAME = "UART0";
    // UART Configuration Parameters
    private static final int BAUD_RATE1 = 9600;
    private static final int BAUD_RATE2 = 115200;
    private static final int DATA_BITS = 8;
    private static final int STOP_BITS = 1;

    private static final int CHUNK_SIZE = 32;
    private static final String MESSAGE_ON = "ON\r\n";
    private static final String MESSAGE_OFF = "OFF\r\n";
    private static final int BUFFER_SIZE = 32;
    private static final int MIN_TIME = 100; // a time is 100 ms

    private int duration;
    private byte[] buffer;
    private PeripheralManager mService;
    private UartDevice uartDevice;
    private Timer aTimer;
    private Boolean LedFlag;
    private Boolean TimerFlag;
    private int timerCounter;
    private StringBuffer data;

    private static Integer count = 0;

    private TextView textLedStatus;
    private TextView textData;
    private Button btnToggle;
    private Button btnON;
    private Button btnOFF;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        init();
        /////////////////////////////////////
        textLedStatus = findViewById(R.id.textViewLedStatus);
        textData = findViewById(R.id.textViewData);

        btnToggle = findViewById(R.id.btnToggle);
        btnToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                aTimer.cancel();
                aTimer.purge();
                aTimer = new Timer();
                aTimer.schedule(new Task(), 0, MIN_TIME);
            }
        });

        btnON = findViewById(R.id.btnON);
        btnON.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                aTimer.cancel();
                aTimer.purge();
                try {
                    writeUartData(uartDevice, MESSAGE_ON);
                    textLedStatus.setText("ON");
                } catch (IOException e) {
                    Log.e(TAG, "ERROR send message", e);
                }
            }
        });

        btnOFF = findViewById(R.id.btnOFF);
        btnOFF.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                aTimer.cancel();
                aTimer.purge();
                try {
                    writeUartData(uartDevice, MESSAGE_OFF);
                    textLedStatus.setText("OFF");
                } catch (IOException e) {
                    Log.e(TAG, "ERROR send message", e);
                }
            }
        });
    }

    public void init() {
        duration = 5;
        timerCounter = duration;
        buffer = new byte[BUFFER_SIZE];
        data = new StringBuffer();
        LedFlag = false;
        TimerFlag = false;
        aTimer = new Timer();
        mService = PeripheralManager.getInstance();
        List<String> deviceList = mService.getUartDeviceList();
        if (deviceList.isEmpty()) {
            Log.i(TAG, "No UART port available on this device.");
        } else {
            Log.i(TAG, "List of available devices: " + deviceList);
            // Mo thiet bi uart
            try {
                openUart(UART_DEVICE_NAME, BAUD_RATE1);
                if(uartDevice != null) {
                    Log.d("OpenUartDevice", "Success.");
                    setupTask();
                }
            } catch (IOException e) {
                Log.e(TAG, "Unable to open UART device", e);
            }
        }
    }

    private void timerRun() {
        if(timerCounter > 0) {
            timerCounter--;
        }
        if(timerCounter <= 0) {
            TimerFlag = true;
        }
    }

    private void setTimer() {
        timerCounter = duration;
        TimerFlag = false;
    }

    class Task extends TimerTask {

        @Override
        public void run() {
            if (TimerFlag) {
                if(LedFlag) {
                    try {
                        writeUartData(uartDevice, MESSAGE_ON);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                textLedStatus.setText("ON");
                            }
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                else {
                    try {
                        writeUartData(uartDevice, MESSAGE_OFF);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                textLedStatus.setText("OFF");
                            }
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                LedFlag = !LedFlag;
                setTimer();
            }
            timerRun();
        }
    }

    private void setupTask() {
        aTimer.schedule(new Task(), 0, MIN_TIME);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Attempt to close the UART device
        try {
            closeUart();
        } catch (IOException e) {
            Log.e(TAG, "Error closing UART device:", e);
        }
    }

    private UartDeviceCallback mUartCallback = new UartDeviceCallback() {
        @Override
        public boolean onUartDeviceDataAvailable(UartDevice uart) {
            // Read available data from the UART device
            try {
                readUartBuffer(uart);
            } catch (IOException e) {
                Log.w(TAG, "Unable to access UART device", e);
            }

            // Continue listening for more interrupts
            return true;
        }

        @Override
        public void onUartDeviceError(UartDevice uart, int error) {
            Log.w(TAG, uart + ": Error event " + error);
        }
    };
    /* Private Helper Methods */

    /**
     * Access and configure the requested UART device for 8N1.
     *
     * @param name     Name of the UART peripheral device to open.
     * @param baudRate Data transfer rate. Should be a standard UART baud,
     *                 such as 9600, 19200, 38400, 57600, 115200, etc.
     * @throws IOException if an error occurs opening the UART port.
     */
    private void openUart(String name, int baudRate) throws IOException {
        uartDevice = mService.openUartDevice(name);
        // Configure the UART
        uartDevice.setBaudrate(baudRate);
        uartDevice.setDataSize(DATA_BITS);
        uartDevice.setParity(UartDevice.PARITY_NONE);
        uartDevice.setStopBits(STOP_BITS);

        uartDevice.registerUartDeviceCallback(mUartCallback);
    }

    /**
     * Close the UART device connection, if it exists
     */
    private void closeUart() throws IOException {
        if (uartDevice != null) {
            uartDevice.unregisterUartDeviceCallback(mUartCallback);
            try {
                uartDevice.close();
            } finally {
                uartDevice = null;
            }
        }
    }

    public void readUartBuffer(UartDevice uart) throws IOException {
        // Maximum amount of data to read at one time
        byte[] buffer = new byte[CHUNK_SIZE];
        int count = uart.read(buffer, buffer.length);
        Log.d(TAG, "Read " + count + " bytes from peripheral");

        String ms = new String(buffer);
        for (char c : ms.toCharArray()) {
            if (c != '\r' && c != '\n') {
                data.append(c);
            } else {
                Log.d(TAG, "Data: " + data);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        textData.setText("Data: " + data);
                    }
                });
                data.delete(0, data.length());
                break;
            }
        }
    }

    public void writeUartData(UartDevice uart, String message) throws IOException {
        byte[] buffer = message.getBytes();
        int count = uart.write(buffer, buffer.length);
        uart.flush(UartDevice.FLUSH_OUT);
        Log.d(TAG, "Wrote " + count + " bytes to peripheral.");
        Log.d(TAG, "Message: " + new String(buffer));
    }
}
