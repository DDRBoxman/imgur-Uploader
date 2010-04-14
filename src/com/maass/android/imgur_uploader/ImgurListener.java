package com.maass.android.imgur_uploader;

/**
 * This interface is so we don't have to explicitly make
 * ImgurEntity refer to ImgurUpload, rather, we can keep
 * the good ol' Java spirit and have abstractions for it.
 * @author pkilgo
 *
 */

public interface ImgurListener {
	public void receiveLong(long num);
}
