package edu.ucla.cens.whatsinvasive;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.Handler;
import android.text.util.Linkify;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnCreateContextMenuListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;
import android.widget.TwoLineListItem;
import edu.ucla.cens.whatsinvasive.data.PhotoDatabase;
import edu.ucla.cens.whatsinvasive.data.PhotoDatabase.PhotoDatabaseRow;
import edu.ucla.cens.whatsinvasive.tools.Media;

public class Queue extends ListActivity implements PhotoDatabase.OnChangeListener {
	private final int CONTEXT_VIEW = 1;
	private final int CONTEXT_EDIT_NOTE = 2;
	private final int CONTEXT_REMOVE = 3;
	
	private static final int ACTIVITY_EDIT_NOTE = 0;
	
	private PhotoDatabase mDatabase;
	private Cursor mCursor;
	private Handler mHandler;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.setContentView(R.layout.queue);
		this.setTitle(R.string.title_queue);
        
		mHandler = new Handler();
		
        TextView extrat = new TextView(this);   
        extrat.setAutoLinkMask(Linkify.WEB_URLS);
        extrat.setText(getString(R.string.queue_message_full));
        extrat.setTextSize(18);
        extrat.setClickable(false);
        extrat.setLongClickable(false);  
        
        getListView().addFooterView(extrat);
        getListView().setFooterDividersEnabled(true);
        
        mDatabase = new PhotoDatabase(this);
		
		getListView().setOnItemClickListener(new OnItemClickListener(){

			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
					long arg3) {
				if(!(arg1 instanceof TextView) )
					arg1.performLongClick();
			}});
		getListView().setOnCreateContextMenuListener(new OnCreateContextMenuListener(){

			public void onCreateContextMenu(ContextMenu menu, View v,
					ContextMenuInfo menuInfo) {
				
					AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
					TagData data = (TagData) info.targetView.getTag();
					if(data != null){
						menu.setHeaderTitle(getString(R.string.queue_actions));
						menu.add(0, CONTEXT_VIEW, 1, getString(R.string.queue_view));
						if(data.uploaded == null) {
						    menu.add(0, CONTEXT_EDIT_NOTE, 2, getString(R.string.queue_edit_note));
							menu.add(0, CONTEXT_REMOVE, 3, getString(R.string.queue_do_not_upload));
						}
					}
				}				
		});
	}

    public void onDatabseChanged(PhotoDatabase source) {
        mHandler.post(new Runnable() {
            public void run() {
                if(Queue.this.mCursor != null)
                    Queue.this.mCursor.requery();
            }
        });
    }
	
	@Override
	protected void onResume() {
	    mDatabase.tryOpen();
	    PhotoDatabase.addChangeListener(this);
	    
        mCursor = mDatabase.getReadCursor();
	    
        startManagingCursor(mCursor);
        setListAdapter(new QueueAdapter(this, mCursor));
	    
        super.onResume();
	}
	
	@Override
	protected void onPause() {
	    // We have to close the database here so that the other 
	    // activities (e.g. ViewTag) can use the PhotoDatabase
	    PhotoDatabase.removeChangeListener(this);
	    
	    stopManagingCursor(mCursor);
	    mCursor.close();
	    mDatabase.close();
	    
	    super.onPause();
	}
	
	@Override
	protected void onStop() {
	    // We have to close the cursor here and not in the onPause method
	    // if we want to avoid the empty view from being displayed before the 
	    // view is killed
	    mCursor.close();
	    
	    super.onStop();
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) { 
		AdapterView.AdapterContextMenuInfo menuInfo = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
		TagData data = (TagData) menuInfo.targetView.getTag();
		
		switch (item.getItemId()) {
			case CONTEXT_VIEW:
				Intent intent = new Intent(this, ViewTag.class);
				intent.putExtra("id", data.id);
				
				startActivity(intent);
				
				break;
			case CONTEXT_EDIT_NOTE:
			    PhotoDatabaseRow row;
			    
		        row = mDatabase.fetchPhoto(data.id);
		        
	            Intent intent2 = new Intent(this, NoteEdit.class);
	            intent2.putExtra("id", data.id);
	            String title = getString(R.string.note_title_prefix) + " " + row.tagsValue;
	            intent2.putExtra("title", title);
	            intent2.putExtra("note", row.noteValue);
	             
	            startActivityForResult(intent2, ACTIVITY_EDIT_NOTE);
	                
			    break;
			case CONTEXT_REMOVE:
				mDatabase.deletePhoto(data.id);
				
				if(data.filename!=null)
					(new File(data.filename)).delete();
				
				break;
		}
		  
		return false;
	}
	
	@Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
        case ACTIVITY_EDIT_NOTE:
            if(resultCode == Activity.RESULT_OK) {
                int rowId = data.getIntExtra("id", -1);
                String note = data.getStringExtra("note");
                
                mDatabase.tryOpen();
                mDatabase.updateNote(rowId, note);
                
                break;
            }
        }
    }

    private class QueueAdapter extends ResourceCursorAdapter {

		public QueueAdapter(Context context, Cursor c) {
			super(context, R.layout.queue_list_item, c, true);
		}

		@Override
		public void bindView(View v, Context context, Cursor cursor) {
			int indexTags = cursor.getColumnIndexOrThrow(PhotoDatabase.KEY_PHOTO_TAGS);
			int indexTime = cursor.getColumnIndexOrThrow(PhotoDatabase.KEY_PHOTO_TIME);
			int indexPhoto = cursor.getColumnIndexOrThrow(PhotoDatabase.KEY_PHOTO_FILENAME);
			int indexUploaded = cursor.getColumnIndexOrThrow(PhotoDatabase.KEY_PHOTO_UPLOADED);
			int indexLat = cursor.getColumnIndexOrThrow(PhotoDatabase.KEY_PHOTO_LATITUDE);
			int indexLon = cursor.getColumnIndexOrThrow(PhotoDatabase.KEY_PHOTO_LONGITUDE);
			
			TwoLineListItem view = (TwoLineListItem) v;
			
			view.getText1().setText((cursor.getCount() - cursor.getPosition()) +") "+ cursor.getString(indexTags));
			view.getText1().setTextSize(16);
			
			String time = cursor.getString(indexTime);
			SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			df.setTimeZone(TimeZone.getTimeZone("UTC"));
			
			Date date = new Date(0);
			
			try {
			    date = df.parse(time);
            } catch (ParseException e) {
                e.printStackTrace();
            }
            
            df.setTimeZone(TimeZone.getDefault());
			
			String text = getString(R.string.tag_tagged_at) + " " + df.format(date);
			
			if(!cursor.isNull(indexUploaded))
				text += "\nUploaded "+ cursor.getString(indexUploaded);
			
			view.getText2().setText(text);
			
			TagData data = new TagData();
			
			ImageView image = (ImageView) view.findViewById(android.R.id.selectedIcon);
			
			if(cursor.getString(indexPhoto) != null){
				String photo = cursor.getString(indexPhoto);
				
				if((new File(photo)).exists()){
					data.filename = photo;
					
					if(cursor.isNull(indexUploaded))
						image.setImageBitmap(resizeImage(photo, false));
					else
						image.setImageBitmap(resizeImage(photo, true));
				}else{
					image.setImageBitmap(null);
				}
			}else{
				Bitmap preview = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888);	
				
				if(cursor.isNull(indexUploaded)){
					image.setImageBitmap(preview);
				}else{
					Bitmap overlay = BitmapFactory.decodeResource(Queue.this.getResources(), R.drawable.btn_check_buttonless_on);
		        	
		        	Canvas canvas = new Canvas(preview);
		        	canvas.drawBitmap(overlay, 26, 10, null);
		        	
					image.setImageBitmap(preview);
				}
			}
			
			data.id = cursor.getInt(cursor.getColumnIndex(PhotoDatabase.KEY_PHOTO_ROWID));
			data.lat = cursor.getString(indexLat);
			data.lon = cursor.getString(indexLon);
			data.uploaded = cursor.getString(indexUploaded);
				
			v.setTag(data);
		}
		
		private Bitmap resizeImage(String path, boolean uploaded){
            	Bitmap thumb = Media.resizeImage(path, new Media.Size(48,48));
            	
            	if(uploaded) {
        	        	Bitmap preview = Bitmap.createBitmap(thumb.getWidth(), thumb.getHeight(), Bitmap.Config.ARGB_8888);				        	
        	        	
        	        	Bitmap overlay = BitmapFactory.decodeResource(Queue.this.getResources(), R.drawable.btn_check_buttonless_on);
        	        	
        	        	Canvas canvas = new Canvas(preview);
        	        	canvas.drawBitmap(thumb, 0, 0, null);
        	        	canvas.drawBitmap(overlay, (thumb.getWidth()/2)-14, 10, null);
        	        	
        	        	return preview;
            	} else {
            		return thumb;
            	}
		}
	}
	
	private class TagData {
		public String lat;
		public String lon;
		public int id;
		public String filename;
		public String uploaded;
	}
}
