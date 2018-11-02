/*
 * Copyright 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.ghen.loopback;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.GpioCallback;
import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.Pwm;
import com.google.android.things.pio.UartDevice;
import com.google.android.things.pio.UartDeviceCallback;

import java.io.IOException;
import java.io.UTFDataFormatException;
import java.util.concurrent.Delayed;
import static java.lang.Math.pow;

/**
 * Example activity that provides a UART loopback on the
 * specified device. All data received at the specified
 * baud rate will be transferred back out the same UART.
 */
public class LoopbackActivity extends Activity {
    private static final String TAG = "LoopbackActivity";

    // UART Configuration Parameters
    private static final int BAUD_RATE = 115200;
    private static final int DATA_BITS = 8;
    private static final int STOP_BITS = 1;

    private static final int CHUNK_SIZE = 512;

    private HandlerThread mInputThread;
    private Handler mInputHandler;

    private UartDevice mLoopbackDevice;

    private Runnable mTransferUartRunnable = new Runnable() {
        @Override
        public void run() {
            transferUartData();
        }
    };

    // Begin define
    private static final String Led_0 = "BCM5";
    private static final String Led_1 = "BCM6";
    private static final String Led_2 = "BCM12";
    private static final String Pwm_1 = "PWM1";
    private static final String Button_0 = "BCM16";
    private Gpio[] LED_RGB = new Gpio[3];

    private Pwm PWM_PIN;
    private double mActivePulseDuration;
    private static double Pwm_frequency = 100;
    private int Pwm_duty_cycle = 0;
    private boolean Pwm_increasing_bool = true;

    private Button BUTTON_CASE;
    private static int[] Button_state_led = {2000, 1000, 500, 100};
    private int Button_switch_led = 0;
    private int count_switch_button = 0;


    private int ikey_case_action = -1;
    private int Led_clock_T = 500;
    private int count_switch_led = 0;
    private boolean Led_switch_state_5[] = new boolean[3];


    // End define

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Loopback Created");

        // Create a background looper thread for I/O
        mInputThread = new HandlerThread("InputThread");
        mInputThread.start();
        mInputHandler = new Handler(mInputThread.getLooper());

        setContentView(R.layout.activity_main);

        BUTTON_CASE = (Button) findViewById(R.id.button_case);
        BUTTON_CASE.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG,"Event press button.");
                switch (ikey_case_action) {
                    case 2:
                        Exercises_action_2();
                        break;
                    case 4:
                        Exercises_action_4();
                    default:
                        break;
                }
            }
        });



        // Attempt to access the UART device

        try {
            openUart(BoardDefaults.getUartName(), BAUD_RATE);
            // Read any initially buffered data

            /*
            BUTTON_PIN = PeripheralManager.getInstance().openGpio(Button_0);
            BUTTON_PIN.setDirection(Gpio.DIRECTION_IN);
            BUTTON_PIN.setActiveType(Gpio.ACTIVE_LOW);
            BUTTON_PIN.setEdgeTriggerType(Gpio.EDGE_RISING);
            BUTTON_PIN.registerGpioCallback(Exercises_action_2);
            BUTTON_PIN.registerGpioCallback(Exercises_action_4);
            */

            LED_RGB[0] = PeripheralManager.getInstance().openGpio(Led_0);
            LED_RGB[1] = PeripheralManager.getInstance().openGpio(Led_1);
            LED_RGB[2] = PeripheralManager.getInstance().openGpio(Led_2);
            Log.i(TAG,"Setting PCM pin is done.");

            for ( int i = 0; 3 > i; i++){
                LED_RGB[i].setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            }
            Log.i(TAG,"Setting all low PCM pin.");

            PWM_PIN = PeripheralManager.getInstance().openPwm(Pwm_1);
            PWM_PIN.setPwmFrequencyHz(Pwm_frequency);
            Pwm_duty_cycle = 0;
            PWM_PIN.setPwmDutyCycle(Pwm_duty_cycle);
            PWM_PIN.setEnabled(true);
            Log.i(TAG,"Setting PWM pin is done.");

            mInputHandler.post(mTransferUartRunnable);
        } catch (IOException e) {
            Log.e(TAG, "Unable to open UART device", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Loopback Destroyed");

        // Terminate the worker thread
        if (mInputThread != null) {
            mInputThread.quitSafely();
        }

        // Attempt to close the UART device
        try {
            closeUart();

            LED_RGB[0].close();
            LED_RGB[1].close();
            LED_RGB[2].close();
            PWM_PIN.close();
        } catch (IOException e) {
            Log.e(TAG, "Error closing UART device:", e);
        }
    }

    /**
     * Callback invoked when UART receives new incoming data.
     */
    private UartDeviceCallback mCallback = new UartDeviceCallback() {
        @Override
        public boolean onUartDeviceDataAvailable(UartDevice uart) {
            // Queue up a data transfer
            transferUartData();
            //Continue listening for more interrupts
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
     * @param name Name of the UART peripheral device to open.
     * @param baudRate Data transfer rate. Should be a standard UART baud,
     *                 such as 9600, 19200, 38400, 57600, 115200, etc.
     *
     * @throws IOException if an error occurs opening the UART port.
     */
    private void openUart(String name, int baudRate) throws IOException {
        mLoopbackDevice = PeripheralManager.getInstance().openUartDevice(name);
        // Configure the UART
        mLoopbackDevice.setBaudrate(baudRate);
        mLoopbackDevice.setDataSize(DATA_BITS);
        mLoopbackDevice.setParity(UartDevice.PARITY_NONE);
        mLoopbackDevice.setStopBits(STOP_BITS);

        mLoopbackDevice.registerUartDeviceCallback(mInputHandler, mCallback);
    }

    /**
     * Close the UART device connection, if it exists
     */
    private void closeUart() throws IOException {
        if (mLoopbackDevice != null) {
            mLoopbackDevice.unregisterUartDeviceCallback(mCallback);
            try {
                mLoopbackDevice.close();
            } finally {
                mLoopbackDevice = null;
            }
        }
    }

    /**
     * Loop over the contents of the UART RX buffer, transferring each
     * one back to the TX buffer to create a loopback service.
     *
     * Potentially long-running operation. Call from a worker thread.
     */
    private void transferUartData() {
        if (mLoopbackDevice != null) {
            // Loop until there is no more data in the RX buffer.
            try {
                byte[] buffer = new byte[CHUNK_SIZE];
                int read;
                while ((read = mLoopbackDevice.read(buffer, buffer.length)) > 0) {
                    mLoopbackDevice.write(buffer, read);

                    byte[] buffer_key_temp = new byte[read];
                    for(int i = 0; i < read; i++){
                        buffer_key_temp[i] = buffer[i];
                    }
                    String tempkey = new String(buffer_key_temp,"UTF-8");
                    Log.i(TAG, tempkey);
                    Exercises_action(buffer[0]);
                }
            } catch (IOException e) {
                Log.w(TAG, "Unable to transfer data over UART", e);
            }
        }
    }
    /**
     * Nội dung bài làm
     */
    private void Exercises_action(byte key_command){
        Reset_all();
        if(0 > ikey_case_action){
            if('o' == key_command){
                ikey_case_action = 0;
                Log.i(TAG, "The app is unlooked.");
            }
            else{
                Log.i(TAG, "The app has been locked.");
                Log.i(TAG, "Press o to unlock.");
            }
        }
        else{
            switch (key_command){
                case '1':
                    if(1 == ikey_case_action){
                        Log.i(TAG, "Exercises 1 is running.");
                    }
                    else{
                        ikey_case_action = 1;
                        mInputHandler.postDelayed(Do_nothing,1000);
                        Log.i(TAG, "Exercises 1 activities.");
                        Reset_all();
                        mInputHandler.post(Exercises_action_1);
                    }
                    break;
                case '2':
                    if(2 == ikey_case_action){
                        Log.i(TAG, "Exercises 2 is running.");
                    }
                    else{
                        ikey_case_action = 2;
                        mInputHandler.postDelayed(Do_nothing,1000);
                        Log.i(TAG, "Exercises 2 activities.");
                        Reset_all();
                        mInputHandler.post(Exercises_action_1);
                    }
                    break;
                case '3':
                    if(3 == ikey_case_action){
                        Log.i(TAG, "Exercises 3 is running.");
                    }
                    else{
                        ikey_case_action = 3;
                        mInputHandler.postDelayed(Do_nothing,1000);
                        Log.i(TAG, "Exercises 3 activities.");
                        Reset_all();
                        mInputHandler.post(Exercises_action_3);
                    }
                    break;
                case '4':
                    if(4 == ikey_case_action){
                        Log.i(TAG, "Exercises 4 is running.");
                    }
                    else{
                        ikey_case_action = 4;
                        mInputHandler.postDelayed(Do_nothing,1000);
                        Log.i(TAG, "Exercises 4 activities.");
                        Reset_all();
                        mInputHandler.post(Exercises_action_3);
                    }
                    break;
                case '5':
                    if(5 == ikey_case_action){
                        Log.i(TAG, "Exercises 5 is running.");
                    }
                    else{
                        ikey_case_action = 5;
                        mInputHandler.postDelayed(Do_nothing,1000);
                        Log.i(TAG, "Exercises 5 activities.");
                        Reset_all();
                        mInputHandler.post(Exercises_action_5);
                    }
                    break;
                case 'f':
                    ikey_case_action = -1;
                    mInputHandler.postDelayed(Do_nothing,1000);
                    Log.i(TAG, "The app is looked.");
                    Reset_all();
            }
        }
    }
    private Runnable Do_nothing = new Runnable() {
        @Override
        public void run() {
            //Do nothing.
        }
    };
    private void Reset_all(){
        try{
            for(int i = 0; i < 3; i++){
                LED_RGB[i].setValue(false);
            }
            PWM_PIN.setPwmDutyCycle(0);
        }catch (IOException e){
            Log.e(TAG, "Error on Exercises_setting_1", e);
        }
        Pwm_duty_cycle = 0;
        Pwm_increasing_bool = true;


        count_switch_led = 0;
        count_switch_button = 0;
        Led_clock_T = 500;

        for(int i = 0; i < 3; i++){
            Led_switch_state_5[i] = false;
        }
        // case exe 3 init
        if(3 == ikey_case_action){
            try{
                for(int i = 0; i < 3; i++){
                    LED_RGB[i].setValue(true);
                }
                PWM_PIN.setPwmDutyCycle(0);
            }catch (IOException e) {
                Log.e(TAG, "Error on Exercises_setting_1", e);
            }
        }
    }
    /*
        EXERCISE 1
     */
    private Runnable Exercises_action_1 = new Runnable() {
        @Override
        public void run() {
            if(1 == ikey_case_action || 2 == ikey_case_action){
                try{
                    switch (count_switch_led) {
                        case 0:
                            LED_RGB[0].setValue(false);
                            LED_RGB[1].setValue(false);
                            LED_RGB[2].setValue(false);
                            break;
                        case 1:
                            LED_RGB[0].setValue(true);
                            LED_RGB[1].setValue(false);
                            LED_RGB[2].setValue(false);
                            break;
                        case 2:
                            LED_RGB[0].setValue(false);
                            LED_RGB[1].setValue(true);
                            LED_RGB[2].setValue(false);
                            break;
                        case 3:
                            LED_RGB[0].setValue(true);
                            LED_RGB[1].setValue(true);
                            LED_RGB[2].setValue(false);
                            break;
                        case 4:
                            LED_RGB[0].setValue(false);
                            LED_RGB[1].setValue(false);
                            LED_RGB[2].setValue(true);
                            break;
                        case 5:
                            LED_RGB[0].setValue(true);
                            LED_RGB[1].setValue(false);
                            LED_RGB[2].setValue(true);
                            break;
                        case 6:
                            LED_RGB[0].setValue(false);
                            LED_RGB[1].setValue(true);
                            LED_RGB[2].setValue(true);
                            break;
                        case 7:
                            LED_RGB[0].setValue(true);
                            LED_RGB[1].setValue(true);
                            LED_RGB[2].setValue(true);
                            break;
                    }
                    count_switch_led = (count_switch_led + 1) % 8;
                    mInputHandler.postDelayed(Exercises_action_1, Led_clock_T);
                }catch (IOException e){
                    Log.e(TAG, "Error on Exercises_action_1", e);
                }
            }
        }
    };
    /*
        EXERCISE 2
     */
    private void Exercises_action_2() {
        switch (count_switch_button) {
            case 0:
                Led_clock_T = 2000;
                break;
            case 1:
                Led_clock_T = 500;
                break;
            case 2:
                Led_clock_T = 100;
                break;
        }
        count_switch_button = (count_switch_button + 1) % 3;

        Log.i(TAG, "Exercises_action_2 button switched.");
    }
    /*
        EXERCISE 3
     */
    private Runnable Exercises_action_3 = new Runnable() {
        @Override
        public void run() {
            if(3 == ikey_case_action || 4 == ikey_case_action){
                try{
                    PWM_PIN.setPwmDutyCycle(Pwm_duty_cycle);
                    //Log.i(TAG, "Exercises_action_3 pwm one change.");
                    /*
                    if(Pwm_increasing_bool){
                        Pwm_duty_cycle = Pwm_duty_cycle + 10;
                    }
                    else{
                        Pwm_duty_cycle = Pwm_duty_cycle - 10;
                    }
                    if(100 <= Pwm_duty_cycle){
                        Pwm_increasing_bool = false;
                    }
                    if(0 >= Pwm_duty_cycle){
                        Pwm_increasing_bool = true;
                    }
                    */
                    Pwm_duty_cycle = (Pwm_duty_cycle + 10) % 110;
                    //Log.i(TAG, "Exercises_action_3 pwm loop.");
                    mInputHandler.postDelayed(Exercises_action_3, 500);
                }catch (IOException e){
                    Log.e(TAG, "Error on Exercises_action_3", e);
                }
            }
        }
    };

    private void Exercises_action_4() {
        try {
            switch (count_switch_button) {
                case 0:
                    LED_RGB[0].setValue(true);
                    LED_RGB[1].setValue(true);
                    LED_RGB[2].setValue(true);
                    break;
                case 1:
                    LED_RGB[0].setValue(true);
                    LED_RGB[1].setValue(false);
                    LED_RGB[2].setValue(false);
                    break;
                case 2:
                    LED_RGB[0].setValue(false);
                    LED_RGB[1].setValue(true);
                    LED_RGB[2].setValue(false);
                    break;
                case 3:
                    LED_RGB[0].setValue(false);
                    LED_RGB[1].setValue(false);
                    LED_RGB[2].setValue(true);
                    break;
            }
        }catch (IOException e) {
            Log.e(TAG, "Error on Exercises_action_4", e);
        }
        count_switch_button = (count_switch_button + 1) % 4;
        Log.i(TAG, "Exercises_action_4 button switched.");
    }

    private Runnable Exercises_action_5 = new Runnable() {
        @Override
        public void run() {
            if(5 == ikey_case_action){
                try{
                    if(Led_switch_state_5[0]){
                        Led_switch_state_5[0] = false;
                        LED_RGB[0].setValue(false);
                    }
                    else{
                        Led_switch_state_5[0] = true;
                        LED_RGB[0].setValue(true);
                    }
                    // 2
                    if(count_switch_led % 4 == 0){
                        if(Led_switch_state_5[1]){
                            Led_switch_state_5[1] = false;
                            LED_RGB[1].setValue(false);
                        }
                        else{
                            Led_switch_state_5[1] = true;
                            LED_RGB[1].setValue(true);
                        }
                    }
                    // 3
                    if(count_switch_led % 6 == 0){
                        if(Led_switch_state_5[2]){
                            Led_switch_state_5[2] = false;
                            LED_RGB[2].setValue(false);
                        }
                        else{
                            Led_switch_state_5[2] = true;
                            LED_RGB[2].setValue(true);
                        }
                    }
                    //
                    count_switch_led = (count_switch_led + 1) % 24;
                    mInputHandler.postDelayed(Exercises_action_5,500);
                }catch (IOException e){
                    Log.e(TAG, "Error on Exercises_action_5", e);
                }
            }
        }
    };


}