package com.artifex.mupdflib;

import java.io.*;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MD5 {

	public static boolean checkMD5(String md5, File updateFile)
			throws IOException {
		if (md5 == null || md5 == "" || updateFile == null) {
			return false;
		}

		String calculatedDigest = calculateMD5(updateFile);

		if (calculatedDigest == null) {
			return false;
		}
		return calculatedDigest.equalsIgnoreCase(md5);
	}

	public static String calculateMD5(File updateFile) {
		MessageDigest digest = null;
		try {
			digest = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			return null;
		}
		InputStream is = null;
		try {
			is = new FileInputStream(updateFile);
		} catch (FileNotFoundException e) {
			return null;
		}
		byte[] buffer = new byte[8192];
		int read = 0;
		try {
			while ((read = is.read(buffer)) > 0) {
				digest.update(buffer, 0, read);
			}
			byte[] md5sum = digest.digest();
			BigInteger bigInt = new BigInteger(1, md5sum);
			String output = bigInt.toString(16);
			// Fill to 32 chars
			output = String.format("%32s", output).replace(' ', '0');
			return output;
		} catch (IOException e) {
			throw new RuntimeException("Unable to process file for MD5", e);
		} finally {
			try {
				is.close();
			} catch (IOException e) {
				throw new RuntimeException(
						"Unable to close input stream for MD5 calculation", e);
			}
		}
	}

	public static String MD5Hash(String s) {
		MessageDigest m = null;

		try {
			m = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			return "";
		}

		m.update(s.getBytes(), 0, s.length());
		String hash = new BigInteger(1, m.digest()).toString(16);
		return hash;
	}
}
