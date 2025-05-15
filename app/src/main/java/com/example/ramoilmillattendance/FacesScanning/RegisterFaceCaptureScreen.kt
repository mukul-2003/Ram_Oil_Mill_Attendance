package com.example.ramoilmillattendance.FacesScanning

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.ramoilmillattendance.TopAppBar
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

@Composable
fun RegisterFaceCaptureScreen(navController: NavController, uid: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val faceEmbeddingHelper = remember { FaceEmbeddingHelper(context) }

    var message by remember { mutableStateOf("") }

    TopAppBar(
        navController = navController,
        context = context,
        drawerState = rememberDrawerState(DrawerValue.Closed),
        scope = rememberCoroutineScope(),
        screenTitle = "Scan Face"
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            1000
                        )
                    }

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val analyzer = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        var hasProcessed = false

                        analyzer.setAnalyzer(executor) { imageProxy ->
                            if (!hasProcessed) {
                                hasProcessed = true
                                processForEmbedding(
                                    imageProxy,
                                    context,
                                    faceEmbeddingHelper,
                                    uid,
                                    onSuccess = {
                                        message = "Face saved!"
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                        navController.navigate("registerFace") {
                                            popUpTo("registerFace") { inclusive = true }
                                        }
                                    },
                                    onError = {
                                        message = it
                                        hasProcessed = false
                                        Toast.makeText(context, "Error: $it", Toast.LENGTH_SHORT).show()
                                        Log.e("RegisterFace", "Embedding error: $it")
                                    }
                                )
                            } else imageProxy.close()
                        }

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_FRONT_CAMERA,
                                preview,
                                analyzer
                            )
                        } catch (e: Exception) {
                            Log.e("FaceCapture", "Camera bind failed", e)
                        }

                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(message)
        }
    }
}

@Suppress("UnsafeOptInUsageError")
private fun processForEmbedding(
    imageProxy: ImageProxy,
    context: android.content.Context,
    helper: FaceEmbeddingHelper,
    uid: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val mediaImage = imageProxy.image ?: run {
        imageProxy.close()
        return
    }

    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    val detector = FaceDetection.getClient()

    detector.process(inputImage)
        .addOnSuccessListener { faces ->
            if (faces.isNotEmpty()) {
                val bitmap = imageProxyToBitmap(imageProxy)
                val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 112, 112, true)
                val embedding = helper.getEmbedding(resizedBitmap)

                FirebaseFirestore.getInstance()
                    .collection("face_embeddings")
                    .document(uid)
                    .set(mapOf("embedding" to embedding.toList()))
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener {
                        onError("Firestore write failed: ${it.message}")
                    }
            } else {
                onError("No face detected")
            }
            imageProxy.close()
        }
        .addOnFailureListener {
            imageProxy.close()
            onError("Face detection failed: ${it.message}")
        }
}

@OptIn(ExperimentalGetImage::class)
fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
    val image = imageProxy.image ?: throw IllegalArgumentException("Image is null")
    val rotationDegrees = imageProxy.imageInfo.rotationDegrees

    val yBuffer = image.planes[0].buffer
    val uBuffer = image.planes[1].buffer
    val vBuffer = image.planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = android.graphics.YuvImage(
        nv21, android.graphics.ImageFormat.NV21, image.width, image.height, null
    )

    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(android.graphics.Rect(0, 0, image.width, image.height), 100, out)
    val yuv = out.toByteArray()
    val bitmap = android.graphics.BitmapFactory.decodeByteArray(yuv, 0, yuv.size)

    val matrix = Matrix()
    matrix.postRotate(rotationDegrees.toFloat())
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
