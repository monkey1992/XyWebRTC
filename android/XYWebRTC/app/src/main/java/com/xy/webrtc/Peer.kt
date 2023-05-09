package com.xy.webrtc

import android.content.Context
import org.webrtc.*

class Peer(
    private val context: Context,
    private val eglBaseContext: EglBase.Context,
    private val peerConnectionFactory: PeerConnectionFactory,
) : IPeer {

    override fun createVideoTrack(
        isFront: Boolean,
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
            context.applicationContext,
            videoSource.capturerObserver
        )
        // 启动 VideoCapturer
        videoCapturer?.startCapture(480, 640, 30)

        // 创建 VideoTrack
        return peerConnectionFactory.createVideoTrack(videoTrackId, videoSource)
    }

    override fun createAudioTrack(audioTrackId: String): AudioTrack {
        // 创建 AudioSource
        val audioSource =
            peerConnectionFactory.createAudioSource(MediaConstraints())
        // 创建 AudioTrack
        return peerConnectionFactory.createAudioTrack(audioTrackId, audioSource)
    }

    override fun createLocalMediaStream(label: String): MediaStream {
        return peerConnectionFactory.createLocalMediaStream(label)
    }

    override fun addVideoTrack(mediaStream: MediaStream, videoTrack: VideoTrack) {
        mediaStream.addTrack(videoTrack)
    }

    override fun addAudioTrack(mediaStream: MediaStream, audioTrack: AudioTrack) {
        mediaStream.addTrack(audioTrack)
    }

    override fun createPeerConnection(
        iceServers: List<PeerConnection.IceServer>,
        observer: PeerConnection.Observer
    ): PeerConnection? {
        return peerConnectionFactory.createPeerConnection(iceServers, observer)
    }

    override fun addStream(peerConnection: PeerConnection?, stream: MediaStream): Boolean {
        return peerConnection?.addStream(stream) ?: false
    }

    override fun createOffer(
        peerConnection: PeerConnection?,
        observer: SdpObserver?,
        constraints: MediaConstraints?
    ) {
        peerConnection?.createOffer(observer, constraints)
    }

    override fun setLocalDescription(
        peerConnection: PeerConnection?,
        observer: SdpObserver?,
        sdp: SessionDescription?
    ) {
        peerConnection?.setLocalDescription(observer, sdp)
    }

    override fun setRemoteDescription(
        peerConnection: PeerConnection?,
        observer: SdpObserver?,
        sdp: SessionDescription?
    ) {
        peerConnection?.setRemoteDescription(observer, sdp)
    }

    override fun createAnswer(
        peerConnection: PeerConnection?,
        observer: SdpObserver?,
        constraints: MediaConstraints?
    ) {
        peerConnection?.createAnswer(observer, constraints)
    }

    override fun addIceCandidate(
        peerConnection: PeerConnection?,
        candidate: IceCandidate?
    ): Boolean {
        return peerConnection?.addIceCandidate(candidate) ?: false
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
}