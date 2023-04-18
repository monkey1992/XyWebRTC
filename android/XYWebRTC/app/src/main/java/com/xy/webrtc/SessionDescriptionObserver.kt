package com.xy.webrtc

import android.util.Log
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

abstract class SessionDescriptionObserver : SdpObserver {

    override fun onCreateSuccess(sessionDescription: SessionDescription?) {
        Log.d(TAG, "onCreateSuccess $sessionDescription")
    }

    override fun onSetSuccess() {
        Log.d(TAG, "onSetSuccess")
    }

    override fun onCreateFailure(p0: String?) {
        Log.d(TAG, "onCreateFailure $p0")
    }

    override fun onSetFailure(p0: String?) {
        Log.d(TAG, "onSetFailure $p0")
    }

    companion object {

        private const val TAG = "SdpObserver"
    }
}