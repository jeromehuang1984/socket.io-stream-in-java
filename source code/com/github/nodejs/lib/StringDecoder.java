package com.github.nodejs.lib;


import com.github.nodejs.vo.Buffer;

public class StringDecoder {

	// Enough space to store all bytes of a single character. UTF-8 needs 4
	// bytes, but CESU-8 may require up to 6 (3 bytes per surrogate).
	private Buffer charBuffer = new Buffer(6);
	// Number of bytes received for the current incomplete multi-byte character.
	private int charReceived = 0;
	// Number of bytes expected for the current incomplete multi-byte character.
	private int charLength = 0;
	private int surrogateSize;
	private String encoding = "";
	private IWrite writeMethod;
	private IDetect detectMethod;

	private void assertEncoding(String encoding) {
		if (encoding != null && !Buffer.isEncoding(encoding)) {
			throw new Error("Unknown encoding: " + encoding);
		}
	}

	public StringDecoder(String encode) {
		encoding = encode != null ? encode : "utf8";
		encoding = encoding.toLowerCase().replaceAll("[-_]", "");
		assertEncoding(encoding);
		writeMethod = defaultWrite;
		detectMethod = defaultDetect;
		switch (this.encoding) {
		case "utf8":
			// CESU-8 represents each of Surrogate Pair by 3-bytes
			surrogateSize = 3;
			break;
		case "ucs2":
		case "utf16le":
			// UTF-16 represents each of Surrogate Pair by 2-bytes
			surrogateSize = 2;
			detectMethod = utf16DetectIncompleteChar;
			break;
		case "base64":
			// Base-64 stores 3 bytes in 4 chars, and pads the remainder.
			surrogateSize = 3;
			detectMethod = base64DetectIncompleteChar;
			break;
		default:
			writeMethod = passThroughWrite;
			return;
		}
	}

	private interface IWrite {
		public String write(Buffer buffer);
	}

	private interface IDetect {
		public void detect(Buffer buffer);
	}

	private IWrite defaultWrite = new IWrite() {
		@Override
		public String write(Buffer buffer) {
			String charStr = "";
			// if our last write ended with an incomplete multibyte character
			while (charLength > 0) {
				// determine how many remaining bytes this buffer has to offer
				// for this char
				int available = (buffer.length() >= charLength - charReceived) ? charLength
						- charReceived
						: buffer.length();

				// add the new bytes to the char buffer
				buffer.copy(charBuffer, charReceived, 0, available);
				charReceived += available;

				if (charReceived < charLength) {
					// still not enough chars in this buffer? wait for more ...
					return "";
				}

				// remove bytes belonging to the current character from the
				// buffer
				buffer = buffer.slice(available, buffer.length());

				// get the character that was split
				charStr = charBuffer.slice(0, charLength).toString(encoding);

				// CESU-8: lead surrogate (D800-DBFF) is also the incomplete
				// character
				int charCode = charStr.charAt(charStr.length() - 1);
				if (charCode >= 0xD800 && charCode <= 0xDBFF) {
					charLength += surrogateSize;
					charStr = "";
					continue;
				}
				charReceived = charLength = 0;

				// if there are no more bytes in this buffer, just emit our char
				if (buffer.length() == 0) {
					return charStr;
				}
				break;
			}
			// determine and set charLength / charReceived
			detectMethod.detect(buffer);
			int end = buffer.length();
			if (charLength > 0) {
				// buffer the incomplete character bytes we got
				buffer.copy(charBuffer, 0, buffer.length() - charReceived, end);
				end -= charReceived;
			}
			charStr += buffer.toString(encoding, 0, end);
			int lastCharCode = charStr.charAt(charStr.length() - 1);
			// CESU-8: lead surrogate (D800-DBFF) is also the incomplete
			// character
			if (lastCharCode >= 0xd800 && lastCharCode <= 0xdbff) {
				charLength += surrogateSize;
				charReceived += surrogateSize;
				charBuffer.copy(charBuffer, surrogateSize, 0, surrogateSize);
				buffer.copy(charBuffer, 0, 0, surrogateSize);
				return charStr.substring(0, end);
			}
			return charStr;
		}
	};

	// detectIncompleteChar determines if there is an incomplete UTF-8 character
	// at
	// the end of the given buffer. If so, it sets this.charLength to the byte
	// length that character, and sets this.charReceived to the number of bytes
	// that are available for this character.
	private IDetect defaultDetect = new IDetect() {
		@Override
		public void detect(Buffer buffer) {
			// determine how many bytes we have to check at the end of this
			// buffer
			int i = (buffer.length() >= 3) ? 3 : buffer.length();

			// Figure out if one of the last i bytes of our buffer announces an
			// incomplete char.
			for (; i > 0; i--) {
				byte c = buffer.getByte(buffer.length() - i);

				// See http://en.wikipedia.org/wiki/UTF-8#Description

				// 110XXXXX
				if (i == 1 && c >> 5 == 0x06) {
					charLength = 2;
					break;
				}

				// 1110XXXX
				if (i <= 2 && c >> 4 == 0x0E) {
					charLength = 3;
					break;
				}

				// 11110XXX
				if (i <= 3 && c >> 3 == 0x1E) {
					charLength = 4;
					break;
				}
			}
			charReceived = i;
		}
	};

	/**
	 * @param buffer
	 * @return a decoded string from buffer
	 */
	public String write(Buffer buffer) {
		return writeMethod.write(buffer);
	}

	/**
	 * @return any trailing bytes that were left in the buffer.
	 */
	public String end() {
		return end(null);
	}

	public String end(Buffer buffer) {
		String res = "";
		if (buffer != null && buffer.length() > 0) {
			res = writeMethod.write(buffer);
		}
		if (charReceived > 0) {
			res += charBuffer.slice(0, charReceived).toString(encoding);
		}
		return res;
	}

	private IWrite passThroughWrite = new IWrite() {
		@Override
		public String write(Buffer buffer) {
			return buffer.toString(encoding);
		}
	};

	private IDetect utf16DetectIncompleteChar = new IDetect() {
		@Override
		public void detect(Buffer buffer) {
			charReceived = buffer.length() % 2;
			charLength = charReceived > 0 ? 2 : 0;
		}
	};

	private IDetect base64DetectIncompleteChar = new IDetect() {
		@Override
		public void detect(Buffer buffer) {
			charReceived = buffer.length() % 3;
			charLength = charReceived > 0 ? 3 : 0;
		}
	};
}
