package com.xy.webrtc

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.webrtc.*
import org.webrtc.PeerConnectionFactory.InitializationOptions

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 创建 PeerConnectionFactory
        val initializationOptions =
            InitializationOptions.builder(this).createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)
        val peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory()

        // 创建 VideoCapturer
        val videoCapturer: VideoCapturer? = createCameraVideoCapturer()
        // 创建 VideoSource
        val videoSource =
            peerConnectionFactory.createVideoSource(videoCapturer?.isScreencast ?: false)
        val eglBaseContext = EglBase.create().eglBaseContext
        val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBaseContext)
        // 初始化 VideoCapturer
        videoCapturer?.initialize(
            surfaceTextureHelper,
            applicationContext,
            videoSource.capturerObserver
        )
        // 启动 VideoCapturer
        videoCapturer?.startCapture(480, 640, 30)

        // 创建 VideoTrack
        val videoTrack = peerConnectionFactory.createVideoTrack("101", videoSource)

        // 初始化 SurfaceViewRenderer
        val surfaceViewRenderer = findViewById<SurfaceViewRenderer>(R.id.surface_view_renderer)
        surfaceViewRenderer.setMirror(true)
        surfaceViewRenderer.init(eglBaseContext, null)

        // 将 VideoTrack 展示到 SurfaceViewRenderer 中
        videoTrack.addSink(surfaceViewRenderer)
    }

    private fun createCameraVideoCapturer(): CameraVideoCapturer? {
        val enumerator = Camera1Enumerator(false)
        val deviceNames = enumerator.deviceNames
        if (deviceNames.isNullOrEmpty()) {
            return null
        }
        // First, try to find front facing camera
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val videoCapturer: CameraVideoCapturer? =
                    enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }
        // Front facing camera not found, try something else
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                val videoCapturer: CameraVideoCapturer? =
                    enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }
        return null
    }
}