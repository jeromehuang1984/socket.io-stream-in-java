package com.github.nodejs.vo;

import java.nio.charset.Charset;
import java.util.ArrayList;

public class Buffer {

	public byte[] contents;
	private String charsetName = "utf8";
	public int used = 0;
	
	public Buffer(int n) {
		contents = new byte[n];
	}
	
	public Buffer(String data) {
		this(data, "utf8");
	}
	
	public Buffer(byte[] bytes, String encoding) {
		contents = bytes;
		charsetName = encoding;
	}
	
	public Buffer(String data, String encoding) {
		charsetName = encoding;
		Charset charset = Charset.forName(charsetName);
		contents = data.getBytes(charset);
	}
	
	public int length() {
		return contents.length;
	}
	
	public static Buffer concat(ArrayList<Buffer> lst) {
		return concat(lst, -1);
	}
	
	public static Buffer concat(ArrayList<Buffer> lst, int len) {
		if (lst.size() == 0) {
			return new Buffer(0);
		} else if (lst.size() == 1) {
			return lst.get(0);
		}
		if (len < 0) {
			len = 0;
			for (int i = 0; i < lst.size(); i++) {
				len += lst.get(i).length();
			}
		}
		
		Buffer retBuffer = new Buffer(len);
		int pos = 0;
		for (int i = 0; i < lst.size(); i++) {
			Buffer buf = lst.get(i);
			buf.copy(retBuffer, pos);
			pos += buf.length();
		}
		return retBuffer;
	}
	
	public void copy(Buffer targetBuffer, int targetStart) {
		copy(targetBuffer, targetStart, 0, contents.length);
	}
	
	/**
	 * @param targetBuffer
	 * @param targetStart
	 * @param srcStart
	 * @param srcEnd
	 */
	public void copy(Buffer targetBuffer, int targetStart, int srcStart, int srcEnd) {
		int len = srcEnd - srcStart;
		System.arraycopy(contents, srcStart, targetBuffer.contents, targetStart, len);
	}
	
	/**
	 * var a = [1,2,3,4]; a.slice(1,2) => [2] <br/>
	 * var a = [1,2,3,4]; a.slice(1,3) => [2, 3] <br/>
	 * var a = [1,2,3,4]; a.slice(1,5) => [2, 3, 4] <br/>
	 * var a = [1,2,3,4]; a.slice(1,-1) => [2, 3] <br/>
	 * var a = [1,2,3,4]; a.slice(1,-2) => [2] <br/>
	 * var a = [1,2,3,4]; a.slice(1,-3) => [] <br/>
	 * var a = [1,2,3,4]; a.slice(1,-4) => [] <br/>
	 * var a = [1,2,3,4]; a.slice(1) => [2, 3, 4] <br/>
	 * var a = [1,2,3,4]; a.slice(6) => []
	 */
	
	public Buffer slice(int... args) {
		int[] arguments = formatSliceArgs(length(), args);
		int start = arguments[0];
		int end = arguments[1];
		
		int retLen = end - start;
		if (retLen <= 0) {
			return new Buffer(0);
		} else {
			Buffer retBuffer = new Buffer(retLen);
			copy(retBuffer, 0, start, end);
			return retBuffer;
		}
	}
	
	public static int[] formatSliceArgs(int srcLen, int... args) {
		int start = args[0];
		int end = 0;
		if (args.length > 1) {
			end = args[1];
		} else {
			end = srcLen;
		}
		
		start = start > srcLen ? srcLen : start;
		end = end > srcLen ? srcLen : end;
		if (start < 0) {
			start += srcLen;
			start = start < 0 ? 0 : start;
		}
		if (end < 0) {
			end += srcLen;
			end = end < 0 ? 0 : end;
		}
		int[] ret = {start, end};
		return ret;
	}
	
	public static boolean isEncoding(String encoding) {
		switch (encoding.toLowerCase()) {
		case "hex":
		case "utf8":
		case "utf-8":
		case "ascii":
		case "binary":
		case "base64":
		case "ucs2":
		case "ucs-2":
		case "utf16le":
		case "utf-16le":
		case "raw":
			return true;

		default:
			return false;
		}
	}
	
	public byte getByte(int pos) {
		return contents[pos];
	}
	
	
	public String toString() {
		return new String(contents, Charset.forName(charsetName));
	}
	
	public String toString(String charsetName) {
		return new String(contents, Charset.forName(charsetName));
	}
	
	public String toString(String encoding, int start, int end) {
		Buffer buf = slice(start, end);
		return buf.toString(encoding);
	}
	
	public String getCharsetName() {
		return charsetName;
	}
}
