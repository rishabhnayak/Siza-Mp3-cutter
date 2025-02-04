/*
 * Copyright (C) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mp3cutter.ringtonemaker.Activities;

/**
 * Main screen that shows up when you launch Ringdroid. Handles selecting
 * an audio file or using an intent to record a new one, and then
 * launches RingdroidEditActivity from here.
 */

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuItemCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;
import com.google.firebase.messaging.FirebaseMessaging;
import com.mp3cutter.ringtonemaker.Adapters.SongsAdapter;
import com.mp3cutter.ringtonemaker.Models.SongsModel;
import com.mp3cutter.ringtonemaker.R;
import com.mp3cutter.ringtonemaker.Ringdroid.Constants;
import com.mp3cutter.ringtonemaker.Ringdroid.Utils;
import com.mp3cutter.ringtonemaker.Views.FastScroller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static com.mp3cutter.ringtonemaker.Ringdroid.Constants.REQUEST_ID_MULTIPLE_PERMISSIONS;
import static com.mp3cutter.ringtonemaker.Ringdroid.Constants.REQUEST_ID_READ_CONTACTS_PERMISSION;
import static com.mp3cutter.ringtonemaker.Ringdroid.Constants.REQUEST_ID_RECORD_AUDIO_PERMISSION;

/**
 * Main screen that shows up when you launch Ringdroid. Handles selecting
 * an audio file or using an intent to record a new one, and then
 * launches RingdroidEditActivity from here.
 */
public class RingdroidSelectActivity extends AppCompatActivity implements SearchView.OnQueryTextListener {

    private SearchView mSearchView;


    // Result codes
    private static final int REQUEST_CODE_EDIT = 1;
    private static final int REQUEST_CODE_CHOOSE_CONTACT = 2;

    // Context menu
    private static final int CMD_EDIT = 4;
    private static final int CMD_DELETE = 5;
    private static final int CMD_SET_AS_DEFAULT = 6;
    private static final int CMD_SET_AS_CONTACT = 7;
    int mPos;

    /**
     * Called when the activity is first created.
     */
    private RecyclerView mRecyclerView;
    private SongsAdapter mSongsAdapter;
    private ArrayList<SongsModel> mData;
    private Context mContext;

    private Toolbar mToolbar;
    private FastScroller mFastScroller;
    private LinearLayout mPermissionLayout;
    private Button mAllowButton;
    private com.google.android.gms.ads.AdView mAdView;
    public static InterstitialAd mInterstitialAd;
    private long userLeaveTime;
    private boolean isNotPower;
    private long defStop;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mContext = getApplicationContext();

        String status = Environment.getExternalStorageState();
        if (status.equals(Environment.MEDIA_MOUNTED_READ_ONLY)) {
            showFinalAlert(getResources().getText(R.string.sdcard_readonly));
            return;
        }
        if (status.equals(Environment.MEDIA_SHARED)) {
            showFinalAlert(getResources().getText(R.string.sdcard_shared));
            return;
        }
        if (!status.equals(Environment.MEDIA_MOUNTED)) {
            showFinalAlert(getResources().getText(R.string.no_sdcard));
            return;
        }

        Intent intent = getIntent();

        // Inflate our UI from its XML layout description.
        setContentView(R.layout.media_select);

        MobileAds.initialize(this,
                "ca-app-pub-8290517984718171~5944634007");
        mAdView = (com.google.android.gms.ads.AdView)findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);
        mInterstitialAd = new InterstitialAd(this);
        mInterstitialAd.setAdUnitId("ca-app-pub-8290517984718171/8461412636");
        mInterstitialAd.loadAd(new AdRequest.Builder().addTestDevice("687733132860CB54EAEB4940DC16A439").build());

        if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){
            NotificationChannel notificationChannel=new NotificationChannel("timepass","timepass", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager manager =getSystemService(NotificationManager.class);
            manager.createNotificationChannel(notificationChannel);
            specificUser();
        }
        mPermissionLayout = (LinearLayout) findViewById(R.id.permission_message_layout);
        mAllowButton = (Button) findViewById(R.id.button_allow);

        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);


        mData = new ArrayList<>();
        mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);

        mFastScroller = (FastScroller) findViewById(R.id.fast_scroller);
        mFastScroller.setRecyclerView(mRecyclerView);


        mSongsAdapter = new SongsAdapter(this, mData);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getApplicationContext()));
        mRecyclerView.setAdapter(mSongsAdapter);

        Utils.initImageLoader(mContext);

        mAllowButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {


                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Toast.makeText(mContext, "Please allow modify system settings to continue", Toast.LENGTH_SHORT).show();
                        if (!Settings.System.canWrite(getApplicationContext())) {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName()));
                            startActivityForResult(intent, 200);

                        }
                    }

                Utils.checkAndRequestPermissions(RingdroidSelectActivity.this, true);
            }
        });

        if (Utils.checkAndRequestPermissions(this, false)) {
            loadData();
        } else {
            mFastScroller.setVisibility(View.GONE);
            mPermissionLayout.setVisibility(View.VISIBLE);
            mRecyclerView.setVisibility(View.GONE);
        }
    }

//    @Override
//    public void onBackPressed() {
//        if (mInterstitialAd.isLoaded()) {
//            mInterstitialAd.show();
//            finish();
//        } else {
//            finish();
//            Log.d("TAG", "The interstitial wasn't loaded yet.");
//        }
//    }
    @Override
    protected void onStop() {
        super.onStop();
        if (isNotPower) {
            defStop = System.currentTimeMillis() - userLeaveTime;
            if (defStop > 200 ) {
//                if (mInterstitialAd.isLoaded()) {
//                    mInterstitialAd.show();
//
//                } else {
//                    Log.d("TAG", "The interstitial wasn't loaded yet.");
//                }
            }
            if (defStop < 200) {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (mInterstitialAd.isLoaded()) {
                            mInterstitialAd.show();

                        } else {
                            Log.d("TAG", "The interstitial wasn't loaded yet.");
                        }
                    }
                },500);

            }
        }
        isNotPower = false;
    }
    @Override
    public void onUserLeaveHint() {
        // do stuff
        super.onUserLeaveHint();
        userLeaveTime = System.currentTimeMillis() ;
        isNotPower = true;
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_ID_MULTIPLE_PERMISSIONS: {
                Map<String, Integer> perms = new HashMap<>();
                perms.put(Manifest.permission.READ_EXTERNAL_STORAGE, PackageManager.PERMISSION_GRANTED);

                if (grantResults.length > 0) {
                    for (int i = 0; i < permissions.length; i++)
                        perms.put(permissions[i], grantResults[i]);
                    if (perms.get(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                        loadData();
                        mFastScroller.setVisibility(View.VISIBLE);
                        mPermissionLayout.setVisibility(View.GONE);
                        mRecyclerView.setVisibility(View.VISIBLE);
                        invalidateOptionsMenu();
                    }
                }
                break;
            }
            case REQUEST_ID_RECORD_AUDIO_PERMISSION:
                Map<String, Integer> perms = new HashMap<>();
                perms.put(Manifest.permission.RECORD_AUDIO, PackageManager.PERMISSION_GRANTED);
                if (grantResults.length > 0) {
                    for (int i = 0; i < permissions.length; i++)
                        perms.put(permissions[i], grantResults[i]);
                    if (perms.get(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        onRecord();
                    }
                }
                break;
            case REQUEST_ID_READ_CONTACTS_PERMISSION:
                Map<String, Integer> perm = new HashMap<>();
                perm.put(Manifest.permission.READ_CONTACTS, PackageManager.PERMISSION_GRANTED);
                if (grantResults.length > 0) {
                    for (int i = 0; i < permissions.length; i++)
                        perm.put(permissions[i], grantResults[i]);
                    if (perm.get(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                        chooseContactForRingtone(mPos);
                    }
                }
                break;
        }
    }

    private void loadData() {
        try {
            mData.addAll(Utils.getSongList(getApplicationContext(), true, null));
            mData.addAll(Utils.getSongList(getApplicationContext(), false, null));
            mSongsAdapter.updateData(mData);
        }catch (Exception E){
            System.out.println("yhi hai..."+E);
        }

    }

    /**
     * Called with an Activity we started with an Intent returns.
     */

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent dataIntent) {
        if (requestCode != REQUEST_CODE_EDIT) {
            return;
        }

        if (resultCode != RESULT_OK) {
            return;
        }

        setResult(RESULT_OK, dataIntent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.select_options, menu);
        mSearchView = (SearchView) MenuItemCompat.getActionView(menu.findItem(R.id.menu_search));

        if (Utils.checkAndRequestPermissions(this, false)) {
            menu.findItem(R.id.menu_search).setVisible(true);
        } else {
            menu.findItem(R.id.menu_search).setVisible(false);
        }

        mSearchView.setIconifiedByDefault(true);
        mSearchView.setIconified(false);
        mSearchView.clearFocus();
        mSearchView.setOnQueryTextListener(this);

        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_about:
                RingdroidEditActivity.onAbout(this);
                return true;
            case R.id.action_record:
                if (Utils.checkAndRequestAudioPermissions(RingdroidSelectActivity.this)) {
                    onRecord();
                }
                return true;
            default:
                return false;
        }
    }


    public void onPopUpMenuClickListener(View v, final int position) {
        mPos = position;
        final PopupMenu menu = new PopupMenu(this, v);
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.popup_song_edit:
                        startEditor(position);
                        break;
                    case R.id.popup_song_delete:
                        confirmDelete(position);
                        break;
                    case R.id.popup_song_assign_to_contact:
                        if (Utils.checkAndRequestContactsPermissions(RingdroidSelectActivity.this)) {
                            chooseContactForRingtone(position);
                        }
                        break;
                    case R.id.popup_song_set_default_notification:
                        setAsDefaultRingtoneOrNotification(position);
                        break;
                    case R.id.popup_song_set_default_ringtone:
                        setAsDefaultRingtoneOrNotification(position);
                        break;

                }
                return false;
            }
        });
        menu.inflate(R.menu.popup_song);

        if (mData.get(position).mFileType.equalsIgnoreCase(Constants.IS_RINGTONE)) {
            menu.getMenu().findItem(R.id.popup_song_set_default_notification).setVisible(false);
        } else if (mData.get(position).mFileType.equalsIgnoreCase(Constants.IS_NOTIFICATION)) {
            menu.getMenu().findItem(R.id.popup_song_set_default_ringtone).setVisible(false);
            menu.getMenu().findItem(R.id.popup_song_assign_to_contact).setVisible(false);
        } else if (mData.get(position).mFileType.equalsIgnoreCase(Constants.IS_MUSIC)) {
            menu.getMenu().findItem(R.id.popup_song_set_default_notification).setVisible(false);
        }
        menu.show();
    }


    private void setAsDefaultRingtoneOrNotification(int pos) {
        if (!Utils.checkSystemWritePermission(this)) return;
        if (mData.get(pos).mFileType.equalsIgnoreCase(Constants.IS_RINGTONE)) {

            RingtoneManager.setActualDefaultRingtoneUri(
                    RingdroidSelectActivity.this,
                    RingtoneManager.TYPE_RINGTONE,
                    getInternalUri(pos));

            Toast.makeText(
                    RingdroidSelectActivity.this,
                    R.string.default_ringtone_success_message,
                    Toast.LENGTH_SHORT)
                    .show();

        } else if (mData.get(pos).mFileType.equalsIgnoreCase(Constants.IS_MUSIC)) {
            RingtoneManager.setActualDefaultRingtoneUri(
                    RingdroidSelectActivity.this,
                    RingtoneManager.TYPE_RINGTONE,
                    getExtUri(pos));

            Toast.makeText(
                    RingdroidSelectActivity.this,
                    R.string.default_ringtone_success_message,
                    Toast.LENGTH_SHORT)
                    .show();

        } else {
            RingtoneManager.setActualDefaultRingtoneUri(
                    RingdroidSelectActivity.this,
                    RingtoneManager.TYPE_NOTIFICATION,
                    getInternalUri(pos));

            Toast.makeText(
                    RingdroidSelectActivity.this,
                    R.string.default_notification_success_message,
                    Toast.LENGTH_SHORT)
                    .show();
        }
    }

    private int getUriIndex(Cursor c) {
        int uriIndex;
        String[] columnNames = {
                MediaStore.Audio.Media.INTERNAL_CONTENT_URI.toString(),
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.toString()
        };

        for (String columnName : Arrays.asList(columnNames)) {
            uriIndex = c.getColumnIndex(columnName);
            if (uriIndex >= 0) {
                return uriIndex;
            }
            // On some phones and/or Android versions, the column name includes the double quotes.
            uriIndex = c.getColumnIndex("\"" + columnName + "\"");
            if (uriIndex >= 0) {
                return uriIndex;
            }
        }
        return -1;
    }

    private Uri getInternalUri(int pos) {
        return ContentUris.withAppendedId(MediaStore.Audio.Media.INTERNAL_CONTENT_URI, Long.parseLong(mData.get(pos)._ID));
    }

    private Uri getExtUri(int pos) {
        return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, Long.parseLong(mData.get(pos)._ID));
    }


    private boolean chooseContactForRingtone(int pos) {

        Intent intent = new Intent(RingdroidSelectActivity.this, ChooseContactActivity.class);
        if (mData.get(pos).mFileType.equalsIgnoreCase(Constants.IS_RINGTONE)) {
            intent.putExtra(Constants.FILE_NAME, String.valueOf(getInternalUri(pos)));
        } else if (mData.get(pos).mFileType.equalsIgnoreCase(Constants.IS_MUSIC)) {
            intent.putExtra(Constants.FILE_NAME, String.valueOf(getExtUri(pos)));
        } else {
            intent.putExtra(Constants.FILE_NAME, String.valueOf(getInternalUri(pos)));
        }
        startActivity(intent);

        return true;
    }

    private void confirmDelete(int pos) {
        // See if the selected list item was created by Ringdroid to
        // determine which alert message to show

        String artist = mData.get(pos).mArtistName;
        CharSequence ringdroidArtist = getResources().getText(R.string.artist_name);

        CharSequence message;
        if (artist.equals(ringdroidArtist)) {
            message = getResources().getText(
                    R.string.confirm_delete_ringdroid);
        } else {
            message = getResources().getText(
                    R.string.confirm_delete_non_ringdroid);
        }

        CharSequence title;
        if (mData.get(pos).mFileType.equalsIgnoreCase(Constants.IS_RINGTONE)) {
            title = getResources().getText(R.string.delete_ringtone);
        } else if (mData.get(pos).mFileType.equalsIgnoreCase(Constants.IS_ALARM)) {
            title = getResources().getText(R.string.delete_alarm);
        } else if (mData.get(pos).mFileType.equalsIgnoreCase(Constants.IS_NOTIFICATION)) {
            title = getResources().getText(R.string.delete_notification);
        } else if (mData.get(pos).mFileType.equalsIgnoreCase(Constants.IS_MUSIC)) {
            title = getResources().getText(R.string.delete_music);
        } else {
            title = getResources().getText(R.string.delete_audio);
        }

        new AlertDialog.Builder(RingdroidSelectActivity.this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(
                        R.string.delete_ok_button,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                                int whichButton) {
                                onDelete();
                            }
                        })
                .setNegativeButton(
                        R.string.delete_cancel_button,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                                int whichButton) {
                            }
                        })
                .setCancelable(true)
                .show();
    }

    private void onDelete() {
//        Cursor c = mAdapter.getCursor();
//        int dataIndex = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
//        String filename = c.getString(dataIndex);
//
//        int uriIndex = getUriIndex(c);
//        if (uriIndex == -1) {
//            showFinalAlert(getResources().getText(R.string.delete_failed));
//            return;
//        }
//
//        if (!new File(filename).delete()) {
//            showFinalAlert(getResources().getText(R.string.delete_failed));
//        }
//
//        String itemUri = c.getString(uriIndex) + "/" + c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));
//        getContentResolver().delete(Uri.parse(itemUri), null, null);
    }

    private void showFinalAlert(CharSequence message) {
        new AlertDialog.Builder(RingdroidSelectActivity.this)
                .setTitle(getResources().getText(R.string.alert_title_failure))
                .setMessage(message)
                .setPositiveButton(
                        R.string.alert_ok_button,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                                int whichButton) {
                                finish();
                            }
                        })
                .setCancelable(false)
                .show();
    }

    private void onRecord() {
        try {
            Intent intent = new Intent(RingdroidSelectActivity.this, RingdroidEditActivity.class);
            intent.putExtra("FILE_PATH", "record");
            startActivityForResult(intent, REQUEST_CODE_EDIT);
        } catch (Exception e) {
            Log.e("Ringdroid", "Couldn't start editor");
        }
    }


    public void onItemClicked(int adapterPosition) {
        startEditor(adapterPosition);
    }

    private void startEditor(int pos) {

        Intent intent = new Intent(mContext, RingdroidEditActivity.class);
        intent.putExtra("FILE_PATH", mData.get(pos).mPath);
        startActivity(intent);
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        mData.clear();
        mData.addAll(Utils.getSongList(getApplicationContext(), true, newText));
        mData.addAll(Utils.getSongList(getApplicationContext(), false, newText));
        mSongsAdapter.updateData(mData);
        return false;
    }

        public void specificUser(){
        FirebaseMessaging.getInstance().subscribeToTopic("timepass");
    }



}