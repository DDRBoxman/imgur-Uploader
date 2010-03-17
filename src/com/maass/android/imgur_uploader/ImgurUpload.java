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

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.Toast;

public class ImgurUpload extends Activity {
    private final static String API_KEY = "e67bb2d5ceb42e43f8f7fc38e7ca7376";
	
    private ProgressDialog mDialogWait;
    private Map<String, String> mImgurResponse;
 
    private EditText mEditURL;
    private EditText mEditDelete;
    
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
        
        mDialogWait = ProgressDialog.show(this, "", 
        		"Uploading image. Please wait...", true); 
        
    	Thread loadWorker = new Thread() {
        	public void run()
        	{   
        		mImgurResponse = handleSendIntent(getIntent());
        		handler.sendEmptyMessage(0); 
        	}
    	};
    	
    	loadWorker.start();
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
    			
    			ImgurUpload.this.startActivity(
    					Intent.createChooser(shareLinkIntent, "Share via"));
    		}
        });
    }
    
    private Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            mDialogWait.dismiss();
            
            if (mImgurResponse == null) {
            	Toast.makeText(ImgurUpload.this, "Connection issue", Toast.LENGTH_SHORT).show();
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
    			Log.d(this.getClass().getName(), "FLAG A1");
    			byte[] pictureData = readPictureData(uri);
    		    uri = null;
        	    if (pictureData != null)
        	    {
        	    	Log.d(this.getClass().getName(), "FLAG A2");
        	    	byte[] pictureEncoded = Base64.encodeBase64(pictureData);
        	    	pictureData = null;
        	    	Log.d(this.getClass().getName(), "FLAG A3");
        	    	String pictureDataString = new String(pictureEncoded);
        	    	pictureEncoded = null;
        	    	Log.d(this.getClass().getName(), "FLAG A4");
        	    	HttpResponse response = uploadImage(pictureDataString);
        	    	
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
    private HttpResponse uploadImage(String pictureBase64) {
    	HttpClient httpClient = new DefaultHttpClient();  
    	HttpPost httpPost = new HttpPost("http://imgur.com/api/upload.xml");  

    	try {
    		Log.d(this.getClass().getName(), "FLAG B1");
    		List<NameValuePair> postContent = new ArrayList<NameValuePair>(2);  
    		postContent.add(new BasicNameValuePair("key", API_KEY));  
    		postContent.add(new BasicNameValuePair("image", pictureBase64));  
    		httpPost.setEntity(new UrlEncodedFormEntity(postContent));  
    		httpPost.addHeader("Accept-Encoding", "html/xml");
    		Log.d(this.getClass().getName(), "FLAG B2");
    		return httpClient.execute(httpPost);  
    	} catch (ClientProtocolException e) {  
    		Log.d(this.getClass().getName(), "FLAG Bx", e);
    	} catch (IOException e) {  
    		Log.d(this.getClass().getName(), "FLAG Bx", e);
    	}  
    	
    	return null;
    }
    
    //Returns a map that contains objects with the following keys:
    // error - the imgur error message if any (null if no error)
    // delete - the url used to delete the uploaded image (null if error)
    // original - the url to the uploaded image (null if error)
    private Map<String,String> parseResponse(HttpResponse response) {
    	String xmlResponse = null;
    	
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
}