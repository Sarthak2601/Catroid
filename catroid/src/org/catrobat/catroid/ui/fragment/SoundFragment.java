/**
 *  Catroid: An on-device visual programming system for Android devices
 *  Copyright (C) 2010-2013 The Catrobat Team
 *  (<http://developer.catrobat.org/credits>)
 *  
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *  
 *  An additional term exception under section 7 of the GNU Affero
 *  General Public License, version 3, is available at
 *  http://developer.catrobat.org/license_additional_term
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU Affero General Public License for more details.
 *  
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.catrobat.catroid.ui.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;

import org.catrobat.catroid.ProjectManager;
import org.catrobat.catroid.R;
import org.catrobat.catroid.common.Constants;
import org.catrobat.catroid.common.SoundInfo;
import org.catrobat.catroid.io.StorageHandler;
import org.catrobat.catroid.ui.BottomBar;
import org.catrobat.catroid.ui.ScriptActivity;
import org.catrobat.catroid.ui.SoundViewHolder;
import org.catrobat.catroid.ui.adapter.SoundAdapter;
import org.catrobat.catroid.ui.adapter.SoundBaseAdapter;
import org.catrobat.catroid.ui.controller.BackPackListManager;
import org.catrobat.catroid.ui.controller.SoundController;
import org.catrobat.catroid.ui.dialogs.DeleteSoundDialog;
import org.catrobat.catroid.ui.dialogs.RenameSoundDialog;
import org.catrobat.catroid.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class SoundFragment extends ScriptActivityFragment implements SoundBaseAdapter.OnSoundEditListener,
		LoaderManager.LoaderCallbacks<Cursor>, Dialog.OnKeyListener {

	public static final String TAG = SoundFragment.class.getSimpleName();

	private static int selectedSoundPosition = Constants.NO_POSITION;

	private static String actionModeTitle;

	private static String singleItemAppendixDeleteActionMode;
	private static String multipleItemAppendixDeleteActionMode;

	private MediaPlayer mediaPlayer;
	private SoundBaseAdapter adapter;
	private ArrayList<SoundInfo> soundInfoList;
	private SoundInfo selectedSoundInfo;

	private ListView listView;

	private SoundDeletedReceiver soundDeletedReceiver;
	private SoundRenamedReceiver soundRenamedReceiver;
	private SoundCopiedReceiver soundCopiedReceiver;

	private SoundsListInitReceiver soundsListInitReceiver;

	private ActionMode actionMode;

	private boolean isRenameActionMode;
	private boolean isResultHandled = false;

	private OnSoundInfoListChangedAfterNewListener soundInfoListChangedAfterNewListener;

	private LinearLayout addButton;

	private static final int RESULT_OK = -1;

	public void setOnSoundInfoListChangedAfterNewListener(OnSoundInfoListChangedAfterNewListener listener) {
		soundInfoListChangedAfterNewListener = listener;
	}

	private void setHandleAddbutton() {
		addButton = (LinearLayout) getSherlockActivity().findViewById(R.id.button_add);
		addButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				handleAddButton();
			}
		});

	}

	@Override
	public void onCreate(Bundle savedInstanceState) {

		Log.d("TAG", "SoundFragment called!");

		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);

	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View rootView = inflater.inflate(R.layout.sound_list, null);
		return rootView;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		Log.d("TAG", "SoundFragment-->onActivityCreated()");

		listView = getListView();
		registerForContextMenu(listView);

		if (savedInstanceState != null) {
			selectedSoundInfo = (SoundInfo) savedInstanceState
					.getSerializable(SoundController.BUNDLE_ARGUMENTS_SELECTED_SOUND);
		}
		soundInfoList = ProjectManager.getInstance().getCurrentSprite().getSoundList();

		adapter = new SoundAdapter(getActivity(), R.layout.fragment_sound_soundlist_item, soundInfoList, false);

		adapter.setOnSoundEditListener(this);
		setListAdapter(adapter);
		((SoundAdapter) adapter).setSoundFragment(this);

		Utils.loadProjectIfNeeded(getActivity());
		setHandleAddbutton();

		Log.d("TAG", "SoundFragment-->onActivityCreated() ends");

	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		menu.findItem(R.id.copy).setVisible(true);
		menu.findItem(R.id.edit_in_pocket_paint).setVisible(false);
		super.onPrepareOptionsMenu(menu);
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putSerializable(SoundController.BUNDLE_ARGUMENTS_SELECTED_SOUND, selectedSoundInfo);
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onStart() {
		super.onStart();
		mediaPlayer = new MediaPlayer();
		initClickListener();
	}

	@Override
	public void onResume() {
		super.onResume();

		setHandleAddbutton();

		if (!Utils.checkForExternalStorageAvailableAndDisplayErrorIfNot(getActivity())) {
			return;
		}

		if (soundRenamedReceiver == null) {
			soundRenamedReceiver = new SoundRenamedReceiver();
		}

		if (soundDeletedReceiver == null) {
			soundDeletedReceiver = new SoundDeletedReceiver();
		}

		if (soundCopiedReceiver == null) {
			soundCopiedReceiver = new SoundCopiedReceiver();
		}

		if (soundsListInitReceiver == null) {
			soundsListInitReceiver = new SoundsListInitReceiver();
		}

		IntentFilter intentFilterRenameSound = new IntentFilter(ScriptActivity.ACTION_SOUND_RENAMED);
		getActivity().registerReceiver(soundRenamedReceiver, intentFilterRenameSound);

		IntentFilter intentFilterDeleteSound = new IntentFilter(ScriptActivity.ACTION_SOUND_DELETED);
		getActivity().registerReceiver(soundDeletedReceiver, intentFilterDeleteSound);

		IntentFilter intentFilterCopySound = new IntentFilter(ScriptActivity.ACTION_SOUND_COPIED);
		getActivity().registerReceiver(soundCopiedReceiver, intentFilterCopySound);

		IntentFilter intentFilterSoundsListInit = new IntentFilter(ScriptActivity.ACTION_SOUNDS_LIST_INIT);
		getActivity().registerReceiver(soundsListInitReceiver, intentFilterSoundsListInit);

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getActivity()
				.getApplicationContext());

		setShowDetails(settings.getBoolean(SoundController.SHARED_PREFERENCE_NAME, false));

		SoundController.getInstance().handleAddButtonFromNew(this);
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (!hidden) {
			SoundController.getInstance().handleAddButtonFromNew(this);
		}
	}

	@Override
	public void onPause() {
		super.onPause();

		ProjectManager projectManager = ProjectManager.getInstance();
		if (projectManager.getCurrentProject() != null) {
			projectManager.saveProject();
		}
		SoundController.getInstance().stopSound(mediaPlayer, soundInfoList);
		adapter.notifyDataSetChanged();

		if (soundRenamedReceiver != null) {
			getActivity().unregisterReceiver(soundRenamedReceiver);
		}

		if (soundDeletedReceiver != null) {
			getActivity().unregisterReceiver(soundDeletedReceiver);
		}

		if (soundsListInitReceiver != null) {
			getActivity().unregisterReceiver(soundsListInitReceiver);
		}

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getActivity()
				.getApplicationContext());
		SharedPreferences.Editor editor = settings.edit();

		editor.putBoolean(SoundController.SHARED_PREFERENCE_NAME, getShowDetails());
		editor.commit();

	}

	@Override
	public void onStop() {
		super.onStop();
		mediaPlayer.reset();
		mediaPlayer.release();
		mediaPlayer = null;
	}

	@Override
	public void startCopyActionMode() {

		if (actionMode == null) {
			SoundController.getInstance().stopSoundAndUpdateList(mediaPlayer, soundInfoList, adapter);
			actionMode = getSherlockActivity().startActionMode(copyModeCallBack);
			unregisterForContextMenu(listView);
			BottomBar.disableButtons(getActivity());
			isRenameActionMode = false;
		}

	}

	@Override
	public void startRenameActionMode() {
		if (actionMode == null) {
			SoundController.getInstance().stopSoundAndUpdateList(mediaPlayer, soundInfoList, adapter);
			actionMode = getSherlockActivity().startActionMode(renameModeCallBack);
			unregisterForContextMenu(listView);
			BottomBar.disableButtons(getActivity());
			isRenameActionMode = true;
		}
	}

	@Override
	public void startDeleteActionMode() {

		if (actionMode == null) {
			SoundController.getInstance().stopSoundAndUpdateList(mediaPlayer, soundInfoList, adapter);
			actionMode = getSherlockActivity().startActionMode(deleteModeCallBack);
			unregisterForContextMenu(listView);
			BottomBar.disableButtons(getActivity());
			isRenameActionMode = false;
		}
	}

	@Override
	public void startEditInPocketPaintActionMode() {

	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		Log.d("TAG", "SoundFragment --> onActivityResult()");

		//when new sound title is selected and ready to be added to the catroid project
		if (resultCode == Activity.RESULT_OK && requestCode == SoundController.REQUEST_SELECT_MUSIC && data != null) {
			Bundle arguments = new Bundle();
			arguments.putParcelable(SoundController.BUNDLE_ARGUMENTS_SELECTED_SOUND, data.getData());

			if (getLoaderManager().getLoader(SoundController.ID_LOADER_MEDIA_IMAGE) == null) {
				getLoaderManager().initLoader(SoundController.ID_LOADER_MEDIA_IMAGE, arguments, this);
			} else {
				getLoaderManager().restartLoader(SoundController.ID_LOADER_MEDIA_IMAGE, arguments, this);
			}
		}
		if (requestCode == SoundController.REQUEST_SELECT_MUSIC) {
			Log.d("SoundFragment", "onActivityResult RequestMusic");
			setHandleAddbutton();

		}

		if (requestCode == 1) {

			if (resultCode == RESULT_OK) {
				String result = data.getStringExtra("result");
				Log.d("TAG", "Result code was RESULT_OK!");

				SoundController.getInstance().backPackSound(selectedSoundInfo,
						BackPackListManager.getInstance().getBackPackSoundFragment(),
						BackPackListManager.getInstance().getSoundInfoArrayList(),
						BackPackListManager.getInstance().getBackPackSoundFragment().getBackPackSoundAdapter());

			} else {
				Log.d("TAG", "Result Code doesn't match!");
			}
		}

	}

	@Override
	public void onSoundPlay(View view) {
		SoundController.getInstance().handlePlaySoundButton(view, soundInfoList, mediaPlayer, adapter);
	}

	@Override
	public void onSoundPause(View view) {
		handlePauseSoundButton(view);
	}

	@Override
	public void onSoundChecked() {

		if (isRenameActionMode || actionMode == null) {
			return;
		}

		int numberOfSelectedItems = adapter.getAmountOfCheckedItems();

		if (numberOfSelectedItems == 0) {
			actionMode.setTitle(actionModeTitle);
		} else {
			String appendix = multipleItemAppendixDeleteActionMode;

			if (numberOfSelectedItems == 1) {
				appendix = singleItemAppendixDeleteActionMode;
			}

			String numberOfItems = Integer.toString(numberOfSelectedItems);
			String completeTitle = actionModeTitle + " " + numberOfItems + " " + appendix;

			int titleLength = actionModeTitle.length();

			Spannable completeSpannedTitle = new SpannableString(completeTitle);
			completeSpannedTitle.setSpan(
					new ForegroundColorSpan(getResources().getColor(R.color.actionbar_title_color)), titleLength + 1,
					titleLength + (1 + numberOfItems.length()), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

			actionMode.setTitle(completeSpannedTitle);
		}
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle arguments) {
		return SoundController.getInstance().onCreateLoader(id, arguments, getActivity());
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		CopyAudioFilesTask task = new CopyAudioFilesTask();
		String audioPath = SoundController.getInstance().onLoadFinished(loader, data, getActivity());
		if (!audioPath.isEmpty()) {
			task.execute(audioPath);
			getLoaderManager().destroyLoader(SoundController.ID_LOADER_MEDIA_IMAGE);
		}

		getActivity().sendBroadcast(new Intent(ScriptActivity.ACTION_BRICK_LIST_CHANGED));

		isResultHandled = true;
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
	}

	public void handlePauseSoundButton(View view) {
		final int position = (Integer) view.getTag();
		pauseSound(soundInfoList.get(position));
		adapter.notifyDataSetChanged();
	}

	public void pauseSound(SoundInfo soundInfo) {
		mediaPlayer.pause();
		soundInfo.isPlaying = false;
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);

		if (SoundController.getInstance().isSoundPlaying(mediaPlayer)) {
			SoundController.getInstance().stopSoundAndUpdateList(mediaPlayer, soundInfoList, adapter);
		}
		selectedSoundInfo = adapter.getItem(selectedSoundPosition);
		menu.setHeaderTitle(selectedSoundInfo.getTitle());
		adapter.addCheckedItem(((AdapterContextMenuInfo) menuInfo).position);

		getSherlockActivity().getMenuInflater().inflate(R.menu.context_menu_default, menu);
		menu.findItem(R.id.context_menu_copy).setVisible(true);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		switch (item.getItemId()) {

			case R.id.context_menu_backpack:
				Log.d("TAG", "Context Menu BackPack!");

				Intent intent = new Intent(getActivity(), BackPackActivity.class);
				intent.putExtra(BackPackActivity.EXTRA_FRAGMENT_POSITION, 2);

				startActivityForResult(intent, 1);

				// moved to onActivityResult()

				//				SoundController.getInstance().backPackSound(selectedSoundInfo,
				//						BackPackListManager.getInstance().getBackPackSoundFragment(),
				//						BackPackListManager.getInstance().getSoundInfoArrayList(),
				//						BackPackListManager.getInstance().getBackPackSoundFragment().getBackPackSoundAdapter());

				break;

			case R.id.context_menu_copy:
				SoundInfo newSoundInfo = SoundController.getInstance().copySound(selectedSoundInfo, soundInfoList,
						adapter);
				updateSoundAdapter(newSoundInfo);
				break;

			case R.id.context_menu_cut:
				break;

			case R.id.context_menu_insert_below:
				break;

			case R.id.context_menu_move:
				break;

			case R.id.context_menu_rename:
				showRenameDialog();
				break;

			case R.id.context_menu_delete:
				showConfirmDeleteDialog();
				break;
		}
		return super.onContextItemSelected(item);
	}

	private void updateSoundAdapter(SoundInfo newSoundInfo) {

		if (soundInfoListChangedAfterNewListener != null) {
			soundInfoListChangedAfterNewListener.onSoundInfoListChangedAfterNew(newSoundInfo);
		}
		//scroll down the list to the new item:
		{
			final ListView listView = getListView();
			listView.post(new Runnable() {
				@Override
				public void run() {
					listView.setSelection(listView.getCount() - 1);
				}
			});
		}

		if (isResultHandled) {
			isResultHandled = false;

			ScriptActivity scriptActivity = (ScriptActivity) getActivity();
			if (scriptActivity.getIsSoundFragmentFromPlaySoundBrickNew()
					&& scriptActivity.getIsSoundFragmentHandleAddButtonHandled()) {
				SoundController.getInstance().switchToScriptFragment(this);
			}
		}
	}

	@Override
	public void setSelectMode(int selectMode) {
		adapter.setSelectMode(selectMode);
		adapter.notifyDataSetChanged();
	}

	@Override
	public int getSelectMode() {
		return adapter.getSelectMode();
	}

	@Override
	public void setShowDetails(boolean showDetails) {
		// TODO CHANGE THIS!!! (was just a quick fix)
		if (adapter != null) {
			adapter.setShowDetails(showDetails);
			adapter.notifyDataSetChanged();
		}
	}

	@Override
	public boolean getShowDetails() {
		// TODO CHANGE THIS!!! (was just a quick fix)
		if (adapter != null) {
			return adapter.getShowDetails();
		} else {
			return false;
		}
	}

	@Override
	public void handleAddButton() {
		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType("audio/*");

		startActivityForResult(Intent.createChooser(intent, getString(R.string.sound_select_source)),
				SoundController.REQUEST_SELECT_MUSIC);
	}

	@Override
	public void showRenameDialog() {
		RenameSoundDialog renameSoundDialog = RenameSoundDialog.newInstance(selectedSoundInfo.getTitle());
		renameSoundDialog.show(getFragmentManager(), RenameSoundDialog.DIALOG_FRAGMENT_TAG);
	}

	@Override
	protected void showDeleteDialog() {
		DeleteSoundDialog deleteSoundDialog = DeleteSoundDialog.newInstance(selectedSoundPosition);
		deleteSoundDialog.show(getFragmentManager(), DeleteSoundDialog.DIALOG_FRAGMENT_TAG);
	}

	private class SoundRenamedReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(ScriptActivity.ACTION_SOUND_RENAMED)) {
				String newSoundTitle = intent.getExtras().getString(RenameSoundDialog.EXTRA_NEW_SOUND_TITLE);

				if (newSoundTitle != null && !newSoundTitle.equalsIgnoreCase("")) {
					selectedSoundInfo.setTitle(newSoundTitle);
					adapter.notifyDataSetChanged();
				}
			}
		}
	}

	private class SoundDeletedReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(ScriptActivity.ACTION_SOUND_DELETED)) {
				adapter.notifyDataSetChanged();
				getActivity().sendBroadcast(new Intent(ScriptActivity.ACTION_BRICK_LIST_CHANGED));
			}
		}
	}

	private class SoundCopiedReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {

			Log.d("TAG", "SoundCopiedReceiver!");

			if (intent.getAction().equals(ScriptActivity.ACTION_SOUND_COPIED)) {
				adapter.notifyDataSetChanged();
				getActivity().sendBroadcast(new Intent(ScriptActivity.ACTION_BRICK_LIST_CHANGED));
			}
		}
	}

	private ActionMode.Callback renameModeCallBack = new ActionMode.Callback() {

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			return false;
		}

		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			setSelectMode(ListView.CHOICE_MODE_SINGLE);
			mode.setTitle(getString(R.string.rename));

			setActionModeActive(true);

			return true;
		}

		@Override
		public boolean onActionItemClicked(ActionMode mode, com.actionbarsherlock.view.MenuItem item) {
			return false;
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {
			((SoundAdapter) adapter).onDestroyActionModeRename(mode, listView);
		}
	};

	private ActionMode.Callback copyModeCallBack = new ActionMode.Callback() {

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			return false;
		}

		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {

			Log.d("TAG", "CopyModeCallBack, Type ActionMode.Callback");

			setSelectMode(ListView.CHOICE_MODE_MULTIPLE);
			setActionModeActive(true);

			actionModeTitle = getString(R.string.copy);
			singleItemAppendixDeleteActionMode = getString(R.string.category_sound);
			multipleItemAppendixDeleteActionMode = getString(R.string.sounds);

			mode.setTitle(actionModeTitle);

			return true;
		}

		@Override
		public boolean onActionItemClicked(ActionMode mode, com.actionbarsherlock.view.MenuItem item) {
			return false;
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {

			((SoundAdapter) adapter).onDestroyActionModeCopy(mode);

		}

	};

	private ActionMode.Callback deleteModeCallBack = new ActionMode.Callback() {

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			return false;
		}

		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			setSelectMode(ListView.CHOICE_MODE_MULTIPLE);
			setActionModeActive(true);

			mode.setTitle(R.string.delete);

			actionModeTitle = getString(R.string.delete);
			singleItemAppendixDeleteActionMode = getString(R.string.category_sound);
			multipleItemAppendixDeleteActionMode = getString(R.string.sounds);

			return true;
		}

		@Override
		public boolean onActionItemClicked(ActionMode mode, com.actionbarsherlock.view.MenuItem item) {
			return false;
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {
			if (adapter.getAmountOfCheckedItems() == 0) {
				clearCheckedSoundsAndEnableButtons();
			} else {
				showConfirmDeleteDialog();
			}
		}
	};

	private void initClickListener() {
		listView.setOnItemLongClickListener(new OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				selectedSoundPosition = position;
				return false;
			}
		});
	}

	private void showConfirmDeleteDialog() {
		String yes = getActivity().getString(R.string.yes);
		String no = getActivity().getString(R.string.no);
		String title = "";
		if (adapter.getAmountOfCheckedItems() == 1) {
			title = getActivity().getString(R.string.dialog_confirm_delete_sound_title);
		} else {
			title = getActivity().getString(R.string.dialog_confirm_delete_multiple_sounds_title);
		}

		String message = getActivity().getString(R.string.dialog_confirm_delete_sound_message);

		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(title);
		builder.setMessage(message);
		builder.setPositiveButton(yes, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int id) {
				SoundController.getInstance().deleteCheckedSounds(getActivity(), adapter, soundInfoList, mediaPlayer);
				clearCheckedSoundsAndEnableButtons();
			}
		});
		builder.setNegativeButton(no, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int id) {
				dialog.cancel();
				clearCheckedSoundsAndEnableButtons();
			}
		});

		AlertDialog alertDialog = builder.create();
		alertDialog.show();
	}

	public void clearCheckedSoundsAndEnableButtons() {
		setSelectMode(ListView.CHOICE_MODE_NONE);
		adapter.clearCheckedItems();

		actionMode = null;
		setActionModeActive(false);

		registerForContextMenu(listView);
		BottomBar.enableButtons(getActivity());
	}

	@Override
	public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
		switch (keyCode) {
			case KeyEvent.KEYCODE_BACK:
				ScriptActivity scriptActivity = (ScriptActivity) getActivity();
				if (scriptActivity.getIsSoundFragmentFromPlaySoundBrickNew()) {
					SoundController.getInstance().switchToScriptFragment(this);

					return true;
				}
			default:
				break;
		}
		return false;
	}

	public View getView(int position, View convertView) {
		SoundViewHolder holder;

		if (convertView == null) {
			convertView = View.inflate(getActivity(), R.layout.fragment_sound_soundlist_item, null);

			holder = new SoundViewHolder();
			holder.playButton = (ImageButton) convertView.findViewById(R.id.fragment_sound_item_play_image_button);
			holder.pauseButton = (ImageButton) convertView.findViewById(R.id.fragment_sound_item_pause_image_button);

			holder.playButton.setVisibility(Button.VISIBLE);
			holder.pauseButton.setVisibility(Button.GONE);

			holder.soundFragmentButtonLayout = (LinearLayout) convertView
					.findViewById(R.id.fragment_sound_item_main_linear_layout);
			holder.checkbox = (CheckBox) convertView.findViewById(R.id.fragment_sound_item_checkbox);
			holder.titleTextView = (TextView) convertView.findViewById(R.id.fragment_sound_item_title_text_view);
			holder.timeSeperatorTextView = (TextView) convertView
					.findViewById(R.id.fragment_sound_item_time_seperator_text_view);
			holder.timeDurationTextView = (TextView) convertView
					.findViewById(R.id.fragment_sound_item_duration_text_view);
			holder.soundFileSizePrefixTextView = (TextView) convertView
					.findViewById(R.id.fragment_sound_item_size_prefix_text_view);
			holder.soundFileSizeTextView = (TextView) convertView.findViewById(R.id.fragment_sound_item_size_text_view);

			holder.timePlayedChronometer = (Chronometer) convertView
					.findViewById(R.id.fragment_sound_item_time_played_chronometer);

			convertView.setTag(holder);
		} else {
			holder = (SoundViewHolder) convertView.getTag();
		}
		SoundController controller = SoundController.getInstance();
		Log.v("Adapter *********", controller.toString());
		controller.updateSoundLogic(position, holder, adapter);

		return convertView;
	}

	public interface OnSoundInfoListChangedAfterNewListener {

		public void onSoundInfoListChangedAfterNew(SoundInfo soundInfo);

	}

	public SoundDeletedReceiver getSoundDeletedReceiver() {
		return soundDeletedReceiver;
	}

	public void setSoundDeletedReceiver(SoundDeletedReceiver soundDeletedReceiver) {
		this.soundDeletedReceiver = soundDeletedReceiver;
	}

	public SoundRenamedReceiver getSoundRenamedReceiver() {
		return soundRenamedReceiver;
	}

	public void setSoundRenamedReceiver(SoundRenamedReceiver soundRenamedReceiver) {
		this.soundRenamedReceiver = soundRenamedReceiver;
	}

	public SoundCopiedReceiver getSoundCopiedReceiver() {
		return soundCopiedReceiver;
	}

	public void setSoundCopiedReceiver(SoundCopiedReceiver soundCopiedReceiver) {
		this.soundCopiedReceiver = soundCopiedReceiver;
	}

	public class CopyAudioFilesTask extends AsyncTask<String, Void, File> {
		private ProgressDialog progressDialog = new ProgressDialog(getActivity());

		@Override
		protected void onPreExecute() {
			progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
			progressDialog.setTitle(getString(R.string.loading));
			progressDialog.show();
		}

		@Override
		protected File doInBackground(String... path) {
			File file = null;
			try {
				file = StorageHandler.getInstance().copySoundFile(path[0]);
			} catch (IOException e) {
				Log.e("CATROID", "Cannot load sound.", e);
			}
			return file;
		}

		@Override
		protected void onPostExecute(File file) {
			progressDialog.dismiss();

			if (file != null) {
				String fileName = file.getName();
				String soundTitle = fileName.substring(fileName.indexOf('_') + 1, fileName.lastIndexOf('.'));
				SoundController.getInstance().updateSoundAdapter(soundTitle, fileName, soundInfoList, adapter);
			} else {
				Utils.showErrorDialog(getActivity(), getString(R.string.error_load_sound));
			}
		}

	}

	public void setSelectedSoundInfo(SoundInfo selectedSoundInfo) {
		this.selectedSoundInfo = selectedSoundInfo;
	}

	public ArrayList<SoundInfo> getSoundInfoList() {
		return soundInfoList;
	}

	private class SoundsListInitReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(ScriptActivity.ACTION_SOUNDS_LIST_INIT)) {
				adapter.notifyDataSetChanged();
			}
		}
	}

}
