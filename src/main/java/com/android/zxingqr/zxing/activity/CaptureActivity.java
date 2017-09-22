package com.android.zxingqr.zxing.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.android.zxingqr.R;
import com.android.zxingqr.zxing.camera.CameraManager;
import com.android.zxingqr.zxing.decoding.CaptureActivityHandler;
import com.android.zxingqr.zxing.decoding.InactivityTimer;
import com.android.zxingqr.zxing.decoding.RGBLuminanceSource;
import com.android.zxingqr.zxing.view.ViewfinderView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Map;
import java.util.Vector;

/**
 * Initial the camera
 */
public class CaptureActivity extends Activity implements Callback {

	private static final int REQUEST_CODE_SCAN_GALLERY = 100;
	private boolean playBeep;
	private boolean vibrate;
	private boolean hasSurface;
	private String characterSet;
	private int ifOpenLight = 0;//判断是否开启闪光灯
	private MediaPlayer mediaPlayer;
	private ViewfinderView viewfinderView;
	private CaptureActivityHandler handler;
	private Vector<BarcodeFormat> decodeFormats;
	private InactivityTimer inactivityTimer;
	private static final float BEEP_VOLUME = 0.10f;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initWindow();
		setContentView(R.layout.activity_capture);
		CameraManager.init(getApplication());
		viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
		hasSurface = false;
		inactivityTimer = new InactivityTimer(this);
	}

	/**
	 * 初始化窗口
	 */
	private void initWindow() {
		// API_19及其以上透明掉状态栏
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
			layoutParams.flags = WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS | layoutParams.flags;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
		SurfaceHolder surfaceHolder = surfaceView.getHolder();
		if (hasSurface) {
			initCamera(surfaceHolder);
		} else {
			surfaceHolder.addCallback(this);
			surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		}
		decodeFormats = null;
		characterSet = null;

		playBeep = true;
		AudioManager audioService = (AudioManager) getSystemService(AUDIO_SERVICE);
		if (audioService.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
			playBeep = false;
		}
		initBeepSound();
		vibrate = true;

	}

	@Override
	protected void onPause() {
		super.onPause();
		if (handler != null) {
			handler.quitSynchronously();
			handler = null;
		}
		CameraManager.get().closeDriver();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.scanner_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.scan_local:
				//打开手机中的相册
				Intent innerIntent = new Intent(Intent.ACTION_GET_CONTENT); //"android.intent.action.GET_CONTENT"
				innerIntent.setType("image/*");
				Intent wrapperIntent = Intent.createChooser(innerIntent, "选择二维码图片");
				this.startActivityForResult(wrapperIntent, REQUEST_CODE_SCAN_GALLERY);
				return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	protected void onDestroy() {
		inactivityTimer.shutdown();
		super.onDestroy();
	}

	/**
	 * Handler scan result
	 * @param result
	 * @param barcode
	 * 获取结果
	 */
	public void handleDecode(Result result, Bitmap barcode) {
		inactivityTimer.onActivity();
		playBeepSoundAndVibrate();
		String resultString = result.getText();
		// FIXME
		if (resultString.equals("")) {
			Toast.makeText(CaptureActivity.this, "扫描失败!", Toast.LENGTH_SHORT)
					.show();
		} else {

			Intent resultIntent = new Intent();
			Bundle bundle = new Bundle();
			bundle.putString("result", resultString);
			bundle.putParcelable("bitmap", barcode);
			resultIntent.putExtras(bundle);
			setResult(RESULT_OK, resultIntent);
		}
		finish();
	}

	/*
	 * 获取带二维码的相片进行扫描
	 */
	public void pickPictureFromAblum(View v) {
		Intent mIntent = new Intent(
				Intent.ACTION_PICK,
				MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
		startActivityForResult(mIntent, 1);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onActivityResult(int, int,
	 * android.content.Intent) 对相册获取的结果进行分析
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {

		if (resultCode == RESULT_OK) {
			switch (requestCode) {
			case REQUEST_CODE_SCAN_GALLERY:
				Uri selectedImage = data.getData();
				String[] filePathColumn = { MediaStore.Images.Media.DATA };

				Cursor cursor = getContentResolver().query(selectedImage,
						filePathColumn, null, null, null);
				cursor.moveToFirst();

				int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
				String picturePath = cursor.getString(columnIndex);
				cursor.close();

				Result resultString = scanningImage1(picturePath);
				if (resultString == null) {
					Toast.makeText(getApplicationContext(), "解析错误，请选择正确的二维码图片", Toast.LENGTH_LONG).show();
				} else {
					String resultImage = resultString.getText();
					if (resultImage.equals("")) {
						Toast.makeText(CaptureActivity.this, "扫描失败",
								Toast.LENGTH_SHORT).show();
					} else {
						Intent resultIntent = new Intent();
						Bundle bundle = new Bundle();
						bundle.putString("result", resultImage);
						resultIntent.putExtras(bundle);
						CaptureActivity.this.setResult(RESULT_OK, resultIntent);
					}
					CaptureActivity.this.finish();
				}
				break;
			default:
				break;
			}
		}
		super.onActivityResult(requestCode, resultCode, data);
	}
	
	/**
	 * 解析QR图内容
	 * 
	 * @return
	 */
	// 解析QR图片
	private Result scanningImage1(String picturePath) {

		if (TextUtils.isEmpty(picturePath)) {
			return null;
		}
		
		Map<DecodeHintType, String> hints1 = new Hashtable<DecodeHintType, String>();
		hints1.put(DecodeHintType.CHARACTER_SET, "utf-8");

		// 获得待解析的图片
		Bitmap bitmap = null;
		try {
			bitmap = BitmapFactory.decodeFile(picturePath);
		} catch (Exception e) {
			e.printStackTrace();
		}
		RGBLuminanceSource source = new RGBLuminanceSource(bitmap);
		BinaryBitmap bitmap1 = new BinaryBitmap(new HybridBinarizer(source));
		QRCodeReader reader = new QRCodeReader();
		Result result;
		try {

			result =  reader.decode(bitmap1, (Hashtable<DecodeHintType, String>) hints1);
			return result;
		} catch (NotFoundException e) {
			Toast.makeText(CaptureActivity.this, "解析错误，请选择正确的二维码图片",
					Toast.LENGTH_LONG).show();
			e.printStackTrace();
		} catch (ChecksumException e) {
			Toast.makeText(CaptureActivity.this, "解析错误，请选择正确的二维码图片",
					Toast.LENGTH_LONG).show();
			e.printStackTrace();
		} catch (FormatException e) {
			Toast.makeText(CaptureActivity.this, "解析错误，请选择正确的二维码图片",
					Toast.LENGTH_LONG).show();
			e.printStackTrace();
		} finally {
			if (bitmap != null && !bitmap.isRecycled()){
				bitmap.recycle();
			}
		}
		return null;
	}

	// 是否开启闪光灯
	public void IfOpenLight(View v) {
		ifOpenLight++;

		switch (ifOpenLight % 2) {
		case 0:
			//关闪光灯
			CameraManager.get().closeLight();
			break;
		case 1:
			//开闪光灯
			CameraManager.get().openLight();
			break;
		default:
			break;
		}
	}

	private void initCamera(SurfaceHolder surfaceHolder) {
		try {
			CameraManager.get().openDriver(surfaceHolder);
		} catch (IOException ioe) {
			return;
		} catch (RuntimeException e) {
			return;
		}
		if (handler == null) {
			handler = new CaptureActivityHandler(this, decodeFormats,
					characterSet);
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if (!hasSurface) {
			hasSurface = true;
			initCamera(holder);
		}

	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		hasSurface = false;

	}

	public ViewfinderView getViewfinderView() {
		return viewfinderView;
	}

	public Handler getHandler() {
		return handler;
	}

	public void drawViewfinder() {
		viewfinderView.drawViewfinder();

	}

	private void initBeepSound() {
		if (playBeep && mediaPlayer == null) {

			setVolumeControlStream(AudioManager.STREAM_MUSIC);
			mediaPlayer = new MediaPlayer();
			mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			mediaPlayer.setOnCompletionListener(beepListener);

			AssetFileDescriptor file = getResources().openRawResourceFd(
					R.raw.beep);
			try {
				mediaPlayer.setDataSource(file.getFileDescriptor(),
						file.getStartOffset(), file.getLength());
				file.close();
				mediaPlayer.setVolume(BEEP_VOLUME, BEEP_VOLUME);
				mediaPlayer.prepare();
			} catch (IOException e) {
				mediaPlayer = null;
			}
		}
	}

	private static final long VIBRATE_DURATION = 200L;

	private void playBeepSoundAndVibrate() {
		if (playBeep && mediaPlayer != null) {
			mediaPlayer.start();
		}
		if (vibrate) {
			Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
			vibrator.vibrate(VIBRATE_DURATION);
		}
	}

	/**
	 * When the beep has finished playing, rewind to queue up another one.
	 */
	private final OnCompletionListener beepListener = new OnCompletionListener() {
		public void onCompletion(MediaPlayer mediaPlayer) {
			mediaPlayer.seekTo(0);
		}
	};

}
