package com.example.jeromehuang.socket_io_stream_android;

import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;
import com.github.nodejs.socketio_stream.StreamSocket;
import com.github.nodejs.stream.FileIO;
import com.github.nodejs.stream.FileReadStream;
import com.github.nodejs.stream.FileWriteStream;
import com.github.nodejs.stream.IOStream;
import com.github.nodejs.vo.StreamOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;


public class MainActivity extends ActionBarActivity {

    private Socket mClient;
    private String mLocalRootPath = "/sdcard0";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        try {
            mClient = IO.socket("http://www.somewhere.com:8443");
            mClient.connect();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        if (mClient != null) {
            registerListeners();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void registerListeners() {
        final StreamSocket steaming = new StreamSocket(mClient);

        mClient.on(Socket.EVENT_CONNECT, new Emitter.Listener() {

            @Override
            public void call(Object... arg0) {
                Log.d("androidSocketIO", "androidSocketIO connected!");
            }

        }).on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {

            @Override
            public void call(Object... arg0) {
                Log.d("androidSocketIO", "androidSocketIO disconnected!");
            }
        }).on(Socket.EVENT_ERROR, new Emitter.Listener() {

            @Override
            public void call(Object... args) {
                Object err = args.length > 0 ? (Object) args[0] : null;
                if (err != null) {
                    String errMsg = "";
                    if (err instanceof JSONObject) {
                        JSONObject obj = (JSONObject)args[0];
                        errMsg = obj.toString();
                    } else if (err instanceof Exception) {
                        ((Exception) err).printStackTrace();
                    } else if (err instanceof Error) {
                        errMsg = ((Error) err).getMessage();
                    }
                    Log.d("androidSocketIO", "error: " + errMsg);
                }
            }
        }).on(Socket.EVENT_MESSAGE, new Emitter.Listener() {

            @Override
            public void call(Object... args) {
                JSONObject obj = (JSONObject)args[0];
                Log.d("androidSocketIO", "message: " + obj.toString());
            }
        }).on("download", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                JSONObject obj = (JSONObject)args[0];
                try {
                    int command = obj.getInt("commandId");
                    String path = obj.getString("path");
                    String format = null;
                    if (obj.has("format")) {
                        format = obj.getString("format");
                    }
                    StreamOptions options = new StreamOptions();
                    options.pictureFormat = format;
                    File f = new File(mLocalRootPath + path);
//					Log.d("androidSocketIO", "download command received:" + path);
                    IOStream stream = new IOStream(options);
//					StreamSocket socket = new StreamSocket(mClient);
                    steaming.emit(command + "", 0, stream);
                    FileReadStream reader = FileIO.createReadStream(mLocalRootPath + path, options);
                    reader.pipe(stream.writeStream, null);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        steaming.on("upload", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {
                    IOStream stream = args.length > 0 ? (IOStream) args[0] : null;
                    JSONObject obj = args.length > 1 ? (JSONObject)args[1] : null;
                    int command = obj.getInt("commandId");
                    String path = obj.getString("path");
                    StreamOptions options = new StreamOptions();
                    FileWriteStream writer = FileIO.createWriteStream(mLocalRootPath + path, options);
                    stream.pipe(writer, null);
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        });
    }
}
