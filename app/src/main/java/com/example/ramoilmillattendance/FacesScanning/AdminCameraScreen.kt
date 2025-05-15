package com.example.ramoilmillattendance.loginsystem

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.example.ramoilmillattendance.FacesScanning.FaceEmbeddingHelper
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import kotlin.math.sqrt

@Composable
fun AdminCameraScreen(navController: NavController) {
    val context = LocalContext.current
    val activity = context as? Activity
    val executor = remember { Executors.newSingleThreadExecutor() }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val faceEmbeddingHelper = remember { FaceEmbeddingHelper(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    var resultMessage by remember { mutableStateOf("") }
    var attendanceMarked by remember { mutableStateOf("Absent") }
    var cameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    LaunchedEffect(Unit) {
        if (!cameraPermissionGranted && activity != null) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.CAMERA),
                100
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (cameraPermissionGranted) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }

                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val analyzer = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            var hasProcessed = false

                            analyzer.setAnalyzer(executor) { imageProxy ->
                                if (!hasProcessed && attendanceMarked == "Absent") {
                                    hasProcessed = true

                                    processRecognitionFlow(
                                        imageProxy,
                                        faceEmbeddingHelper,
                                        context,
                                        onMatched = { name, matchedUid ->
                                            attendanceMarked = "Present"
                                            Toast.makeText(
                                                context,
                                                "Attendance marked for: $name",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            markAttendance(matchedUid, context)
                                            resultMessage = "Attendance marked for: $name"
                                            Handler(Looper.getMainLooper()).postDelayed({
                                                navController.navigate("adminHome") {
                                                    popUpTo("adminCamera") { inclusive = true }
                                                }
                                            }, 1000)
                                        },
                                        onFailed = {
                                            resultMessage = "No match found"
                                            hasProcessed = false
                                        }
                                    )
                                } else {
                                    imageProxy.close()
                                }
                            }

                            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                analyzer
                            )
                        } catch (e: Exception) {
                            Log.e("AdminCameraScreen", "Camera setup failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            Text(
                text = "Camera permission not granted. Please allow it in settings.",
                modifier = Modifier.padding(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = resultMessage,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Suppress("UnsafeOptInUsageError")
private fun processRecognitionFlow(
    imageProxy: ImageProxy,
    helper: FaceEmbeddingHelper,
    context: android.content.Context,
    onMatched: (String, String) -> Unit,
    onFailed: () -> Unit
) {
    val mediaImage = imageProxy.image ?: run {
        imageProxy.close()
        return
    }

    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    val detector = FaceDetection.getClient()

    detector.process(image)
        .addOnSuccessListener { faces ->
            if (faces.isNotEmpty()) {
                val bitmap = imageProxyToBitmap(imageProxy)
                val liveEmbedding = helper.getEmbedding(bitmap)

                FirebaseFirestore.getInstance().collection("face_embeddings").get()
                    .addOnSuccessListener { snapshot ->
                        var bestMatchUid: String? = null
                        var lowestDistance = Float.MAX_VALUE

                        for (doc in snapshot.documents) {
                            val uid = doc.id
                            val storedEmbedding =
                                (doc.get("embedding") as? List<Double>)?.map { it.toFloat() }
                                    ?.toFloatArray()
                            if (storedEmbedding != null && storedEmbedding.size == 192) {
                                val distance = euclideanDistance(liveEmbedding, storedEmbedding)
                                if (distance < 0.95f && distance < lowestDistance) {
                                    lowestDistance = distance
                                    bestMatchUid = uid
                                }
                            }
                        }

                        if (bestMatchUid != null) {
                            FirebaseFirestore.getInstance().collection("users").document(bestMatchUid)
                                .get()
                                .addOnSuccessListener { doc ->
                                    val name = doc.getString("name") ?: "Unknown"
                                    onMatched(name, bestMatchUid)
                                }
                        } else {
                            onFailed()
                        }
                        imageProxy.close()
                    }
                    .addOnFailureListener {
                        Log.e("FaceRecognition", "Failed to match face embeddings: ${it.message}")
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
        .addOnFailureListener {
            Log.e("FaceRecognition", "Face detection failed: ${it.message}")
            imageProxy.close()
        }
}

private fun markAttendance(uid: String, context: android.content.Context) {
    val db = FirebaseFirestore.getInstance()
    val now = Date()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    val date = dateFormat.format(now)
    val time = timeFormat.format(now)

    val data = mapOf(
        "attendance" to mapOf(
            date to mapOf(
                "status" to "Present",
                "time" to time
            )
        )
    )

    db.collection("attendance").document(uid)
        .set(data, SetOptions.merge())
        .addOnSuccessListener {
            Log.d("FaceRecognition", "Attendance marked for $uid")
        }
        .addOnFailureListener {
            Log.e("FaceRecognition", "Failed to mark attendance: ${it.message}")
            Toast.makeText(context, "Error saving attendance: ${it.message}", Toast.LENGTH_LONG).show()
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

private fun euclideanDistance(a: FloatArray, b: FloatArray): Float {
    var sum = 0f
    for (i in a.indices) {
        sum += (a[i] - b[i]) * (a[i] - b[i])
    }
    return sqrt(sum)
}
