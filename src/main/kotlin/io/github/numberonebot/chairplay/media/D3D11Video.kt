// ====================================================================================================
//   UNOFFICIAL LINUX (POP!_OS) PORT: this file is from ChairPlay by Oleksiy Strelets
//   (https://github.com/NumberOneBot/ChairPlay, GPL-3.0) and was MODIFIED on 2026-11-02 for an
//   unofficial, personal-use port to Pop!_OS / Ubuntu Linux. Not affiliated with the original
//   project. See LINUX-PORT-NOTICE.md for what changed.
// ====================================================================================================

package io.github.numberonebot.chairplay.media

import org.bytedeco.ffmpeg.avcodec.AVCodec
import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avutil.AVBufferRef
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec.*
import org.bytedeco.ffmpeg.global.avutil.*
import org.bytedeco.javacpp.IntPointer
import org.bytedeco.javacpp.Pointer
import java.io.Closeable

/**
 * Owns the callback and download surface; the codec context owns its reference to the D3D11 device. On Linux the
 * same path asks for a VA-API device (Intel and AMD GPUs) instead; an FFmpeg build or a driver without it leaves
 * the decoding to the CPU, as a PC without D3D11 decoding does on Windows.
 */
internal class D3D11Video private constructor(
    private val transfer: (AVFrame, AVFrame) -> Int,
    private val hardwareFormat: Int = HARDWARE_FORMAT,
) : Closeable {
    private val downloaded = av_frame_alloc()
    // Keep a strong reference until after avcodec_free_context: FFmpeg retains this native callback.
    val format = object : AVCodecContext.Get_format_AVCodecContext_IntPointer() {
        override fun call(context: AVCodecContext, formats: IntPointer): Int {
            var software = AV_PIX_FMT_NONE
            var i = 0L
            while (true) {
                val candidate = formats.get(i++)
                if (candidate == AV_PIX_FMT_NONE) break
                if (candidate == hardwareFormat) return candidate
                val desc = av_pix_fmt_desc_get(candidate)
                if (software == AV_PIX_FMT_NONE && desc != null && !desc.isNull &&
                    desc.flags() and AV_PIX_FMT_FLAG_HWACCEL.toLong() == 0L) software = candidate
            }
            // FFmpeg calls again without D3D11 if this stream/profile cannot use the device.
            return software
        }
    }

    fun download(frame: AVFrame): AVFrame {
        av_frame_unref(downloaded)
        val result = transfer(downloaded, frame)
        check(result >= 0) { "D3D11 frame transfer failed ($result)" }
        return downloaded
    }

    override fun close() {
        av_frame_free(downloaded)
        format.close()
    }

    companion object {
        private val windows = System.getProperty("os.name").orEmpty().startsWith("Windows")
        private val DEVICE_TYPE = if (windows) AV_HWDEVICE_TYPE_D3D11VA else AV_HWDEVICE_TYPE_VAAPI
        private val HARDWARE_FORMAT = if (windows) AV_PIX_FMT_D3D11 else AV_PIX_FMT_VAAPI

        fun open(context: AVCodecContext, codec: AVCodec): D3D11Video? =
            open(context, codec) { destination, source -> av_hwframe_transfer_data(destination, source, 0) }

        internal fun open(context: AVCodecContext, codec: AVCodec, transfer: (AVFrame, AVFrame) -> Int): D3D11Video? {
            var index = 0
            while (true) {
                val config = avcodec_get_hw_config(codec, index++)
                if (config == null || config.isNull) return null
                if (config.device_type() == DEVICE_TYPE &&
                    config.methods() and AV_CODEC_HW_CONFIG_METHOD_HW_DEVICE_CTX != 0) break
            }
            val device = AVBufferRef(null as Pointer?)
            try {
                if (av_hwdevice_ctx_create(device, DEVICE_TYPE, null as String?, null, 0) < 0) return null
                val hardware = D3D11Video(transfer)
                context.hw_device_ctx(av_buffer_ref(device))
                context.get_format(hardware.format)
                return hardware
            } finally {
                if (!device.isNull) av_buffer_unref(device)
            }
        }
    }
}
