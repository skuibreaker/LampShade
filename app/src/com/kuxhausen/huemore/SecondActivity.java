package com.kuxhausen.huemore;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Toast;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.kuxhausen.huemore.billing.IabHelper;
import com.kuxhausen.huemore.billing.IabResult;
import com.kuxhausen.huemore.billing.Inventory;
import com.kuxhausen.huemore.billing.Purchase;
import com.kuxhausen.huemore.network.GetBulbList;
import com.kuxhausen.huemore.network.GetBulbsAttributes;
import com.kuxhausen.huemore.nfc.NfcWriterActivity;
import com.kuxhausen.huemore.persistence.DatabaseDefinitions.InternalArguments;
import com.kuxhausen.huemore.persistence.DatabaseDefinitions.PlayItems;
import com.kuxhausen.huemore.persistence.DatabaseDefinitions.PreferencesKeys;
import com.kuxhausen.huemore.persistence.DatabaseHelper;
import com.kuxhausen.huemore.state.api.BulbAttributes;
import com.kuxhausen.huemore.state.api.BulbState;
import com.kuxhausen.huemore.timing.AlarmListActivity;
import com.kuxhausen.huemore.ui.registration.DiscoverHubDialogFragment;

/**
 * @author Eric Kuxhausen
 * 
 */
public class SecondActivity extends GodObject implements
		MoodsListFragment.OnMoodSelectedListener {

	DatabaseHelper databaseHelper = new DatabaseHelper(this);
	IabHelper mPlayHelper;
	private SecondActivity m;
	Inventory lastQuerriedInventory;
	public GetBulbList.OnBulbListReturnedListener bulbListenerFragment;
	
	public void setBulbListenerFragment(GetBulbList.OnBulbListReturnedListener frag){
		bulbListenerFragment = frag;
	}
	public GetBulbList.OnBulbListReturnedListener getBulbListenerFragment(){
		return bulbListenerFragment;
	}
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.second_activity);
		m = this;

		
		mMoodManualPagerAdapter = new MoodManualPagerAdapter(this);
		parrentActivity = this;
		// Set up the ViewPager, attaching the adapter.
		mViewPager = (ViewPager) this.findViewById(R.id.pager);
		mViewPager.setAdapter(mMoodManualPagerAdapter);
		
		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(parrentActivity);
		if (settings.getBoolean(PreferencesKeys.DEFAULT_TO_MOODS, true)) {
			mViewPager.setCurrentItem(MOOD_LOCATION);
		}
		brightnessBar = (SeekBar) this.findViewById(R.id.brightnessBar);
		brightnessBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				BulbState hs = new BulbState();
				hs.bri = brightness;
				hs.on = true;

				String[] brightnessState = { gson.toJson(hs) };
				// TODO deal with off?
				parrentActivity.onBrightnessChanged(brightnessState);
				isTrackingTouch = false;
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
				isTrackingTouch = true;
			}

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				brightness = progress;
			}
		});

		
	}

	@Override
	public void onGroupBulbSelected(Integer[] bulb, String name) {
		setGroupS(name);
		setBulbS(bulb);
		// Capture the article fragment from the activity layout
/*		MoodManualPagingFragment moodFrag = (MoodManualPagingFragment) getSupportFragmentManager()
				.findFragmentById(R.id.moods_fragment);

		if (moodFrag != null) {
			// If article frag is available, we're in two-pane layout...

			// Call a method in the ArticleFragment to update its content
			moodFrag.invalidateSelection();
			moodFrag.pollBrightness();

		} else {
			// If the frag is not available, we're in the one-pane layout and
			// must swap frags...

			// Create fragment and give it an argument for the selected article
			MoodManualPagingFragment newFragment = new MoodManualPagingFragment();
			FragmentTransaction transaction = getSupportFragmentManager()
					.beginTransaction();
			transaction
					.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
			// Replace whatever is in the fragment_container view with this
			// fragment,
			// and add the transaction to the back stack so the user can
			// navigate back
			transaction.replace(R.id.fragment_container, newFragment,
					MoodManualPagingFragment.class.getName());
			transaction.addToBackStack(null);

			// Commit the transaction
			transaction.commitAllowingStateLoss();// wtf, why can't I use
													// .commit() w/o error every
													// other launch?
			transaction
					.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);

			this.getSupportActionBar().setTitle(name);
			this.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		}
*/
	}

	@Override
	public void onPause() {
		super.onPause();
		// make sure moved back to group bulb when we come back to the app
		moveToGroupBulb();
	}

	@Override
	public void onBackPressed() {
		super.onBackPressed();
		this.getSupportActionBar().setDisplayHomeAsUpEnabled(false);
	}

	private void moveToGroupBulb() {
//		MoodManualPagingFragment moodFrag = (MoodManualPagingFragment) getSupportFragmentManager()
//				.findFragmentById(R.id.moods_fragment);
//
//		if (moodFrag == null || !moodFrag.isVisible()) {
//			this.onBackPressed();
//		}
	}

	
	


	SeekBar brightnessBar;
	int brightness;
	boolean isTrackingTouch = false;
	
	MoodManualPagerAdapter mMoodManualPagerAdapter;

	private static final int MOOD_LOCATION = 1;
	private static final int MANUAL_LOCATION = 0;

	private static MoodsListFragment moodsListFragment;
	private static ColorWheelFragment colorWheelFragment;

	ViewPager mViewPager;
	GodObject parrentActivity;
	
	@Override
	public void onSelected(Integer[] bulbNum, String name,
			GroupsListFragment groups, BulbsFragment bulbs) {
		// TODO Auto-generated method stub
		//throw not implimented exception
	}
	
	/**
	 * A {@link android.support.v4.app.FragmentStatePagerAdapter} that returns a
	 * fragment representing an object in the collection.
	 */
	public static class MoodManualPagerAdapter extends FragmentPagerAdapter {

		GodObject frag;
		
		public MoodManualPagerAdapter(GodObject godObject) {
			super(godObject.getSupportFragmentManager());
			frag = godObject;
		}

		@Override
		public Fragment getItem(int i) {
			switch (i) {
			case MOOD_LOCATION:
				if (moodsListFragment == null)
					moodsListFragment = new MoodsListFragment();
				return moodsListFragment;
			case MANUAL_LOCATION:
				if (colorWheelFragment == null) {
					colorWheelFragment = new ColorWheelFragment();
					colorWheelFragment.hideTransitionTime();
				}
				return colorWheelFragment;
			default:
				return null;
			}
		}

		@Override
		public int getCount() {
			return 2;
		}

		@Override
		public CharSequence getPageTitle(int position) {
			switch (position) {
			case MOOD_LOCATION:
				return frag.getString(R.string.cap_moods);
			case MANUAL_LOCATION:
				return frag.getString(R.string.cap_manual);
			}
			return "";
		}
	}
	public void pollBrightness() {
		GetBulbsAttributes getBulbsAttributes = new GetBulbsAttributes(
				parrentActivity, parrentActivity.getBulbs(), this,
				this.parrentActivity);
		getBulbsAttributes.execute();
	}
	public void invalidateSelection() {
		((MoodsListFragment) (mMoodManualPagerAdapter.getItem(MOOD_LOCATION)))
				.invalidateSelection();
	}
	@Override
	public void onResume() {
		super.onResume();
		pollBrightness();
	}
	@Override
	public void onListReturned(BulbAttributes[] bulbsAttributes) {
		if (!isTrackingTouch && bulbsAttributes != null
				&& bulbsAttributes.length > 0) {
			int brightnessSum = 0;
			int brightnessPool = 0;
			for (BulbAttributes ba : bulbsAttributes) {
				if (ba != null) {
					if (ba.state.on == false)
						brightnessPool++;
					else {
						brightnessSum += ba.state.bri;
						brightnessPool++;
					}
				}
			}
			if (brightnessPool == 0)
				return;
			int brightnessAverage = brightnessSum / brightnessPool;

			brightness = brightnessAverage;
			brightnessBar.setProgress(brightnessAverage);
		}
	}



	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.second, menu);
		if (PreferenceManager.getDefaultSharedPreferences(this).getInt(
				PreferencesKeys.BULBS_UNLOCKED,
				PreferencesKeys.ALWAYS_FREE_BULBS) > PreferencesKeys.ALWAYS_FREE_BULBS) {
			// has pro version

			if (NfcAdapter.getDefaultAdapter(this) == null) {
				// hide nfc link if nfc not supported
				MenuItem nfcItem = menu.findItem(R.id.action_nfc);
				if (nfcItem != null) {
					nfcItem.setEnabled(false);
					nfcItem.setVisible(false);
				}
			}
		} else {
			MenuItem nfcItem = menu.findItem(R.id.action_nfc);
			if (nfcItem != null) {
				nfcItem.setEnabled(false);
				nfcItem.setVisible(false);
			}
			MenuItem alarmItem = menu.findItem(R.id.action_alarms);
			if (alarmItem != null) {
				alarmItem.setEnabled(false);
				alarmItem.setVisible(false);
			}
		}

		if ((getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) < Configuration.SCREENLAYOUT_SIZE_LARGE) {
			MenuItem bothItem = menu.findItem(R.id.action_add_both);
			if (bothItem != null) {
				bothItem.setEnabled(false);
				bothItem.setVisible(false);
			}
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case android.R.id.home:
			moveToGroupBulb();
			return true;
		case R.id.action_register_with_hub:
			// RegisterWithHubDialogFragment rwhdf = new
			// RegisterWithHubDialogFragment();
			// rwhdf.show(getSupportFragmentManager(),
			// InternalArguments.FRAG_MANAGER_DIALOG_TAG);
			DiscoverHubDialogFragment dhdf = new DiscoverHubDialogFragment();
			dhdf.show(getSupportFragmentManager(),
					InternalArguments.FRAG_MANAGER_DIALOG_TAG);
			return true;
		case R.id.action_settings:
			SettingsDialogFragment settings = new SettingsDialogFragment();
			settings.show(getSupportFragmentManager(),
					InternalArguments.FRAG_MANAGER_DIALOG_TAG);
			return true;
		case R.id.action_add_both:
			AddMoodGroupSelectorDialogFragment addBoth = new AddMoodGroupSelectorDialogFragment();
			addBoth.show(getSupportFragmentManager(),
					InternalArguments.FRAG_MANAGER_DIALOG_TAG);
			return true;
		case R.id.action_nfc:
			if (!NfcAdapter.getDefaultAdapter(this).isEnabled()) {
				// startActivity(new
				// Intent(SettingsDialogFragment.ACTION_NFC_SETTINGS));
				Toast.makeText(this, this.getString(R.string.nfc_disabled),
						Toast.LENGTH_SHORT).show();

			} else {
				Intent i = new Intent(this, NfcWriterActivity.class);
				this.startActivity(i);
			}
			return true;
		case R.id.action_alarms:
			Intent i = new Intent(this, AlarmListActivity.class);
			this.startActivity(i);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
	
}