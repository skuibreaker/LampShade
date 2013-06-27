package com.kuxhausen.huemore;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.nfc.NfcAdapter;
import android.os.AsyncTask;
import android.os.Build;
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
import com.kuxhausen.huemore.SecondActivity.MoodManualPagerAdapter;
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
public class MainActivity extends GodObject implements
		MoodsListFragment.OnMoodSelectedListener {

	DatabaseHelper databaseHelper = new DatabaseHelper(this);
	IabHelper mPlayHelper;
	private MainActivity m;
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

		setContentView(R.layout.hue_more);
		m = this;
		
		mGroupBulbPagerAdapter = new GroupBulbPagerAdapter(this);
		parrentActivity = this;
		// Set up the ViewPager, attaching the adapter.
		mViewPager1 = (ViewPager) this.findViewById(R.id.group_pager);
		mViewPager1.setAdapter(mGroupBulbPagerAdapter);
		
		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(parrentActivity);
		if (settings.getBoolean(PreferencesKeys.DEFAULT_TO_GROUPS, false)) {
			if (mViewPager1.getCurrentItem() != GROUP_LOCATION)
				mViewPager1.setCurrentItem(GROUP_LOCATION);
		} else {
			if (mViewPager1.getCurrentItem() != BULB_LOCATION)
				mViewPager1.setCurrentItem(BULB_LOCATION);
		}
		
		if ((getResources().getConfiguration().screenLayout &
				 Configuration.SCREENLAYOUT_SIZE_MASK) >=
				 Configuration.SCREENLAYOUT_SIZE_LARGE){
					
			mMoodManualPagerAdapter = new MoodManualPagerAdapter(this);
			parrentActivity = this;
			// Set up the ViewPager, attaching the adapter.
			mViewPager2 = (ViewPager) this.findViewById(R.id.mood_pager);
			mViewPager2.setAdapter(mMoodManualPagerAdapter);
			
			if (settings.getBoolean(PreferencesKeys.DEFAULT_TO_MOODS, true)) {
				mViewPager2.setCurrentItem(MOOD_LOCATION);
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
		
		
		initializationDatabaseChecks();
		initializeBillingCode();
		

		Bundle b = this.getIntent().getExtras();
		if (b != null && b.containsKey(InternalArguments.PROMPT_UPGRADE)
				&& b.getBoolean(InternalArguments.PROMPT_UPGRADE)) {
			UnlocksDialogFragment unlocks = new UnlocksDialogFragment();
			unlocks.show(getSupportFragmentManager(),
					InternalArguments.FRAG_MANAGER_DIALOG_TAG);
		}
	}

	@Override
	public void onGroupBulbSelected(Integer[] bulb, String name) {
		setGroupS(name);
		setBulbS(bulb);
		if ((getResources().getConfiguration().screenLayout &
				 Configuration.SCREENLAYOUT_SIZE_MASK) >=
				 Configuration.SCREENLAYOUT_SIZE_LARGE){
			invalidateSelection();
			pollBrightness();
		 }else{
			Intent i = new Intent(this, SecondActivity.class);
			i.putExtra(InternalArguments.SERIALIZED_GOD_OBJECT, this.getSerialized());
			this.startActivity(i);
		 }
	}
	
	GroupBulbPagerAdapter mGroupBulbPagerAdapter;

	private static final int GROUP_LOCATION = 1;
	private static final int BULB_LOCATION = 0;

	private static GroupsListFragment groupsListFragment;
	private static BulbsFragment bulbsFragment;

	ViewPager mViewPager1;
	GodObject parrentActivity;
	
	public void onSelected(Integer[] bulbNum, String name,
			GroupsListFragment groups, BulbsFragment bulbs) {
		if (groups == groupsListFragment && groups != null
				&& bulbsFragment != null)
			bulbsFragment.invalidateSelection();
		if (bulbs == bulbsFragment && bulbs != null
				&& groupsListFragment != null)
			groupsListFragment.invalidateSelection();

		if (parrentActivity != null || bulbNum == null || name == null)
			parrentActivity.onGroupBulbSelected(bulbNum, name);
	}
		
	public static class GroupBulbPagerAdapter extends FragmentPagerAdapter {

		GodObject frag;

		public GroupBulbPagerAdapter(GodObject godObject) {
			super(godObject.getSupportFragmentManager());
			frag = godObject;
		}

		@Override
		public Fragment getItem(int i) {
			switch (i) {
			case GROUP_LOCATION:
				if (groupsListFragment == null) {
					groupsListFragment = new GroupsListFragment();
					groupsListFragment.setSelectionListener(frag);
				}
				return groupsListFragment;
			case BULB_LOCATION:
				if (bulbsFragment == null) {
					bulbsFragment = new BulbsFragment();
					bulbsFragment.setSelectionListener(frag);
				}
				return bulbsFragment;
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
			case GROUP_LOCATION:
				return frag.getString(R.string.cap_groups);
			case BULB_LOCATION:
				return frag.getString(R.string.cap_bulbs);

			}
			return "";
		}
	}
	
	SeekBar brightnessBar;
	int brightness;
	boolean isTrackingTouch = false;
	
	MoodManualPagerAdapter mMoodManualPagerAdapter;

	private static final int MOOD_LOCATION = 1;
	private static final int MANUAL_LOCATION = 0;

	private static MoodsListFragment moodsListFragment;
	private static ColorWheelFragment colorWheelFragment;

	ViewPager mViewPager2;
	
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
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public void pollBrightness() {
		if ((getResources().getConfiguration().screenLayout &
				 Configuration.SCREENLAYOUT_SIZE_MASK) >=
				 Configuration.SCREENLAYOUT_SIZE_LARGE){
			GetBulbsAttributes getBulbsAttributes = new GetBulbsAttributes(
					parrentActivity, parrentActivity.getBulbs(), this,
					this.parrentActivity);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
				getBulbsAttributes.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			} else {
				getBulbsAttributes.execute();
			}
		}
	}
	public void invalidateSelection() {
		if ((getResources().getConfiguration().screenLayout &
				 Configuration.SCREENLAYOUT_SIZE_MASK) >=
				 Configuration.SCREENLAYOUT_SIZE_LARGE){
			((MoodsListFragment) (mMoodManualPagerAdapter.getItem(MOOD_LOCATION)))
					.invalidateSelection();
		}
	}
	@Override
	public void onResume() {
		super.onResume();
		if ((getResources().getConfiguration().screenLayout &
				 Configuration.SCREENLAYOUT_SIZE_MASK) >=
				 Configuration.SCREENLAYOUT_SIZE_LARGE){
			pollBrightness();
		}
	}
	@Override
	public void onListReturned(BulbAttributes[] bulbsAttributes) {
		if ((getResources().getConfiguration().screenLayout &
				 Configuration.SCREENLAYOUT_SIZE_MASK) >=
				 Configuration.SCREENLAYOUT_SIZE_LARGE){
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
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.main, menu);
		if (PreferenceManager.getDefaultSharedPreferences(this).getInt(
				PreferencesKeys.BULBS_UNLOCKED,
				PreferencesKeys.ALWAYS_FREE_BULBS) > PreferencesKeys.ALWAYS_FREE_BULBS) {
			// has pro version

			// hide unlocks button
			MenuItem unlocksItem = menu.findItem(R.id.action_unlocks);
			if (unlocksItem != null) {
				unlocksItem.setEnabled(false);
				unlocksItem.setVisible(false);
			}
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
		case R.id.action_unlocks:
			UnlocksDialogFragment unlocks = new UnlocksDialogFragment();
			unlocks.show(getSupportFragmentManager(),
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
	
	
	@Override
	public void onDestroy() {
		if (mPlayHelper != null) {
			try {
				mPlayHelper.dispose();
			} catch (IllegalArgumentException e) {
			}
		}
		mPlayHelper = null;
		Log.d("asdf", "mPlayHelperDestroyed" + (mPlayHelper == null));
		super.onDestroy();
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		// Log.d(TAG, "onActivityResult(" + requestCode + "," + resultCode + ","
		// + data);

		// Pass on the activity result to the helper for handling
		if (!mPlayHelper.handleActivityResult(requestCode, resultCode, data)) {
			// not handled, so handle it ourselves (here's where you'd
			// perform any handling of activity results not related to in-app
			// billing...
			super.onActivityResult(requestCode, resultCode, data);
		} else {
			// Log.d(TAG, "onActivityResult handled by IABUtil.");
		}
	}
	
	private void initializationDatabaseChecks(){
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		if (!settings.contains(PreferencesKeys.TWO_POINT_TWO_UPDATE)) {
			databaseHelper.updatedTwoPointOnePointOne();
			// Run this before all the other updates, as it launches the update
			// dialog, which needs to know update history
			if (settings.contains(PreferencesKeys.TWO_POINT_OH_UPDATE)
					&& (settings.getInt(PreferencesKeys.BULBS_UNLOCKED,
							PreferencesKeys.ALWAYS_FREE_BULBS) > PreferencesKeys.ALWAYS_FREE_BULBS)) {
				VersionHistoryDialogFragment vhdf = new VersionHistoryDialogFragment();
				vhdf.show(getSupportFragmentManager(),
						InternalArguments.FRAG_MANAGER_DIALOG_TAG);
			}
			// Mark no longer update two point two in preferences cache
			Editor edit = settings.edit();
			edit.putBoolean(PreferencesKeys.TWO_POINT_TWO_UPDATE, false);
			edit.commit();
		}

		if (!settings.contains(PreferencesKeys.FIRST_RUN)) {
			databaseHelper.initialPopulate();// initialize database

			// Mark no longer first run in preferences cache
			Editor edit = settings.edit();
			edit.putBoolean(PreferencesKeys.FIRST_RUN, false);
			edit.putInt(PreferencesKeys.BULBS_UNLOCKED,
					PreferencesKeys.ALWAYS_FREE_BULBS);// TODO load from
			// google store
			edit.commit();
		}
		if (!settings.contains(PreferencesKeys.THIRD_UPDATE)) {
			databaseHelper.updatedPopulate();
			// Mark no longer first update in preferences cache
			Editor edit = settings.edit();
			edit.putBoolean(PreferencesKeys.THIRD_UPDATE, false);
			edit.commit();
		}
		if (!settings.contains(PreferencesKeys.TWO_POINT_OH_UPDATE)) {
			databaseHelper.updatedTwoPointOh();
			// Mark no longer first update in preferences cache
			Editor edit = settings.edit();
			edit.putBoolean(PreferencesKeys.TWO_POINT_OH_UPDATE, false);
			edit.commit();
		}
		if (!settings.contains(PreferencesKeys.TWO_POINT_ONE_UPDATE)) {
			databaseHelper.updatedTwoPointOne();
			// Mark no longer first update in preferences cache
			Editor edit = settings.edit();
			edit.putBoolean(PreferencesKeys.TWO_POINT_ONE_UPDATE, false);
			edit.commit();
		}
		if (!settings.contains(PreferencesKeys.TWO_POINT_ONE_POINT_ONE_UPDATE)) {
			databaseHelper.updatedTwoPointOnePointOne();
			// Mark no longer first update in preferences cache
			Editor edit = settings.edit();
			edit.putBoolean(PreferencesKeys.TWO_POINT_ONE_POINT_ONE_UPDATE,
					false);
			edit.commit();
		}

		if (!settings.contains(PreferencesKeys.DEFAULT_TO_GROUPS)) {
			Editor edit = settings.edit();
			edit.putBoolean(PreferencesKeys.DEFAULT_TO_GROUPS, false);
			edit.commit();
		}
		if (!settings.contains(PreferencesKeys.DEFAULT_TO_MOODS)) {
			Editor edit = settings.edit();
			edit.putBoolean(PreferencesKeys.DEFAULT_TO_MOODS, true);
			edit.commit();
		}

		// check to see if the bridge IP address is setup yet
		if (!settings.contains(PreferencesKeys.BRIDGE_IP_ADDRESS)) {
			DiscoverHubDialogFragment dhdf = new DiscoverHubDialogFragment();
			dhdf.show(this.getSupportFragmentManager(),
					InternalArguments.FRAG_MANAGER_DIALOG_TAG);
		}
	}

	private void initializeBillingCode(){
		String firstChunk = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAgPUhHgGEdnpyPMAWgP3Xw/jHkReU1O0n6d4rtcULxOrVl/hcZlOsVyByMIZY5wMD84gmMXjbz8pFb4RymFTP7Yp8LSEGiw6DOXc7ydNd0lbZ4WtKyDEwwaio1wRbRPxdU7/4JBpMCh9L6geYx6nYLt0ExZEFxULV3dZJpIlEkEYaNGk/64gc0l34yybccYfORrWzu8u+";
		String secondChunk = "5YxJ5k1ikIJJ2I7/2Rp5AXkj2dWybmT+AGx83zh8+iMGGawEQerGtso9NUqpyZWU08EO9DcF8r2KnFwjmyWvqJ2JzbqCMNt0A08IGQNOrd16/C/65GE6J/EtsggkNIgQti6jD7zd3b2NAQIDAQAB";
		String base64EncodedPublicKey = firstChunk + secondChunk;
		// compute your public key and store it in base64EncodedPublicKey
		mPlayHelper = new IabHelper(this, base64EncodedPublicKey);
		Log.d("asdf", "mPlayHelperCreated" + (mPlayHelper != null));
		mPlayHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
			@TargetApi(Build.VERSION_CODES.HONEYCOMB)
			@Override
			public void onIabSetupFinished(IabResult result) {
				if (!result.isSuccess()) {
					// Oh noes, there was a problem.
					// Log.d("asdf", "Problem setting up In-app Billing: "+
					// result);
				} else {
					// Hooray, IAB is fully set up!
					mPlayHelper.queryInventoryAsync(mGotInventoryListener);
					if (m.bulbListenerFragment != null) {
						GetBulbList pushGroupMood = new GetBulbList(m,
								m.bulbListenerFragment, m);
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
							pushGroupMood.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
						} else {
							pushGroupMood.execute();
						}
					}
				}
			}
		});
	}
	
	// Listener that's called when we finish querying the items and subscriptions we own
	IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
		@Override
		public void onQueryInventoryFinished(IabResult result,
				Inventory inventory) {

			// Log.d("asdf", "Query inventory finished.");
			if (result.isFailure()) {
				// handle error
				return;
			} else {
				// Log.d("asdf", "Query inventory was successful.");
				lastQuerriedInventory = inventory;
				int numUnlocked = PreferencesKeys.ALWAYS_FREE_BULBS;
				if (inventory.hasPurchase(PlayItems.FIVE_BULB_UNLOCK_1))
					numUnlocked = Math.max(50, numUnlocked);
				if (inventory.hasPurchase(PlayItems.BUY_ME_A_BULB_DONATION_1))
					numUnlocked = Math.max(50, numUnlocked);
				// update UI accordingly

				// Get preferences cache
				SharedPreferences settings = PreferenceManager
						.getDefaultSharedPreferences(m);
				int previousMax = settings.getInt(
						PreferencesKeys.BULBS_UNLOCKED,
						PreferencesKeys.ALWAYS_FREE_BULBS);
				if (numUnlocked > previousMax) {
					// Update the number held in settings
					Editor edit = settings.edit();
					edit.putInt(PreferencesKeys.BULBS_UNLOCKED, numUnlocked);
					edit.commit();

					databaseHelper.addBulbs(previousMax, numUnlocked);// initialize
																		// database
				}
			}
			/*
			 * Check for items we own. Notice that for each purchase, we check
			 * the developer payload to see if it's correct! See
			 * verifyDeveloperPayload().
			 */
			/*
			 * // Do we have the premium upgrade? Purchase premiumPurchase =
			 * inventory.getPurchase(SKU_PREMIUM); mIsPremium = (premiumPurchase
			 * != null && verifyDeveloperPayload(premiumPurchase)); Log.d(TAG,
			 * "User is " + (mIsPremium ? "PREMIUM" : "NOT PREMIUM"));
			 * 
			 * 
			 * updateUi(); setWaitScreen(false); Log.d(TAG,
			 * "Initial inventory query finished; enabling main UI.");
			 */
		}
	};

	/** Verifies the developer payload of a purchase. */
	boolean verifyDeveloperPayload(Purchase p) {
		String payload = p.getDeveloperPayload();
		/*
		 * TODO: verify that the developer payload of the purchase is correct.
		 * It will be the same one that you sent when initiating the purchase.
		 * 
		 * WARNING: Locally generating a random string when starting a purchase
		 * and verifying it here might seem like a good approach, but this will
		 * fail in the case where the user purchases an item on one device and
		 * then uses your app on a different device, because on the other device
		 * you will not have access to the random string you originally
		 * generated.
		 * 
		 * So a good developer payload has these characteristics:
		 * 
		 * 1. If two different users purchase an item, the payload is different
		 * between them, so that one user's purchase can't be replayed to
		 * another user.
		 * 
		 * 2. The payload must be such that you can verify it even when the app
		 * wasn't the one who initiated the purchase flow (so that items
		 * purchased by the user on one device work on other devices owned by
		 * the user).
		 * 
		 * Using your own server to store and verify developer payloads across
		 * app installations is recommended.
		 */
		return true;
	}
}
