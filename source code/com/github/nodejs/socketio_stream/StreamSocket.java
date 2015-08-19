package com.github.nodejs.socketio_stream;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.json.JSONArray;
import org.json.JSONException;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.Ack;
import com.github.nkzawa.socketio.client.Socket;
import com.github.nodejs.stream.IOStream;
import com.github.nodejs.vo.ReadableChunk;
import com.github.nodejs.vo.SocketError;

public class StreamSocket extends Emitter {

	private Socket sio;
	private String uuid;
	private ConcurrentMap<String, IOStream> streams = new ConcurrentHashMap<String, IOStream>();
	private static final String eventName = "stream";
	String[] exportEvents = new String[]{"error", "upload"};
	
	//Bidirectional stream socket which wraps Socket.IO.
	public StreamSocket(Socket socket) {
		uuid = UUID.randomUUID().toString();
		sio = socket;
		sio.on(eventName + "-read", _onread);
		sio.on(eventName + "-write", _onwrite);
		sio.on(eventName + "-end", _onend);
		sio.on(eventName + "-error", _onerror);
		sio.on("disconnect", _ondisconnect);
		sio.on("error", new Listener() {
			@Override
			public void call(Object... arg0) {
				emit("error", "error from StreamSocket");
			}
		});
		//sio.on(eventName, emit.bind(this));
		sio.on(eventName, new Listener() {
			@Override
			public void call(Object... args) {
				if (args.length > 0 && args[0] instanceof String) {
					String ssEventName = (String) args[0];
					if (ssEventName != null) {
						JSONArray pos = (JSONArray) args[1];
						Object[] arguments = new Object[args.length - 2];
						for (int i = 0; i < args.length - 2; i++) {
							arguments[i] = args[i + 2];
						}
						int posIndex = 0;
						try {
							posIndex = pos.getInt(0);
						} catch (JSONException e) {
							e.printStackTrace();
						}
						String streamId = (String) arguments[posIndex];
						if (streams.get(streamId) != null) {
							_error(streamId, new SocketError("id already exists"));
						}
						IOStream stream = new IOStream();
						stream.id = streamId;
						stream.socket = StreamSocket.this;
						streams.put(streamId, stream);
						arguments[posIndex] = stream;
						
						StreamSocket.this.emit(ssEventName, arguments);
					}
				}
			}
		});
	}
	
	public void _end(String streamId) {
		sio.emit(eventName + "-end", streamId);
	}
	
	public void _error(String streamId, SocketError err) {
		sio.emit(eventName + "-error", streamId, err);
	}
	
	public void cleanup(String streamId)  {
		streams.remove(streamId);
	}
	
	//Notifies the read event.
	public void _read(String streamId, int size) {
		sio.emit(eventName + "-read", streamId, size);
	}
	
	//Requests to write a chunk.
	public void _write(String streamId, ReadableChunk chunk, Ack cb) {
		sio.emit(eventName + "-write", streamId, chunk.encodeBytesToBase64Str(), "base64", cb);
	}
	
	private Listener _onread = new Listener() {
		@Override
		public void call(Object... args) {
			String streamId = args.length > 0 ? (String) args[0] : null;
//			Log.d("jerome", "stream socket, _onread, streamId:" + streamId);
			int size = args.length > 1 ? (int) args[1] : 0;
			if (streamId == null) {
				return;
			}
			IOStream stream = streams.get(streamId);
			if (stream != null) {
				stream._onread.call(size);
			} else {
				_error(streamId, new SocketError("invalid stream id"));
			}
		}
	};
	
	private Listener _onwrite = new Listener() {
		@Override
		public void call(Object... args) {
			String streamId = args.length > 0 ? (String) args[0] : null;
			ReadableChunk chunk = null;
			if (args.length > 1) {
				if (args[1] instanceof ReadableChunk) {
					chunk = (ReadableChunk) args[1];
				} else if (args[1] instanceof String) {
					chunk = new ReadableChunk((String) args[1]);
				}
			}
			String encoding = args.length > 2 ? (String) args[2] : null;
			Ack cb = args.length > 3 ? (Ack) args[3] : null;
			if (streamId == null) {
				return;
			}
			IOStream stream = streams.get(streamId);
			if (stream != null) {
				if ("base64".equals(encoding)) {
					chunk.decodeBase64StrToBytes();
				}
				stream._onwrite.call(chunk, encoding, cb);
			} else {
				_error(streamId, new SocketError("invalid stream id"));
			}
		}
	};
	
	private Listener _onend = new Listener() {
		@Override
		public void call(Object... args) {
			String streamId = args.length > 0 ? (String) args[0] : null;
			if (streamId == null) {
				return;
			}
			IOStream stream = streams.get(streamId);
			if (stream != null) {
				stream._end();
			} else {
				_error(streamId, new SocketError("invalid stream id"));
			}
		}
	};
	
	private Listener _onerror = new Listener() {
		@Override
		public void call(Object... args) {
			String streamId = args.length > 0 ? (String) args[0] : null;
			String msg = args.length > 1 ? (String) args[1] : null;
			if (streamId == null) {
				return;
			}
			IOStream stream = streams.get(streamId);
			if (stream != null) {
				SocketError err = new SocketError(msg);
				err.remote = true;
				stream.emit("error", err);
			} else {
				System.out.println("invalid socket stream id: " + streamId);
			}
		}
	};
	
	private Listener _ondisconnect = new Listener() {
		@Override
		public void call(Object... arg0) {
			Iterator<Entry<String, IOStream>> iter = streams.entrySet().iterator();
			while (iter.hasNext()) {
				Map.Entry entry = (Map.Entry) iter.next();
				IOStream stream = (IOStream) entry.getValue();
				stream.destroy();
				// Close streams when the underlaying
				// socket.io connection is closed (regardless why)
				stream.emit("close");
				stream.emit("error", new Error("Connection aborted"));
			}
		}
	};
	
	@Override
	public Emitter emit(String event, Object... args) {
		for (int i = 0; i < exportEvents.length; i++) {
			if (exportEvents[i].equals(event)) {
				return super.emit(event, args);
			}
		}
		_stream(event, args);
		return this;
	}
	
	//Sends a new stream request.
	private void _stream(String event, Object... args) {
		int type = 0;
		try {
			type = Integer.parseInt(event);
		} catch (NumberFormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
		int index = 0;
//		ArrayList<Integer> pos = new ArrayList<Integer>();
		JSONArray pos = new JSONArray();
		Object[] arguments = new Object[args.length + 2];
		arguments[0] = type;
		for (Object obj : args) {
			arguments[index + 2] = obj;
			if (obj instanceof IOStream) {
				pos.put(index);
				IOStream stream = (IOStream) obj;
				stream.id = UUID.randomUUID().toString();
				stream.socket = this;
				arguments[index + 2] = stream.id;
				streams.put(stream.id, stream);
			}
			index++;
		}
		arguments[1] = pos;
//		Log.d("jerome", "Sends a new stream request, arguments:" + arguments[0] + "," + arguments[1]+"," + arguments[2]+"," + arguments[3]);
		sio.emit(eventName, arguments);
	}
	
}
