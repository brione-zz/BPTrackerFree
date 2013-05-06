/*
 * Copyright 2012 - Brion Noble Emde
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.eyebrowssoftware.bptrackerfree.fragments;

import java.text.DateFormat;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.eyebrowssoftware.bptrackerfree.BPRecords;
import com.eyebrowssoftware.bptrackerfree.BPRecords.BPRecord;
import com.eyebrowssoftware.bptrackerfree.BPTrackerFree;
import com.eyebrowssoftware.bptrackerfree.R;
import com.eyebrowssoftware.bptrackerfree.fragments.AlertDialogFragment.AlertDialogButtonListener;

/**
 * Main Fragment for the list view
 *
 * @author brione
 *
 */
public class BPRecordListFragment extends ListFragment implements LoaderManager.LoaderCallbacks<Cursor>,
        AlertDialogButtonListener, View.OnClickListener {
    static final String TAG = "BPRecordListFragment";

    /**
     * Hosting activities must provide this interface
     *
     * @author brionemde
     *
     */
    public interface BPRecordListFragmentListener {
        /**
         * Insert a new item of the content uri
         *
         * @param uri
         *            : Group content uri (BPRecords.CONTENT_URI for this app)
         *
         */
        public void newItem(Uri uri);

        /**
         * Inform the host activity that the user has elected to delete the item with _id == id
         *
         * @param uri
         *            : the content uri of the item to delete
         */
        public void deleteItem(Uri uri);

        /**
         * Inform the host activity that the user has elected to send the item with _id == id
         *
         * @param uri
         *            : the content uri of the item to be sent
         */
        public void sendItem(Uri uri);

        /**
         * Inform the host activity that the user has elected to edit the item with _id == id
         *
         * @param uri
         *            : the content uri of the item to be edited
         */
        public void editItem(Uri uri);

        /**
         * Interrogate the host activity whether we're in dual-pane mode or not
         *
         * @return true if dual-pane, false if single-pane
         */
        public boolean isDualPane();
    };

    private static final String[] VALS = { BPRecord.CREATED_DATE, BPRecord.CREATED_DATE, BPRecord.SYSTOLIC,
        BPRecord.DIASTOLIC, BPRecord.PULSE, BPRecord.NOTE };

    private static final int[] IDS = { R.id.date, R.id.time, R.id.sys_value, R.id.dia_value, R.id.pulse_value,
        R.id.note };
    private static final int LIST_LOADER_ID = 1206;
    private static final String CONTEXT_URI = "context_uri";
    private static final String SELECTION = "selection";

    // The state that needs to be saved and stored
    private int mCurrentCheckPosition = 0;
    private long mContextMenuRecordId = 0;
    private View mProgressContainer;
    private View mListContainer;
    private View mEmptyContent;
    private Uri mStartUri;
    private boolean mListShown = true; // default state of list container is shown, progress hidden
    private BPRecordListFragmentListener mListener;

    public BPRecordListFragmentListener getListener() {
        return mListener;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View layout = inflater.inflate(R.layout.bp_record_list_fragment, container, false);
        // The list group is visible by default
        mProgressContainer = layout.findViewById(R.id.progress_container);
        mListContainer = layout.findViewById(R.id.listContainer);
        mEmptyContent = layout.findViewById(R.id.empty_content);
        mEmptyContent.setOnClickListener(this);
        return layout;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        this.getListView().setOnCreateContextMenuListener(null);
        mProgressContainer = null;
        mListContainer = null;
        mEmptyContent = null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setHasOptionsMenu(true);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(CONTEXT_URI, mContextMenuRecordId);
        outState.putInt(SELECTION, mCurrentCheckPosition);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (activity instanceof BPRecordListFragmentListener) {
            mListener = (BPRecordListFragmentListener) activity;
        }
        else {
            throw new ClassCastException(activity.toString() + " must implemenet BPRecordListFragment.Listener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (savedInstanceState != null) {
            mContextMenuRecordId = savedInstanceState.getLong(CONTEXT_URI);
            mCurrentCheckPosition = savedInstanceState.getInt(SELECTION);
        }
        Activity hostActivity = this.getActivity();
        Intent intent = hostActivity.getIntent();
        mStartUri = intent.getData();
        if (mStartUri == null) {
            mStartUri = BPRecords.CONTENT_URI;
        }
        ListView lv = this.getListView();
        lv.setItemsCanFocus(false);
        if (mListener.isDualPane()) {
            lv.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        }
        lv.setOnCreateContextMenuListener(this);
        lv.addHeaderView(this.getLayoutInflater(null).inflate(R.layout.bp_record_list_header, null), null, true);

        // No cursor yet. Will be assigned when the CursorLoader query is complete
        SimpleCursorAdapter adapter = new SimpleCursorAdapter(hostActivity, R.layout.bp_record_list_item, null, VALS,
            IDS, 0);
        adapter.setViewBinder(new MyViewBinder());
        this.setListAdapter(adapter);
        this.setListShown(false);
        // Set up our cursor loader. It manages the cursors from now on
        this.getLoaderManager().initLoader(LIST_LOADER_ID, null, this);
    }

    @Override
    public void setListShown(boolean show) {
        setListShown(show, true);
    }

    @Override
    public void setListShownNoAnimation(boolean show) {
        setListShown(show, false);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        Log.i(TAG, "Got a click at position: " + position + " id: " + id);
        if (id < 0) {
            mListener.newItem(BPRecords.CONTENT_URI);
        }
        else {
            mListener.editItem(ContentUris.withAppendedId(BPRecords.CONTENT_URI, id));
        }
    }

    private class MyViewBinder implements SimpleCursorAdapter.ViewBinder {
        String val;

        @Override
        public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
            int id = view.getId();

            switch (id) {
                case R.id.sys_value:
                case R.id.dia_value:
                case R.id.pulse_value: // Pulse
                    ((TextView) view).setText(String.valueOf(cursor.getInt(columnIndex)));
                    return true;
                case R.id.date: // Date
                case R.id.time: // Time -- these use the same cursor column
                    long datetime = cursor.getLong(columnIndex);
                    val = (id == R.id.date) ? BPTrackerFree.getDateString(datetime, DateFormat.SHORT) : BPTrackerFree
                        .getTimeString(datetime, DateFormat.SHORT);
                    ((TextView) view).setText(val);
                    return true;
                case R.id.note:
                    String note = cursor.getString(columnIndex);
                    if (note != null && note.length() > 0) {
                        ((TextView) view).setText(note);
                        view.setVisibility(View.VISIBLE);
                    }
                    else {
                        view.setVisibility(View.GONE);
                    }
                    return true;
                default:
                    return false;
            }
        }
    }

    /**
     * Called when the Cursor Loader is created
     */
    @Override
    public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
        // Create a CursorLoader that will take care of creating a cursor for the data
        CursorLoader loader = new CursorLoader(getActivity(), mStartUri, BPTrackerFree.PROJECTION, null, null,
            BPRecord.DEFAULT_SORT_ORDER);
        return loader;
    }

    /**
     * Called when the load of the cursor is finished
     */
    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        if (loader.getId() == LIST_LOADER_ID) {
            if (this.isResumed()) {
                this.setListShown(true);
            }
            else {
                this.setListShownNoAnimation(true);
            }
            ((SimpleCursorAdapter) this.getListAdapter()).swapCursor(cursor);
        }
    }

    /**
     * Called when the loader is reset, we swap out the current cursor with null
     */
    @Override
    public void onLoaderReset(Loader<Cursor> arg0) {
        ((SimpleCursorAdapter) this.getListAdapter()).swapCursor(null);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.bp_list_fragment_options_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_new) {
            insertRecord();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {

        AdapterView.AdapterContextMenuInfo info;

        try {
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        }
        catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return;
        }
        Cursor cursor = (Cursor) getListAdapter().getItem(info.position);
        if (cursor != null && cursor.moveToFirst()) {
            long datetime = cursor.getLong(BPTrackerFree.COLUMN_CREATED_AT_INDEX);
            String date = BPTrackerFree.getDateString(datetime, DateFormat.SHORT);
            String time = BPTrackerFree.getTimeString(datetime, DateFormat.SHORT);
            String fmt = getString(R.string.datetime_format);
            menu.setHeaderTitle(String.format(fmt, date, time));
        }
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.bp_list_context_menu, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
            // Get the Uri of the record we're intending to operate on
            mContextMenuRecordId = info.id;
            switch (item.getItemId()) {
                case R.id.menu_delete:
                    mListener.deleteItem(ContentUris.withAppendedId(BPRecords.CONTENT_URI, info.id));
                    // ----
                    // Code to be moved to parent activity
                    showDeleteConfirmationDialog();
                    return true;
                case R.id.menu_edit:
                    mListener.editItem(ContentUris.withAppendedId(BPRecords.CONTENT_URI, info.id));
                    return true;
                case R.id.menu_send:
                    mListener.sendItem(ContentUris.withAppendedId(BPRecords.CONTENT_URI, info.id));
                    return true;
            }
        }
        catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
        }
        return super.onContextItemSelected(item);
    }

    private void insertRecord() {
        Uri data = BPRecords.CONTENT_URI;
        Intent intent = new Intent(Intent.ACTION_INSERT, data);
        startActivity(intent);
    }

    private static final String DIALOG_TAG = "bprecord_list_fragment_delete_tag";

    // Lint is complaining, but according to the documentation, show() does a commit on the transaction
    // http://developer.android.com/reference/android/app/DialogFragment.html#show(android.app.FragmentTransaction,%20java.lang.String)
    @SuppressLint("CommitTransaction")
    void showDeleteConfirmationDialog() {
        // DialogFragment.show() will take care of adding the fragment
        // in a transaction.  We also want to remove any currently showing
        // dialog, so make our own transaction and take care of that here.
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        Fragment prev = getFragmentManager().findFragmentByTag(DIALOG_TAG);
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(DIALOG_TAG);

        // Create and show the dialog.
        DialogFragment newFragment = AlertDialogFragment.getNewInstance(
                R.string.label_delete_history, R.string.msg_delete, R.string.label_yes, R.string.label_no);
        newFragment.setTargetFragment(this, 0);
        newFragment.show(ft, DIALOG_TAG);
    }

    private void deleteRecord() {
        Uri contextMenuUri = ContentUris.withAppendedId(mStartUri, mContextMenuRecordId);
        getActivity().getContentResolver().delete(contextMenuUri, null, null);
    }

    @Override
    public void onNegativeButtonClicked() {
        // nothing to do, dialog is cancelled already
    }

    @Override
    public void onPositiveButtonClicked() {
        deleteRecord();
    }

    /**
     * Control whether the list is being displayed. You can make it not displayed if you are waiting for the initial
     * data to show in it. During this time an indeterminant progress indicator will be shown instead.
     *
     * @param shown
     *            If true, the list view is shown; if false, the progress indicator. The initial value is true.
     * @param animate
     *            If true, an animation will be used to transition to the new state.
     */
    private void setListShown(boolean shown, boolean animate) {
        if (mProgressContainer == null) {
            throw new IllegalStateException("Can't be used with a custom content view");
        }
        if (mListShown == shown) {
            return;
        }
        mListShown = shown;
        if (shown) {
            if (animate) {
                mProgressContainer.startAnimation(AnimationUtils.loadAnimation(getActivity(), android.R.anim.fade_out));
                mListContainer.startAnimation(AnimationUtils.loadAnimation(getActivity(), android.R.anim.fade_in));
            }
            else {
                mProgressContainer.clearAnimation();
                mListContainer.clearAnimation();
            }
            mProgressContainer.setVisibility(View.GONE);
            mListContainer.setVisibility(View.VISIBLE);
        }
        else {
            if (animate) {
                mProgressContainer.startAnimation(AnimationUtils.loadAnimation(getActivity(), android.R.anim.fade_in));
                mListContainer.startAnimation(AnimationUtils.loadAnimation(getActivity(), android.R.anim.fade_out));
            }
            else {
                mProgressContainer.clearAnimation();
                mListContainer.clearAnimation();
            }
            mProgressContainer.setVisibility(View.VISIBLE);
            mListContainer.setVisibility(View.GONE);
        }
    }

    @Override
    public void onClick(View v) {
        insertRecord();
    }

}
