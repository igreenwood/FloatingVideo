package com.example.floatingvideo;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.VideoView;

import com.devbrackets.android.exomedia.EMVideoView;
import com.google.android.exoplayer.util.VerboseLogUtil;

public class VideoService extends Service{
    private static final String TAG = VideoService.class.getSimpleName();
    VideoView videoView;
    LayoutInflater inflater;
    WindowManager wm;
    WindowManager.LayoutParams params;
    int dispWidth, dispHeight;
    float w, h;
    float density;
    Rect dispRect = new Rect();
    boolean resizing = false;

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
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        density = metrics.density;
        w = 300 * density;
        h = 225 * density;
        dispWidth = metrics.widthPixels;
        dispHeight = metrics.heightPixels;
        dispRect = new Rect(Math.round(-dispWidth/2), Math.round(-dispHeight/2), Math.round(dispWidth/2), Math.round(dispHeight/2));
        Log.d(TAG, "dispWidth = "+dispWidth);
        Log.d(TAG, "dispHeihgt = "+dispHeight);

        params = new WindowManager.LayoutParams(
                Math.round(w),
                Math.round(h),
                dispRect.right - Math.round(w/2),
                dispRect.bottom - Math.round(h/2),
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        setupVideoView();

        return super.onStartCommand(intent, flags, startId);
    }

    private void setupVideoView() {
        videoView =(VideoView) inflater.inflate(R.layout.video_content, null);
        wm.addView(videoView, params);
        videoView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
//                        Log.d(TAG, "event.getRawX()= " + (event.getRawX() - dispWidth / 2));
//                        Log.d(TAG, "event.getRawY()= " + (event.getRawY() - dispHeight / 2));
//                        Log.d(TAG, "event.getX()= " + event.getX());
//                        Log.d(TAG, "event.getY()= " + event.getY());
//                        Log.d(TAG, "params.x = " + params.x);
//                        Log.d(TAG, "params.y = " + params.y);
//                        Log.d(TAG, "params.width = " + (params.width));
//                        Log.d(TAG, "params.height = " + (params.height));
                        RectF removeRect = new RectF();
                        removeRect.right = params.x + params.width / 2;
                        removeRect.left = removeRect.right - density * 64;
                        removeRect.top = params.y - params.height / 2;
                        removeRect.bottom = removeRect.top + density * 64;
                        RectF resizeRect = new RectF();
                        resizeRect.right = params.x + params.width / 2;
                        resizeRect.left = removeRect.right - density * 64;
                        resizeRect.bottom = params.y + params.height / 2;
                        resizeRect.top = resizeRect.bottom - density * 64;

                        if (removeRect.contains(event.getRawX() - dispWidth / 2, event.getRawY() - dispHeight / 2)) {
                            wm.removeView(videoView);
                            videoView = null;
                            stopSelf();
                            return true;
                        } else if (resizeRect.contains(event.getRawX() - dispWidth / 2, event.getRawY() - dispHeight / 2)) {
                            resizing = true;
                            Log.d(TAG, "resize start");
                        }
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int diffX = (int) (event.getRawX() - initialTouchX);
                        int diffY = (int) (event.getRawY() - initialTouchY);
                        if (resizing) {
                            int oldW = params.width;
                            int oldH = params.height;
                            int newW = oldW + diffX;
                            int newH = oldH + diffY;
                            params.x = constrain(dispRect.left + Math.round(w / 2), dispRect.right - Math.round(Math.round(w / 2)), -oldW/2 + (diffX + oldW)/2);
                            params.y = constrain(dispRect.top + Math.round(h / 2), dispRect.bottom - Math.round(h / 2), -oldH/2 + (diffY + oldH)/2);
                            params.width = newW;
                            params.height = newH;
                            wm.updateViewLayout(videoView, params);
                        } else {
                            params.x = constrain(dispRect.left + Math.round(w / 2), dispRect.right - Math.round(Math.round(w / 2)), initialX + diffX);
                            params.y = constrain(dispRect.top + Math.round(h / 2), dispRect.bottom - Math.round(h / 2), initialY + diffY);
                            wm.updateViewLayout(videoView, params);
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        return true;
                    case MotionEvent.ACTION_UP:
                        resizing = false;
                        return true;
                }
                return false;
            }
        });
        try {
            videoView.setVideoPath("android.resource://" + getPackageName() + "/" + R.raw.sample);
            videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){
                @Override
                public void onCompletion(MediaPlayer mp) {
                    videoView.seekTo(0);
                    videoView.start();
                }
            });
            videoView.start();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(videoView != null) wm.removeView(videoView);
    }

    private int constrain(int min, int max, int value){
        if(value < min) return min;
        if(value > max) return max;
        return value;
    }
}
