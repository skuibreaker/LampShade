package com.kuxhausen.huemore.editmood;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.google.gson.Gson;
import com.kuxhausen.huemore.GodObject;
import com.kuxhausen.huemore.R;
import com.kuxhausen.huemore.R.id;
import com.kuxhausen.huemore.R.layout;
import com.kuxhausen.huemore.editmood.EditStatePagerDialogFragment.OnCreateColorListener;
import com.kuxhausen.huemore.persistence.DatabaseDefinitions.InternalArguments;
import com.kuxhausen.huemore.persistence.Utils;
import com.kuxhausen.huemore.state.Event;
import com.kuxhausen.huemore.state.Mood;
import com.kuxhausen.huemore.state.api.BulbState;

public class EditColorTempFragment extends Fragment implements
		OnSeekBarChangeListener, OnCreateColorListener {

	private int mInitialColor;
	private BulbState hs = new BulbState();
	{
		hs.on = true;
		hs.effect = "none";

		hs.ct = 1000000/4000;
	}
	Gson gson = new Gson();
	SeekBar seekBar;
	EditText tempEditText;
	int seekBarOffset = 2000;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mInitialColor = 0;

		View groupDialogView = inflater.inflate(R.layout.edit_temp_color, null);

		seekBar = (SeekBar) groupDialogView.findViewById(R.id.temperatureBar);
		
		seekBar.setProgress((1000000 / hs.ct) - seekBarOffset );
		
		seekBar.setOnSeekBarChangeListener(this);
		
		tempEditText = (EditText) groupDialogView
				.findViewById(R.id.temperatureText);
		tempEditText.setVisibility(View.VISIBLE);
		
		tempEditText.setText(hs.getCT());
		
		tempEditText.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId,
					KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_DONE) {
					int temp = Integer.parseInt((tempEditText.getText()
							.toString()));
					temp = Math.max(temp, 0);
					temp = Math.min(temp, seekBarOffset + seekBar.getMax());
					seekBar.setProgress(temp - seekBarOffset);
					hs.ct = ((1000000 / temp));
					preview();
				}
				return false;
			}

		});

		return groupDialogView;
	}

	public void loadPrevious(BulbState bs){
		if(bs.ct!=null)
			hs.ct = bs.ct;
	}
	@Override
	public void onProgressChanged(SeekBar seekBar, int progress,
			boolean fromUser) {
		if(fromUser){
			hs.ct = ((1000000 / (seekBarOffset + seekBar.getProgress())));
			tempEditText.setText("" + (seekBarOffset + seekBar.getProgress()));
			//preview();
		}
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
		hs.ct = ((1000000 / (seekBarOffset + seekBar.getProgress())));
		tempEditText.setText("" + (seekBarOffset + seekBar.getProgress()));
		//preview();
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
		hs.ct = ((1000000 / (seekBarOffset + seekBar.getProgress())));
		tempEditText.setText("" + (seekBarOffset + seekBar.getProgress()));
		preview();
	}

	public void preview() {
		Mood m = Utils.generateSimpleMood(hs);
		
		Utils.transmit(this.getActivity(), InternalArguments.ENCODED_TRANSIENT_MOOD, m, ((GodObject) getActivity()).getBulbs(), null);
	}

	@Override
	public Intent onCreateColor(Integer transitionTime) {
		hs.transitiontime = transitionTime;
		Intent i = new Intent();
		i.putExtra(InternalArguments.HUE_STATE, gson.toJson(hs));
		i.putExtra(InternalArguments.COLOR, 0xffffffff);
		return i;
	}

}
