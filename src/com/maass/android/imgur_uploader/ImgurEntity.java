package com.maass.android.imgur_uploader;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;

/**
 * An extension to UrlEncodedFormEntity so that we can
 * count the number of bytes written to the OutputStream
 * and do stuff like track its progress.
 * @author pkilgo
 *
 */
public class ImgurEntity extends UrlEncodedFormEntity {
	private ImgurListener mCallback;
	
	public ImgurEntity(List<? extends NameValuePair> parameters, ImgurListener listener) throws UnsupportedEncodingException {
		super(parameters);
		mCallback = listener;
	}
	
	/**
	 * Overridden method to force writing via our CountingOutputStream.
	 */
	public void writeTo(final OutputStream out) throws IOException {
		super.writeTo(new CountingOutputStream(out, mCallback));
	}
	
	/**
	 * Extension of FilterOutputStream.
	 * This class counts the number of bytes
	 * to be written and keeps track, finally writing to 
	 * the OutputStream specified, and notifies the callback
	 * that something has changed.
	 * @author pkilgo
	 *
	 */
    public static class CountingOutputStream extends FilterOutputStream {
        private long mTransferred;
        private ImgurListener mListener;

        public CountingOutputStream(final OutputStream out, final ImgurListener listener) {
            super(out);
            mTransferred = 0;
            mListener = listener;
        }

        public void write(byte[] b, int off, int len) throws IOException {
            super.write(b, off, len);
            mTransferred += len;
            sendTotal(len);
        }

        public void write(int b) throws IOException {
            super.write(b);
            mTransferred++;
            sendTotal(1);
        }
        
        private void sendTotal(int len) {
            // Tell the listener we write some bytes and the total
           mListener.receiveLong(mTransferred);
        }
    }
}
