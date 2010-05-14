package com.eyebrowssoftware.bptrackerfree;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import com.csvreader.CsvWriter;
import com.eyebrowssoftware.bptrackerfree.BPRecords.BPRecord;

public class BPSend extends Activity implements CompoundButton.OnCheckedChangeListener, OnClickListener {

	private static final String TAG = "BPSend";

	private static final String[] PROJECTION = { 
		BPRecord._ID,
		BPRecord.SYSTOLIC, 
		BPRecord.DIASTOLIC, 
		BPRecord.PULSE,
		BPRecord.CREATED_DATE,
		BPRecord.NOTE
	};

	private static final int COLUMN_ID_INDEX = 0;
	private static final int COLUMN_SYSTOLIC_INDEX = 1;
	private static final int COLUMN_DIASTOLIC_INDEX = 2;
	private static final int COLUMN_PULSE_INDEX = 3;
	private static final int COLUMN_CREATED_AT_INDEX = 4;
	private static final int COLUMN_NOTE_INDEX = 5;

	private Uri mUri;

	private TextView mMsgLabelView;
	private TextView mMsgView;
	private CheckBox mSendText;
	private CheckBox mSendFile;
	private Button mSendButton;

	private String mMsgLabelString;

	public static final boolean ALL_DATES = true;
	public static final String REVERSE = "reverse";

	// This may or may not be used
	private boolean mReverse = true;

	private Calendar mToCalendar = GregorianCalendar.getInstance();
	private Calendar mFromCalendar = GregorianCalendar.getInstance();
	
	@Override
	protected void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		this.setContentView(R.layout.bp_send);

		Intent intent = getIntent();
		if (intent.getData() == null) {
			intent.setData(BPRecords.CONTENT_URI);
		}
		mReverse = intent.getBooleanExtra(REVERSE, true);

		mUri = intent.getData();
		
		mMsgLabelView = (TextView) findViewById(R.id.message_label);
		mMsgLabelString = getString(R.string.label_message_format);
		mMsgView = (TextView) findViewById(R.id.message);

		mSendText = (CheckBox) findViewById(R.id.text);
		mSendText.setChecked(true);
		mSendText.setOnCheckedChangeListener(this);

		mSendFile = (CheckBox) findViewById(R.id.attach);
		mSendFile.setChecked(true);
		mSendFile.setOnCheckedChangeListener(this);

		mSendButton = (Button) findViewById(R.id.send);
		mSendButton.setOnClickListener(this);
		querySendData();
	}
	
	private void querySendData() {
		String where = null;
		String[] whereArgs = null;
		long newest;
		long oldest;
		
		newest = oldest = System.currentTimeMillis();
		
		Cursor cursor = getContentResolver().query(mUri, PROJECTION, where, whereArgs, BPRecord.CREATED_DATE + ((mReverse) ? " DESC" : "ASC"));
		String msg = getMessage(cursor);
		
		if (cursor == null) {
			Toast.makeText(BPSend.this, R.string.msg_nothing_to_send, Toast.LENGTH_SHORT).show();
		} else {
			if(cursor.moveToFirst()) {

				long first_time = cursor.getLong(COLUMN_CREATED_AT_INDEX);
				cursor.moveToLast();
				long last_time = cursor.getLong(COLUMN_CREATED_AT_INDEX);
				if (mReverse) {
					newest = first_time;
					oldest = last_time;
				} else {
					oldest = first_time;
					newest = last_time;
				}
			} else {
				Toast.makeText(BPSend.this, R.string.msg_nothing_to_send, Toast.LENGTH_SHORT).show();
			}
			cursor.close();
		}
		mToCalendar.setTimeInMillis(newest);
		mFromCalendar.setTimeInMillis(oldest);
        mMsgLabelView.setText(String.format(mMsgLabelString, msg.length()));
        mMsgView.setText(msg);
	}

	public void onClick(View v) {
		if (v.equals(mSendButton)) {
			String msg = mMsgView.getText().toString();
			if (msg.length() > 0) {
				sendData(msg);
				finish();
			} else {
				Toast.makeText(this, R.string.msg_nothing_to_send, Toast.LENGTH_SHORT).show();
			}
		}
	}
	
	private static final String FILENAME = "data.csv";
	private static final String MSGNAME = "bpdata.csv";

	private void sendData(String msg) {
		// We're going to send the data as message text and/or as an attachment
		if (msg == null || !(mSendText.isChecked() || mSendFile.isChecked())) {
			Toast.makeText(this, R.string.msg_nothing_to_send, Toast.LENGTH_SHORT).show();
			return;
		}
		try {
			Intent i = new Intent(Intent.ACTION_SEND);
			if (mSendText.isChecked())
				i.putExtra(Intent.EXTRA_TEXT, msg);
			if (mSendFile.isChecked()) {
				FileOutputStream fos = this.openFileOutput(FILENAME, Context.MODE_WORLD_READABLE);
				fos.write(msg.getBytes());
				fos.close();
				Uri fileUri = Uri.fromFile(getFileStreamPath(FILENAME));
				// Log.d(TAG, "File Uri: " + fileUri.toString());
				i.putExtra(Intent.EXTRA_STREAM, fileUri);
				i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
			}
			i.putExtra(Intent.EXTRA_TITLE, MSGNAME);
			i.putExtra(Intent.EXTRA_SUBJECT, MSGNAME);
			i.setType("text/plain");
			Intent ai = Intent.createChooser(i, getString(R.string.msg_choose_send_method));
			ai.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(ai);
		} catch (FileNotFoundException e) {
			Log.e(TAG, getString(R.string.title_error));
			Toast.makeText(this, getString(R.string.title_error), Toast.LENGTH_SHORT).show();
			e.printStackTrace();
		} catch (IOException e) {
			Log.e(TAG, getString(R.string.title_error));
			Toast.makeText(this, getString(R.string.title_error), Toast.LENGTH_SHORT).show();
			e.printStackTrace();
		}
	}


	public void onCheckedChanged(CompoundButton check_box, boolean checked) {
		if (check_box.equals(mSendText) && !checked && !mSendFile.isChecked())
			Toast.makeText(BPSend.this, R.string.msg_nothing_to_send, Toast.LENGTH_SHORT).show();
		
		if (check_box.equals(mSendFile) && !checked && !mSendText.isChecked())
			Toast.makeText(BPSend.this, R.string.msg_nothing_to_send, Toast.LENGTH_SHORT).show();
	}


	// Uses the member Cursor mRecordsCursor
	private String getMessage(Cursor cursor) {
		
		String date_localized;
		String time_localized;
		String sys_localized;
		String dia_localized;
		String pls_localized;
		String note_localized;

		Resources res = getResources();
		date_localized = res.getString(R.string.bp_send_date);
		time_localized = res.getString(R.string.bp_send_time);
		sys_localized = res.getString(R.string.bp_send_sys);
		dia_localized = res.getString(R.string.bp_send_dia);
		pls_localized = res.getString(R.string.bp_send_pls);
		note_localized = res.getString(R.string.bp_send_note);

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		CsvWriter csvw = new CsvWriter(baos, ',', Charset.forName("UTF-8"));
		if (cursor != null && cursor.moveToFirst()) {
			try {
				String[] cnames = cursor.getColumnNames();
				int columns = cnames.length;
				
				for (int j = 0; j < columns; ++j) {
					if (j == COLUMN_ID_INDEX) { // put out nothing for the id column
						continue;
					}
					else if (j == COLUMN_SYSTOLIC_INDEX) {
						csvw.write(sys_localized);
					} else if (j == COLUMN_DIASTOLIC_INDEX) {
						csvw.write(dia_localized);
					} else if (j == COLUMN_PULSE_INDEX) {
						csvw.write(pls_localized);
					} else if (j == COLUMN_CREATED_AT_INDEX) {
						// This turns into two columns
						csvw.write(date_localized);
						csvw.write(time_localized);
					} else if (j == COLUMN_NOTE_INDEX) {
						csvw.write(note_localized);
					} else
						csvw.write(cnames[j]);
				}
				csvw.endRecord();
				do {
					// the final separator of each field is put on at the end.
					for (int j = 0; j < columns; ++j) {
						if (j == COLUMN_ID_INDEX) {
							continue;
						} else if (j == COLUMN_CREATED_AT_INDEX) {
							String date = BPTrackerFree.getDateString(cursor
									.getLong(j), DateFormat.SHORT);
							String time = BPTrackerFree.getTimeString(cursor
									.getLong(j), DateFormat.SHORT);
							csvw.write(date);
							csvw.write(time);
						} else if (j == COLUMN_NOTE_INDEX) { 
							csvw.write(String.valueOf(cursor.getString(j)));
						} else
							csvw.write(String.valueOf(cursor.getInt(j)));
					}
					csvw.endRecord();
				} while (cursor.moveToNext());
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				csvw.close();
			}
		}
		return baos.toString();
	}

}
