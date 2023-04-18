package com.xy.webrtc

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.webrtc.*
import org.webrtc.PeerConnection.IceServer
import org.webrtc.PeerConnectionFactory.InitializationOptions


class MainActivity : AppCompatActivity() {

    private var localPeerConnection: PeerConnection? = null
    private var remotePeerConnection: PeerConnection? = null
    private lateinit var localMediaStream: MediaStream
    private lateinit var remoteMediaStream: MediaStream

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

        // lcoal
        // localSurfaceViewRenderer
        val localSurfaceViewRenderer = findViewById<SurfaceViewRenderer>(R.id.svr_local)
        localSurfaceViewRenderer.setMirror(true)
        localSurfaceViewRenderer.init(eglBaseContext, null)
        // localVideoTrack
        val localVideoTrack = createVideoTrack(
            true,
            peerConnectionFactory,
            eglBaseContext,
            "surfaceTextureHelperLocalThread",
            "local_video_track"
        )
        // localMediaStream
        localMediaStream = peerConnectionFactory.createLocalMediaStream("localMediaStream")
        localMediaStream.addTrack(localVideoTrack)
        // localPeerConnection
        localPeerConnection = peerConnectionFactory.createPeerConnection(iceServers, object :
            PeerConnectionObserver() {
            override fun onIceCandidate(iceCandidate: IceCandidate?) {
                super.onIceCandidate(iceCandidate)
                remotePeerConnection?.addIceCandidate(iceCandidate)
            }

            override fun onAddStream(mediaStream: MediaStream?) {
                super.onAddStream(mediaStream)
                // 将 VideoTrack 展示到 SurfaceViewRenderer 中
                if (mediaStream == null) {
                    return
                }
                val videoTracks = mediaStream.videoTracks
                if (videoTracks.isNullOrEmpty()) {
                    return
                }
                val remoteVideoTrack = videoTracks[0] ?: return
                runOnUiThread { remoteVideoTrack.addSink(localSurfaceViewRenderer) }
            }
        })
        localPeerConnection?.addStream(localMediaStream)
        localPeerConnection?.createOffer(object : SessionDescriptionObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                // Local setLocalDescription
                localPeerConnection?.setLocalDescription(object :
                    SessionDescriptionObserver() {}, sessionDescription)
                // Remote setRemoteDescription
                remotePeerConnection?.setRemoteDescription(object :
                    SessionDescriptionObserver() {}, sessionDescription)

                remotePeerConnection?.addStream(remoteMediaStream)
                // createAnswer
                remotePeerConnection?.createAnswer(object : SessionDescriptionObserver() {
                    override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                        // Remote setLocalDescription
                        remotePeerConnection?.setLocalDescription(object :
                            SessionDescriptionObserver() {}, sessionDescription)
                        // Local setRemoteDescription
                        localPeerConnection?.setRemoteDescription(object :
                            SessionDescriptionObserver() {}, sessionDescription)
                    }
                }, MediaConstraints())
            }
        }, MediaConstraints())

        // remote
        // remoteSurfaceViewRenderer
        val remoteSurfaceViewRenderer = findViewById<SurfaceViewRenderer>(R.id.svr_remote)
        remoteSurfaceViewRenderer.setMirror(true)
        remoteSurfaceViewRenderer.init(eglBaseContext, null)
        val remoteVideoTrack = createVideoTrack(
            false,
            peerConnectionFactory,
            eglBaseContext,
            "surfaceTextureHelperRemoteThread",
            "remote_video_track"
        )
        val remoteMediaStream = peerConnectionFactory.createLocalMediaStream("remoteMediaStream")
        remoteMediaStream.addTrack(remoteVideoTrack)

        remotePeerConnection = peerConnectionFactory.createPeerConnection(iceServers, object :
            PeerConnectionObserver() {
            override fun onIceCandidate(iceCandidate: IceCandidate?) {
                super.onIceCandidate(iceCandidate)
                localPeerConnection?.addIceCandidate(iceCandidate)
            }

            override fun onAddStream(mediaStream: MediaStream?) {
                super.onAddStream(mediaStream)
                // 将 VideoTrack 展示到 SurfaceViewRenderer 中
                if (mediaStream == null) {
                    return
                }
                val videoTracks = mediaStream.videoTracks
                if (videoTracks.isNullOrEmpty()) {
                    return
                }
                val localVideoTrack = videoTracks[0] ?: return
                runOnUiThread { localVideoTrack.addSink(remoteSurfaceViewRenderer) }
            }
        })
    }

    private fun createVideoTrack(
        isFront: Boolean,
        peerConnectionFactory: PeerConnectionFactory,
        eglBaseContext: EglBase.Context,
        threadName: String,
        videoTrackId: String
    ): VideoTrack {
        // 创建 VideoCapturer
        val videoCapturer: VideoCapturer? = createCameraVideoCapturer(isFront)
        // 创建 VideoSource
        val videoSource =
            peerConnectionFactory.createVideoSource(videoCapturer?.isScreencast ?: false)
        val surfaceTextureHelper = SurfaceTextureHelper.create(threadName, eglBaseContext)
        // 初始化 VideoCapturer
        videoCapturer?.initialize(
            surfaceTextureHelper,
            applicationContext,
            videoSource.capturerObserver
        )
        // 启动 VideoCapturer
        videoCapturer?.startCapture(480, 640, 30)

        // 创建 VideoTrack
        return peerConnectionFactory.createVideoTrack(videoTrackId, videoSource)
    }

    private fun createCameraVideoCapturer(isFront: Boolean): CameraVideoCapturer? {
        val enumerator = Camera1Enumerator(false)
        val deviceNames = enumerator.deviceNames
        if (deviceNames.isNullOrEmpty()) {
            return null
        }
        for (deviceName in deviceNames) {
            if (isFront) {
                if (enumerator.isFrontFacing(deviceName)) {
                    val videoCapturer = enumerator.createCapturer(deviceName, null)
                    if (videoCapturer != null) {
                        return videoCapturer
                    }
                }
            } else {
                if (enumerator.isBackFacing(deviceName)) {
                    val videoCapturer = enumerator.createCapturer(deviceName, null)
                    if (videoCapturer != null) {
                        return videoCapturer
                    }
                }
            }
        }
        return null
    }

    private fun call(localMediaStream: MediaStream, remoteMediaStream: MediaStream) {

    }
}