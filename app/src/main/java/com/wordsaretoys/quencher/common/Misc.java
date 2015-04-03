package com.wordsaretoys.quencher.common;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.security.auth.x500.X500Principal;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;

public class Misc {

	// debug cert 
	private static final X500Principal DEBUG_DN = 
			new X500Principal("CN=Android Debug,O=Android,C=US");
	
	/**
	 * reads a text file from the assets
	 * @param context activity context
	 * @param fileName name of text file
	 * @return text of file
	 */
	public static String getTextAsset(Context context, String fileName) {
		String text = "";
		try {
			InputStream stream = context.getAssets().open(fileName);
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
			String line;
				while( (line = reader.readLine()) != null) {
					text += line + "\n";
				}
		} catch(IOException e) {
			e.printStackTrace();
		}
		return text;
	}

	/**
	 * get debug cert status of app
	 * @param ctx context
	 * @return true if app built with debug cert
	 */
	public static boolean isDebuggable(Context ctx)
	{
	    boolean debuggable = false;

	    try
	    {
	        PackageInfo pinfo = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(),PackageManager.GET_SIGNATURES);
	        Signature signatures[] = pinfo.signatures;

	        CertificateFactory cf = CertificateFactory.getInstance("X.509");

	        for ( int i = 0; i < signatures.length;i++)
	        {   
	            ByteArrayInputStream stream = new ByteArrayInputStream(signatures[i].toByteArray());
	            X509Certificate cert = (X509Certificate) cf.generateCertificate(stream);       
	            debuggable = cert.getSubjectX500Principal().equals(DEBUG_DN);
	            if (debuggable)
	                break;
	        }
	    }
	    catch (NameNotFoundException e)
	    {
	        //debuggable variable will remain false
	    }
	    catch (CertificateException e)
	    {
	        //debuggable variable will remain false
	    }
	    return debuggable;
	}	
}
