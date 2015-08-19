package com.github.nodejs.stream;

import com.github.nkzawa.emitter.Emitter;
import com.github.nodejs.vo.StreamOptions;

public class Duplex extends Emitter implements IPipable {

	public Writable writeStream;
	public Readable readStream;
	
	public Duplex() {}
	
	public void setRead(Readable read) {
		readStream = read;
	}
	
	public void setWrite(Writable write) {
		writeStream = write;
	}

	@Override
	public IPipable pipe(Writable dest, StreamOptions options) {
		// TODO Auto-generated method stub
		return readStream.pipe(dest, options);
	}
	
}
