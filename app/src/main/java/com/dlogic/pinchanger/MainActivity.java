package com.dlogic.pinchanger;

import androidx.appcompat.app.AppCompatActivity;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.dlogic.uFCoder;
import com.dlogic.uFCoderHelper;

import java.security.PublicKey;

public class MainActivity extends AppCompatActivity {

    static {
        System.loadLibrary("uFCoder"); //Load uFCoder library
    }

    uFCoderHelper UFC;
    uFCoder uFCoder;

    @Override
    protected void onPause() {
        UFC.callOnPause(this);
        super.onPause();
    }

    @Override
    protected void onResume() {
        UFC.callOnResume(this);
        super.onResume();
    }

    ProgressDialog dialog;
    private Handler handler;
    public boolean LOOP = false;
    private final int STATUS_IS_OK = 0;
    int status = 0;
    public static String loginPin;
    public static String newPin;
    public static String newPinAgain;
    public static String pukStr;
    public static boolean CHANGE_PIN = false;
    public static boolean UNBLOCK_PIN = false;

    EditText loginPinET;
    EditText newPinET;
    EditText newPinAgainET;
    EditText pukET;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        UFC = uFCoderHelper.getInstance(this);
        uFCoder = new uFCoder(getApplicationContext());

        loginPinET = findViewById(R.id.loginPinID);
        newPinET = findViewById(R.id.newPinId);
        newPinAgainET = findViewById(R.id.newPinAgainId);
        pukET = findViewById(R.id.pukId);

        status = uFCoder.ReaderOpenEx(5, "", 0, "");

        Button btnChangePin = findViewById(R.id.btnChangePin);
        btnChangePin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                CHANGE_PIN = true;

                loginPin = loginPinET.getText().toString();
                newPin = newPinET.getText().toString();
                newPinAgain = newPinAgainET.getText().toString();

                if(loginPin.length() != 8)
                {
                    Toast.makeText(getApplicationContext(), "PIN must contain 8 digits", Toast.LENGTH_SHORT).show();
                    CHANGE_PIN = false;
                    return;
                }

                if(newPin.length() != 8)
                {
                    Toast.makeText(getApplicationContext(), "PIN must contain 8 digits", Toast.LENGTH_SHORT).show();
                    CHANGE_PIN = false;
                    return;
                }

                if(newPinAgain.length() != 8)
                {
                    Toast.makeText(getApplicationContext(), "PIN must contain 8 digits", Toast.LENGTH_SHORT).show();
                    CHANGE_PIN = false;
                    return;
                }

                if(!newPin.equals(newPinAgain))
                {
                    Toast.makeText(getApplicationContext(), "New pins are not matched", Toast.LENGTH_SHORT).show();
                    CHANGE_PIN = false;
                    return;
                }

                LOOP = true;

                dialog = ProgressDialog.show(MainActivity.this, "",
                        "Tap DL Signer card on the phone...", true);
            }
        });

        Button btnUnblockPUK = findViewById(R.id.btnPinUnblockID);
        btnUnblockPUK.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                UNBLOCK_PIN = true;

                pukStr = pukET.getText().toString();

                if(pukStr.length() != 8)
                {
                    Toast.makeText(getApplicationContext(), "PUK must contain 8 digits", Toast.LENGTH_SHORT).show();
                    UNBLOCK_PIN = false;
                    return;
                }

                LOOP = true;

                dialog = ProgressDialog.show(MainActivity.this, "",
                        "Tap DL Signer card on the phone...", true);
            }
        });

        handler = new Handler(){
            public void handleMessage(android.os.Message msg) {
                if(msg.what == STATUS_IS_OK)
                {
                    dialog.dismiss();
                    LOOP = false;

                    new Thread() {
                        public void run() {

                            if(CHANGE_PIN)
                            {
                                ChangeUserPIN(loginPin, newPin);
                                CHANGE_PIN = false;
                            }
                            else if(UNBLOCK_PIN)
                            {
                                UnblockPIN(pukStr);
                                UNBLOCK_PIN = false;
                            }
                        }
                    }.start();
                }
            }
        };

        new Thread() {
            public void run() {

                while(true)
                {
                    if(LOOP)
                    {
                        byte[] cmd = {0x00, 0x00};
                        byte[] resp = new byte[100];
                        int[] respLen = new int[1];
                        int thread_status = uFCoder.APDUPlainTransceive(cmd, 2, resp, respLen);

                        switch (thread_status) {
                            case 0:
                                handler.obtainMessage(STATUS_IS_OK, -1, -1)
                                        .sendToTarget();
                                break;
                        }

                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

        }.start();

    }

    public void ShowStatus(final String status)
    {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), status, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void ShowPinTries(final short pinTries)
    {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                TextView pinTriesText = findViewById(R.id.pinTriesId);

                pinTriesText.setText(Short.toString(pinTries));
            }
        });
    }

    public void ShowPukTries(final short pukTries)
    {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                TextView pukTriesText = findViewById(R.id.pukTriesId);

                pukTriesText.setText(Short.toString(pukTries));
            }
        });
    }

    public void ChangeUserPIN(String LOGINPIN, String NEWPIN)
    {
        short[] PinTriesRemaining = new short[1];

        do{
            byte[] aid = { (byte)0xF0, 0x44, 0x4C, 0x6F, 0x67, 0x69, 0x63, 0x00, 0x01 };
            byte[] selection_respone = new byte[16];

            status = uFCoder.JCAppSelectByAid(aid, (byte)aid.length, selection_respone);
            Log.e("JCAppSelectByAid", uFCoder.UFR_Status2String(status));

            if(status != 0)
                break;

            byte[] bloginPin = LOGINPIN.getBytes();

            status = uFCoder.JCAppLogin((byte)0, bloginPin, (byte)8);
            Log.e("JCAppLogin", uFCoder.UFR_Status2String(status));

            if(status != 0)
                break;

            byte[] changePin = NEWPIN.getBytes();

            status = uFCoder.JCAppPinChange((byte)0, changePin, (byte)8);
            Log.e("JCAppPinChange", uFCoder.UFR_Status2String(status));

            if(status != 0)
                break;

            status = uFCoder.JCAppGetPinTriesRemaining((byte)0, PinTriesRemaining);

            if(status != 0)
                break;

            ShowPinTries(PinTriesRemaining[0]);
        }
        while (false);

        if(status != 0)
        {
            if (((int)status & 0xFFFFC0) == 0x0A63C0)
            {
                ShowStatus("Wrong PIN, tries remaining: " + ((int)status & 0x3F));
                ShowPinTries((short)((short) status & 0x3F));
            }
            else
            {
                ShowStatus(uFCoder.UFR_Status2String(status));
            }
        }
        else
        {
            ShowStatus("PIN successfully changed");
        }
    }

    public void UnblockPIN(String PUKSTR)
    {
        short[] PinTriesRemaining = new short[1];
        short[] PukTriesRemaining = new short[1];

        do {

            byte[] aid = { (byte)0xF0, 0x44, 0x4C, 0x6F, 0x67, 0x69, 0x63, 0x00, 0x01 };
            byte[] selection_respone = new byte[16];

            status = uFCoder.JCAppSelectByAid(aid, (byte)aid.length, selection_respone);

            if(status != 0)
                break;

            byte[] puk = PUKSTR.getBytes();
            status = uFCoder.JCAppPinUnblock((byte)0, puk, (byte)8);

            if(status != 0)
                break;

            status = uFCoder.JCAppGetPinTriesRemaining((byte)2, PukTriesRemaining);

            if(status != 0)
                break;

            ShowPukTries(PukTriesRemaining[0]);

            status = uFCoder.JCAppGetPinTriesRemaining((byte)0, PinTriesRemaining);

            if(status != 0)
                break;

            ShowPinTries(PinTriesRemaining[0]);
        }
        while(false);

        if(status != 0)
        {
            if (((int)status & 0xFFFFC0) == 0x0A63C0)
            {
                ShowStatus("Wrong PUK, tries remaining: " + ((int)status & 0x3F));
                ShowPukTries((short) ((short)status & 0x3F));
            }
            else
            {
                ShowStatus(uFCoder.UFR_Status2String(status));
            }
        }
        else
        {
            ShowStatus("PIN successfully unblocked");
        }
    }

    public void onStop () {
        LOOP = false;
        super.onStop();
    }
}
