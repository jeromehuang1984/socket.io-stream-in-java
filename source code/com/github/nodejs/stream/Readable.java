package com.github.nodejs.stream;

import java.util.ArrayList;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.thread.EventThread;
import com.github.nodejs.lib.StringDecoder;
import com.github.nodejs.vo.Buffer;
import com.github.nodejs.vo.ReadableChunk;
import com.github.nodejs.vo.StreamOptions;

public abstract class Readable extends Emitter implements IPipable {

	public boolean readable = true;
	protected ReadableState _readableState = null;

	// Don't raise the hwm > 128MB
	private static final int MAX_HWM = 0x800000;

	public Readable() {}
	
	public Readable(StreamOptions options) {
		_readableState = new ReadableState(options);
	}
	
	public ReadableState getState() {
		return _readableState;
	}
	
	public int listenerCount(String eventType) {
		return listeners(eventType).size();
	}
	
	@Override
	public Emitter on(String event, Listener fn) {
		Emitter ret = super.on(event, fn);
		if (event.equals("data")
				&& _readableState.flowingStatus != ReadableState.FLOW_STATUS_UNFLOW) {
			resume();
		}
		if ("readable".equals(event) && readable) {
			ReadableState state = this._readableState;
			if (!state.readableListening) {
				state.readableListening = true;
				state.emittedReadable = false;
				state.needReadable = true;
				if (!state.reading) {
					final Readable self = this;
					EventThread.nextTick(new Runnable() {

						@Override
						public void run() {
							// TODO Auto-generated method stub
							System.out.println("readable nexttick read 0");
							self.read(0);
						}
					});
				} else if (state.length > 0) {
					emitReadable(this);
				}
			}
		}
		return ret;
	}

	private int roundUpToNextPowerOf2(int n) {
		int ret = n;
		if (n >= MAX_HWM) {
			ret = MAX_HWM;
		} else {
			// Get the next highest power of 2
			ret--;
			for (int p = 1; p < 32; p <<= 1)
				ret |= ret >> p;
			ret++;
		}
		return ret;
	}

	public int howMuchToRead(int n, ReadableState state, boolean sizeUnknown) {
		if (state.length == 0 && state.ended) {
			return 0;
		}
		if (state.objectMode) {
			return n == 0 ? 0 : 1;
		}
		if (sizeUnknown) {
			if (state.isFlowing() && state.buffer.size() > 0) {
				return state.buffer.get(0).length();
			} else {
				return state.length;
			}
		}
		if (n <= 0) {
			return 0;
		}

		//If we're asking for more than the target buffer level, then raise the water mark.  Bump up to the next highest
		//power of 2, to prevent increasing it excessively in tiny amounts.
		if (n > state.highWaterMark) {
			state.highWaterMark = roundUpToNextPowerOf2(n);
		}

		// don't have that much.  return null, unless we've ended.
		if (n > state.length) {
			if (!state.ended) {
				state.needReadable = true;
				return 0;
			} else
				return state.length;
		}
		return n;
	}
	
	private void onEofChunk(Readable stream) {
		ReadableState state = stream.getState();
		if (state.decoder != null && !state.ended) {
			String chunk = state.decoder.end();
			if (chunk != null && chunk.length() > 0) {
				state.buffer.add(new ReadableChunk(chunk));
				state.length += state.objectMode ? 1 : chunk.length();
			}
		}
		state.ended = true;
		
		// emit 'readable' now to make sure it gets picked up.
		emitReadable(stream);
	}
	
	private boolean readableAddChunk(Readable stream, ReadableChunk chunk, String encoding, boolean addToFront) {
		ReadableState state = stream.getState();
		if (!ReadableChunk.isEmpty(chunk)) {
			if (state.objectMode || chunk.length() > 0) {
				if (state.ended && !addToFront) {
					System.out.println("Error! stream.push() after EOF");
				} else if (state.endEmitted && addToFront) {
					System.out.println("Error! stream.unshift() after end event");
				} else {
					if (state.decoder != null && !addToFront && encoding.length() == 0) {
						String decoded = state.decoder.write(chunk.bytes);
						chunk = new ReadableChunk(decoded);
					}
					if (!addToFront) {
						state.reading = false;
					}
					
					// if we want the data now, just emit it.
					if (state.isFlowing() && state.length == 0 && !state.sync) {
						stream.emit("data", chunk);
						stream.read(0);
					} else {
						// update the buffer info.
						state.length += state.objectMode ? 1 : chunk.length();
						if (addToFront) {
							state.buffer.add(0, chunk);
						} else {
							state.buffer.add(chunk);
						}
						
						if (state.needReadable) {
							emitReadable(stream);
						}
					}
					maybeReadMore(stream, state);
				}
			} else if (!addToFront) {
				state.reading = false;
			}
		} else {
			state.reading = false;
			if (!state.ended) {
				onEofChunk(stream);
			}
		}
//		Log.d("jerome", "readableAddChunk, state.buffer size:" + state.buffer.size());
		return needMoreData(state);
	}
	
	public boolean push(ReadableChunk chunk, String encoding) {
		ReadableState state = _readableState;
		String charset = encoding;
		ReadableChunk current = chunk;
		if (chunk != null && chunk.chars != null && !state.objectMode) {
			if (encoding == null || encoding.length() == 0) {
				charset = state.defaultEncoding;
				if (!charset.equals(state.encoding)) {
					current = new ReadableChunk(new Buffer(chunk.chars, charset));
					charset = "";
				}
			}
		}
		return readableAddChunk(this, current, charset, false);
	}
	
	public boolean unshift(ReadableChunk chunk) {
		return readableAddChunk(this, chunk, "", true);
	}
	
	private boolean needMoreData(ReadableState state) {
		return !state.ended && (state.needReadable || state.length < state.highWaterMark ||
		          state.length ==0);
	}
	
	private void maybeReadMore(final Readable stream, final ReadableState state) {
		if (!state.readingMore) {
			state.readingMore = true;
		}
		EventThread.nextTick(new Runnable() {
			@Override
			public void run() {
				// TODO Auto-generated method stub
				maybeReadMore_(stream, state);
			}
		});
	}
	
	private void maybeReadMore_(Readable stream, ReadableState state) {
		int len = state.length;
		while (!state.reading && !state.isFlowing() && !state.ended
				&& state.length < state.highWaterMark) {
			stream.read(0);
			if (len == state.length)
				// didn't get any data, stop spinning.
				break;
			else
				len = state.length;
		}
		state.readingMore = false;
	}
	
	//Pluck off n bytes from an array of buffers. Length is the combined lengths of all the buffers in the list.
	private ReadableChunk fromList(int n, ReadableState state) {
		ArrayList<ReadableChunk> list = state.buffer;
		int length = state.length;
		boolean stringMode = state.decoder == null ? false : true;
		boolean objectMode = state.objectMode;
		ReadableChunk ret = null;
		
		if (list.size() == 0) {
			return null;
		}
		
		if (length == 0) {
			return null;
		} else if (objectMode) {
			ret = list.remove(0);
		} else if (n == 0 || n >= length) {
			// read it all, truncate the array.
			ret = ReadableChunk.concat(list);
			list.clear();
		} else {
			// read just some of it.
		    if (n < list.get(0).length()) {
		    	// just take a part of the first list item. slice is the same for buffers and strings.
		    	ReadableChunk buf = list.get(0);
		    	ret = buf.slice(0, n);
		    	list.set(0, buf.slice(n));
		    } else if (n == list.get(0).length()) {
		    	ret = list.remove(0);
		    } else {
		    	// complex case.
		    	// we have enough to cover it, but it spans past the first buffer.
		    	String retStr = "";
		    	Buffer retBuf = null;
		    	if (!stringMode) {
		    		retBuf = new Buffer(n);
		    	}
		    	int c = 0;
				for (int i = 0, l = list.size(); i < l && c < n; i++) {
					ReadableChunk buf = list.get(0);
					int cpy = Math.min(n - c, buf.length());

					if (stringMode) 
						retStr += buf.slice(0, cpy).chars;
					else
						buf.bytes.copy(retBuf, c, 0, cpy);

					if (cpy < buf.length())
						list.set(0, buf.slice(cpy));
					else
						list.remove(0);

					c += cpy;
				}
				if (retBuf != null) {
					ret = new ReadableChunk(retBuf);
				} else {
					ret = new ReadableChunk(retStr);
				}
		    }
		}
		return ret;
	}
	
	public ReadableChunk read() {
		return read(-1, true);
	}
	
	public ReadableChunk read(int n) {
		return read(n, false);
	}
	
	public ReadableChunk read(int n, boolean sizeUnknown) {
		ReadableState state = _readableState;
		int nOrig = n;
		if (n > 0 || sizeUnknown) {
			state.emittedReadable = false;
		}
		
		//if we're doing read(0) to trigger a readable event, but we already have a bunch of data in the buffer, 
		//then just trigger the 'readable' event and move on.
		if (n == 0 && state.needReadable &&
				(state.length >= state.highWaterMark || state.ended)) {
			if (state.length == 0 && state.ended) {
				endReadable(this);
			} else {
				emitReadable(this);
			}
			return null;
		}
		
		n = howMuchToRead(n, state, sizeUnknown);
		// if we've ended, and we're now clear, then finish it up.
		if (n == 0 && state.ended) {
			if (state.length == 0) {
				endReadable(this);
			}
			return null;
		}
		
		boolean doRead = state.needReadable;
		if (state.length == 0 || state.length < state.highWaterMark) {
			doRead = true;
		}
		// however, if we've ended, then there's no point, and if we're already reading, then it's unnecessary.
		if (state.ended || state.reading) {
			doRead = false;
		}
		if (doRead) {
			state.reading = true;
			state.sync = true;
			// if the length is currently zero, then we *need* a readable event.
			if (state.length == 0) {
				state.needReadable = true;
			}
			// call internal read method
			this._read(state.highWaterMark);
			state.sync = false;
		}
		// If _read pushed data synchronously, then `reading` will be false,
		// and we need to re-evaluate how much data we can return to the user.
		if (doRead && !state.reading) {
			n = howMuchToRead(nOrig, state, sizeUnknown);
		}
		ReadableChunk ret = null;
		if (n > 0) {
			if (listenerCount("data") > 0) {
				ret = fromList(n, state);
			}
		}
		if (ReadableChunk.isEmpty(ret)) {
			state.needReadable = true;
			n = 0;
		}
		state.length -= n;
		
		// If we have nothing in the buffer, then we want to know as soon as we *do* get something into the buffer.
		if (state.length == 0 && !state.ended) {
			state.needReadable = true;
		}
		// If we tried to read() past the EOF, then emit end on the next tick.
		if (nOrig != n && state.ended && state.length == 0) {
			endReadable(this);
		}
		
		if (!ReadableChunk.isEmpty(ret)) {
			this.emit("data", ret);
		}
		return ret;
	}
	
	//abstract method.  to be overridden in specific implementation classes. call cb(er, data) where data is <= n in length.
	//for virtual (non-string, non-buffer) streams, "length" is somewhat arbitrary, and perhaps not very meaningful.
	abstract protected void _read(int n);
	
	public void resume() {
		ReadableState state = getState();
		if (!state.isFlowing()) {
			state.flowingStatus = ReadableState.FLOW_STATUS_FLOWING;
			if (!state.reading) {
				read(0);
			}
			resume(this);
		}
	}
	
	private void resume(final Readable stream) {
		ReadableState state = stream.getState();
		if (!state.resumeScheduled) {
			state.resumeScheduled = true;
			EventThread.nextTick(new Runnable() {
				@Override
				public void run() {
					resume_(stream);
				}
			});
		}
	}
	
	private void resume_(Readable stream) {
		ReadableState state = stream.getState();
		state.resumeScheduled = false;
		stream.emit("resume");
		flow(stream);
		if (state.isFlowing() && !state.reading) {
			stream.read(0);
		}
	}
	
	public void pause() {
		if (_readableState.flowingStatus != ReadableState.FLOW_STATUS_UNFLOW) {
			_readableState.flowingStatus = ReadableState.FLOW_STATUS_UNFLOW;
			emit("pause");
		}
	}
	
	private void flow(Readable stream) {
		ReadableState state = stream.getState();
		if (state.isFlowing()) {
			Object chunk = null;
			do {
				chunk = stream.read();
			} while (chunk != null && state.isFlowing());
		}
	}
	
	private void emitReadable(final Readable stream) {
		ReadableState state = stream.getState();
		state.needReadable = false;
		if (!state.emittedReadable) {
			System.out.println("emitReadable, flowing:"+ (state.flowingStatus == ReadableState.FLOW_STATUS_FLOWING));
			state.emittedReadable = true;
			if (state.sync) {
				EventThread.nextTick(new Runnable() {
					@Override
					public void run() {
						emitReadable_(stream);
					}
				});
			} else {
				emitReadable_(stream);
			}
		}
	}
	
	private void emitReadable_(Readable stream) {
		stream.emit("readable");
		flow(stream);
	}
	
	private void endReadable(final Readable stream) {
		final ReadableState state = stream.getState();
		if (state.length > 0) {
			System.out.println("Error! this is bug in endReadable, should never happen!");
		}
		if (!state.endEmitted) {
			state.ended = true;
			EventThread.nextTick(new Runnable() {
				@Override
				public void run() {
					if (!state.endEmitted && state.length == 0) {
						state.endEmitted = true;
						stream.readable = false;
						stream.emit("end");
					}
				}
			});
		}
	}

	public void pipeOnDrain() {
		ReadableState state = this.getState();
		if (state.awaitDrain > 0) {
			state.awaitDrain--;
		}
		if (state.awaitDrain == 0 && listenerCount("data") > 0) {
			state.flowingStatus = ReadableState.FLOW_STATUS_FLOWING;
			flow(this);
		}
	}
	
	@Override
	public IPipable pipe(final Writable dest, StreamOptions options) {
		ReadableState state = getState();
		Readable src = this;
		final Pipe pipeObj = new Pipe(src, dest);
		state.pipes.add(dest);
		boolean doEnd = true;
		final Listener endFn = doEnd ? pipeObj.onend : pipeObj.oncleanup;
		if (state.endEmitted) {
			EventThread.nextTick(new Runnable() {
				@Override
				public void run() {
					endFn.call();
				}
			});
		} else {
			src.once("end", endFn);
		}
		
		dest.on("unpipe", pipeObj.onunpipe);
		// when the dest drains, it reduces the awaitDrain counter on the source.  This would be more elegant with a .once()
		// handler in flow(), but adding and removing repeatedly is too slow.
		dest.on("drain", pipeObj.ondrain);
		src.on("data", pipeObj.ondata);
		// Both close and finish should trigger unpipe, but only once.
		dest.once("close", pipeObj.onclose);
		dest.once("finish", pipeObj.onfinish);
		// tell the dest that it's being piped to
		dest.emit("pipe", src);
		
		// start the flow if it hasn't been started already.
		if (!state.isFlowing()) {
			src.resume();
		}
		return dest;
	}
	
	public IPipable unpipe(Writable dest) {
		ReadableState state = getState();
		// if we're not piping anywhere, then do nothing.
		  if (state.pipes.size() == 0)
		    return this;

		  // just one destination.  most common case.
		  if (state.pipes.size() == 1) {
		    // passed in one, but it's not the right one.
		    if (dest != null && !dest.equals(state.pipes.get(0)))
		      return this;

		    if (dest == null)
		      dest = (Writable) state.pipes.get(0);

		    // got a match.
		    state.pipes = new ArrayList<IPipable>();
		    state.flowingStatus = ReadableState.FLOW_STATUS_UNFLOW;
		    if (dest != null) {
		    	dest.emit("unpipe", this);
		    }
		    return this;
		  }

		  // slow case. multiple pipe destinations.
		  if (dest == null) {
		    // remove all.
		    ArrayList<IPipable> dests = state.pipes;
		    state.pipes.clear();
		    state.flowingStatus = ReadableState.FLOW_STATUS_UNFLOW;

		    for (int i = 0; i < dests.size(); i++) {
		    	Emitter emitter = (Emitter) dests.get(i);
		    	emitter.emit("unpipe", this);
		    }
		    return this;
		  }

		  // try to find the right one.
		  int i = state.pipes.indexOf(dest);
		  if (i < 0)
		    return this;

		  state.pipes.remove(i);
		  dest.emit("unpipe", this);

		  return this;
	}
	
	public class ReadableState {
		public int highWaterMark; // control buffer's size
		public ArrayList<ReadableChunk> buffer = new ArrayList<ReadableChunk>();
		public int length = 0; // how many buffer used
		public ArrayList<IPipable> pipes = new ArrayList<IPipable>();

		public static final int FLOW_STATUS_UNSET = 0;
		public static final int FLOW_STATUS_FLOWING = 1;
		public static final int FLOW_STATUS_UNFLOW = 2;
		public int flowingStatus = 0;
		public boolean ended = false;
		public boolean endEmitted = false;
		public boolean reading = false;

		// a flag to be able to tell if the onwrite cb is called immediately,
		// or on a later tick. We set this to true at first, because any
		// actions that shouldn't happen until "later" should generally also
		// not happen before the first write call.
		public boolean sync = true;

		// whenever we return null, then we set a flag to say that we're
		// awaiting a 'readable' event emission.
		public boolean needReadable = false;
		public boolean emittedReadable = false;
		public boolean readableListening = false;

		// object stream flag. Used to make read(n) ignore n and to make all the
		// buffer merging and length checks go away
		public boolean objectMode;

		public String defaultEncoding = "utf8";

		// when piping, we only care about 'readable' events that happen after
		// read()ing all the bytes and not getting any pushback.
		public boolean ranOut = false;

		// the number of writers that are awaiting a drain event in .pipe()s
		public int awaitDrain = 0;

		// if true, a maybeReadMore has been scheduled
		public boolean readingMore = false;

		public StringDecoder decoder = null;
		public String encoding = null;
		
		public boolean resumeScheduled = false;

		public ReadableState(StreamOptions options) {
			if (options != null) {
				// the point at which it stops calling _read() to fill the
				// buffer
				// Note: 0 is a valid value, means
				// "don't call _read preemptively ever"
				highWaterMark = options.highWaterMark >= 0 ? options.highWaterMark
						: 16 * 1024;
				objectMode = options.objectMode;
				if (options.defaultEncoding != null
						&& options.defaultEncoding.length() > 0) {
					defaultEncoding = options.defaultEncoding;
				}
				if (options.encoding != null && options.encoding.length() > 0) {
					encoding = options.encoding;
					decoder = new StringDecoder(encoding);
				}
			}
		}

		public boolean isFlowing() {
			return flowingStatus == FLOW_STATUS_FLOWING;
		}
	}
	
	private class Pipe {
		Readable src;
		Writable dst;
		private ReadableState state;

		public Pipe(Readable source, Writable dest) {
			src = source;
			dst = dest;
			state = src.getState();
		}

		public void cleanup() {
			dst.off("close", onclose);
			dst.off("finish", onfinish);
			dst.off("drain", ondrain);
			dst.off("error", onerror);
			dst.off("unpipe", onunpipe);
			src.off("end", onend);
			src.off("data", ondata);

			// if the reader is waiting for a drain event from this
			// specific writer, then it would cause it to never start
			// flowing again.
			// So, if this is awaiting a drain, then we just call it now.
			// If we don't know, then assume that we are waiting for one.
			if (state.awaitDrain > 0
					&& (dst.getState() == null || dst.getState().needDrain)) {
				src.pipeOnDrain();
			}
		}
		
		public Listener oncleanup = new Listener() {
			@Override
			public void call(Object... arg0) {
				cleanup();
			}
		};
		
		public Listener ondrain = new Listener() {
			@Override
			public void call(Object... arg0) {
				src.pipeOnDrain();
			}
		};
		
		public Listener onend = new Listener() {
			@Override
			public void call(Object... arg0) {
				dst.end();
			}
		};
		
		public Listener onunpipe = new Listener() {
			@Override
			public void call(Object... args) {
				Readable readable = (Readable) args[0];
				if (readable.equals(src)) {
					cleanup();
				}
			}
		};
		
		public Listener ondata = new Listener() {
			@Override
			public void call(Object... args) {
				ReadableChunk chunk = (ReadableChunk) args[0];
				boolean ret = dst.write(chunk);
				if (!ret) {
					src._readableState.awaitDrain++;
					src.pause();
				}
			}
		};
		
		public Listener onerror = new Listener() {
			@Override
			public void call(Object... args) {
				String msg = (String) args[0];
				System.out.println("Error! " + msg);
				unpipe();
				dst.off("error", onerror);
			}
		};
		
		public Listener onclose = new Listener() {
			@Override
			public void call(Object... args) {
				dst.off("finish", onfinish);
				unpipe();
			}
		};
		
		public Listener onfinish = new Listener() {
			@Override
			public void call(Object... args) {
				dst.off("close", onclose);
				unpipe();
			}
		};
		
		private void unpipe() {
			src.unpipe(dst);
		}
	}
}
