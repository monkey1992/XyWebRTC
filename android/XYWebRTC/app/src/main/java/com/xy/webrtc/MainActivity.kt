package com.xy.webrtc

import android.Manifest
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import org.webrtc.*
import org.webrtc.PeerConnection.IceServer
import org.webrtc.PeerConnectionFactory.InitializationOptions
import java.util.*


class MainActivity : AppCompatActivity(), SignalingClient.Callback {

    private val eglBaseContext by lazy { EglBase.create().eglBaseContext }

    private lateinit var peer: IPeer

    private val peerConnectionMap: HashMap<String, PeerConnection> by lazy {
        HashMap()
    }

    private val iceServers: ArrayList<IceServer> by lazy {
        ArrayList<IceServer>().apply {
            add(IceServer.builder(STUN_SERVER_URL).createIceServer())
        }
    }

    private lateinit var mediaStream: MediaStream

    private lateinit var surfaceViewRendererContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        surfaceViewRendererContainer = findViewById(R.id.ll_svr_container)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                ), REQUEST_PERMISSIONS_CODE
            )
        } else {
            startWebRTC()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS_CODE) {
            if (grantResults.all { it == PERMISSION_GRANTED }) {
                startWebRTC()
            }
        }
    }

    private fun startWebRTC() {
        // 创建 PeerConnectionFactory
        val initializationOptions =
            InitializationOptions.builder(this).createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)
        val options = PeerConnectionFactory.Options()
        val defaultVideoEncoderFactory = DefaultVideoEncoderFactory(eglBaseContext, true, true)
        val defaultVideoDecoderFactory = DefaultVideoDecoderFactory(eglBaseContext)
        val peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoEncoderFactory(defaultVideoEncoderFactory)
            .setVideoDecoderFactory(defaultVideoDecoderFactory)
            .createPeerConnectionFactory()

        // localSurfaceViewRenderer
        val localSurfaceViewRenderer = findViewById<SurfaceViewRenderer>(R.id.svr_local)
        localSurfaceViewRenderer.setMirror(false)
        localSurfaceViewRenderer.init(eglBaseContext, null)

        // peer
        peer = Peer(this, eglBaseContext, peerConnectionFactory)

        // VideoTrack
        val videoTrack = peer.createVideoTrack(
            false,
            "videoTrack",
            "videoTrack_${UUID.randomUUID()}"
        )
        videoTrack.addSink(localSurfaceViewRenderer)

        // localMediaStream
        mediaStream = peer.createLocalMediaStream("localMediaStream")
        // localMediaStream addTrack
        peer.addTrack(mediaStream, videoTrack)

        SignalingClient.setCallback(this)
    }

    private fun addRemoteSurfaceViewRender(): SurfaceViewRenderer {
        val surfaceViewRenderer = SurfaceViewRenderer(this)
        surfaceViewRenderer.setMirror(false)
        surfaceViewRenderer.init(eglBaseContext, null)
        surfaceViewRendererContainer.addView(
            surfaceViewRenderer,
            LinearLayout.LayoutParams.MATCH_PARENT,
            (300 * resources.displayMetrics.density).toInt()
        )
        return surfaceViewRenderer
    }

    private fun getOrCreatePeerConnection(socketId: String): PeerConnection? {
        var peerConnection = peerConnectionMap[socketId]
        if (peerConnection != null) {
            return peerConnection
        }
        peerConnection = peer.createPeerConnection(iceServers, object : PeerConnectionObserver() {
            override fun onIceCandidate(iceCandidate: IceCandidate?) {
                super.onIceCandidate(iceCandidate)
                SignalingClient.sendIceCandidate(iceCandidate, socketId)
            }

            override fun onAddStream(mediaStream: MediaStream?) {
                super.onAddStream(mediaStream)
                // 接收到来自远端的 MediaStream
                if (mediaStream == null) {
                    return
                }
                val videoTracks = mediaStream.videoTracks
                if (videoTracks.isNullOrEmpty()) {
                    return
                }
                val remoteVideoTrack = videoTracks[0] ?: return
                // 将 remoteVideoTrack 展示到 remoteSurfaceViewRenderer 中
                runOnUiThread { remoteVideoTrack.addSink(addRemoteSurfaceViewRender()) }
            }
        })?.apply {
            addStream(mediaStream)
            peerConnectionMap[socketId] = this
        }
        return peerConnection
    }

    override fun onCreateRoom() {
    }

    override fun onPeerJoined(socketId: String) {
        getOrCreatePeerConnection(socketId)?.apply {
            createOffer(object : SessionDescriptionObserver() {
                override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                    super.onCreateSuccess(sessionDescription)
                    setLocalDescription(
                        object : SessionDescriptionObserver() {},
                        sessionDescription
                    )
                    SignalingClient.sendSessionDescription(sessionDescription, socketId)
                }
            }, MediaConstraints())
        }
    }

    override fun onSelfJoined() {
    }

    override fun onPeerLeave(msg: String?) {
    }

    override fun onOfferReceived(data: JSONObject) {
        val socketId = data.optString("from")
        getOrCreatePeerConnection(socketId)?.apply {
            setRemoteDescription(
                object : SessionDescriptionObserver() {},
                SessionDescription(SessionDescription.Type.OFFER, data.optString("sdp"))
            )
            createAnswer(object : SessionDescriptionObserver() {
                override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                    super.onCreateSuccess(sessionDescription)
                    setLocalDescription(
                        object : SessionDescriptionObserver() {},
                        sessionDescription
                    )
                    SignalingClient.sendSessionDescription(sessionDescription, socketId)
                }
            }, MediaConstraints())
        }
    }

    override fun onAnswerReceived(data: JSONObject) {
        getOrCreatePeerConnection(data.optString("from"))?.setRemoteDescription(
            object : SessionDescriptionObserver() {},
            SessionDescription(SessionDescription.Type.ANSWER, data.optString("sdp"))
        )
    }

    override fun onIceCandidateReceived(data: JSONObject) {
        getOrCreatePeerConnection(data.optString("from"))?.addIceCandidate(
            IceCandidate(
                data.optString("id"),
                data.optInt("label"),
                data.optString("candidate")
            )
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        SignalingClient.destroy()
    }

    companion object {

        private const val REQUEST_PERMISSIONS_CODE = 0

        private const val STUN_SERVER_URL = "stun:stun.l.google.com:19302"
    }
}