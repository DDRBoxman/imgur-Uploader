/*
 * Copyright (C) Michael Maass 2009 <code.realm@gmail.com>
 * 
 * main.c is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * main.c is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along
 * with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.maass.android.imgur_uploader;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec_1_4.binary.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import com.maass.android.imgur_uploader.ImgurEntity.CountingOutputStream;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.ClipboardManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.Toast;

public class ImgurUpload extends Activity implements ImgurListener {
    private final static String API_KEY = "e67bb2d5ceb42e43f8f7fc38e7ca7376";
    private final static int
    	USER_WAIT = 0xBeC001,
    	PROGRESS_METER = 0xFACE0FF;
	
    private ProgressDialog mMeterDialog;
    private ProgressDialog mWaitDialog;
    
    private Map<String, String> mImgurResponse;
 
    private EditText mEditURL;
    private EditText mEditDelete;
    
    private long mTransferred;
    private String mPayload;
    private boolean mUploading = false;
    
    private final boolean mDebug = false;
    
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        final Object savedData = getLastNonConfigurationInstance();
        
        mEditURL = (EditText)findViewById(R.id.url);
    	mEditDelete = (EditText)findViewById(R.id.delete);
    
    	setEventHandlers();
    	
        //Rotated so just restore the links and don't do
        // the rest
        if (savedData != null)
        {
        	String[] latestLinks = (String[])savedData;
        	mEditURL.setText(latestLinks[0]);
        	mEditDelete.setText(latestLinks[1]);
        	
        	return;
        }
        
        // New thread updates progress dialog at 2 Hz.
        // Is killed when mUploading == false
        Thread updateWorker = new Thread () {
        	public void run() {
        		while(mUploading) {
        			try { Thread.sleep(500); }
        			catch(InterruptedException e) { break; }
        			
        			if(mPayload == null || mMeterDialog == null) continue;
        			
        			int size = mPayload.length();
        			mMeterDialog.setMax(size);
        			mMeterDialog.setProgress((int) mTransferred);
        		}
        	}
        };
        
    	Thread loadWorker = new Thread() {
        	public void run()
        	{   
        		mImgurResponse = handleSendIntent(getIntent());
        		uploadHandler.sendEmptyMessage(0); 
        	}
    	};
    	
    	showDialog(USER_WAIT);
    	mUploading = true;
    	loadWorker.start();
    	updateWorker.start();
    }
    
    protected void onSaveInstanceState(Bundle icicle) {
    	icicle.putString("payload", mPayload);
    }
    
    protected void onRestoreInstanceState(Bundle icicle) {
    	mPayload = icicle.getString("payload");
    }
    
    @Override
    public Dialog onCreateDialog(int id) {
    	Resources res = getResources();
    	switch(id) {
    	case PROGRESS_METER:
    		mMeterDialog = new ProgressDialog(this);
    		mMeterDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
    		mMeterDialog.setTitle(R.string.uploading_image);
    		mMeterDialog.setMax(mPayload.length());
    		return mMeterDialog;
    		
    	case USER_WAIT:
    		mWaitDialog = new ProgressDialog(this);
    		mWaitDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
    		mWaitDialog.setTitle(R.string.please_wait);
    		mWaitDialog.setMessage(res.getString(R.string.preparing_image));
    		return mWaitDialog;
    	}
    	
    	return null;
    }
    
    public Object onRetainNonConfigurationInstance() {
    	String[] latestLinks = new String[2];
    	
    	latestLinks[0] = mEditURL.getText().toString();
    	latestLinks[1] = mEditDelete.getText().toString();
    	
        return latestLinks;
    }
    
    private void setEventHandlers() {
    	//Clicking the url copy button copies the original url
    	//to the global clipboard
    	findViewById(R.id.copyURL).setOnClickListener(new OnClickListener() {
    		public void onClick(View v) {
    			ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
       			clipboard.setText(mEditURL.getText());
    		}
        });
    	
    	//Clicking url share button displays screen to select how
        //to share the image link
    	findViewById(R.id.shareURL).setOnClickListener(new OnClickListener() {
    		public void onClick(View v) {
    			Intent shareLinkIntent = new Intent(Intent.ACTION_SEND);
                
    			shareLinkIntent.putExtra(Intent.EXTRA_TEXT, mEditURL.getText().toString());
    			shareLinkIntent.setType("text/plain");
    			
    			ImgurUpload.this.startActivity(
    					Intent.createChooser(shareLinkIntent, "Share via"));
    		}
        });
    	
        //Clicking the delete copy button copies the delete url
    	//to the global clipboard
    	findViewById(R.id.copyDelete).setOnClickListener(new OnClickListener() {
    		public void onClick(View v) {
    			ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
       			clipboard.setText(mEditDelete.getText());
    		}
        });
    	
    	//Clicking delete share button displays screen to select how
        //to share the delete link
    	findViewById(R.id.shareDelete).setOnClickListener(new OnClickListener() {
    		public void onClick(View v) {
    			Intent shareLinkIntent = new Intent(Intent.ACTION_SEND);
                
    			shareLinkIntent.putExtra(Intent.EXTRA_TEXT, mEditDelete.getText().toString());
    			shareLinkIntent.setType("text/plain");
    			
    			Resources res = getResources();
    			ImgurUpload.this.startActivity(
    					Intent.createChooser(shareLinkIntent, res.getString( R.string.share_via) ));
    		}
        });
    }
    
    /** Dismisses the "wait" dialog and calls up the "progress" dialog 
     * Added because Android subsystem doesn't like you messing
     * with a ProgressDialog once you have already mucked with it.
     */
    private Handler encodeHandler = new Handler() {
    	public void handleMessage(Message msg) {
    		dismissDialog(USER_WAIT);
    		showDialog(PROGRESS_METER);
    	}
    };
    
    private Handler uploadHandler = new Handler() {
        public void handleMessage(Message msg) {
        	mUploading = false; // Kills a thread that updates meter dialog
            dismissDialog(PROGRESS_METER);
            
            if (mImgurResponse == null) {
            	Toast.makeText(ImgurUpload.this, R.string.connection_error, Toast.LENGTH_SHORT).show();
            } else if (mImgurResponse.get("error") != null) {
            	Toast.makeText(ImgurUpload.this, mImgurResponse.get("error"), Toast.LENGTH_SHORT).show();
            } else {
            	mEditURL.setText(mImgurResponse.get("original"));
            	mEditDelete.setText(mImgurResponse.get("delete"));
            }
        }
    };
    
    //Returns a map that contains objects with the following keys:
    // error - the imgur error message if any (null if no error)
    // delete - the url used to delete the uploaded image (null if error)
    // original - the url to the uploaded image (null if error)
    //The map is null if error
    private Map<String, String> handleSendIntent(Intent intent) {
    	Bundle extras = intent.getExtras();

    	if (Intent.ACTION_SEND.equals(intent.getAction()) &&
    			(extras != null) &&
    			extras.containsKey(Intent.EXTRA_STREAM)) {

    		Uri uri = (Uri)extras.getParcelable(Intent.EXTRA_STREAM);
    	    
    		if (uri != null) {
    			byte[] pictureData = readPictureData(uri);
    		    
        	    if (pictureData != null)
        	    {
        	    	byte[] pictureEncoded = Base64.encodeBase64(pictureData);
        	    	mPayload = new String(pictureEncoded);
        	    	HttpResponse response = uploadImage();
        	    	
        	    	return parseResponse(response);
        	    }
    		}
    	}
    	
    	return null;
    }
    
    //Returns the raw bytes of a picture from an
    //intent uri
    private byte[] readPictureData(Uri uri) {
    	try {
	    	InputStream inputStream = this.getContentResolver().openInputStream(uri);
    	    BufferedInputStream binaryStream = new BufferedInputStream(inputStream);
    	    DataInputStream dataStream = new DataInputStream(binaryStream);
	    	
	        byte[] pictureData = new byte[dataStream.available()];
	        dataStream.read(pictureData);

	        inputStream.close();
	        binaryStream.close();
	        dataStream.close();
	        
	        return pictureData;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
	    } catch (IOException e) {
	        e.printStackTrace();
	    }
	    
	    return null;
    }
    
    //Uploads an image to imgur given the contents of
    //the image in base64.
    private HttpResponse uploadImage() {
    	HttpClient httpClient = new DefaultHttpClient();  
    	HttpPost httpPost = new HttpPost("http://imgur.com/api/upload.xml");  

    	// Change over to display uploadiness
    	encodeHandler.sendEmptyMessage(0);
    	
    	try {  
    		
    		List<NameValuePair> postContent = new ArrayList<NameValuePair>(2);  
    		postContent.add(new BasicNameValuePair("key", API_KEY));  
    		postContent.add(new BasicNameValuePair("image", mPayload));
    		HttpEntity httpEntity = new ImgurEntity(postContent, this);
    		
    		httpPost.setEntity(httpEntity);  
    		httpPost.addHeader("Accept-Encoding", "html/xml");
    		
    		// Don't actually upload if we're in debug mode
    		if(mDebug) {
    			// Pause a little bit...
    			int size = mPayload.length();
    			
    			mTransferred = 0;
    			while(mTransferred < size) {
    				mTransferred = Math.min(size, mTransferred + size / 20);
    				try { Thread.sleep(500); }
    				catch(InterruptedException e) { break; }
    			}
    			
    			return null;
    		}
    		else {
    			return httpClient.execute(httpPost);
    		}
    		
    	} catch (ClientProtocolException e) {  
    		e.printStackTrace();
    	} catch (IOException e) {  
    		e.printStackTrace();
    	}  
    	
    	return null;
    }
    
    //Returns a map that contains objects with the following keys:
    // error - the imgur error message if any (null if no error)
    // delete - the url used to delete the uploaded image (null if error)
    // original - the url to the uploaded image (null if error)
    private Map<String,String> parseResponse(HttpResponse response) {
    	String xmlResponse = null;
    	
    	if(mDebug) {
    		HashMap<String,String> ret = new HashMap<String,String>();
    		ret.put("delete", "{DELETE_URL}");
    		ret.put("original", "{IMAGE_URL}");
    		return ret;
    	}
    	
    	try {
			xmlResponse = EntityUtils.toString(response.getEntity());
		} catch (ParseException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		if (xmlResponse == null) return null;
		
		HashMap<String, String> ret = new HashMap<String, String>();
		ret.put("error", getXMLElementValue(xmlResponse, "error_msg"));
		ret.put("delete", getXMLElementValue(xmlResponse, "delete_page"));
		ret.put("original", getXMLElementValue(xmlResponse, "original_image"));
		
		return ret;
    }
   
    //Returns a string that contains the value between
    // <elemenName></elementName>
    private String getXMLElementValue(String xml, String elementName) {
    	if (xml.indexOf(elementName) >= 0)
    		return xml.substring(xml.indexOf(elementName) + elementName.length() + 1, 
    				xml.lastIndexOf(elementName) - 2);
    	else
    		return null;
    }

	@Override
	public void receiveLong(long num) {
		mTransferred = num;
	}
}