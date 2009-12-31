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
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.Toast;

public class imgur_upload extends Activity {
    private final String API_KEY = "e67bb2d5ceb42e43f8f7fc38e7ca7376";
	
    private ProgressDialog dialogWait;
    private Map<String, String> imgurResponse;
 
    private EditText editURL;
    private EditText editDelete;
    
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
 
        editURL = (EditText)findViewById(R.id.url);
    	editDelete = (EditText)findViewById(R.id.delete);
    
    	setEventHandlers();
    	
        dialogWait = ProgressDialog.show(this, "", 
        		"Uploading image. Please wait...", true); 
        
    	Thread loadWorker = new Thread() {
        	public void run()
        	{   
        		imgurResponse = handleSendIntent(getIntent());
        		handler.sendEmptyMessage(0); 
        	}
    	};
    	
    	loadWorker.start();
    }
    
    private void setEventHandlers() {
        //Clicking url share button displays screen to select how
        //to share the image link
    	findViewById(R.id.shareURL).setOnClickListener(new OnClickListener() {
    		public void onClick(View v) {
    			Intent shareLinkIntent = new Intent(Intent.ACTION_SEND);
                
    			shareLinkIntent.putExtra(Intent.EXTRA_TEXT, editURL.getText().toString());
    			shareLinkIntent.setType("text/plain");
    			
    			imgur_upload.this.startActivity(
    					Intent.createChooser(shareLinkIntent, "Share via"));
    		}
        });
    	
        //Clicking delete share button displays screen to select how
        //to share the delete link
    	findViewById(R.id.shareDelete).setOnClickListener(new OnClickListener() {
    		public void onClick(View v) {
    			Intent shareLinkIntent = new Intent(Intent.ACTION_SEND);
                
    			shareLinkIntent.putExtra(Intent.EXTRA_TEXT, editDelete.getText().toString());
    			shareLinkIntent.setType("text/plain");
    			
    			imgur_upload.this.startActivity(
    					Intent.createChooser(shareLinkIntent, "Share via"));
    		}
        });
    }
    
    private Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            dialogWait.dismiss();
            
            if (imgurResponse == null) {
            	Toast.makeText(imgur_upload.this, "Connection issue", Toast.LENGTH_SHORT).show();
            } else if (imgurResponse.get("error") != null) {
            	Toast.makeText(imgur_upload.this, imgurResponse.get("error"), Toast.LENGTH_SHORT).show();
            } else {
            	editURL.setText(imgurResponse.get("original"));
            	editDelete.setText(imgurResponse.get("delete"));
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
        	    	HttpResponse response = uploadImage(new String(pictureEncoded));
        	    	
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
    		List<NameValuePair> postContent = new ArrayList<NameValuePair>(2);  
    		postContent.add(new BasicNameValuePair("key", API_KEY));  
    		postContent.add(new BasicNameValuePair("image", pictureBase64));  
    		httpPost.setEntity(new UrlEncodedFormEntity(postContent));  
    		httpPost.addHeader("Accept-Encoding", "html/xml");
    		
    		return httpClient.execute(httpPost);  
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