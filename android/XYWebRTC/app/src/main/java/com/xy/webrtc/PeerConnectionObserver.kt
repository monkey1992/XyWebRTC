package com.xy.webrtc

import android.util.Log
import org.webrtc.*
import org.webrtc.PeerConnection.Observer

abstract class PeerConnectionObserver : Observer {

    override fun onSignalingChange(signalingState: PeerConnection.SignalingState?) {
        Log.d(TAG, "onSignalingChange $signalingState")
    }

    override fun onIceConnectionChange(iceConnectionState: PeerConnection.IceConnectionState?) {
        Log.d(TAG, "onIceConnectionChange $iceConnectionState")
    }

    override fun onIceConnectionReceivingChange(p0: Boolean) {
        Log.d(TAG, "onIceConnectionReceivingChange $p0")
    }

    override fun onIceGatheringChange(iceGatheringState: PeerConnection.IceGatheringState?) {
        Log.d(TAG, "onIceGatheringChange $iceGatheringState")
    }

    override fun onIceCandidate(iceCandidate: IceCandidate?) {
        Log.d(TAG, "onIceCandidate $iceCandidate")
    }

    override fun onIceCandidatesRemoved(iceCandidates: Array<out IceCandidate>?) {
        Log.d(TAG, "onIceCandidatesRemoved $iceCandidates")
    }

    override fun onAddStream(mediaStream: MediaStream?) {
        Log.d(TAG, "onAddStream $mediaStream")
    }

    override fun onRemoveStream(mediaStream: MediaStream?) {
        Log.d(TAG, "onRemoveStream $mediaStream")
    }

    override fun onDataChannel(dataChannel: DataChannel?) {
        Log.d(TAG, "onDataChannel $dataChannel")
    }

    override fun onRenegotiationNeeded() {
        Log.d(TAG, "onRenegotiationNeeded")
    }

    override fun onAddTrack(rtpReceiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
        Log.d(TAG, "onAddTrack $rtpReceiver, $mediaStreams")
    }

    companion object {

        private const val TAG = "PeerConnectionObserver"
    }
}