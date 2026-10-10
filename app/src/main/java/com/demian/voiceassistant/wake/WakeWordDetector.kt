package com.demian.voiceassistant.wake

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlin.concurrent.thread

/**
 * 唤醒词检测（基于 sherpa-onnx 的关键词唤醒 KWS）。
 *
 * 原理：在后台线程持续读取麦克风音频，喂给离线关键词识别模型；
 * 一旦检测到唤醒词，就通过回调通知外面。
 *
 * 注意：唤醒词检测和语音识别（SpeechRecognizer）都要独占麦克风，
 * 所以两者不能同时工作——检测到唤醒词后应先 [stop]，识别完再 [start]。
 */
class WakeWordDetector(private val context: Context) {

    @Volatile
    private var running = false
    private var worker: Thread? = null
    private var audioRecord: AudioRecord? = null

    /** 开始监听。onDetected 在后台线程回调，调用方需自行切回主线程。 */
    fun start(onDetected: (String) -> Unit) {
        if (running) return
        running = true
        worker = thread(name = "wake-word") {
            var spotter: KeywordSpotter? = null
            var record: AudioRecord? = null
            try {
                spotter = createSpotter()
                val stream = spotter.createStream(keywordsString())
                record = createRecorder() ?: return@thread
                audioRecord = record
                record.startRecording()

                val buffer = ShortArray((0.1 * SAMPLE_RATE).toInt())
                while (running) {
                    val n = record.read(buffer, 0, buffer.size)
                    if (n <= 0) continue
                    val samples = FloatArray(n) { buffer[it] / 32768.0f }
                    stream.acceptWaveform(samples, SAMPLE_RATE)
                    while (running && spotter.isReady(stream)) {
                        spotter.decode(stream)
                        val keyword = spotter.getResult(stream).keyword
                        if (keyword.isNotBlank()) {
                            spotter.reset(stream) // 检测到后立刻重置，避免重复触发
                            onDetected(keyword)
                        }
                    }
                }
                stream.release()
            } catch (t: Throwable) {
                Log.e(TAG, "唤醒词检测出错", t)
            } finally {
                record?.let {
                    runCatching { it.stop() }
                    it.release()
                }
                spotter?.release()
                audioRecord = null
            }
        }
    }

    /** 停止监听并释放麦克风。 */
    fun stop() {
        running = false
        worker?.join(1500)
        worker = null
    }

    private fun createRecorder(): AudioRecord? {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            Log.e(TAG, "无法获取录音缓冲区大小: $minBuffer")
            return null
        }
        return AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer * 2
        )
    }

    private fun createSpotter(): KeywordSpotter {
        val config = KeywordSpotterConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = "$MODEL_DIR/encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
                    decoder = "$MODEL_DIR/decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
                    joiner = "$MODEL_DIR/joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
                ),
                tokens = "$MODEL_DIR/tokens.txt",
                modelType = "zipformer2",
            ),
            keywordsFile = "$MODEL_DIR/keywords.txt",
        )
        return KeywordSpotter(assetManager = context.assets, config = config)
    }

    /** 读取 assets 里的 keywords.txt，把换行换成 "/" 作为 createStream 的入参。 */
    private fun keywordsString(): String {
        return context.assets.open("$MODEL_DIR/keywords.txt")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
            .replace("\n", "/")
            .trim()
    }

    companion object {
        private const val TAG = "WakeWord"
        private const val SAMPLE_RATE = 16000
        const val MODEL_DIR = "sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01"
    }
}
