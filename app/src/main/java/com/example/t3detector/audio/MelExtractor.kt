package com.example.t3detector.audio

import kotlin.math.*

class MelExtractor(
    private val sampleRate: Int = 16000,
    private val nMels: Int = 64,
    private val nFft: Int = 2048,
    private val hopLength: Int = 512
) {

    private val window = hannWindow(nFft)
    private val melFilter = melFilterBank()

    // Pre-allocate FFT buffers to reduce garbage collection
    private val fftRealBuf = FloatArray(nFft)
    private val fftImagBuf = FloatArray(nFft)

    // === Public API ===
    fun extractMelDb(samples: FloatArray): Array<FloatArray> {
        val padded = padReflect(samples, nFft / 2)
        val stft = stftMag(padded)

        // Power = magnitude^2
        val power = Array(stft.size) { i ->
            FloatArray(stft[0].size) { j ->
                stft[i][j] * stft[i][j]
            }
        }

        val mel = applyMel(power)
        val melDb = powerToDb(mel)
        return normalize0to1(melDb)
    }

    // ======== 1. Hann Window ========
    private fun hannWindow(n: Int): FloatArray =
        FloatArray(n) { i -> (0.5f - 0.5f * cos(2 * Math.PI * i / n).toFloat()) }

    // ======== 2. Librosa-style reflect padding ========
    private fun padReflect(signal: FloatArray, pad: Int): FloatArray {
        val out = FloatArray(signal.size + pad * 2)
        for (i in 0 until pad) {
            out[i] = signal[pad - i]
        }
        for (i in signal.indices) out[i + pad] = signal[i]
        val end = signal.lastIndex
        for (i in 0 until pad) {
            out[pad + signal.size + i] = signal[end - i]
        }
        return out
    }

    // ======== 3. STFT Magnitude ========
    private fun stftMag(sig: FloatArray): Array<FloatArray> {
        val frames = (sig.size - nFft) / hopLength + 1
        val out = Array(nFft / 2 + 1) { FloatArray(frames) }

        for (f in 0 until frames) {
            val start = f * hopLength

            // Fill windowed frame into real buffer, clear imaginary
            for (i in 0 until nFft) {
                val idx = start + i
                val s = if (idx < sig.size) sig[idx] else 0f
                fftRealBuf[i] = s * window[i]
                fftImagBuf[i] = 0f
            }

            // Perform FFT
            fft(fftRealBuf, fftImagBuf)

            // Compute Magnitude (only first N/2 + 1 bins)
            for (i in out.indices) {
                val re = fftRealBuf[i]
                val im = fftImagBuf[i]
                out[i][f] = sqrt(re * re + im * im)
            }
        }
        return out
    }

    // ======== 4. Standard Cooley-Tukey FFT (Radix-2) ========
    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size

        // Bit reversal permutation
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
            var k = n / 2
            while (k <= j) {
                j -= k
                k /= 2
            }
            j += k
        }

        // Butterfly operations
        var len = 2
        while (len <= n) {
            val ang = -2.0 * Math.PI / len
            val wLenRe = cos(ang).toFloat()
            val wLenIm = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var wRe = 1.0f
                var wIm = 0.0f
                for (k in 0 until len / 2) {
                    val idx = i + k + len / 2
                    val uRe = re[i + k]
                    val uIm = im[i + k]

                    val vRe = re[idx] * wRe - im[idx] * wIm
                    val vIm = re[idx] * wIm + im[idx] * wRe

                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[idx] = uRe - vRe
                    im[idx] = uIm - vIm

                    val wReNext = wRe * wLenRe - wIm * wLenIm
                    wIm = wRe * wLenIm + wIm * wLenRe
                    wRe = wReNext
                }
                i += len
            }
            len = len shl 1
        }
    }

    // ======== 5. Mel Filterbank ========
    private fun melFilterBank(): Array<FloatArray> {
        val numFreqs = nFft / 2 + 1
        val filters = Array(nMels) { FloatArray(numFreqs) }

        fun hzToMel(hz: Double): Double = 2595.0 * ln(1.0 + hz / 700.0) / ln(10.0)
        fun melToHz(mel: Double): Double = 700.0 * (10.0.pow(mel / 2595.0) - 1.0)

        val fMin = 0.0
        val fMax = sampleRate / 2.0
        val melMin = hzToMel(fMin)
        val melMax = hzToMel(fMax)

        val melPoints = DoubleArray(nMels + 2) {
            melMin + (melMax - melMin) * it / (nMels + 1)
        }
        val hzPoints = melPoints.map { melToHz(it) }
        val bins = hzPoints.map { floor((nFft + 1) * it / sampleRate).toInt() }

        for (m in 1..nMels) {
            val left = bins[m - 1]
            val center = bins[m]
            val right = bins[m + 1]

            for (k in left until center)
                filters[m - 1][k] = ((k - left).toFloat()) / (center - left)
            for (k in center until right)
                filters[m - 1][k] = ((right - k).toFloat()) / (right - center)
        }
        return filters
    }

    // ======== 6. Apply Mel Filter ========
    private fun applyMel(power: Array<FloatArray>): Array<FloatArray> {
        val numFrames = power[0].size
        val melSpec = Array(nMels) { FloatArray(numFrames) }

        for (m in 0 until nMels) {
            val filt = melFilter[m]
            for (t in 0 until numFrames) {
                var sum = 0f
                for (f in power.indices) {
                    sum += power[f][t] * filt[f]
                }
                melSpec[m][t] = sum
            }
        }
        return melSpec
    }

    // ======== 7. power_to_db ========
    private fun powerToDb(mel: Array<FloatArray>): Array<FloatArray> {
        val out = Array(mel.size) { FloatArray(mel[0].size) }
        val maxVal = mel.maxOf { row -> row.maxOrNull() ?: 1e-10f }
        val ref = max(maxVal, 1e-10f)
        val topDb = 80f

        for (i in mel.indices) {
            for (j in mel[0].indices) {
                val p = max(1e-10f, mel[i][j])
                var db = 10f * log10(p / ref)
                db = max(db, -topDb)
                out[i][j] = db
            }
        }
        return out
    }

    // ======== 8. Normalize ========
    private fun normalize0to1(m: Array<FloatArray>): Array<FloatArray> {
        var minVal = Float.MAX_VALUE
        var maxVal = -Float.MAX_VALUE

        for (row in m) {
            for (v in row) {
                if (v < minVal) minVal = v
                if (v > maxVal) maxVal = v
            }
        }
        val range = maxVal - minVal + 1e-6f
        val out = Array(m.size) { FloatArray(m[0].size) }
        for (i in m.indices) {
            for (j in m[0].indices) {
                out[i][j] = (m[i][j] - minVal) / range
            }
        }
        return out
    }
}