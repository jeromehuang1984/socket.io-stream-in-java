package com.github.nodejs.stream;

import android.util.Log;

import com.github.nkzawa.thread.EventThread;
import com.github.nodejs.vo.Buffer;
import com.github.nodejs.vo.ReadableChunk;
import com.github.nodejs.vo.StreamOptions;

public class FileReadStream extends Readable {

	private FileIO fileIO;
	Buffer pool = null;
	private final int kMinPoolSpace = 128;
	
	private void allocNewPool(int size) {
		pool = new Buffer(size);
		pool.used = 0;
	}
	
	public FileReadStream(String filePath, StreamOptions options) {
		fileIO = new FileIO();
		StreamOptions opt = options;
		if (opt == null) {
			opt = new StreamOptions();
		}
		opt.highWaterMark = 64 * 1024;
		_readableState = new ReadableState(opt);
		fileIO.path = filePath;
		if (!fileIO.opened) {
			open(opt.pictureFormat);
		}
		on("end", new Listener() {
			@Override
			public void call(Object... arg0) {
				destroy();
			}
		});
	}

	private void open(String picFormat) {
		fileIO.openInputStream(fileIO.path, picFormat, new Listener() {
			@Override
			public void call(Object... args) {
				Error err = args.length > 0 ? (Error) args[0] : null;
				if (err != null) {
					if (fileIO.autoClose) {
						destroy();
					}
					emit("error", err);
					return;
				}
				fileIO.opened = true;
				emit("open");
				// start the flow of data.
				read(0);
			}
		});
	}

	private void destroy() {
		if (fileIO.destroyed) {
			return;
		}
		fileIO.destroyed = true;
		if (fileIO.opened) {
			close(null);
		}
	}

	private void close(Listener cb) {
		if (cb != null) {
			once("close", cb);
		}
		if (!fileIO.opened) {
			once("open", onclose);
			return;
		}
		if (fileIO.closed) {
			EventThread.nextTick(new Runnable() {
				@Override
				public void run() {
					emit("close");
				}
			});
			return;
		}
		fileIO.closed = true;
		onclose.call();
	}

	private Listener onclose = new Listener() {
		@Override
		public void call(Object... arg0) {
			fileIO.closeInputStream();
			emit("close");
			fileIO.opened = false;
		}
	};

	@Override
	protected void _read(int n) {
		if (!fileIO.opened) {
			final int size = n;
			once("open", new Listener() {
				@Override
				public void call(Object... arg0) {
					_read(size);
				}
			});
			return;
		}
		if (fileIO.destroyed) {
			return;
		}
		if (pool == null || pool.length() - pool.used < kMinPoolSpace) {
			// discard the old pool.
			pool = null;
			allocNewPool(_readableState.highWaterMark);
		}

		final Buffer thisPool = pool;
		int toRead = Math.min(pool.length() - pool.used, n);
		// toRead = Math.min(end - pos + 1, toRead);
		final int start = pool.used;
		// already read everything we were supposed to read! treat as EOF.
		if (toRead <= 0) {
			push(null, null);
			return;
		}
		// the actual read
		fileIO.readFromStream(pool.contents, pool.used, toRead, fileIO.pos, new Listener() {
			@Override
			public void call(Object... args) {
				Error err = args.length > 0 ? (Error) args[0] : null;
				int bytesRead = args.length > 1 ? (int) args[1] : 0;
				if (err != null) {
					if (fileIO.autoClose) {
						destroy();
					}
					emit("error", err);
				} else {
					Buffer buf = null;
					if (bytesRead > 0) {
						buf = thisPool.slice(start, start + bytesRead);
					}
					push(new ReadableChunk(buf), null);
				}
			}
		});
		fileIO.pos += toRead;
		pool.used += toRead;
	}
}
