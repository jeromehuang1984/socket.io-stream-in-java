package com.github.nodejs.stream;

import java.util.ArrayList;

import android.util.Log;

import com.github.nkzawa.socketio.client.Ack;
import com.github.nodejs.socketio_stream.StreamSocket;
import com.github.nodejs.vo.ReadableChunk;
import com.github.nodejs.vo.SocketError;
import com.github.nodejs.vo.StreamOptions;

public class IOStream extends Duplex {
	
	public String id;
	public StreamSocket socket;
	public ArrayList<ReadReq> pushBuffer = new ArrayList<ReadReq>();
	public ArrayList<WriteReq> writeBuffer = new ArrayList<WriteReq>();
	private boolean _readable = false;
	private boolean _writable = false;
	public boolean destroyed = false;
	// default to *not* allowing half open sockets
	public boolean allowHalfOpen = false;
	
	public IOStream() {
		this(new StreamOptions());
	}
	public IOStream(StreamOptions option) {
		if (option != null) {
			allowHalfOpen = option.allowHalfOpen;
		}
		initDuplex(option);
		writeStream.on("finish", _onfinish);
		readStream.on("end", _onend);
		writeStream.on("error", _onerror);
		readStream.on("error", _onerror);
	}
	
	private void initDuplex(StreamOptions option) {
		setRead(new Readable(option) {
			
			//Local read
			@Override
			protected void _read(int size) {
				// TODO Auto-generated method stub
				if (destroyed) return;
				if (pushBuffer.size() > 0) {
					// flush buffer and end if it exists.
					ReadReq req = pushBuffer.remove(0);
					while (true) {
						if (!req.push()) {
							break;
						}
						if (pushBuffer.size() > 0) {
							req = pushBuffer.remove(0);
						} else {
							break;
						}
					}
					return;
				}
				_readable = true;
				// Go get data from remote stream
				// Calls
				// ._onread remotely
				// then
				// ._onwrite locally
				socket._read(id, size);
			}
		});
		
		setWrite(new Writable(option) {
			//Write local data to remote stream
			@Override
			public void _write(ReadableChunk chunk, String encoding, Ack cb) {
				if (destroyed) {
					return;
				}
				WriteReq req = new WriteReq(chunk, encoding, cb);
				if (_writable) {
					req.write();
				} else {
					writeBuffer.add(req);
				}
			}
		});
	}
	
	//Read from remote stream
	public Listener _onread = new Listener() {
		
		@Override
		public void call(Object... arg0) {
			if (writeBuffer.size() > 0) {
				WriteReq writeReq = writeBuffer.remove(0);
				if (writeReq != null) {
					writeReq.write();
				}
//				_writable = true;
			}
		}
	};
	
	//Write the data fetched remotely so that we can now read locally
	public Listener _onwrite = new Listener() {
		
		@Override
		public void call(Object... args) {
			ReadableChunk chunk = args.length > 0 ? (ReadableChunk) args[0] : null;
			String encoding = args.length > 1 ? (String) args[1] : null;
			Ack cb = args.length > 2 ? (Ack) args[2] : null;
			ReadReq readReq = new ReadReq(chunk, encoding, cb);
			if (_readable) {
				readReq.push();
			} else {
				pushBuffer.add(readReq);
			}
		}
	};
	
	//When ending send 'end' event to remote stream
	public void _end() {
		if (pushBuffer.size() > 0) {
			pushBuffer.add(new ReadReq(null, null, new Ack() {
				@Override
				public void call(Object... args) {
					_done();
				}
			}));
		} else {
			_done();
		}
	}
	
	//Remote stream just ended
	private boolean _done() {
		_readable = false;
		// signal the end of the data.
		return readStream.push(null, null);
	}
	
	private Listener _onfinish = new Listener() {
		
		@Override
		public void call(Object... arg0) {
			if (socket != null) {
				socket._end(id);
			}
			writeStream.writable = false;
			writeStream.getState().ended = true;
			
			if (!readStream.readable || readStream.getState().ended) {
				destroy();
				return;
			}
			if (!allowHalfOpen) {
				readStream.push(null, null);
				// just in case we're waiting for an EOF.
				if (readStream.readable && !readStream.getState().endEmitted) {
					readStream.read(0);
				}
			}
		}
	};
	
	private Listener _onend = new Listener() {
		
		@Override
		public void call(Object... arg0) {
			readStream.readable = false;
			readStream.getState().ended = true;
			if (!writeStream.writable || writeStream.getState().finished) {
				destroy();
				return;
			}
			if (!allowHalfOpen) {
				writeStream.end();
			}
		}
	};
	
	private Listener _onerror = new Listener() {
		
		@Override
		public void call(Object... args) {
			// check if the error came from remote stream.
			Object obj = args.length > 0 ? args[0] : null;
			if (obj != null && obj instanceof SocketError) {
				SocketError err = (SocketError) obj;
				if (err != null && !err.remote && socket != null) {
					// notify the error to the corresponding remote stream.
					socket._error(id, err);
				}
			}
			destroy();
		}
	};
	
	public void destroy() {
		if (destroyed) {
			return;
		}
		readStream.readable = writeStream.writable = false;
		if (socket != null) {
			socket.cleanup(id);
			socket = null;
		}
		destroyed = true;
	}
	
	private class WriteReq {
		public ReadableChunk chunk;
		public String encoding;
		public Ack cb;
		
		public WriteReq(ReadableChunk data, String charset, Ack callback) {
			chunk = data;
			encoding = charset;
			cb = callback;
		}
		
		public void write() {
			if (destroyed) {
				return;
			}
			_writable = false;
			socket._write(id, chunk, cb);
			_writable = true;
		}
	}
	
	private class ReadReq {
		public ReadableChunk chunk;
		public String encoding;
		public Ack cb;
		
		public ReadReq(ReadableChunk data, String charset, Ack callback) {
			chunk = data;
			encoding = charset;
			cb = callback;
		}
		
		public boolean push() {
			_readable = false;
			boolean ret = readStream.push(ReadableChunk.isEmpty(chunk) ? 
					new ReadableChunk("") : chunk, encoding);
			cb.call();
			return ret;
		}
	}
	
}
