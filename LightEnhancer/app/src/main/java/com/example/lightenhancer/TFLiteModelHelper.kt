package com.example.lightenhancer

import android.content.res.AssetFileDescriptor
import android.content.res.AssetManager
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class TFLiteModelHelper(private val assetManager: AssetManager) {

  private lateinit var interpreter: Interpreter
  private val outputShape: IntArray = intArrayOf(1, 500, 500, 3)

  init {
    //
    loadModel()
  }

  private fun loadModel() {
    val modelDescriptor: AssetFileDescriptor = assetManager.openFd("model_enhancer2.tflite")
    val inputStream = FileInputStream(modelDescriptor.fileDescriptor)
    val modelByteBuffer = inputStream.channel.map(
      FileChannel.MapMode.READ_ONLY,
      modelDescriptor.startOffset,
      modelDescriptor.declaredLength
    )

    val options = Interpreter.Options()
    interpreter = Interpreter(modelByteBuffer, options)
  }

  fun generateImage(inputImageBuffer: ByteBuffer): ByteBuffer {
    val outputImageBuffer =
      ByteBuffer.allocateDirect(outputShape[1] * outputShape[2] * outputShape[3] * 4)
        .apply {
          order(ByteOrder.nativeOrder())
        }

    interpreter.run(inputImageBuffer, outputImageBuffer)

    outputImageBuffer.rewind()
    return outputImageBuffer
  }
}
