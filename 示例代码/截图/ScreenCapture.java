package com.zjy.screencapturetest;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.RequiresApi;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ScreenCapture {
    private static final String TAG = "screenCapture";

    private MediaProjection mMediaProjection = null;
    private VirtualDisplay mVirtualDisplay = null;

    private static int mResultCode = 0;
    private static Intent mResultData = null;
    private static MediaProjectionManager mMediaProjectionManager1 = null;

    private int windowWidth = 0;
    private int windowHeight = 0;
    private ImageReader mImageReader = null;
    private int mScreenDensity = 0;

    private Handler handler = new Handler(Looper.getMainLooper());
    private Activity activity;
    private boolean mIsCaptureSuccess = true;


    @TargetApi(Build.VERSION_CODES.KITKAT)
    public ScreenCapture(Activity activity, Intent intent, int resultCode) {
        this.activity = activity;
        mResultData = intent;
        mResultCode = resultCode;
        try {
            createVirtualEnvironment();
        } catch (Exception e) {
            captureFail("ScreenCapture -> Exception: " + e);
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public void toCapture() {
        try {
            handler.postDelayed(new Runnable() {
                public void run() {
                    Log.d(TAG, "toCapture: startVirtual start");
                    startVirtual();
                    Log.d(TAG, "toCapture: startVirtual end");
                }
            }, 10);

            handler.postDelayed(new Runnable() {
                public void run() {
                    try {
                        Log.d(TAG, "toCapture: startCapture start");
                        startCapture();
                        Log.d(TAG, "toCapture: startCapture end");
                    } catch (Exception e) {
                        captureFail("toCapture -> exception1: " + e);
                    }
                }
            }, 100);
        } catch (Exception e) {
            captureFail("toCapture -> exception2: " + e);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void createVirtualEnvironment() {
        mMediaProjectionManager1 = (MediaProjectionManager) activity.getApplication().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        WindowManager windowManager = (WindowManager) activity.getApplication().getSystemService(Context.WINDOW_SERVICE);
        windowWidth = getScreenWidth(activity);
        windowHeight = getScreenHeight(activity);
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;
        mImageReader = ImageReader.newInstance(windowWidth, windowHeight, 0x1, 2); //ImageFormat.RGB_565
        Log.d(TAG, "createVirtualEnvironment: prepared the virtual environment");
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void startVirtual() {
        if (mMediaProjection != null) {
            Log.d(TAG, "startVirtual: to virtualDisplay");
            virtualDisplay();
        } else {
            Log.d(TAG, "startVirtual: to build mediaProjection and virtualDisplay");
            setUpMediaProjection();
            virtualDisplay();
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void setUpMediaProjection() {
        try {
            mMediaProjection = mMediaProjectionManager1.getMediaProjection(mResultCode, mResultData);
        } catch (Exception e) {
            captureFail("setUpMediaProjection -> Exception: " + e);
        }

    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void virtualDisplay() {
        try {
            mVirtualDisplay = mMediaProjection.createVirtualDisplay("screen-mirror",
                    windowWidth, windowHeight, mScreenDensity, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    mImageReader.getSurface(), null, null);
            Log.d(TAG, "virtualDisplay: virtual displayed.");
        } catch (Exception e) {
            captureFail("virtualDisplay -> Exception: " + e);
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void startCapture() throws Exception {
        Image image = mImageReader.acquireLatestImage();
        if (image == null) {
            Log.d(TAG, "startCapture: image==null, restart -> toCapture");
            handler.post(new Runnable() {
                @Override
                public void run() {
                    toCapture();
                }
            });
            return;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        final Image.Plane[] planes = image.getPlanes();
        final ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * width;
        Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        image.close();
        Log.d(TAG, "startCapture: image data captured, To save Bitmap.");

        saveCutBitmap(bitmap);
        bitmap.recycle();
    }

    private void saveCutBitmap(Bitmap cutBitmap) {
        String savePath = Environment.getExternalStorageDirectory().getPath() + "/testPic";
        String fileName = getTimeByLong(System.currentTimeMillis())+".png";
        File file = new File(savePath, fileName);
        File dir = file.getParentFile();
        if (!dir.exists() && !dir.mkdirs()) {
            captureFail("can not create dir: " + dir.getAbsolutePath());
        }

        try {
            if (!file.exists() && !file.createNewFile()) {
                captureFail("can not create file: " + file.getAbsolutePath());
            }
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            cutBitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream);
            fileOutputStream.flush();
            fileOutputStream.close();
            Log.d(TAG, "saveCutBitmap: save bitmap success.");
        } catch (IOException e) {
            captureFail("saveCutBitmap -> Exception:" + e);
        }
        onDestroy();
        if (mIsCaptureSuccess) {
            Intent imageIntent = new Intent(Intent.ACTION_SEND);
            imageIntent.setType("image/png");
            imageIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
            imageIntent.putExtra(Intent.EXTRA_SUBJECT, "分享");
            imageIntent.putExtra(Intent.EXTRA_TEXT, "");
            imageIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(Intent.createChooser(imageIntent, "分享"));
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void tearDownMediaProjection() {
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void stopVirtual() {
        if (mVirtualDisplay == null) {
            return;
        }
        mVirtualDisplay.release();
        mVirtualDisplay = null;
    }

    public void onDestroy() {
        stopVirtual();
        tearDownMediaProjection();
    }

    private int getScreenWidth(Activity activity) {
        DisplayMetrics localDisplayMetrics = new DisplayMetrics();
        ((WindowManager) activity.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getMetrics(localDisplayMetrics);
        return localDisplayMetrics.widthPixels;
    }

    private int getScreenHeight(Activity activity) {
        return activity.getWindowManager().getDefaultDisplay().getHeight() + getNavigationBarHeight(activity);
    }

    private int getNavigationBarHeight(Activity activity) {
        if (!isNavigationBarShow(activity)) {
            return 0;
        }
        Resources resources = activity.getResources();
        int resourceId = resources.getIdentifier("navigation_bar_height",
                "dimen", "android");
        return resources.getDimensionPixelSize(resourceId);
    }

    private boolean isNavigationBarShow(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            Display display = activity.getWindowManager().getDefaultDisplay();
            Point size = new Point();
            Point realSize = new Point();
            display.getSize(size);
            display.getRealSize(realSize);
            return realSize.y != size.y;
        } else {
            boolean menu = ViewConfiguration.get(activity).hasPermanentMenuKey();
            boolean back = KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_BACK);

            return !(menu || back);
        }
    }

    private void captureFail(String logMes) {
        mIsCaptureSuccess = false;
        Toast.makeText(activity, "截图失败", Toast.LENGTH_SHORT).show();
        Log.d(TAG, logMes);
    }

    private String getTimeByLong(long time) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.CHINA);
        return sdf.format(new Date(time));
    }
}