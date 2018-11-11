package com.galarzaa.androidthings.samples;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.galarzaa.androidthings.Rc522;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.SpiDevice;


import android.util.Log;
import static android.content.ContentValues.TAG;
import com.galarzaa.androidthings.samples.MVVM.VM.NPNHomeViewModel;
import com.galarzaa.androidthings.samples.MVVM.View.NPNHomeView;

import java.io.IOException;

public class MainActivity extends Activity implements NPNHomeView {
    private Rc522 mRc522;
    RfidTask mRfidTask;
    private TextView mTagDetectedView;
    private TextView mTagUidView;
    private TextView mTagResultsView;
    private Button BUTTON_WRITE;

    private Button BUTTON_READ;

    private SpiDevice spiDevice;
    private Gpio gpioReset;
    private Gpio LED_GREEN;
    private Gpio LED_RED;
    private Gpio LED_BLUE;

    private boolean Command_read = false;
    private boolean Command_write = false;
    private boolean is_the_true_card = true;
    private int  Blind_red_token = 0;
    private boolean Blind_red_switch = false;

    private static final String SPI_PORT    = "SPI0.0";
    private static final String PIN_RESET   = "BCM25";
    private static final String Led_green   = "BCM5";
    private static final String Led_red     = "BCM6";
    private static final String Led_blue    = "BCM12";

    private static final String http_header   = "http://demo1.chipfc.com/SensorValue/update?sensorid=7&sensorvalue=";

    String resultsText = "";

    private Handler mHandlerSPI = new Handler();

    private NPNHomeViewModel mHomeViewModel;


    private int c;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTagDetectedView = (TextView)findViewById(R.id.tag_read);
        mTagUidView = (TextView)findViewById(R.id.tag_uid);
        mTagResultsView = (TextView) findViewById(R.id.tag_results);


        //Initiate NPNHomeView Object
        mHomeViewModel = new NPNHomeViewModel();
        mHomeViewModel.attach(this, this);

        Log.i(TAG,"Start define button");

        BUTTON_READ = (Button)findViewById(R.id.button_read);
        BUTTON_READ.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Command_read = true;
                mRfidTask = new RfidTask(mRc522);
                mRfidTask.execute();
                ((Button)v).setText(R.string.reading);
            }
        });

        BUTTON_WRITE = (Button)findViewById(R.id.button_write);
        BUTTON_WRITE.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Command_write = true;
                mRfidTask = new RfidTask(mRc522);
                mRfidTask.execute();
                ((Button)v).setText(R.string.writing);
            }
        });
        Log.i(TAG,"Finish define button");



        PeripheralManager pioService = PeripheralManager.getInstance();
        try {

            LED_GREEN = pioService.openGpio(Led_green);
            LED_RED = pioService.openGpio(Led_red);
            LED_BLUE = pioService.openGpio(Led_blue);
            LED_GREEN.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            LED_RED.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            LED_BLUE.setDirection(Gpio.DIRECTION_OUT_INITIALLY_HIGH);


            spiDevice = pioService.openSpiDevice(SPI_PORT);
            gpioReset = pioService.openGpio(PIN_RESET);
            mRc522 = new Rc522(spiDevice, gpioReset);
            mRc522.setDebugging(true);

        } catch (IOException e) {
            Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try{
            if(spiDevice != null){
                spiDevice.close();
            }
            if(gpioReset != null){
                gpioReset.close();
            }
            if(LED_GREEN != null){
               LED_GREEN.close();
            }
            if(LED_RED != null){
                LED_RED.close();
            }
            if(LED_BLUE != null){
                LED_BLUE.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    //Mess Lab 4
    @Override
    public void onSuccessUpdateServer(String message) {
        if (message.indexOf("OK") >= 0 && message.indexOf("200") >= 0 ) {
            Log.d("Send", "success!!!");
            Toast.makeText(this,"Success!!!!!!",Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onErrorUpdateServer(String message) {
        Log.d(TAG,"Upload server failed!!!!");
        Toast.makeText(this,"Upload Failed!",Toast.LENGTH_SHORT).show();
    }



    private class RfidTask extends AsyncTask<Object, Object, Boolean> {
        private static final String TAG = "RfidTask";
        private Rc522 rc522;

        RfidTask(Rc522 rc522){
            this.rc522 = rc522;
        }

        @Override
        protected void onPreExecute() {
            BUTTON_WRITE.setEnabled(false);
            BUTTON_READ.setEnabled(false);
            mTagResultsView.setVisibility(View.GONE);
            mTagDetectedView.setVisibility(View.GONE);
            mTagUidView.setVisibility(View.GONE);
            resultsText = "";
        }

        @Override
        protected Boolean doInBackground(Object... params) {
            rc522.stopCrypto();
            while(true){
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return null;
                }
                //Check if a RFID tag has been found
                if(!rc522.request()){
                    continue;
                }
                //Check for collision errors
                if(!rc522.antiCollisionDetect()){
                    continue;
                }
                byte[] uuid = rc522.getUid();
                return rc522.selectTag(uuid);
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if(!success){
                mTagResultsView.setText(R.string.unknown_error);
                return;
            }
            // Try to avoid doing any non RC522 operations until you're done communicating with it.
            byte address = Rc522.getBlockAddress(2,1);
            // Mifare's card default key A and key B, the key may have been changed previously
            byte[] key = {(byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF};
            // Each sector holds 16 bytes
            // Data that will be written to sector 2, block 1
            //byte[] newData = {0x0F,0x0E,0x0D,0x0C,0x0B,0x0A,0x09,0x08,0x07,0x06,0x05,0x04,0x03,0x02,0x01,0x00};
            // In this case, Rc522.AUTH_A or Rc522.AUTH_B can be used

            byte[] GT_ghen = "BAO 1022 1410239".getBytes();
            // Nội dung cần ghi

            try {
                //We need to authenticate the card, each sector can have a different key
                boolean result = rc522.authenticateCard(Rc522.AUTH_A, address, key);
                if (!result) {
                    mTagResultsView.setText(R.string.authetication_error);
                    return;
                }

                // WRITE TO TAG
                if(Command_write){
                    result = rc522.writeBlock(address, GT_ghen);
                    // Ghi nội dung

                    if(!result){
                        mTagResultsView.setText(R.string.write_error);
                        return;
                    }
                    resultsText += "Sector written successfully";
                }

                byte[] buffer = new byte[16];
                //Since we're still using the same block, we don't need to authenticate again
                result = rc522.readBlock(address, buffer);
                if(!result){
                    mTagResultsView.setText(R.string.read_error);
                    return;
                }
                resultsText += "\nSector read successfully: "+ new String(buffer);



                //Rc522.dataToHexString(buffer
                rc522.stopCrypto();
                mTagResultsView.setText(resultsText);


                //Condition card
                String con_1 = new String(buffer);
                String con_2 = new String(GT_ghen);
                if(con_1.equals(con_2)){
                    is_the_true_card = true;
                }
                else{
                    is_the_true_card = false;
                }

            }finally{

                //READ FROM TAG
                if(Command_read){
                    if(is_the_true_card){
                        mHandlerSPI.post(Led_green_behavior);
                        mHandlerSPI.postDelayed(Reset_led_to_default, 2000);
                    }
                    else{
                        Blind_red_token = 10;
                        mHandlerSPI.post(Blind_red_duration);
                    }
                }

                BUTTON_WRITE.setEnabled(true);
                BUTTON_WRITE.setText(R.string.write);
                BUTTON_READ.setEnabled(true);
                BUTTON_READ.setText(R.string.read);

                mTagUidView.setText(getString(R.string.tag_uid,rc522.getUidString()));


                //Code cho lab 4
                String temp = rc522.getUidString("");
                String link_to_send = http_header + temp;
                mHomeViewModel.updateToServer(link_to_send);
                Log.d("URL: ",link_to_send);

                mTagResultsView.setVisibility(View.VISIBLE);
                mTagDetectedView.setVisibility(View.VISIBLE);
                mTagUidView.setVisibility(View.VISIBLE);


            }
            Command_write = false;
            Command_read = false;
        }
    }



    private Runnable Reset_led_to_default = new Runnable() {
        @Override
        public void run() {
            Blind_red_token = 0;
            Blind_red_switch = false;
            mHandlerSPI.post(Led_blue_behavior);
        }
    };
    private Runnable Led_red_behavior = new Runnable() {
        @Override
        public void run() {
            try{
                LED_GREEN.setValue(false);
                LED_RED.setValue(true);
                LED_BLUE.setValue(false);

            }catch (IOException e) {
                e.printStackTrace();
            }
        }
    };
    private Runnable Led_green_behavior = new Runnable() {
        @Override
        public void run() {
            try{
                LED_GREEN.setValue(true);
                LED_RED.setValue(false);
                LED_BLUE.setValue(false);

            }catch (IOException e) {
                e.printStackTrace();
            }
        }
    };
    private Runnable Led_blue_behavior = new Runnable() {
        @Override
        public void run() {
            try{
                LED_GREEN.setValue(false);
                LED_RED.setValue(false);
                LED_BLUE.setValue(true);

            }catch (IOException e) {
                e.printStackTrace();
            }
        }
    };
    private Runnable Led_reset_behavior = new Runnable() {
        @Override
        public void run() {
            try{
                LED_GREEN.setValue(false);
                LED_RED.setValue(false);
                LED_BLUE.setValue(false);

            }catch (IOException e) {
                e.printStackTrace();
            }
        }
    };
    private Runnable Blind_red_duration = new Runnable() {
        @Override
        public void run() {
            if(0 < Blind_red_token){
                if(Blind_red_switch){
                    mHandlerSPI.post(Led_red_behavior);
                }
                else{
                    mHandlerSPI.post(Led_reset_behavior);
                }
                Blind_red_token = Blind_red_token - 1;
                Blind_red_switch = !Blind_red_switch;
                mHandlerSPI.postDelayed(Blind_red_duration, 200);
            }
            else{
                mHandlerSPI.post(Reset_led_to_default);
            }
        }
    };
}
