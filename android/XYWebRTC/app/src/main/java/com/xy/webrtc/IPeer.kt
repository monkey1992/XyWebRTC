package com.xy.webrtc

import org.webrtc.*
import org.webrtc.PeerConnection.IceServer

interface IPeer {

    fun createVideoTrack(isFront: Boolean, threadName: String, videoTrackId: String): VideoTrack

    fun createAudioTrack(audioTrackId: String): AudioTrack

    fun createLocalMediaStream(label: String): MediaStream

    fun addVideoTrack(mediaStream: MediaStream, videoTrack: VideoTrack)

    fun addAudioTrack(mediaStream: MediaStream, audioTrack: AudioTrack)

    fun createPeerConnection(
        iceServers: List<IceServer>,
        observer: PeerConnection.Observer
    ): PeerConnection?

    fun addStream(peerConnection: PeerConnection?, stream: MediaStream): Boolean

    fun createOffer(
        peerConnection: PeerConnection?,
        observer: SdpObserver?,
        constraints: MediaConstraints?
    )

    fun setLocalDescription(
        peerConnection: PeerConnection?,
        observer: SdpObserver?,
        sdp: SessionDescription?
    )

    fun setRemoteDescription(
        peerConnection: PeerConnection?,
        observer: SdpObserver?,
        sdp: SessionDescription?
    )

    fun createAnswer(
        peerConnection: PeerConnection?,
        observer: SdpObserver?,
        constraints: MediaConstraints?
    )

    fun addIceCandidate(peerConnection: PeerConnection?, candidate: IceCandidate?): Boolean
}