package com.github.nodejs.stream;

import com.github.nkzawa.emitter.Emitter.Listener;
import com.github.nkzawa.socketio.client.Ack;
import com.github.nkzawa.thread.EventThread;
import com.github.nodejs.stream.Writable.WritableState;
import com.github.nodejs.vo.ReadableChunk;
import com.github.nodejs.vo.StreamOptions;

public class FileWriteStream extends Writable {

	private int bytesWritten = 0;
	private FileIO fileIO;
	
	public FileWriteStream(String filePath, StreamOptions options) {
		fileIO = new FileIO();
		StreamOptions opt = options;
		if (opt == null) {
			opt = new StreamOptions();
		}
		_writableState = new WritableState(opt, this);
		fileIO.path = filePath;
		if (!fileIO.opened) {
			open();
		}
		// dispose on finish.
		once("finish", close);
	}
	
	private void open() {
		fileIO.openOutputStream(fileIO.path, new Listener() {
			@Override
			public void call(Object... args) {
				Error err = args.length > 0 ? (Error) args[0] : null;
				if (err != null) {
					destroy();
					emit("error", err);
					return;
				}
				fileIO.opened = true;
				emit("open");
			}
		});
	}
	
	private void destroy() {
		if (fileIO.destroyed) {
			return;
		}
		fileIO.destroyed = true;
		if (fileIO.opened) {
			close.call();
		}
	}
	
	private Listener close = new Listener() {
		
		@Override
		public void call(Object... args) {
			Listener cb = args.length > 0 ? (Listener) args[0] : null;
			if (cb != null) {
				once("close", cb);
			}
			if (!fileIO.opened) {
				once("open", doclose);
			}
			if (fileIO.closed) {
				EventThread.nextTick(new Runnable() {
					@Override
					public void run() {
						emit("close");
					}
				});
			}
			doclose.call();
		}
	};
	
	private Listener doclose = new Listener() {
		@Override
		public void call(Object... arg0) {
			fileIO.closeOutputStream();
			emit("close");
			fileIO.opened = false;
		}
	};
	@Override
	public void _write(final ReadableChunk chunk, final String encoding, final Ack cb) {
		if (!fileIO.opened) {
			once("open", new Listener() {
				@Override
				public void call(Object... arg0) {
					_write(chunk, encoding, cb);
				}
			});
		}
		fileIO.writeToStream(chunk.bytes.contents, 0, chunk.length(), new Listener() {
			@Override
			public void call(Object... args) {
				Error err = args.length > 0 ? (Error) args[0] : null;
				int bytes = args.length > 1 ? (int) args[1] : 0;
				if (err != null) {
					destroy();
					cb.call(err);
				} else {
					bytesWritten += bytes;
					cb.call();
				}
			}
		});
	}

}
