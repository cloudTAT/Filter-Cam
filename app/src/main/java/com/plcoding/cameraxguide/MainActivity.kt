@file:OptIn(ExperimentalMaterial3Api::class)

package com.plcoding.cameraxguide

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture.OnImageCapturedCallback
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.plcoding.cameraxguide.ui.theme.CameraXGuideTheme
import kotlinx.coroutines.launch

//IMPORTS FOR IMAGE PROCESSING
import android.content.Context;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.Scalar
import org.opencv.core.Size


//IMAGE PROCESSING
object ImageUtils {

    fun enhanceImageQuality(inputBitmap: Bitmap, contrast: Float, brightness: Int, sharpenAmount: Float): Bitmap {
        // Convert Bitmap to Mat (OpenCV format)
        val inputMat = Mat()
        Utils.bitmapToMat(inputBitmap, inputMat)

        // Create a blank Mat for the output image
        val outputMat = Mat(inputMat.size(), inputMat.type())

        // Apply contrast and brightness adjustment
        inputMat.convertTo(outputMat, -1, contrast.toDouble(), brightness.toDouble())

        // Apply sharpening
        val sharpenedMat = Mat()
        Imgproc.GaussianBlur(outputMat, sharpenedMat, org.opencv.core.Size(0.0, 0.0), 10.0)
        Core.addWeighted(outputMat, 1.5 + sharpenAmount, sharpenedMat, -0.5 * sharpenAmount, 0.0, sharpenedMat)

        // Convert Mat back to Bitmap
        val outputBitmap = Bitmap.createBitmap(sharpenedMat.cols(), sharpenedMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(sharpenedMat, outputBitmap)

        // Release Mats
        inputMat.release()
        outputMat.release()
        sharpenedMat.release()

        return outputBitmap
    }
}
    fun applyGaussianBlur(context: Context, source: Bitmap, radius: Float): Bitmap {
        // Create a new bitmap for the blurred image
        val blurredBitmap = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)

        // Create RenderScript
        val rs = RenderScript.create(context)

        // Create an allocation from Bitmap
        val input = Allocation.createFromBitmap(rs, source, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT)

        // Create allocation for output
        val output = Allocation.createTyped(rs, input.type)

        // Create Gaussian blur script
        val script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
        script.setInput(input)

        // Set the blur radius
        script.setRadius(radius)

        // Run the blur script
        script.forEach(output)

        // Copy the output allocation to the blurred bitmap
        output.copyTo(blurredBitmap)

        // Release resources
        rs.destroy()

        return blurredBitmap
    }

    fun extractOutlines(capturedBitmap: Bitmap): Bitmap {
        // Convert Bitmap to Mat (OpenCV format)
        val mat = Mat()
        Utils.bitmapToMat(capturedBitmap, mat)

        // Convert the image to grayscale
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2GRAY)

        // Apply Gaussian blur to reduce noise
        val blurred = Mat()
        Imgproc.GaussianBlur(mat, blurred, Size(5.0, 5.0), 0.0)

        // Perform Canny edge detection
        val edges = Mat()
        Imgproc.Canny(blurred, edges, 50.0, 150.0)

        // Convert edges Mat back to Bitmap
        val edgesBitmap = Bitmap.createBitmap(edges.cols(), edges.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(edges, edgesBitmap)

        // Release Mats
        mat.release()
        blurred.release()
        edges.release()

        return edgesBitmap
    }

    fun applyLaplacianSharpening(original: Bitmap, kernelSize: Int, scale: Double, delta: Double): Bitmap {
        // Convert Bitmap to Mat (OpenCV format)
        val mat = Mat(original.width, original.height, CvType.CV_8UC1)
        Utils.bitmapToMat(original, mat)

        // Convert the image to grayscale
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2GRAY)

        // Apply Laplacian sharpening
        val sharpened = Mat()
        Imgproc.Laplacian(mat, sharpened, CvType.CV_8UC1, kernelSize, scale, delta)

        // Convert Mat back to Bitmap
        val sharpenedBitmap = Bitmap.createBitmap(sharpened.cols(), sharpened.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(sharpened, sharpenedBitmap)

        // Release Mats
        mat.release()
        sharpened.release()

        return sharpenedBitmap
    }
fun applyUnsharpMasking(original: Bitmap, amount: Float): Bitmap {
    // Create a new bitmap for the sharpened image
    val sharpenedBitmap = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)

    // Loop through each pixel to calculate the unsharp masking
    for (x in 0 until original.width) {
        for (y in 0 until original.height) {
            val pixelOriginal = original.getPixel(x, y)

            // Extract ARGB components
            val alpha = pixelOriginal shr 24 and 0xFF
            val redOriginal = pixelOriginal shr 16 and 0xFF
            val greenOriginal = pixelOriginal shr 8 and 0xFF
            val blueOriginal = pixelOriginal and 0xFF

            // Calculate the average of RGB values
            val avgOriginal = (redOriginal + greenOriginal + blueOriginal) / 3

            // Calculate the difference
            val diff = avgOriginal - redOriginal

            // Apply unsharp masking
            val newRed = (redOriginal + amount * diff).toInt().coerceIn(0, 255)
            val newGreen = (greenOriginal + amount * diff).toInt().coerceIn(0, 255)
            val newBlue = (blueOriginal + amount * diff).toInt().coerceIn(0, 255)

            // Compose the new pixel value
            val newPixel = alpha shl 24 or (newRed shl 16) or (newGreen shl 8) or newBlue
            sharpenedBitmap.setPixel(x, y, newPixel)
        }
    }

    return sharpenedBitmap
}





class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize OpenCV
        if (!OpenCVLoader.initDebug()) {
            // Handle initialization error if needed
        }

        if (!hasRequiredPermissions()) {
            ActivityCompat.requestPermissions(
                this, CAMERAX_PERMISSIONS, 0
            )
        }
        setContent {
            CameraXGuideTheme {
                val scope = rememberCoroutineScope()
                val scaffoldState = rememberBottomSheetScaffoldState()
                val controller = remember {
                    LifecycleCameraController(applicationContext).apply {
                        setEnabledUseCases(
                            CameraController.IMAGE_CAPTURE or
                                    CameraController.VIDEO_CAPTURE
                        )
                    }
                }
                val viewModel = viewModel<MainViewModel>()
                val bitmaps by viewModel.bitmaps.collectAsState()

                BottomSheetScaffold(
                    scaffoldState = scaffoldState,
                    sheetPeekHeight = 0.dp,
                    sheetContent = {
                        PhotoBottomSheetContent(
                            bitmaps = bitmaps,
                            modifier = Modifier
                                .fillMaxWidth()
                        )
                    }
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        CameraPreview(
                            controller = controller,
                            modifier = Modifier
                                .fillMaxSize()
                        )

                        IconButton(
                            onClick = {
                                controller.cameraSelector =
                                    if (controller.cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                                        CameraSelector.DEFAULT_FRONT_CAMERA
                                    } else CameraSelector.DEFAULT_BACK_CAMERA
                            },
                            modifier = Modifier
                                .offset(16.dp, 16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = "Switch camera"
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        scaffoldState.bottomSheetState.expand()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Photo,
                                    contentDescription = "Open gallery"
                                )
                            }
                            IconButton(
                                onClick = {
                                    takePhoto(
                                        controller = controller,
                                        onPhotoTaken = viewModel::onTakePhoto
                                    )
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = "Take photo"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun takePhoto(
        controller: LifecycleCameraController,
        onPhotoTaken: (Bitmap) -> Unit
    ) {
        controller.takePicture(
            ContextCompat.getMainExecutor(applicationContext),
            object : OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    super.onCaptureSuccess(image)

                    val matrix = Matrix().apply {
                        postRotate(image.imageInfo.rotationDegrees.toFloat())
                    }
                    val rotatedBitmap = Bitmap.createBitmap(
                        image.toBitmap(),
                        0,
                        0,
                        image.width,
                        image.height,
                        matrix,
                        true
                    )

                    //IMAGE PROCESSING
                    //val blurRadius = 3f // Adjust the blur radius as needed
                    //val unsharpAmount = 3f // Adjust the amount of sharpening as needed
                    val kernelSize = 3 // Adjust the kernel size as needed (e.g., 3x3, 5x5)
                    val scale = 1.0 // Adjust the scale factor as needed
                    val delta = 0.0 // Adjust the delta value as needed

                    // Apply Gaussian blur
                    //val blurredBitmap = ImageUtils.applyGaussianBlur(applicationContext, rotatedBitmap, blurRadius)

                    // Apply Laplacian sharpening
                    // val sharpenedBitmap = ImageUtils.applyLaplacianSharpening(rotatedBitmap, kernelSize, scale, delta)
                    //val sharpenedBitmap = ImageUtils.applyUnsharpMasking(rotatedBitmap, unsharpAmount)

                    // Apply Laplacian sharpening
                    // val sharpenedBitmap = ImageUtils.applyLaplacianSharpening(rotatedBitmap, kernelSize, scale, delta)

                    //val edgesBitmap = ImageUtils.extractOutlines(rotatedBitmap)

                    val enhancedBitmap = ImageUtils.enhanceImageQuality(rotatedBitmap, 1.5f, 10, 0.5f)

                    // Display image
                    onPhotoTaken(enhancedBitmap)
                }

                override fun onError(exception: ImageCaptureException) {
                    super.onError(exception)
                    Log.e("Camera", "Couldn't take photo: ", exception)
                }
            }
        )
    }

    private fun hasRequiredPermissions(): Boolean {
        return CAMERAX_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(
                applicationContext,
                it
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        private val CAMERAX_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
        )
    }
}