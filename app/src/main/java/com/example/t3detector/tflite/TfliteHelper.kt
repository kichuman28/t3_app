package com.example.t3detector.tflite

import android.content.Context
import com.example.t3detector.audio.MelExtractor
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class TfliteHelper(context: Context) {

    private val modelName = "t3_detector_cnn.tflite"
    private var interpreter: Interpreter
    private val extractor = MelExtractor()   // <- REAL mel extractor instance

    companion object {
        const val MEL_BINS = 64
        const val MEL_FRAMES = 94
    }

    init {
        val fd = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fd.fileDescriptor)
        val modelBuffer = inputStream.channel.map(
            FileChannel.MapMode.READ_ONLY,
            fd.startOffset,
            fd.declaredLength
        )
        interpreter = Interpreter(modelBuffer)
    }

    fun runInference(waveform: FloatArray): Pair<String, Float> {

        // 1. Extract mel spectrogram (64x94)
        val mel = extractor.extractMelDb(waveform)

        // 2. Create input tensor [1][64][94][1]
        val inputBuffer = ByteBuffer.allocateDirect(1 * MEL_BINS * MEL_FRAMES * 1 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())

        for (i in 0 until MEL_BINS) {
            for (j in 0 until MEL_FRAMES) {
                inputBuffer.putFloat(mel[i][j])
            }
        }

        // 3. Prepare output tensor
        val output = Array(1) { FloatArray(2) }

        // 4. Run inference
        interpreter.run(inputBuffer, output)

        // 5. Decode
        val idx = if (output[0][1] > output[0][0]) 1 else 0
        val label = if (idx == 1) "T3" else "Not_T3"
        val conf = output[0][idx]

        return label to conf
    }
}
