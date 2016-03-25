package com.example.floatingvideo;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.IOException;

public class VideoService extends Service implements TextureView.SurfaceTextureListener, MediaPlayer.OnCompletionListener{
    private static final String TAG = VideoService.class.getSimpleName();
    private static final float MIN_SCALE = 0.3f;
    private static final float MAX_SCALE = 2.0f;

    TextView removeButton, resizeButton;
    TextureView videoView;
    FrameLayout container;
    LayoutInflater inflater;
    WindowManager.LayoutParams params;
    MediaPlayer mediaPlayer;
    WindowManager wm;
    DisplayManager dm;
    Display display;
    DisplayMetrics displayMetrics = new DisplayMetrics();

    int dispWidth, dispHeight;
    int statusBarHeight;
    float w, h;
    float density;
    Rect dispRect = new Rect();
    float scale = 1.0f;
    boolean attached = false;

    public VideoService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");

        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        dm = (DisplayManager) getSystemService(
                Context.DISPLAY_SERVICE);
        display = wm.getDefaultDisplay();
        final Resources resources = getResources();
        final int statusBarHeightId = resources.getIdentifier("status_bar_height", "dimen", "android");
        if (statusBarHeightId > 0) {
            statusBarHeight = resources.getDimensionPixelSize(statusBarHeightId);
        } else {
            statusBarHeight = 0;
        }
        updateDefaultDisplayInfo();
        dm.registerDisplayListener(displayListener, null);

        inflater = LayoutInflater.from(this);
        container = (FrameLayout) inflater.inflate(R.layout.video_content, null);
        videoView = (TextureView) container.findViewById(R.id.video_view);
        removeButton = (TextView) container.findViewById(R.id.remove_button);
        resizeButton = (TextView) container.findViewById(R.id.resize_button);

        params = new WindowManager.LayoutParams(WindowManager.LayoutParams.TYPE_PRIORITY_PHONE);
        params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        params.format = PixelFormat.TRANSLUCENT;
        params.alpha = 1.0f;
        params.gravity = Gravity.TOP | Gravity.LEFT;

        setupCoordinates();
        wm.addView(container, params);
        attached = true;
        videoView.setSurfaceTextureListener(this);
        container.setOnTouchListener(moveListener);
        removeButton.setOnTouchListener(removeListener);
        resizeButton.setOnTouchListener(resizeListener);

        return START_NOT_STICKY;
    }

    private boolean updateDefaultDisplayInfo() {
        display.getMetrics(displayMetrics);
        dispWidth = displayMetrics.widthPixels;
        dispHeight = displayMetrics.heightPixels;
        return true;
    }

    private void setupCoordinates() {
        w = (w == 0) ? displayMetrics.widthPixels / MAX_SCALE : Math.min(w, displayMetrics.widthPixels / MAX_SCALE);
        h = (h == 0) ? w * 3f / 4f : Math.min(h, w * 3f / 4f);
        dispRect = new Rect(0, statusBarHeight, dispWidth, dispHeight);
        params.x = constrain(dispRect.left, dispRect.right - Math.round(Math.round(w * scale)), Math.round(dispWidth - w * scale));
        params.y = constrain(dispRect.top, dispRect.bottom - Math.round(h * scale), Math.round(dispHeight - h * scale));
        params.width = Math.round(w * scale);
        params.height = Math.round(h * scale);
        videoView.setPivotX(0);
        videoView.setPivotY(0);
        videoView.getLayoutParams().width = Math.round(w);
        videoView.getLayoutParams().height = Math.round(h);
        videoView.setScaleX(scale);
        videoView.setScaleY(scale);
    }

    private void relayout() {
        if (!attached) return;
        setupCoordinates();
        wm.updateViewLayout(container, params);
    }

    private void dismiss() {
        if (!attached) return;
        attached = false;
        dm.unregisterDisplayListener(displayListener);
        wm.removeView(container);
    }

    private final View.OnTouchListener moveListener = new View.OnTouchListener() {
        private int initialX;
        private int initialY;
        private float initialTouchX;
        private float initialTouchY;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = params.x;
                    initialY = params.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int diffX = (int) (event.getRawX() - initialTouchX);
                    int diffY = (int) (event.getRawY() - initialTouchY);
                    params.x = constrain(dispRect.left, dispRect.right - Math.round(Math.round(params.width)), initialX + diffX);
                    params.y = constrain(dispRect.top, dispRect.bottom - Math.round(Math.round(params.height)), initialY + diffY);
                    wm.updateViewLayout(container, params);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    return true;
                case MotionEvent.ACTION_UP:
                    return true;
            }
            return false;
        }
    };

    private final View.OnTouchListener removeListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    dismiss();
                    stopSelf();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    return true;
                case MotionEvent.ACTION_UP:
                    return true;
            }
            return false;
        }
    };

    private final View.OnTouchListener resizeListener = new View.OnTouchListener() {
        private int initialX;
        private int initialY;
        private float initialTouchX;
        private float initialTouchY;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = params.x;
                    initialY = params.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float touchX = event.getRawX();
                    float touchY = event.getRawY();
                    int diffX = (int) (touchX - initialTouchX);
                    int oldW = params.width;
                    int newW = oldW + diffX;
                    float newScale = scale * (float) newW / (float) oldW;
                    scale = constrain(MIN_SCALE, MAX_SCALE, newScale);
                    videoView.setScaleX(scale);
                    videoView.setScaleY(scale);
                    params.width = Math.round(w * scale);
                    params.height = Math.round(h * scale);
                    wm.updateViewLayout(container, params);
                    initialTouchX = touchX;
                    initialTouchY = touchY;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    return true;
                case MotionEvent.ACTION_UP:
                    return true;
            }
            return false;
        }
    };

    private final DisplayManager.DisplayListener displayListener =
            new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                    Log.d(TAG, "onDisplayAdded");
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    Log.d(TAG, "onDisplayChanged");
                    if (displayId == display.getDisplayId()) {
                        updateDefaultDisplayInfo();
                        setupCoordinates();
                        relayout();
                    }
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                    Log.d(TAG, "onDisplayRemoved");
                    if (displayId == display.getDisplayId()) {
                        dismiss();
                    }
                }
            };

    @Override
    public void onDestroy() {
        super.onDestroy();
        dismiss();
    }

    private int constrain(int min, int max, int value) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private float constrain(float min, float max, float value) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.d(TAG, "onSurfaceTextureAvailable");
        Surface s = new Surface(surface);
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.sample));
            mediaPlayer.setSurface(s);
            mediaPlayer.setOnCompletionListener(this);
            mediaPlayer.prepare();
            mediaPlayer.start();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.d(TAG, "onSurfaceTextureSizeChanged");
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.d(TAG, "onSurfaceTextureDestroyed");
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        mediaPlayer.seekTo(0);
        mediaPlayer.start();
    }
}
