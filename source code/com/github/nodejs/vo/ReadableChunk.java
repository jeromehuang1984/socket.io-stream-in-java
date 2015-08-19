package com.github.nodejs.vo;

import java.util.ArrayList;

import android.util.Base64;

public class ReadableChunk {
	
	public Buffer bytes = null;
	public String chars = null;
	
	public ReadableChunk() {}
	
	public ReadableChunk(Buffer buffer) {
		bytes = buffer;
		chars = null;
	}
	
	public ReadableChunk(String str) {
		bytes = null;
		chars = str;
	}
	
	public int length() {
		if (bytes != null) {
			return bytes.length();
		}
		if (chars != null) {
			return chars.length();
		}
		return 0;
	}
	
	public static String concatStr(ArrayList<ReadableChunk> lst) {
		String ret = "";
		if (lst != null && lst.size() > 0) {
			for (int i = 0; i < lst.size(); i++) {
				ReadableChunk item = lst.get(0);
				if (item.chars != null) {
					ret += item.chars;
				}
			}
		}
		return ret;
	}
	
	public static Buffer concatBytes(ArrayList<ReadableChunk> lst) {
		ArrayList<Buffer> bufLst = new ArrayList<Buffer>();
		int len = 0;
		if (lst != null && lst.size() > 0) {
			for (int i = 0; i < lst.size(); i++) {
				ReadableChunk item = lst.get(0);
				if (item.bytes != null) {
					bufLst.add(item.bytes);
					len += item.bytes.length();
				}
			}
		}
		return Buffer.concat(bufLst, len);
	}
	
	public static ReadableChunk concat(ArrayList<ReadableChunk> lst) {
		if (lst != null && lst.size() > 0) {
			ReadableChunk firstItem = lst.get(0);
			if (firstItem.chars != null) {
				return new ReadableChunk(concatStr(lst));
			} else if (firstItem.bytes != null) {
				return new ReadableChunk(concatBytes(lst));
			}
		}
		return null;
	}
	
	public ReadableChunk slice(int... args) {
		if (bytes != null) {
			return new ReadableChunk(bytes.slice(args));
		} else if (chars != null) {
			int[] arguments = Buffer.formatSliceArgs(chars.length(), args);
			int start = arguments[0];
			int end = arguments[1];
			if (start >= end) {
				return new ReadableChunk("");
			}
			return new ReadableChunk(chars.substring(start, end));
		}
		return new ReadableChunk();
	}
	
	public static boolean isEmpty(ReadableChunk chunk) {
		if (chunk == null) {
			return true;
		}
		if (chunk.bytes != null) {
			return chunk.bytes.length() == 0;
		} else if (chunk.chars != null) {
			return chunk.chars.length() == 0;
		}
		return true;
	}
	
	public String toString(String charset) {
		if (bytes != null) {
			return bytes.toString(charset);
		}
		return chars;
	}
	
	public String encodeBytesToBase64Str() {
		String base64Str = Base64.encodeToString(bytes.contents, Base64.DEFAULT);
		return base64Str;
	}
	
	public void decodeBase64StrToBytes() {
		if (chars != null && chars.length() > 0) {
			byte[] decoded = Base64.decode(chars, Base64.DEFAULT);
			bytes = new Buffer(decoded, "base64");
			chars = null;
		}
	}
}
