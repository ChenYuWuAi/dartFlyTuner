package com.iiise.dartflytuner

import android.Manifest                                       // 权限常量
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider            // 绑定生命周期
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions    // 扫码选项
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.barcode.common.Barcode

class ScannerActivity : AppCompatActivity() {
    private val PERM_REQ_CAMERA = 1002
    private val REQUEST_PICK_IMAGE = 1003
    private lateinit var previewView: PreviewView

    // 调用系统图片选择器
    private fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
        }
        startActivityForResult(Intent.createChooser(intent, "选择图片"), REQUEST_PICK_IMAGE)
    }


    private fun scanImageFromUri(uri: Uri) {
        try {
            // 根据文件路径创建 InputImage :contentReference[oaicite:4]{index=4}
            val image = InputImage.fromFilePath(this, uri)
            val scanner = BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build()
            ) // 指定只扫码 QR 码可提速 :contentReference[oaicite:5]{index=5}

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        barcode.rawValue?.let { value ->
                            setResult(RESULT_OK, Intent().putExtra("SCANNED_JSON", value))
                            finish()
                            return@addOnSuccessListener
                        }
                    }
                    Toast.makeText(this, "未检测到二维码", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "扫码失败: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Toast.makeText(this, "无法读取图片: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                scanImageFromUri(uri)
            }
        }
    }

    // Analyzer：将每帧交给 ML Kit 扫描条码
    private inner class YourImageAnalyzer : ImageAnalysis.Analyzer {
        @androidx.annotation.OptIn(ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image =
                    InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                BarcodeScanning.getClient(
                    BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .build()
                ).process(image)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            barcode.rawValue?.let { value ->
                                val resultIntent = android.content.Intent().apply {
                                    putExtra("SCANNED_JSON", value)
                                }
                                setResult(RESULT_OK, resultIntent)
                                finish()
                                return@addOnSuccessListener
                            }
                        }
                    }
                    .addOnCompleteListener { imageProxy.close() }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanner)
        previewView = findViewById(R.id.previewView)
        findViewById<MaterialButton>(R.id.btn_pick_image).setOnClickListener {
            pickImageFromGallery()
        }

        // 动态申请相机权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), PERM_REQ_CAMERA
            )
        } else {
            startCamera()
        }
    }

    // 权限回调
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_REQ_CAMERA &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            Toast.makeText(this, "需要相机权限才能扫码", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // Preview 用例
            val previewUseCase = Preview.Builder().build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            // Analysis 用例
            val analysisUseCase = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(ContextCompat.getMainExecutor(this), YourImageAnalyzer())
                }

            // 解绑旧的，用例绑定到生命周期
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this, cameraSelector, previewUseCase, analysisUseCase
            )
        }, ContextCompat.getMainExecutor(this))
    }
}
