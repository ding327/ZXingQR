package com.android.zxingqr;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.zxingqr.zxing.activity.CaptureActivity;
import com.android.zxingqr.zxing.encoding.EncodingHandler;

public class MainActivity extends AppCompatActivity {

    private ImageView mImgQr;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mImgQr = (ImageView) findViewById(R.id.img_qr);
    }

    public void OnScanQR(View view) {
        Intent intent = new Intent(this, CaptureActivity.class);
        startActivityForResult(intent, 0);
    }

    public void OnCreateQR(View view) {
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
        Bitmap www = EncodingHandler.createQRCode("http://www.esmartgym.com/", 600, 600, bitmap);
        mImgQr.setImageBitmap(www);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK && requestCode == 0) {
            Bundle extras = data.getExtras();
            if (extras != null) {
                String result = extras.getString("result");
                Bitmap bitmap = extras.getParcelable("bitmap");
                mImgQr.setImageBitmap(bitmap);
                Toast.makeText(getBaseContext(), result, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
