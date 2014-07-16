package com.artifex.mupdflib;


public abstract class CallbackApplication {

	public interface MuPDFCallbackInterface {
		public void callbackMethod(String str);
	}
	
	public final static class MuPDFCallbackClass{
		private static String gaiStr = "";
		public static MuPDFCallbackInterface callback;
	    public MuPDFCallbackClass(){}
		static void sendGaiView(String str) {
			if (callback!=null) {
				gaiStr = str;
				callback.callbackMethod(gaiStr);
			}
		}
	}
	

}

