package com.multiwii.Utilities;

import com.multiwii.multiwiiremote.App;

import android.app.Activity;
import android.view.View;

import view.mjpeg.MjpegInputStream;
import view.mjpeg.MjpegView;

public class Camera {
	private MjpegView mv;
	private Activity mActivity;
    private boolean isStart = false;

    public boolean isStart() {
        return isStart;
    }

	public Camera(MjpegView mv, Activity mActivity) {
		this.mv = mv;
		this.mActivity = mActivity;
        isStart = false;
	}

	public void start() {
		String URL = "http://" + ((App) mActivity.getApplication()).IpAddress
				+ ":8080/?action=stream";
		mv.setSource(MjpegInputStream.read(URL));
		mv.setDisplayMode(MjpegView.SIZE_BEST_FIT);
        mv.showFps(true);
       // mv.setVisibility(View.VISIBLE);
        isStart = true;
	}

	public void stop() {
        mv.stopPlayback();
        isStart = false;
	}
}
