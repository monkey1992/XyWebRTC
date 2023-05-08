package com.xy.webrtc

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.net.URISyntaxException
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object SignalingClient {

    private const val TAG = "SignalingClient"

    private const val room = "OldPlace"

    private lateinit var socket: Socket

    private var callback: Callback? = null

    private val trustAll by lazy {
        arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                    Log.d(TAG, "checkClientTrusted")
                }

                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                    Log.d(TAG, "checkServerTrusted")
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> {
                    return emptyArray()
                }
            }
        )
    }

    init {
        init()
    }

    private fun init() {
        Log.d(TAG, "init start")
        try {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAll, null)
            IO.setDefaultHostnameVerifier { _: String?, _: SSLSession? -> true }
            IO.setDefaultSSLContext(sslContext)
            // Replace with your uri
            socket = IO.socket("http://172.20.10.2:8080").apply {
                connect()
                val socketId = id()
                emit("create or join", room)
                on("created") {
                    Log.d(TAG, "room created socketId: $socketId")
                    callback?.onCreateRoom()
                }
                on("full") { Log.d(TAG, "room full socketId: $socketId") }
                on("join") {
                    Log.d(TAG, "peer joined socketId: $socketId, ${Arrays.toString(it)}")
                    callback?.onPeerJoined(it[1].toString())
                }
                on("joined") {
                    Log.d(TAG, "self joined socketId: $socketId")
                    callback?.onSelfJoined()
                }
                on("log") {
                    Log.d(TAG, "log call socketId: $socketId, ${Arrays.toString(it)}")
                }
                on("bye") { args: Array<Any> ->
                    Log.d(TAG, "bye " + args[0])
                    callback?.onPeerLeave(args[0] as String)
                }
                on("message") { args: Array<Any?> ->
                    Log.d(TAG, "message " + args.contentToString())
                    val arg = args[0]
                    if (arg is JSONObject) {
                        when (arg.optString("type")) {
                            "offer" -> {
                                callback?.onOfferReceived(arg)
                            }
                            "answer" -> {
                                callback?.onAnswerReceived(arg)
                            }
                            "candidate" -> {
                                callback?.onIceCandidateReceived(arg)
                            }
                        }
                    }
                }
            }
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
            Log.e(TAG, "init " + e.localizedMessage)
        } catch (e: KeyManagementException) {
            e.printStackTrace()
            Log.e(TAG, "init " + e.localizedMessage)
        } catch (e: URISyntaxException) {
            e.printStackTrace()
            Log.e(TAG, "init " + e.localizedMessage)
        }
        Log.d(TAG, "init end")
    }

    fun setCallback(callback: Callback?) {
        this.callback = callback
    }

    fun sendIceCandidate(iceCandidate: IceCandidate?, to: String) {
        if (iceCandidate == null) {
            return
        }
        val jo = JSONObject()
        try {
            jo.put("type", "candidate")
            jo.put("label", iceCandidate.sdpMLineIndex)
            jo.put("id", iceCandidate.sdpMid)
            jo.put("candidate", iceCandidate.sdp)
            jo.put("from", socket.id())
            jo.put("to", to)
            socket.emit("message", jo)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    fun sendSessionDescription(sdp: SessionDescription?, to: String) {
        if (sdp == null) {
            return
        }
        val jo = JSONObject()
        try {
            jo.put("type", sdp.type.canonicalForm())
            jo.put("sdp", sdp.description)
            jo.put("from", socket.id())
            jo.put("to", to)
            socket.emit("message", jo)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    fun destroy() {
        socket.emit("bye", socket.id());
        socket.close()
    }

    interface Callback {

        fun onCreateRoom()

        fun onPeerJoined(socketId: String)

        fun onSelfJoined()

        fun onPeerLeave(msg: String?)

        fun onOfferReceived(data: JSONObject)

        fun onAnswerReceived(data: JSONObject)

        fun onIceCandidateReceived(data: JSONObject)
    }
}