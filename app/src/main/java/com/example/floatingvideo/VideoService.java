package com.example.floatingvideo;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
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
    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 2.0f;

    TextView moveButton, removeButton, resizeButton;
    TextureView videoView;
    FrameLayout container;
    LayoutInflater inflater;
    WindowManager wm;
    WindowManager.LayoutParams params;
    MediaPlayer mediaPlayer;
    DisplayManager dm;
    Display display;
    DisplayMetrics displayMetrics = new DisplayMetrics();

    int dispWidth, dispHeight;
    float w, h;
    float density;
    Rect dispRect = new Rect();
    float scale = 1.0f;

    public VideoService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        inflater = LayoutInflater.from(this);

        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        dm = (DisplayManager) getSystemService(
                Context.DISPLAY_SERVICE);
        display = wm.getDefaultDisplay();
        updateDefaultDisplayInfo();
        dm.registerDisplayListener(displayListener, null);

        density = displayMetrics.density;
        w = 300 * density;
        h = 225 * density;

        dispRect = new Rect(0, 0, dispWidth, dispHeight);
        params = new WindowManager.LayoutParams(WindowManager.LayoutParams.TYPE_PHONE);
        params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        params.alpha = 0.8f;
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = constrain(dispRect.left, dispRect.right - Math.round(Math.round(w)), Math.round(dispWidth - w));
        params.y = constrain(dispRect.top, dispRect.bottom - Math.round(h), Math.round(dispHeight - h));
        params.width = Math.round(w);
        params.height = Math.round(h);
        setupVideoView();

        return super.onStartCommand(intent, flags, startId);
    }

    private void setupVideoView() {
        container = (FrameLayout) inflater.inflate(R.layout.video_content, null);
        wm.addView(container, params);
        videoView = (TextureView) container.findViewById(R.id.video_view);
        removeButton = (TextView) container.findViewById(R.id.remove_button);
        resizeButton = (TextView) container.findViewById(R.id.resize_button);
        moveButton = (TextView) container.findViewById(R.id.move_button);
        moveButton.setOnTouchListener(new View.OnTouchListener() {
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
        });
        removeButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        wm.removeView(container);
                        container = null;
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
        });
        videoView.setPivotX(0);
        videoView.setPivotY(0);
        videoView.getLayoutParams().width = Math.round(w);
        videoView.getLayoutParams().height = Math.round(h);
        videoView.setSurfaceTextureListener(this);
        resizeButton.setOnTouchListener(new View.OnTouchListener() {
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
                        Log.d(TAG, "scale = " + scale);
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
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (container != null) wm.removeView(container);
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
        Surface s = new Surface(surface);
        try {
            mediaPlayer= new MediaPlayer();
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

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.d(TAG, "onSurfaceTextureDestroyed");
        if(mediaPlayer != null){
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

    private boolean updateDefaultDisplayInfo() {
        display.getMetrics(displayMetrics);
        dispWidth = displayMetrics.widthPixels;
        dispHeight = displayMetrics.heightPixels;
        return true;
    }

    private void dismiss(){
        dm.unregisterDisplayListener(displayListener);
        wm.removeView(container);
    }

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
                        if (updateDefaultDisplayInfo()) {

                        }else{
                            dismiss();
                        }
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
}
