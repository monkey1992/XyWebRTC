package com.xy.webrtc;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.socket.client.IO;
import io.socket.client.Socket;

public class SignalingClient {

    private static final String TAG = "SignalingClient";

    private static SignalingClient instance;

    private SignalingClient() {
        init();
    }

    public static SignalingClient get() {
        if (instance == null) {
            synchronized (SignalingClient.class) {
                if (instance == null) {
                    instance = new SignalingClient();
                }
            }
        }
        return instance;
    }

    private Socket socket;
    private final String room = "OldPlace";
    private Callback callback;

    private final TrustManager[] trustAll = new TrustManager[]{
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    Log.d(TAG, "checkClientTrusted");
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    Log.d(TAG, "checkServerTrusted");
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }
    };

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    private void init() {
        Log.d(TAG, "init start");
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAll, null);
            IO.setDefaultHostnameVerifier((hostname, session) -> true);
            IO.setDefaultSSLContext(sslContext);

//            socket = IO.socket("https://192.168.43.170:8080");
            socket = IO.socket("http://172.20.10.2:8080");
            socket.connect();

            socket.emit("create or join", room);

            socket.on("created", args -> {
                Log.d(TAG, "room created");
                callback.onCreateRoom();
            });
            socket.on("full", args -> {
                Log.d(TAG, "room full");
            });
            socket.on("join", args -> {
                Log.d(TAG, "peer joined");
                callback.onPeerJoined();
            });
            socket.on("joined", args -> {
                Log.d(TAG, "self joined");
                callback.onSelfJoined();
            });
            socket.on("log", args -> {
                Log.d(TAG, "log call " + Arrays.toString(args));
            });
            socket.on("bye", args -> {
                Log.d(TAG, "bye " + args[0]);
                callback.onPeerLeave((String) args[0]);
            });
            socket.on("message", args -> {
                Log.d(TAG, "message " + Arrays.toString(args));
                Object arg = args[0];
                if (arg instanceof String) {

                } else if (arg instanceof JSONObject) {
                    JSONObject data = (JSONObject) arg;
                    String type = data.optString("type");
                    if ("offer".equals(type)) {
                        callback.onOfferReceived(data);
                    } else if ("answer".equals(type)) {
                        callback.onAnswerReceived(data);
                    } else if ("candidate".equals(type)) {
                        callback.onIceCandidateReceived(data);
                    }
                }
            });
        } catch (NoSuchAlgorithmException | KeyManagementException | URISyntaxException e) {
            e.printStackTrace();
            Log.e(TAG, "init " + e.getLocalizedMessage());
        }
        Log.d(TAG, "init end");
    }

    public void sendIceCandidate(IceCandidate iceCandidate) {
        JSONObject jo = new JSONObject();
        try {
            jo.put("type", "candidate");
            jo.put("label", iceCandidate.sdpMLineIndex);
            jo.put("id", iceCandidate.sdpMid);
            jo.put("candidate", iceCandidate.sdp);

            socket.emit("message", jo);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void sendSessionDescription(SessionDescription sdp) {
        JSONObject jo = new JSONObject();
        try {
            jo.put("type", sdp.type.canonicalForm());
            jo.put("sdp", sdp.description);

            socket.emit("message", jo);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public interface Callback {

        void onCreateRoom();

        void onPeerJoined();

        void onSelfJoined();

        void onPeerLeave(String msg);

        void onOfferReceived(JSONObject data);

        void onAnswerReceived(JSONObject data);

        void onIceCandidateReceived(JSONObject data);
    }
}