package com.xy.webrtc

import android.Manifest
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import org.webrtc.*
import org.webrtc.PeerConnection.IceServer
import org.webrtc.PeerConnectionFactory.InitializationOptions
import java.util.*

class MainActivity : AppCompatActivity(), SignalingClient.Callback {

    private lateinit var peer: IPeer

    private var peerConnection: PeerConnection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
        // 创建 EglBase.Context
        val eglBaseContext = EglBase.create().eglBaseContext

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

        // remoteSurfaceViewRenderer
        val remoteSurfaceViewRenderer = findViewById<SurfaceViewRenderer>(R.id.svr_remote)
        remoteSurfaceViewRenderer.setMirror(false)
        remoteSurfaceViewRenderer.init(eglBaseContext, null)

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
        val localMediaStream = peer.createLocalMediaStream("localMediaStream")
        // localMediaStream addTrack
        peer.addTrack(localMediaStream, videoTrack)

        SignalingClient.get().setCallback(this)

        // iceServers
        val iceServers: ArrayList<IceServer> = ArrayList()
        iceServers.add(IceServer.builder("stun:stun.l.google.com:19302").createIceServer())

        // Create PeerConnection
        peerConnection = peer.createPeerConnection(iceServers, object :
            PeerConnectionObserver() {
            override fun onIceCandidate(iceCandidate: IceCandidate?) {
                super.onIceCandidate(iceCandidate)
                // Send IceCandidate
                SignalingClient.get().sendIceCandidate(iceCandidate)
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
                runOnUiThread { remoteVideoTrack.addSink(remoteSurfaceViewRenderer) }
            }
        })?.apply {
            addStream(localMediaStream)
        }
    }

    override fun onCreateRoom() {
    }

    override fun onPeerJoined() {
    }

    override fun onSelfJoined() {
        peerConnection?.createOffer(object : SessionDescriptionObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                super.onCreateSuccess(sessionDescription)
                peerConnection?.setLocalDescription(
                    object : SessionDescriptionObserver() {},
                    sessionDescription
                )
                SignalingClient.get().sendSessionDescription(sessionDescription)
            }
        }, MediaConstraints())
    }

    override fun onPeerLeave(msg: String?) {
    }

    override fun onOfferReceived(data: JSONObject?) {
        peerConnection?.setRemoteDescription(
            object : SessionDescriptionObserver() {},
            SessionDescription(SessionDescription.Type.OFFER, data?.optString("sdp"))
        )
        peerConnection?.createAnswer(object : SessionDescriptionObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                super.onCreateSuccess(sessionDescription)
                peerConnection?.setLocalDescription(
                    object : SessionDescriptionObserver() {},
                    sessionDescription
                )
                SignalingClient.get().sendSessionDescription(sessionDescription)
            }
        }, MediaConstraints())
    }

    override fun onAnswerReceived(data: JSONObject?) {
        peerConnection?.setRemoteDescription(
            object : SessionDescriptionObserver() {},
            SessionDescription(SessionDescription.Type.ANSWER, data?.optString("sdp"))
        )
    }

    override fun onIceCandidateReceived(data: JSONObject?) {
        peerConnection?.addIceCandidate(
            IceCandidate(
                data?.optString("id"),
                data?.optInt("label") ?: 0,
                data?.optString("candidate")
            )
        )
    }

    companion object {

        private const val REQUEST_PERMISSIONS_CODE = 0
    }
}