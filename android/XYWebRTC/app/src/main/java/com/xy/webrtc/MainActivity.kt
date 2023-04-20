package com.xy.webrtc

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.webrtc.*
import org.webrtc.PeerConnection.IceServer
import org.webrtc.PeerConnectionFactory.InitializationOptions

class MainActivity : AppCompatActivity() {

    private lateinit var localPeer: IPeer

    private lateinit var remotePeer: IPeer

    private var localPeerConnection: PeerConnection? = null

    private var remotePeerConnection: PeerConnection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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

        // iceServers
        val iceServers: List<IceServer> = ArrayList()

        // Local
        // localSurfaceViewRenderer
        val localSurfaceViewRenderer = findViewById<SurfaceViewRenderer>(R.id.svr_local)
        localSurfaceViewRenderer.setMirror(true)
        localSurfaceViewRenderer.init(eglBaseContext, null)
        // localPeer
        localPeer = Peer(this, eglBaseContext, peerConnectionFactory)
        // localVideoTrack
        val localVideoTrack = localPeer.createVideoTrack(
            true,
            "surfaceTextureHelperLocalThread",
            "local_video_track"
        )
        // localMediaStream
        val localMediaStream = localPeer.createLocalMediaStream("localMediaStream")
        // Local addTrack
        localPeer.addTrack(localMediaStream, localVideoTrack)
        // localPeerConnection
        localPeerConnection = localPeer.createPeerConnection(iceServers, object :
            PeerConnectionObserver() {
            override fun onIceCandidate(iceCandidate: IceCandidate?) {
                super.onIceCandidate(iceCandidate)
                // Remote addIceCandidate
                remotePeer.addIceCandidate(remotePeerConnection, iceCandidate)
            }

            override fun onAddStream(mediaStream: MediaStream?) {
                super.onAddStream(mediaStream)
                // 接收到来自 remotePeer 的 MediaStream
                if (mediaStream == null) {
                    return
                }
                val videoTracks = mediaStream.videoTracks
                if (videoTracks.isNullOrEmpty()) {
                    return
                }
                val videoTrack = videoTracks[0] ?: return
                // 将 videoTrack 展示到 localSurfaceViewRenderer 中
                runOnUiThread { videoTrack.addSink(localSurfaceViewRenderer) }
            }
        })
        // Local addStream
        localPeer.addStream(localPeerConnection, localMediaStream)

        // Remote
        // remoteSurfaceViewRenderer
        val remoteSurfaceViewRenderer = findViewById<SurfaceViewRenderer>(R.id.svr_remote)
        remoteSurfaceViewRenderer.setMirror(true)
        remoteSurfaceViewRenderer.init(eglBaseContext, null)
        // remotePeer
        remotePeer = Peer(this, eglBaseContext, peerConnectionFactory)
        // remoteVideoTrack
        val remoteVideoTrack = remotePeer.createVideoTrack(
            false,
            "surfaceTextureHelperRemoteThread",
            "remote_video_track"
        )
        // remoteMediaStream
        val remoteMediaStream = remotePeer.createLocalMediaStream("remoteMediaStream")
        // Remote addTrack
        remotePeer.addTrack(remoteMediaStream, remoteVideoTrack)
        // remotePeerConnection
        remotePeerConnection = remotePeer.createPeerConnection(iceServers, object :
            PeerConnectionObserver() {
            override fun onIceCandidate(iceCandidate: IceCandidate?) {
                super.onIceCandidate(iceCandidate)
                // Local addIceCandidate
                localPeer.addIceCandidate(localPeerConnection, iceCandidate)
            }

            override fun onAddStream(mediaStream: MediaStream?) {
                super.onAddStream(mediaStream)
                // 接收到来自 localPeer 的 MediaStream
                if (mediaStream == null) {
                    return
                }
                val videoTracks = mediaStream.videoTracks
                if (videoTracks.isNullOrEmpty()) {
                    return
                }
                val videoTrack = videoTracks[0] ?: return
                // 将 videoTrack 展示到 remoteSurfaceViewRenderer 中
                runOnUiThread { videoTrack.addSink(remoteSurfaceViewRenderer) }
            }
        })
        // Remote addStream
        remotePeer.addStream(remotePeerConnection, remoteMediaStream)

        // Local peer to Remote peer
        // Local createOffer
        localPeer.createOffer(localPeerConnection, object : SessionDescriptionObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                super.onCreateSuccess(sessionDescription)
                // Local setLocalDescription
                localPeer.setLocalDescription(localPeerConnection, object :
                    SessionDescriptionObserver() {}, sessionDescription)
                // Remote setRemoteDescription
                remotePeer.setRemoteDescription(remotePeerConnection, object :
                    SessionDescriptionObserver() {}, sessionDescription)

                // Remote createAnswer
                remotePeer.createAnswer(
                    remotePeerConnection,
                    object : SessionDescriptionObserver() {
                        override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                            super.onCreateSuccess(sessionDescription)
                            // Remote setLocalDescription
                            remotePeer.setLocalDescription(remotePeerConnection, object :
                                SessionDescriptionObserver() {}, sessionDescription)
                            // Local setRemoteDescription
                            localPeer.setRemoteDescription(localPeerConnection, object :
                                SessionDescriptionObserver() {}, sessionDescription)
                        }
                    },
                    MediaConstraints()
                )
            }
        }, MediaConstraints())
    }
}