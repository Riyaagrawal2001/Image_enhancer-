package com.example.lightenhancer

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.lightenhancer.ui.theme.LightEnhancerTheme
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : ComponentActivity() {

  private lateinit var tfliteHelper: TFLiteModelHelper

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    tfliteHelper = TFLiteModelHelper(assets)


    setContent {
      LightEnhancerTheme {

        val generatedImage = remember {
          mutableStateOf<Bitmap?>(null)
        }
        // A surface container using the 'background' color from the theme
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          RequestContentPermission { inputImage ->

            val maxWidth = 500
            val maxHeight = 500

            val downscaledBitmap = downscaleBitmap(inputImage, maxWidth, maxHeight)

            val resizedInputImage: Bitmap =
              Bitmap.createScaledBitmap(downscaledBitmap, 500, 500, true)

            val inputImageBuffer = convertBitmapToByteBuffer(resizedInputImage)
            val generatedImageBuffer = tfliteHelper.generateImage(inputImageBuffer)

            generatedImage.value = convertByteBufferToBitmap(generatedImageBuffer)

          }
        }
      }
    }
  }
}

private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
  val imageByteBuffer = ByteBuffer.allocateDirect(500 * 500 * 3 * 4).apply {
    order(ByteOrder.nativeOrder())
  }

  val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)

  for (y in 0 until 500) {
    for (x in 0 until 500) {
      val pixel = mutableBitmap.getPixel(x, y)
      imageByteBuffer.putFloat((pixel shr 16 and 0xFF) / 255.0f) // Red channel
      imageByteBuffer.putFloat((pixel shr 8 and 0xFF) / 255.0f)  // Green channel
      imageByteBuffer.putFloat((pixel and 0xFF) / 255.0f)         // Blue channel
    }
  }
  mutableBitmap.recycle()

  imageByteBuffer.rewind()
  return imageByteBuffer
}

private fun convertByteBufferToBitmap(byteBuffer: ByteBuffer): Bitmap {
  val outputBitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
  byteBuffer.rewind()

  for (y in 0 until 500) {
    for (x in 0 until 500) {
      val red = (byteBuffer.float * 255).toInt()
      val green = (byteBuffer.float * 255).toInt()
      val blue = (byteBuffer.float * 255).toInt()
      val color = 0xFF shl 24 or (red shl 16) or (green shl 8) or blue
      outputBitmap.setPixel(x, y, color)
    }
  }

  return outputBitmap
}

fun downscaleBitmap(originalBitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
  val originalWidth = originalBitmap.width
  val originalHeight = originalBitmap.height

  val scaleFactorX = maxWidth.toFloat() / originalWidth
  val scaleFactorY = maxHeight.toFloat() / originalHeight
  val scaleFactor = if (scaleFactorX < scaleFactorY) scaleFactorX else scaleFactorY

  val scaledWidth = (originalWidth * scaleFactor).toInt()
  val scaledHeight = (originalHeight * scaleFactor).toInt()

  return Bitmap.createScaledBitmap(originalBitmap, scaledWidth, scaledHeight, true)
}

@Composable
fun RequestContentPermission(
  onFetchUri: (Bitmap) -> Unit,
) {
  Log.d("RequestContentPermission", "RequestContentPermission: request permission")
  var imageUri by remember {
    mutableStateOf<Uri?>(null)
  }
  val context = LocalContext.current
  val bitmap = remember {
    mutableStateOf<Bitmap?>(null)
  }

  LaunchedEffect(bitmap.value) {
    bitmap.value?.let(onFetchUri)
  }

  val launcher = rememberLauncherForActivityResult(
    contract =
    ActivityResultContracts.GetContent()
  ) { uri: Uri? ->
    imageUri = uri
  }
  Column {
    Button(onClick = {
      launcher.launch("image/*")
    }) {
      Text(text = "Pick image")
    }

    Spacer(modifier = Modifier.height(12.dp))

    imageUri?.let {
      if (Build.VERSION.SDK_INT < 28) {
        bitmap.value = MediaStore.Images
          .Media.getBitmap(context.contentResolver, it)
      } else {
        val source = ImageDecoder
          .createSource(context.contentResolver, it)
        bitmap.value = ImageDecoder.decodeBitmap(source)
      }

      bitmap.value?.let { btm ->
        Image(
          bitmap = btm.asImageBitmap(),
          contentDescription = null,
          modifier = Modifier.size(400.dp)
        )
      }
    }

  }
}
