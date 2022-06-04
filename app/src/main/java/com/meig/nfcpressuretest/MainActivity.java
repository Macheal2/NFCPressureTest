package com.meig.nfcpressuretest;

import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.nfc.tech.NfcBarcode;
import android.nfc.tech.NfcF;
import android.nfc.tech.NfcV;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Method;

public class MainActivity extends AppCompatActivity {
    private MainActivity mContext;

    private NfcAdapter mDefaultAdapter;
    private PendingIntent pendingIntent;
    private static String[][] NFC_TECHLISTS;
    private boolean mLastNfcStatus = true;
    private boolean mReadCardA1Status = false;
    private boolean mReadCardA2Status = false;
    private boolean mReadCardB1Status = false;
    private boolean mReadCardB2Status = false;

    private static IntentFilter[] NFC_FILTERS;


    public TextView mFlag;
    public LinearLayout mLayout;
    public TextView mNFCInfo;
    private TextView mNFCInfoA0;
    private TextView mNFCInfoA4;
    private TextView mNFCInfoB0;
    private TextView mNFCInfoB4;

    private Button mResetButton;
    private TextView mTotal;
    private TextView mSuccess;
    private TextView mFailed;
    private TextView mTips;

    private Button mNFCBtn;
    private TextView mNFCStatus;

    private boolean mIsPCBATest = true;

    private SharedPreferences sp;

    private static final int MAX_TIMES = 10000;

    private PowerManager mPM;
    private PowerManager.WakeLock mWakeLock;
    private AlertDialog mDialog;

    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 1000:
                    mFlag.setVisibility(View.GONE);
                    mLayout.setVisibility(View.VISIBLE);
                    break;
                case 1006:
                    String content = (String) msg.obj;
                    Log.d("Yar", "1006 content:" + content);
                    if(content.contains("0a00")){
                        mReadCardA1Status= true;
                        mNFCInfoA0.setText(String.format(getResources().getString(R.string.nfc_info_a0), "0a00"));
                        showNfcInfo();
                    }else if(content.contains("0a04")){
                        mReadCardA2Status= true;
                        mNFCInfoA4.setText(String.format(getResources().getString(R.string.nfc_info_a4), "0a04"));
                        showNfcInfo();
                    }else if(content.contains("0b00")){
                        mReadCardB1Status= true;
                        mNFCInfoB0.setText(String.format(getResources().getString(R.string.nfc_info_b0), "0b00"));
                        showNfcInfo();
                    }else if(content.contains("0b04")){
                        mReadCardB2Status= true;
                        mNFCInfoB4.setText(String.format(getResources().getString(R.string.nfc_info_b4), "0b04"));
                        showNfcInfo();
                    }
                    mHandler.sendEmptyMessage(1005);
                    break;
                case 1007:
                    SharedPreferences.Editor editor = sp.edit();
                    editor.putInt("total_times", 0);
                    editor.putInt("success_times", 0);
                    editor.putInt("failed_times", 0);
                    editor.commit();
                    mTotal.setText(getString(R.string.nfc_total_times, 0));
                    mSuccess.setText(getString(R.string.nfc_success_times, 0));
                    mFailed.setText(getString(R.string.nfc_failed_times, 0));
                    mTips.setVisibility(View.GONE);

                    if (mDefaultAdapter != null) {
                        Log.d("Yar", "handler mDefaultAdapter = " + mDefaultAdapter);
                        mDefaultAdapter.enableForegroundDispatch(mContext, pendingIntent, NFC_FILTERS, NFC_TECHLISTS);
                    }
                    break;
                case 1008:
                    int total = sp.getInt("total_times", 0);
                    int success = sp.getInt("success_times", 0);
                    int failed = sp.getInt("failed_times", 0);
                    mTotal.setText(getString(R.string.nfc_total_times, total));
                    mSuccess.setText(getString(R.string.nfc_success_times, success));
                    mFailed.setText(getString(R.string.nfc_failed_times, failed));
                    if (total < MAX_TIMES) {
                        mTips.setVisibility(View.GONE);
                    } else {
                        mTips.setText(getString(R.string.nfc_max_times_tip, total));
                        mTips.setVisibility(View.VISIBLE);
                    }

                    break;
                case 1009:
                    if (mDefaultAdapter != null && mDefaultAdapter.isEnabled()) {
                        mNFCBtn.setVisibility(View.GONE);
                        mNFCStatus.setVisibility(View.GONE);
                    } else {
                        mNFCBtn.setVisibility(View.VISIBLE);
                        mNFCStatus.setVisibility(View.VISIBLE);
                    }
                    break;

                case 1010:
                    if (mDefaultAdapter != null && !mDefaultAdapter.isEnabled()) {
                        mDefaultAdapter.enable();
                        mDefaultAdapter.enableForegroundDispatch(mContext, pendingIntent, NFC_FILTERS, NFC_TECHLISTS);
                    }

                    android.util.Log.i("Yar", " 1010 ========");
                    break;
                case 1011:
                    if (mDefaultAdapter != null && mDefaultAdapter.isEnabled()) {
                        mDefaultAdapter.disable();
                        mDefaultAdapter.disableForegroundDispatch(mContext);
                    }
                    android.util.Log.i("Yar", " 1011 ========");
                    mHandler.removeMessages(1010);
                    mHandler.sendEmptyMessageDelayed(1010, 2000);
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sp = getSharedPreferences("nfc_count", Context.MODE_PRIVATE);
        mContext = this;

        mPM = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = mPM.newWakeLock(PowerManager.FULL_WAKE_LOCK, "NFC test");

        setContentView(R.layout.activity_main);

        mNFCInfo = (TextView) findViewById(R.id.nfc_info);

        mFlag = (TextView) findViewById(R.id.flag);
        mLayout = (LinearLayout) findViewById(R.id.layout);
        mNFCInfoA0 = (TextView) findViewById(R.id.nfc_info_a0);
        mNFCInfoA4 = (TextView) findViewById(R.id.nfc_info_a4);
        mNFCInfoB0 = (TextView) findViewById(R.id.nfc_info_b0);
        mNFCInfoB4 = (TextView) findViewById(R.id.nfc_info_b4);

        mResetButton = (Button) findViewById(R.id.reset);
        mResetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mHandler.removeMessages(1007);
                mHandler.sendEmptyMessageDelayed(1007, 50);
            }
        });
        mTotal = (TextView) findViewById(R.id.total);
        mSuccess = (TextView) findViewById(R.id.success);
        mFailed = (TextView) findViewById(R.id.failed);
        mTips = (TextView) findViewById(R.id.tips);

        mNFCBtn = (Button) findViewById(R.id.nfc_btn);
        mNFCBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /*Intent i = new Intent(Settings.ACTION_NFC_SETTINGS);
                startActivity(i);*/
                mDefaultAdapter.enable();
                mHandler.removeMessages(1009);
                mHandler.sendEmptyMessageDelayed(1009, 1000);
            }
        });
        mNFCStatus = (TextView) findViewById(R.id.nfc_status);

        mHandler.sendEmptyMessage(1000);
        //initNFC();
    }

    private void initNFC() {
        Log.d("Yar", "initNFC start");

        mDefaultAdapter = NfcAdapter.getDefaultAdapter(mContext);

        try {
            if (!mDefaultAdapter.isEnabled()) {
                mLastNfcStatus = false;
                //mDefaultAdapter.enable();
            } else {
                Log.d("Yar", "initNFC 2222");

            }

        } catch (Exception e) {
            Log.d("Yar", "initNFC exception: " + e);
        }

        NFC_TECHLISTS = new String[][]{{IsoDep.class.getName()}, {NfcA.class.getName()}, {NfcB.class.getName()}, {NfcF.class.getName()}, {NfcV.class.getName()},
                {Ndef.class.getName()}, {NdefFormatable.class.getName()}, {MifareClassic.class.getName()}, {MifareUltralight.class.getName()}, {NfcBarcode.class.getName()}};
        try {
            NFC_FILTERS =
                    new IntentFilter[] { new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED, "*/*") ,
                            new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED,"*/*"),
                            new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED,"*/*")};
        } catch (IntentFilter.MalformedMimeTypeException e) {
            Log.d("Yar", "initNFC create NFC_FILTERS exception: " + e.getMessage());
        }
        pendingIntent = PendingIntent.getActivity(mContext, 0,
                new Intent(mContext, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        Log.d("Yar", "initNFC end");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mWakeLock != null && !mWakeLock.isHeld()) {
            mWakeLock.acquire();
        }

        initNFC();
        mHandler.sendEmptyMessage(1009);
        Log.d("Yar", "onResume() ------------");
        if (mDefaultAdapter != null) {
            Log.d("Yar", "onResume mDefaultAdapter = " + mDefaultAdapter);
            mDefaultAdapter.enableForegroundDispatch(this, pendingIntent, NFC_FILTERS, NFC_TECHLISTS);
        }
        mHandler.removeMessages(1008);
        mHandler.sendEmptyMessageDelayed(1008, 50);
        int total = sp.getInt("total_times", 0);
        if (total >= MAX_TIMES) {
            if(mDefaultAdapter!=null){
                Log.d("Yar", "total = " + total);
                mDefaultAdapter.disableForegroundDispatch(this);//关闭前台发布系统
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        getNdefMsg(intent);
        int total = sp.getInt("total_times", 0);
        Log.d("Yar", "total = " + total);
        SharedPreferences.Editor editor = sp.edit();
        editor.putInt("total_times", (total+1));
        editor.commit();
        mHandler.removeMessages(1008);
        mHandler.sendEmptyMessageDelayed(1008, 50);
        mHandler.sendEmptyMessageDelayed(1011, 1000);
    }

    public void getNdefMsg(Intent intent) {
        Log.d("Yar", "getNdefMsg start");
        if (intent == null) {
            Log.d("Yar", "getNdefMsg 1");
            int failedTimes = sp.getInt("failed_times", 0);
            SharedPreferences.Editor editor = sp.edit();
            editor.putInt("failed_times", (failedTimes+1));
            editor.commit();
            Log.d("Yar", "0. failedTimes = " + failedTimes);
            return;
        }
        //return null;

        //nfc卡支持的格式
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        String[] temp = tag.getTechList();
        for (String s : temp) {
            Log.d("Yar", "resolveIntent tag: " + s);
        }

        String action = intent.getAction();
        Log.d("Yar", "action:" + action);

        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action) ||
                NfcAdapter.ACTION_TECH_DISCOVERED.equals(action) ||
                NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)) {
            Parcelable[] rawMessage = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            NdefMessage[] ndefMessages;

            // 判断是哪种类型的数据 默认为NDEF格式
            if (rawMessage != null) {
                Log.d("Yar", "getNdefMsg: ndef格式 ");
                ndefMessages = new NdefMessage[rawMessage.length];
                Log.d("Yar", "rawMessage.length : " + rawMessage.length );

                int successTimes = sp.getInt("success_times", 0);
                SharedPreferences.Editor editor = sp.edit();
                editor.putInt("success_times", (successTimes+1));
                editor.commit();
                Log.d("Yar", "successTimes = " + successTimes);
                for (int i = 0; i < rawMessage.length; i++) {
                    ndefMessages[i] = (NdefMessage) rawMessage[i];
                    Log.d("Yar", "ndefMessages[i] : " + ndefMessages[i] );
                    NdefRecord record = ndefMessages[i].getRecords()[0];
                    String content = ByteUtil.hexStr2Str(ByteUtil.bytes2HexStr(record.getPayload()));
                    Log.d("Yar", "getPayload1 : " + ByteUtil.bytes2HexStr(record.getPayload()) );
                    Log.d("Yar", "getPayload2:[" +ByteUtil.hexStr2Str(ByteUtil.bytes2HexStr(record.getPayload())) + "].");

                    Message msg = mHandler.obtainMessage();
                    msg.what = 1006;
                    msg.obj = content;
                    mHandler.sendMessage(msg);
                }
            } else {
                //未知类型 (公交卡类型)
                Log.d("Yar", "getNdefMsg: 未知类型");
                //对应的解析操作，在Github上有

                int failedTimes = sp.getInt("failed_times", 0);
                SharedPreferences.Editor editor = sp.edit();
                editor.putInt("failed_times", (failedTimes+1));
                editor.commit();
                Log.d("Yar", "1. failedTimes = " + failedTimes);
            }
            //return ndefMessages;
            Log.d("Yar", "getNdefMsg 2");
            return;
        }
        Log.d("Yar", "getNdefMsg end");
        return;
    }

    private void showNfcInfo(){
        if (!mReadCardA1Status) {
            mNFCInfo.setText(R.string.nfc_a0);
        } else if (!mReadCardA2Status && !mIsPCBATest) {
            mNFCInfo.setText(R.string.nfc_a4);
        } else if(!mReadCardB1Status && !mIsPCBATest) {
            mNFCInfo.setText(R.string.nfc_b0);
        } else if(!mReadCardB2Status && !mIsPCBATest) {
            mNFCInfo.setText(R.string.nfc_b4);
        } else {
            mNFCInfo.setText(R.string.nfc_layout_tag);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        Log.d("Yar", "onPause start");
        if(mDefaultAdapter!=null){
            Log.d("Yar", "onPause mDefaultAdapter!=null");
            mDefaultAdapter.disableForegroundDispatch(this);//关闭前台发布系统
        }
        Log.d("Yar", "onPause end");
    }

    @Override
    protected void onDestroy() {
        //mHandler.removeCallbacks(mRun);
        mHandler.removeMessages(1000);

        mHandler.removeMessages(1006);
        try {
            if (mDefaultAdapter != null) {
                //add by wangxing for taskid 2611
                Log.d("Yar", "onDestroy mDefaultAdapter = " + mDefaultAdapter);
                if (!mLastNfcStatus) {
                    Log.d("Yar", "onDestroy disable");
                    mDefaultAdapter.disable();
                }
                mDefaultAdapter.disableForegroundDispatch(this);//关闭前台发布系统
            }
        } catch (Exception e){
            Log.d("Yar", "onDestroy exception:" + e.getMessage());
        }
        super.onDestroy();
    }
}