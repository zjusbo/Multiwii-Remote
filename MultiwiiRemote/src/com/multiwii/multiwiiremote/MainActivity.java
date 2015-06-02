package com.multiwii.multiwiiremote;


import view.Arrow.ArrowView;
import view.joystick.DualJoystickView;
import view.joystick.JoystickView;
import view.mjpeg.MjpegView;

import com.multiwii.Utilities.Camera;
import com.multiwii.Utilities.Utilities;
import com.multiwii.communication.DeviceListActivity;
import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.ToggleButton;
import static com.multiwii.multiwiiremote.RCSignals.*;

public class MainActivity extends BaseActivity {
    private long requestStartTime;
    private boolean isReceived = true;
    private long lastRequestTime = 0;
    private int packageSendNum = 0;
    private int packageLostNum = 0;
    private Camera mCam;

    private enum InputMode {
		ACCELEROMETER, TOUCH
	}

	private InputMode inputMode = InputMode.TOUCH;
    private ArrowView arrowView;
    private DualJoystickView dualJoystickView;
	private ToggleButton auxBtn[] = new ToggleButton[4];
    private ToggleButton synchronizeHead;
    private TextView txtHeader;
	private TextView txtStatus;
	private TextView txtUIDebug;
    private int mwcHeading = 0;
    private float packageLostRate = 0;
    private String debugTextTemplate = "%sPhone Heading: %d\nMWC Heading:%d\nDelay: %dms\nPackage Lost Rate: %f%%";
    private long delayTime = 0;
    private long [] delayTimeRaw = new long[5];
    private boolean isDelayTimeAvailable = false;
    private int delayTimeIdx = 0;
    private boolean synchronizeHeadButton = false;
    public CheckBox getChkUsePhoneHeading() {
        return chkUsePhoneHeading;
    }

    public void setChkUsePhoneHeading(CheckBox chkUsePhoneHeading) {
        this.chkUsePhoneHeading = chkUsePhoneHeading;
    }

    private CheckBox chkUsePhoneHeading;
	public RCSignals rc;
	// private byte timer = 0;
	// private Camera mCam;
	MainActivityEvents mEvents;
	private MainActivityCommunicationHandler mHandler;

	private final int BLUETOOTH_SEARCH_RETURN = 1;
	private final int SETTINGS_MODIFY = 2;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		//KeepScreenOn(app.KeepScreenOn);
		KeepScreenOn(true);
		
		rc = new RCSignals();
		mCam = new Camera((MjpegView) this.findViewById(R.id.webcamView), this);
		mEvents = new MainActivityEvents(this);
		mHandler = new MainActivityCommunicationHandler(this);
		
		txtStatus = (TextView) findViewById(R.id.status);
		txtHeader = (TextView) findViewById(R.id.throttleView);
		txtUIDebug = (TextView) findViewById(R.id.debugTxt);
		for (int x = 0; x < auxBtn.length; x++)
			auxBtn[x] = (ToggleButton) findViewById(getResources().getIdentifier("aux" + (x + 1) + "Btn", "id",	getPackageName()));
        synchronizeHead = (ToggleButton) findViewById(R.id.synchronizeHeadButton);
        dualJoystickView = (DualJoystickView) findViewById(R.id.DualJoystickView);
        chkUsePhoneHeading = (CheckBox) findViewById(R.id.chkUsePhoneHeading);
		arrowView = (ArrowView) findViewById(R.id.ArrowView);
		Init();
	}

	private void Init() {
		app.SetHandler(mHandler); //App class will automatically bind to commMW
        app.setMainActivity(this);
		chkUsePhoneHeading.setOnCheckedChangeListener(mEvents.mCheckChangeListener);
        arrowView.setMainActivity(this);
		((Button) findViewById(R.id.switchModes)).setOnClickListener(mEvents.mClickListener);
		//joystick.setOnJostickMovedListener(mEvents._listener);
        dualJoystickView.stickR.setOnJostickMovedListener(mEvents._listener);
        dualJoystickView.stickL.setOnJostickMovedListener(mEvents._throttleListener);
        dualJoystickView.stickL.setAutoReturnToCenter(false);
        dualJoystickView.stickL.setAutoReturnToMid(true);
		for (int x = 0; x < auxBtn.length; x++)
			auxBtn[x].setOnClickListener(mEvents.mClickListener);
		settingsModified();
	}
	public boolean getConnectState(){
        return app.commMW.Connected;
    }
	public void FrequentTasks() {
		mHandler.sendEmptyMessage(7);
        //send RC signal
		if(app.commMW.Connected) {
		//Create payload TODO
            //new cycle begin if an ATTITUDE ACK is received or 300ms passed(package lost)
            long currentTime = System.currentTimeMillis();
            if(app.protocol.isIs_ATTITUDE_received() == true){
                    app.protocol.setIs_ATTITUDE_received(false);
                    delayTimeRaw[delayTimeIdx++] = app.protocol.attitudeReceivedTime - lastRequestTime;
                    if(delayTimeIdx == delayTimeRaw.length){
                        delayTimeIdx = 0;
                        isDelayTimeAvailable = true;
                    }
                    if(isDelayTimeAvailable == true){
                        long sum = 0;
                        for(long i : delayTimeRaw){
                            sum += i;
                        }
                        delayTime = sum / delayTimeRaw.length;
                    }
                lastRequestTime = currentTime;
                app.protocol.SendRequestMSP_ATTITUDE();
                packageSendNum++;
                mwcHeading = app.protocol.head;
                }
            //we consider it as a package lost, resend package
            else if(currentTime - lastRequestTime > 50){
                packageLostNum++;
                packageSendNum++;
                app.protocol.SendRequestMSP_ATTITUDE();
            }
            if(packageSendNum >= 100){
                packageLostRate = (float)packageLostNum / packageSendNum * 100;
                packageLostNum = packageSendNum = 0;
            }

            //synchronize heading of plane and phone,
            //currently using Proportion (without Integration and Differential)
            if(synchronizeHead.isChecked()){
                synchronizeHeadButton = true;
                int yawOffsetRange = 70;
                double yawOffset = calYawOffset(app.sensors.heading, mwcHeading, 1, 0.1, yawOffsetRange);
                this.rc.setAdjustedYaw((int)yawOffset);
           }
            else{
                if(synchronizeHeadButton == true){
                    this.rc.setAdjustedYaw(0);
                    synchronizeHeadButton = false;
                }
            }
            //send rc signal
            app.protocol.SendRequestMSP_SET_RAW_RC(rc.get()); //TODO Check that delay isnt too big from other tasks

            //set MWC Headings in every 500 miliseconds
//            if (chkUsePhoneHeading.isChecked()){
//                    //refresh heading in every 500 mili seconds
//                    if( currentTime - lastHeadingRefreshTime > 500 ){
//                        thisHeading = app.sensors.heading;
//                        app.protocol.SendRequestMSP_SET_HEAD(thisHeading);
//                        lastHeadingRefreshTime = currentTime;
//                    }
//                }


            //SONG BO ADD BEGIN---------------------------------

            //data transmitted from MWC to Phone is processed in ProcessSerialData() in ConnectedThread
//            while(!app.protocol.is_SET_RAW_RC_received ){
//                app.protocol.ProcessSerialData(false);
//                if (app.protocol.is_SET_HEAD_received){
//                    lastValidateHeading = thisHeading;
//                    app.protocol.is_SET_HEAD_received = false;
//                }
//                delayTime = System.currentTimeMillis() - requestStartTime;
//            }
//            app.protocol.is_SET_RAW_RC_received = false;
            //SONG BO ADD END


            //check cam status
            if(app.UseCamera == true && mCam.isStart() == false){
                mCam.start();
            }
            else if(app.UseCamera == false && mCam.isStart() == true){
                mCam.stop();
            }
		}
		app.FrequentTasks();
	}

    private double PIoutput = 0;
    private long PISamplingTime = -1;
    private double PIlastDelta = 0;
    //samplingInterval in seconds
    private double PI(double delta, double P, double I, double outputLimit){
        double output;
        boolean isIntegration = false;
        boolean isOverflow = false;
        long samplingInterval = System.currentTimeMillis() - PISamplingTime;
        //first call this function
        //we only use P to calculate the output
        if(PISamplingTime == -1){
            PISamplingTime = System.currentTimeMillis();
            PIoutput = delta * P;
            PIlastDelta = delta;
            isIntegration = false;
        }
        //use P and I to calculate the output
        else{
            double outputDelta;
            PISamplingTime = System.currentTimeMillis();
            //interval is too big, reset the PI parameters
            if(samplingInterval > 100){
                PISamplingTime = -1;
                PIoutput = delta * P;
                isIntegration = false;
            }
            else{
                outputDelta = P * ((delta - PIlastDelta) + samplingInterval / 1000.0 * I * delta);
                PIlastDelta = delta;
                PIoutput = PIoutput + outputDelta;
                isIntegration = true;
            }
        }
        if(PIoutput > outputLimit){
            output = outputLimit;
            isOverflow = true;
        }
        else if(PIoutput < -outputLimit){
            output = -outputLimit;
            isOverflow = true;
        }
        else{
            output = PIoutput;
            isOverflow = false;
        }
        if(isIntegration == true && isOverflow == true){
            //终止积分作用，防止过饱和
            PIoutput -= samplingInterval / 1000.0 * I * delta;
        }
        return output;
    }
    private double calYawOffset(int targetHeading, int currentHeading, double P, double I, int maxRange) {
       double delta;
       delta = targetHeading - currentHeading;
       if(delta < -180){
           delta += 360;
       }
       else if( delta > 180){
           delta -= 360;
       }
       //now delta ranges from -180~+180
       delta = 100.0 / 180 * delta;

       //now delta ranges from -100~100
       delta = PI(delta, P, I, maxRange);
       return delta;

    }

    public void  setStatus(String status) {
		this.txtStatus.setText(status);
		app.Status = status;
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		switch (requestCode) {
			case BLUETOOTH_SEARCH_RETURN:
				if (resultCode == Activity.RESULT_OK) {
					String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
					app.protocol.Connect(address, app.BaudRate, app.BluetoothConnectionStartDelay);
				}
			break;
			case SETTINGS_MODIFY:
				settingsModified();
			break;
		}
	}
	private void settingsModified() {
		rc.ThrottleResolution = app.ThrottleResolution;
		rc.TrimRoll = app.TrimRoll;
		rc.TrimPitch = app.TrimPitch;
		rc.RollPitchLimit = app.RollPitchLimit;
		
		if (app.AuxTextChanged) {
			setAuxbtnTxt(auxBtn[0], app.Aux1Txt);
			setAuxbtnTxt(auxBtn[1], app.Aux2Txt);
			setAuxbtnTxt(auxBtn[2], app.Aux3Txt);
			setAuxbtnTxt(auxBtn[3], app.Aux4Txt);
			app.AuxTextChanged = false;
		}
		setStatus("Ready " + app.comMode.toString());
	}

    public int getPhoneHeading(){
        return app.sensors.heading;
    }

    public int getPlaneHeading(){
        return mwcHeading;
    }
	private void setAuxbtnTxt(ToggleButton mButton, String text) {
		mButton.setText(text);
		mButton.setTextOn(text);
		mButton.setTextOff(text);
	}
	public void Connect() {
		switch (app.comMode) {
		case BLUETOOTH:
			Intent searchBtDevices = new Intent(this, DeviceListActivity.class);
			startActivityForResult(searchBtDevices, BLUETOOTH_SEARCH_RETURN);
			break;
		case WIFI:
			app.protocol.Connect(app.IpAddress, app.IpPort, app.WifiConnectionStartDelay);
			break;
		}
	}
	public void UpdateUI() {
		txtHeader.setText(rc.adjustMode.getValue() + rc.get(rc.adjustMode.getId()));
		txtUIDebug.setText(app.UIDebug ? String.format(debugTextTemplate, rc.toStringNoThrottle(), app.sensors.heading, mwcHeading, delayTime, packageLostRate)  : "");
		txtStatus.setText(app.Status);
	}
	
	// int requests[] = new int[] { MultirotorData.MSP_DEBUG,
	// MultirotorData.MSP_ALTITUDE, MultirotorData.MSP_RC };
	public void onSensorsStateChangeRotate() {
		if (inputMode == InputMode.ACCELEROMETER) {
			float xCoordinate = (float) Utilities.mapCons(-app.sensors.pitch,
					app.sensors.getMinValue(),
					app.sensors.getMaxValue(),
					-dualJoystickView.stickR.getMovementRange()/2, dualJoystickView.stickR.getMovementRange()/2);
			float yCoordinate = (float) Utilities.mapCons(-app.sensors.roll,
					app.sensors.getMinValue(),
					app.sensors.getMaxValue(),
					-dualJoystickView.stickR.getMovementRange()/2, dualJoystickView.stickR.getMovementRange()/2);
          dualJoystickView.stickR.setCoordinates(xCoordinate, yCoordinate);
		}
	}

	public void aux1_Click(View v) {
		// Replaced with When Throttle >= 1030
        rc.set(AUX1, ((ToggleButton) v).isChecked());
	}

	public void aux2_Click(View v) {
		rc.set(AUX2, ((ToggleButton) v).isChecked());
	}

	public void aux3_Click(View v) {
		rc.set(AUX3, ((ToggleButton) v).isChecked());
	}

	public void aux4_Click(View v) {
		rc.set(AUX4, ((ToggleButton) v).isChecked());
	}
	
	@Override
	protected void onStop() {
		super.onStop();
        Log.d("LifeCycle", "I'm in onStop in mainActivity");
        //stop camera
        if(mCam.isStart()){
            mCam.stop();
        }
		app.stop();

	}
	// ///////////////////Menu///////////////////
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.connect:
			Connect();
			break;
		case R.id.accelOn:
			if (!app.sensors.isRotateSensorSupported())
				Utilities.showToast("Sorry, your device does not have an accelerometer", this);
			else {
				switch (inputMode) {
					case TOUCH:
						inputMode = InputMode.ACCELEROMETER;
					break;
					case ACCELEROMETER:
						inputMode = InputMode.TOUCH;
						dualJoystickView.stickR.returnHandleToCenter();
					break;
				}
			}
			break;
		case R.id.menu_settings:
			if (rc.isFlying()) {
				Utilities.showToast(
						"Set throttle under 1100 to access settings", this);
			} else {
				this.startActivityForResult(new Intent(this, Settings.class), SETTINGS_MODIFY);
			}
			break;
		}
		return super.onContextItemSelected(item);
	}

	// /////////////////////////////End Menu/////////////////////////////
	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
	if(app.PreventExitWhenFlying && rc.isFlying() && (event.getKeyCode() == KeyEvent.KEYCODE_BACK || event.getKeyCode() == KeyEvent.KEYCODE_APP_SWITCH || event.getKeyCode() == KeyEvent.KEYCODE_HOME))
	{
		Utilities.showToast("Detected flying. Please put throttle under 1100.", this);
		return true;
	}
		if (event.getAction() == KeyEvent.ACTION_DOWN)
			switch (event.getKeyCode()) {
			case KeyEvent.KEYCODE_VOLUME_UP:
				rc.adjustRcValue(-1);
				return true;
			case KeyEvent.KEYCODE_VOLUME_DOWN:
				rc.adjustRcValue(1);
				return true;
			case KeyEvent.KEYCODE_SEARCH:
				app.setManualModeOn(!app.getManualMode());
				return true;
			}
		return super.dispatchKeyEvent(event);
	}
}
