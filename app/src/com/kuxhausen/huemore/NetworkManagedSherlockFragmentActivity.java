package com.kuxhausen.huemore;

import java.util.ArrayList;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.kuxhausen.huemore.MoodExecuterService.LocalBinder;
import com.kuxhausen.huemore.MoodExecuterService.OnBrightnessChangedListener;
import com.kuxhausen.huemore.network.BulbListSuccessListener.OnBulbListReturnedListener;
import com.kuxhausen.huemore.network.ConnectionMonitor;
import com.kuxhausen.huemore.network.OnConnectionStatusChangedListener;
import com.kuxhausen.huemore.persistence.DatabaseDefinitions.InternalArguments;
import com.kuxhausen.huemore.persistence.HueUrlEncoder;
import com.kuxhausen.huemore.state.Mood;

public class NetworkManagedSherlockFragmentActivity extends
		SherlockFragmentActivity implements OnConnectionStatusChangedListener, OnBrightnessChangedListener{

    
	private String moodName;
	private String groupName;
	
	private int[] bulbsCache;
	private Integer brightnessCache;
	
	public void setGroup(int[] bulbs, String optionalName){
		if(mBound)
			mService.onGroupSelected(bulbs, null);
		else
			bulbsCache = bulbs;
		
		groupName = optionalName;
	}
	public void setBrightness(int b){
		if(mBound)
			mService.setBrightness(b);
		else
			brightnessCache = b;
	}
	public void startMood(Mood m, String optionalName){
		Intent intent = new Intent(this, MoodExecuterService.class);
		intent.putExtra(InternalArguments.ENCODED_MOOD, HueUrlEncoder.encode(m,null,null));
		intent.putExtra(InternalArguments.MOOD_NAME, optionalName);
		this.startService(intent);
	}
	public void stopMood(){
		if(mBound){
			mService.stopMood();
		}
	}
	
	public String getCurentMoodName(){
		if(moodName!=null)
			return moodName;
		return "";
	}
	public String getCurentGroupName(){
		if(groupName!=null)
			return groupName;
		return "";
	}
	
	
	public OnBulbListReturnedListener bulbListenerFragment;
	
	public void setBulbListenerFragment(OnBulbListReturnedListener frag){
		bulbListenerFragment = frag;
	}
	public OnBulbListReturnedListener getBulbListenerFragment(){
		return bulbListenerFragment;
	}
	
	
	private MoodExecuterService mService = new MoodExecuterService();
    private boolean mBound = false;
	private ArrayList<OnServiceConnectedListener> serviceListeners = new ArrayList<OnServiceConnectedListener>();
    
	//register for a one time on service connected message
	public void registerOnServiceConnectedListener(OnServiceConnectedListener l){
		if(mBound)
			l.onServiceConnected();
		else
			serviceListeners.add(l);
	}
	
	public MoodExecuterService getService(){
		if(mBound)
			return mService;
		else
			return null;
	}
	public boolean boundToService(){
		return mBound;
	}
	
    @Override
	public void onConnectionStatusChanged(boolean status) {
		//override in subclass if needed
	}
    
    @Override
	public void onBrightnessChanged(int brightness) {
    	//override in subclass if needed
	}

	@Override
	public void onStart() {
		super.onStart();
		// Bind to LocalService
        Intent intent = new Intent(this, MoodExecuterService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	public void onStop() {
		super.onStop();
		
		// Unbind from the service
        if (mBound) {
        	mService.connectionListeners.remove(this);
        	mService.removeBrightnessListener(this);
            unbindService(mConnection);
            mBound = false;
        }
	}
	
	/** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            LocalBinder binder = (LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            mService.connectionListeners.add(NetworkManagedSherlockFragmentActivity.this);
            mService.registerBrightnessListener(NetworkManagedSherlockFragmentActivity.this);
            
            for(OnServiceConnectedListener l: serviceListeners){
            	l.onServiceConnected();
            }
            serviceListeners.clear();
            
            if(bulbsCache!=null){
    			mService.onGroupSelected(bulbsCache, null);
    			bulbsCache=null;
            }
            if(brightnessCache!=null){
            	mService.setBrightness(brightnessCache);
            	brightnessCache=null;
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };
    
    public interface OnServiceConnectedListener{
    	public abstract void onServiceConnected();
    }
}
