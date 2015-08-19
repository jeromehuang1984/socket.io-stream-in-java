package com.github.nodejs.stream;

import java.util.ArrayList;

import android.util.Log;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.Ack;
import com.github.nkzawa.thread.EventThread;
import com.github.nodejs.vo.Buffer;
import com.github.nodejs.vo.ReadableChunk;
import com.github.nodejs.vo.StreamOptions;

public abstract class Writable extends Emitter implements IPipable {

	public boolean writable = true;
	private boolean _writev = false;
	protected WritableState _writableState;

	public Writable() {}
	public Writable(StreamOptions options) {
		_writableState = new WritableState(options, this);
	}
	
	private class WriteReq {
		private ReadableChunk chunk;
		private String encoding;
		private Ack callback;
		private WriteReq next;

		public WriteReq(ReadableChunk chunk, String encoding, Ack cb) {
			this.chunk = chunk;
			this.encoding = encoding;
			this.callback = cb;
			this.next = null;
		}
	}

	public WritableState getState() {
		return _writableState;
	}

	public void writeAfterEnd(Writable stream, final Ack cb) {
		final Error er = new Error("write after end");
		stream.emit("error", er);
		EventThread.nextTick(new Runnable() {
			@Override
			public void run() {
				if (cb != null) {
					cb.call(er);
				}
			}
		});
	}

	public void _writev(ReadableChunk chunk, Ack cb) {
	}

	abstract public void _write(ReadableChunk chunk, String encoding,
			Ack cb);

	//ReadableChunk chunk, String encoding, Ack cb
	public void end(Object... args) {
		ReadableChunk chunk = args.length > 0 ? (ReadableChunk) args[0] : null;
		String encoding = args.length > 1 ? (String) args[1] : null;
		Ack cb = args.length > 2 ? (Ack) args[2] : null;
		WritableState state = getState();
		if (chunk != null) {
			this.write(chunk, encoding);
		}

		// .end() fully uncorks
		if (state.corked > 0) {
			state.corked = 1;
			this.uncork();
		}

		// ignore unnecessary end() calls.
		if (!state.ending && !state.finished)
			endWritable(cb);
	}

	private boolean needFinish() {
		WritableState state = getState();
		return state.ending && state.length == 0
				&& (state.bufferedRequest == null) && !state.finished
				&& !state.writing;
	}

	private void prefinish() {
		WritableState state = getState();
		if (!state.prefinished) {
			state.prefinished = true;
			emit("prefinish");
		}
	}

	private boolean finishMaybe() {
		boolean need = needFinish();
		WritableState state = getState();
		if (need) {
			if (state.pendingcb == 0) {
				prefinish();
				state.finished = true;
				emit("finish");
			} else
				prefinish();
		}
		return need;
	}

	private ReadableChunk decodeChunk(ReadableChunk chunk, String encoding) {
		ReadableChunk ret = chunk;
		WritableState state = getState();
		if (!state.objectMode && state.decodeStrings && chunk.chars != null) {
			Buffer buf = new Buffer(chunk.chars, encoding);
			ret = new ReadableChunk(buf);
		}
		return ret;
	}

	// if we're already writing something, then just put this
	// in the queue, and wait our turn. Otherwise, call _write
	// If we return false, then we need a drain event, so set that flag.
	private boolean writeOrBuffer(Writable stream, ReadableChunk chunk,
			String encoding, Ack cb) {
		WritableState state = stream.getState();
		chunk = decodeChunk(chunk, encoding);
		// if (util.isBuffer(chunk))
		// encoding = "buffer";
		int len = state.objectMode ? 1 : chunk.length();

		state.length += len;

		boolean ret = state.length < state.highWaterMark;
		// we must ensure that previous needDrain will not be reset to false.
		if (!ret)
			state.needDrain = true;

		if (state.writing || state.corked > 0) {
			WriteReq last = state.lastBufferedRequest;
			state.lastBufferedRequest = new WriteReq(chunk, encoding, cb);
			if (last != null) {
				last.next = state.lastBufferedRequest;
			} else {
				state.bufferedRequest = state.lastBufferedRequest;
			}
		} else
			doWrite(stream, false, len, chunk, encoding, cb);

		return ret;
	}

	private void doWrite(Writable stream, boolean writev, int len,
			ReadableChunk chunk, String encoding, Ack cb) {
		WritableState state = stream.getState();
		state.writelen = len;
		state.writecb = cb;
		state.writing = true;
		state.sync = true;
		if (writev)
			stream._writev(chunk, state.onwrite);
		else
			stream._write(chunk, encoding, state.onwrite);
		state.sync = false;
	}

	// if there's something in the buffer waiting, then process it
	public void clearBuffer() {
		WritableState state = getState();
		state.bufferProcessing = true;
		WriteReq entry = state.bufferedRequest;

		if (this._writev && entry != null && entry.next != null) {
			// Fast case, write everything using _writev()
			ReadableChunk buffer = null;
			ArrayList<ReadableChunk> bufferLst = new ArrayList<ReadableChunk>();
			final ArrayList<Ack> cbs = new ArrayList<Ack>();
			while (entry != null) {
				cbs.add(entry.callback);
				bufferLst.add(entry.chunk);
				entry = entry.next;
			}
			buffer = ReadableChunk.concat(bufferLst);

			// count the one we are adding, as well.
			// TODO(isaacs) clean this up
			state.pendingcb++;
			state.lastBufferedRequest = null;
			doWrite(this, true, state.length, buffer, "", new Ack() {
				@Override
				public void call(Object... args) {
					WritableState state = (WritableState) args[0];
					Error err = (Error) args[1];
					for (int i = 0; i < cbs.size(); i++) {
						state.pendingcb--;
						cbs.get(i).call(err);
					}
				}
			});

			// Clear buffer
		} else {
			// Slow case, write chunks one-by-one
			while (entry != null) {
				ReadableChunk chunk = entry.chunk;
				String encoding = entry.encoding;
				Ack cb = entry.callback;
				int len = state.objectMode ? 1 : chunk.length();

				doWrite(this, false, len, chunk, encoding, cb);
				entry = entry.next;
				// if we didn't call the onwrite immediately, then
				// it means that we need to wait until it does.
				// also, that means that the chunk and cb are currently
				// being processed, so move the buffer counter past them.
				if (state.writing) {
					break;
				}
			}

			if (entry == null)
				state.lastBufferedRequest = null;
		}
		state.bufferedRequest = entry;
		state.bufferProcessing = false;
	}

	public void cork() {
		_writableState.corked++;
	}

	public void uncork() {
		WritableState state = getState();
		if (state.corked > 0) {
			state.corked--;
			if (!state.writing && state.corked == 0 && !state.finished
					&& !state.bufferProcessing && state.bufferedRequest != null) {
				clearBuffer();
			}
		}
	}

	private void onwriteError(Writable stream, boolean sync, final Error er,
			final Ack cb) {
		final WritableState state = stream.getState();
		if (sync) {
			EventThread.nextTick(new Runnable() {
				@Override
				public void run() {
					state.pendingcb--;
					if (cb != null) {
						cb.call(er);
					}
				}
			});
		} else {
			state.pendingcb--;
			cb.call(er);
		}

		stream.getState().errorEmitted = true;
		stream.emit("error", er);
	}

	private void onwrite(Error er) {
		WritableState state = getState();
		boolean sync = state.sync;
		final Ack cb = state.writecb;
		onwriteStateUpdate();

		if (er != null)
			onwriteError(this, sync, er, cb);
		else {
			// Check if we're actually ready to finish, but don't emit yet
			final boolean finished = needFinish();

			if (!finished && state.corked == 0 && !state.bufferProcessing
					&& state.bufferedRequest != null) {
				clearBuffer();
			}

			if (sync) {
				EventThread.nextTick(new Runnable() {
					@Override
					public void run() {
						// TODO Auto-generated method stub
						afterWrite(Writable.this, finished, cb);
					}
				});
			} else {
				afterWrite(this, finished, cb);
			}
		}
	}

	public class WritableState {
		public int highWaterMark;
		public boolean objectMode = false;
		public boolean needDrain = false;
		// at the start of calling end()
		public boolean ending = false;
		// when end() has been called, and returned
		public boolean ended = false;
		// when 'finish' is emitted
		public boolean finished = false;
		// should we decode strings into buffers before passing to _write?
		// this is here so that some node-core streams can optimize string
		// handling at a lower level.
		public boolean decodeStrings = false;
		public String defaultEncoding = "utf8";
		public int length = 0;
		// a flag to see when we're in the middle of a write.
		public boolean writing = false;
		// when true all writes will be buffered until .uncork() call
		public int corked = 0;
		public boolean sync = true;
		// a flag to know if we're processing previously buffered items, which
		// may call the _write() callback in the same tick, so that we don't
		// end up in an overlapped onwrite situation.
		public boolean bufferProcessing = false;
		// the callback that the user supplies to write(chunk,encoding,cb)
		public Ack writecb = null;
		// the amount that is being written when _write is called.
		public int writelen = 0;
		public WriteReq bufferedRequest = null;
		public WriteReq lastBufferedRequest = null;
		// number of pending user-supplied write callbacks
		// this must be 0 before 'finish' can be emitted
		public int pendingcb = 0;
		// True if the error was already emitted and should not be thrown again
		public boolean errorEmitted = false;
		// emit prefinish if the only thing we're waiting for is _write cbs
		// This is relevant for synchronous Transform streams
		public boolean prefinished = false;

		public WritableState(StreamOptions options, Writable stream) {
			objectMode = options.objectMode;
//			if (stream instanceof Duplex) {
//				objectMode = objectMode || options.writableObjectMode;
//			}
			int hwm = options.highWaterMark;
			int defaultHwm = objectMode ? 16 : 16 * 1024;
			highWaterMark = (hwm >= 0) ? hwm : defaultHwm;
			boolean noDecode = options.decodeStrings == false;
			decodeStrings = !noDecode;
			defaultEncoding = options.defaultEncoding == null ? "utf8"
					: options.defaultEncoding;
		}

		public ArrayList<WriteReq> getBuffer() {
			WriteReq current = bufferedRequest;
			ArrayList<WriteReq> out = new ArrayList<WriteReq>();
			while (current != null) {
				out.add(current);
				current = current.next;
			}
			return out;
		}

		public Ack onwrite = new Ack() {
			@Override
			public void call(Object... args) {
				Error err = args.length > 0 ? (Error) args[0] : null;
				onwrite(err);
			}
		};
	}

	private void endWritable(final Ack cb) {
		WritableState state = getState();
		state.ending = true;
		finishMaybe();
		if (cb != null) {
			if (state.finished) {
				EventThread.nextTick(new Runnable() {
					@Override
					public void run() {
						cb.call();
					}
				});
			} else {
				once("finish", new Listener() {

					@Override
					public void call(Object... args) {
						// TODO Auto-generated method stub
						cb.call(args);
					}
				});
			}
		}
		state.ended = true;
	}

	// Object chunk, String encoding, Ack cb
	public boolean write(Object... args) {
		ReadableChunk chunk = args.length > 0 ? (ReadableChunk) args[0] : null;
		String encoding = args.length > 1 ? (String) args[1] : null;
		Ack cb = args.length > 2 ? (Ack) args[2] : null;
		WritableState state = getState();
		boolean ret = false;

		if (chunk.bytes != null)
			encoding = "buffer";
		else if (encoding == null || encoding.length() == 0)
			encoding = state.defaultEncoding;

		if (state.ended)
			writeAfterEnd(this, cb);
		else if (chunk != null) {
			state.pendingcb++;
			ret = writeOrBuffer(this, chunk, encoding, cb);
		}

		return ret;
	}

	private void onwriteStateUpdate() {
		WritableState state = getState();
		state.writing = false;
		state.writecb = null;
		state.length -= state.writelen;
		state.writelen = 0;
	}

	private void afterWrite(Writable stream, boolean finished, Ack cb) {
		if (!finished) {
			onwriteDrain.call(stream);
		}
		stream.getState().pendingcb--;
		if (cb != null) {
			cb.call();
		}
		finishMaybe();
	}

	private Listener onwriteDrain = new Listener() {
		@Override
		public void call(Object... args) {
//			Log.d("jerome", "onwriteDrain called!");
			if (args.length > 0) {
				Writable stream = null;
				if (args[0] instanceof Writable) {
					stream = (Writable) args[0];
				}
				if (stream != null) {
					WritableState state = stream.getState();
					if (state.length == 0 && state.needDrain) {
						state.needDrain = false;
						stream.emit("drain");
					}
				}
			}
		}
	};
	
	@Override
	public IPipable pipe(Writable dest, StreamOptions options) {
		System.out.println("cannot implement pipe in Writable");
		return null;
	}

}
