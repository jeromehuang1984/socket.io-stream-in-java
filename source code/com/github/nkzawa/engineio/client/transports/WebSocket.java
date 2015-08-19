package com.github.nkzawa.engineio.client.transports;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.NotYetConnectedException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import com.codebutler.android_websockets.WebSocketClient;
import com.codebutler.android_websockets.WebSocketClient.WsListener;
import com.github.nkzawa.engineio.client.Transport;
import com.github.nkzawa.engineio.parser.Packet;
import com.github.nkzawa.engineio.parser.Parser;
import com.github.nkzawa.parseqs.ParseQS;
import com.github.nkzawa.thread.EventThread;

public class WebSocket extends Transport {

	public static final String NAME = "websocket";

    private WebSocketClient ws;


    public WebSocket(Options opts) {
        super(opts);
        this.name = NAME;
    }

	@SuppressWarnings("unchecked")
	@Override
	protected void doOpen() {
		Map<String, String> headers = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        this.emit(EVENT_REQUEST_HEADERS, headers);

        try {
        	String url = uri();
        	ws = new WebSocketClient(new URI(url), listener, null);
        	ws.connect();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
	}
	
	private WsListener listener = new WsListener() {
		
		@Override
		public void onMessage(byte[] data) {
//			Log.d("jerome", "WebSocketClient, onMessage ByteBuffer len:" + data.length);
			final byte[] fData = data;
            EventThread.exec(new Runnable() {
                @Override
                public void run() {
                    onData(fData);
                }
            });
		}
		
		@Override
		public void onMessage(String message) {
//			Log.d("jerome", "WebSocketClient, onMessage String:" + message);
			final String msg = message;
            EventThread.exec(new Runnable() {
                @Override
                public void run() {
                    onData(msg);
                }
            });
		}
		
		@Override
		public void onError(Exception error) {
			final Exception e = error;
			EventThread.exec(new Runnable() {
                @Override
                public void run() {
                    WebSocket.this.onError("websocket error", e);
                }
            });
		}
		
		@Override
		public void onDisconnect(int code, String reason) {
			EventThread.exec(new Runnable() {
                @Override
                public void run() {
                    onClose();
                }
            });
		}
		
		@Override
		public void onConnect() {
			EventThread.exec(new Runnable() {
                @Override
                public void run() {
                    Map<String, String> headers = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
//                    Iterator<String> it = serverHandshake.iterateHttpFields();
//                    while (it.hasNext()) {
//                        String field = it.next();
//                        if (field == null) continue;
//                        headers.put(field, serverHandshake.getFieldValue(field));
//                    }
                    emit(EVENT_RESPONSE_HEADERS, headers);

                    onOpen();
                }
            });
		}
	};

	@Override
	protected void doClose() {
		if (this.ws != null) {
            this.ws.disconnect();
        }
	}
	
	@Override
	protected void write(Packet[] packets) {
        final WebSocket self = this;
        this.writable = false;
        for (Packet packet : packets) {
            Parser.encodePacket(packet, new Parser.EncodeCallback() {
                @Override
                public void call(Object packet) {
                    if (packet instanceof String) {
                        self.ws.send((String) packet);
                    } else if (packet instanceof byte[]) {
                        try {
							self.ws.send((byte[]) packet);
						} catch (NotYetConnectedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
                    }
                }
            });
        }

        final Runnable ondrain = new Runnable() {
            @Override
            public void run() {
                self.writable = true;
                self.emit(EVENT_DRAIN);
            }
        };

        // fake drain
        // defer to next tick to allow Socket to clear writeBuffer
        EventThread.nextTick(ondrain);
    }
	
	protected String uri() {
        Map<String, String> query = this.query;
        if (query == null) {
            query = new HashMap<String, String>();
        }
        String schema = this.secure ? "wss" : "ws";
        String port = "";

        if (this.port > 0 && (("wss".equals(schema) && this.port != 443)
                || ("ws".equals(schema) && this.port != 80))) {
            port = ":" + this.port;
        }

        if (this.timestampRequests) {
            query.put(this.timestampParam, String.valueOf(new Date().getTime()));
        }

        String _query = ParseQS.encode(query);
        if (_query.length() > 0) {
            _query = "?" + _query;
        }

        return schema + "://" + this.hostname + port + this.path + _query;
    }

}
