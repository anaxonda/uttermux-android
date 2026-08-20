package io.uttermux.android.provider

import android.content.Context
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.*
import java.net.URL
import java.net.HttpURLConnection
import java.security.MessageDigest

data class RemoteAsset(val file:String,val url:String,val sha256:String)
data class LocalModel(val id:String,val engine:String,val url:String,val sha256:String,val model:String,val tokens:String="tokens.txt",val voices:String="",val dataDir:String="espeak-ng-data",val lexicon:String="",
    val secondaryUrl:String="",val secondarySha256:String="",val secondaryFile:String="",val assets:List<RemoteAsset> = emptyList())

class ModelManager(private val context: Context) {
    private val modelsById = linkedMapOf<String,LocalModel>()
    val models: List<LocalModel> get() = synchronized(modelsById) { modelsById.values.toList() }
    init { listOf(
        LocalModel("kokoro-multi-lang-v1_0","kokoro","https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-multi-lang-v1_0.tar.bz2","c133d26353d776da730870dac7da07dbfc9a5e3bc80cc5e8e83ab6e823be7046","model.onnx",voices="voices.bin",lexicon="lexicon-us-en.txt"),
        LocalModel("kitten-nano-en-v0_8-int8","kitten","https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kitten-nano-en-v0_8-int8.tar.bz2","6fa5be852612ce761094ba74ee6123b4fc4acfefa79bf64dc63acae4a83af2fd","model.int8.onnx",voices="voices.bin"),
        LocalModel("kitten-nano-en-v0_1-fp16","kitten","https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kitten-nano-en-v0_1-fp16.tar.bz2","f35dac93754fe2ac97c66e1f468311d0d2130f7f0f5a89bfa1197e09a0cbdec5","model.fp16.onnx",voices="voices.bin"),
        LocalModel("vits-piper-en_US-lessac-medium","vits","https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-lessac-medium.tar.bz2","9e3febfacf0abf4270172d2958bcec246032b7e88efc2720840cc80c93de334e","en_US-lessac-medium.onnx"),
        LocalModel("vits-inflect-en-nano-v2","vits","https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-inflect-en-nano-v2.tar.bz2","9a6b1188b5f3be8813e0552056328495b4e88ffd1ef18ff837272bde7b3bc136","model.onnx"),
        LocalModel("vits-inflect-en-micro-v2","vits","https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-inflect-en-micro-v2.tar.bz2","ed3c594d9c49b6c64ffe27f4b368ea8a700bcc27ff80610771408ee81cd96574","model.onnx"),
        LocalModel("matcha-icefall-en_US-ljspeech","matcha","https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/matcha-icefall-en_US-ljspeech.tar.bz2","ea75702da7456a8b1874728278a835220dc8a26f4e8bd93c83bf53dc27679845","model-steps-3.onnx",secondaryUrl="https://github.com/k2-fsa/sherpa-onnx/releases/download/vocoder-models/vocos-22khz-univ.onnx",secondarySha256="0574a135aa1db2de6e181050db2ec528496cacd4a4701fc5d7faf9f9804c0081",secondaryFile="vocos-22khz-univ.onnx"),
        LocalModel("sherpa-onnx-supertonic-3-tts-int8-2026-05-11","supertonic","https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/sherpa-onnx-supertonic-3-tts-int8-2026-05-11.tar.bz2","82fa96f91c4ef8abaae3a14a3f4153facf88bed821d1f7331cec2700f432c427","duration_predictor.int8.onnx"),
        LocalModel("sherpa-onnx-pocket-tts-int8-2026-01-26","pocket","https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/sherpa-onnx-pocket-tts-int8-2026-01-26.tar.bz2","2f3b88823cbbb9bf0b2477ec8ae7b3fec417b3a87b6bb5f256dba66f2ad967cb","lm_flow.int8.onnx",assets=listOf(
            RemoteAsset("presets/alba-casual.wav","https://huggingface.co/kyutai/tts-voices/resolve/main/alba-mackenna/casual.wav","46264e83cb99115c3d210260e029117566d9c64f20266d10daa78107759ede3e"),
            RemoteAsset("presets/alba-announcer.wav","https://huggingface.co/kyutai/tts-voices/resolve/main/alba-mackenna/announcer.wav","e8b55193435db043833dda62fb759ee2779ace195811340ee8d28c7c4a4ccc24"),
            RemoteAsset("presets/alba-merchant.wav","https://huggingface.co/kyutai/tts-voices/resolve/main/alba-mackenna/merchant.wav","52c24756de299b37998ed83e32fdc8747f874f9dd67f0bcdc38b96d3f70cf488"),
            RemoteAsset("presets/alba-moment.wav","https://huggingface.co/kyutai/tts-voices/resolve/main/alba-mackenna/a-moment-by.wav","a1805f0e3610f0d5985f4abb51979620a012899e810019960310944bbcba509d"),
        )),
        LocalModel("sherpa-onnx-zipvoice-distill-int8-zh-en-emilia","zipvoice","https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/sherpa-onnx-zipvoice-distill-int8-zh-en-emilia.tar.bz2","77219c8b40f4ee8d73a7f902305ff6c1128ef9b54461c41b4ca6ed890b6c2803","encoder.int8.onnx"),
        LocalModel("moss-tts-nano-100m-onnx","moss","","","MOSS-TTS-Nano-100M-ONNX/moss_tts_prefill.onnx",assets=listOf(
            RemoteAsset("MOSS-TTS-Nano-100M-ONNX/browser_poc_manifest.json","https://huggingface.co/OpenMOSS-Team/MOSS-TTS-Nano-100M-ONNX/resolve/main/browser_poc_manifest.json","097d80e993dc29f0bae427590b4f77084a161cb578b50d82c29f455d5faa9eee"),
            RemoteAsset("MOSS-TTS-Nano-100M-ONNX/tts_browser_onnx_meta.json","https://huggingface.co/OpenMOSS-Team/MOSS-TTS-Nano-100M-ONNX/resolve/main/tts_browser_onnx_meta.json","3edf25232dcd0af3d061c837e9a968a39e2f8592e06777d740503c4f2244f95c"),
            RemoteAsset("MOSS-TTS-Nano-100M-ONNX/tokenizer.model","https://huggingface.co/OpenMOSS-Team/MOSS-TTS-Nano-100M-ONNX/resolve/main/tokenizer.model","c353ee1479b536bf414c1b247f5542b6607fb8ae91320e5af1781fee200fddff"),
            RemoteAsset("MOSS-TTS-Nano-100M-ONNX/moss_tts_prefill.onnx","https://huggingface.co/OpenMOSS-Team/MOSS-TTS-Nano-100M-ONNX/resolve/main/moss_tts_prefill.onnx","d56126dcd0574c2f15d98fc6b35eda68d0386b5bd9c5e38e28548d6f2ea8f3db"),
            RemoteAsset("MOSS-TTS-Nano-100M-ONNX/moss_tts_decode_step.onnx","https://huggingface.co/OpenMOSS-Team/MOSS-TTS-Nano-100M-ONNX/resolve/main/moss_tts_decode_step.onnx","698cbc2fc1c2feca16e5895614ed52bbb32ded10f236c076f477b2e69abf32d8"),
            RemoteAsset("MOSS-TTS-Nano-100M-ONNX/moss_tts_local_fixed_sampled_frame.onnx","https://huggingface.co/OpenMOSS-Team/MOSS-TTS-Nano-100M-ONNX/resolve/main/moss_tts_local_fixed_sampled_frame.onnx","40cdb00efc171c450cf91468e01429caa41b0252222cd308e978f58fe354afa8"),
            RemoteAsset("MOSS-TTS-Nano-100M-ONNX/moss_tts_global_shared.data","https://huggingface.co/OpenMOSS-Team/MOSS-TTS-Nano-100M-ONNX/resolve/main/moss_tts_global_shared.data","bce8312c3df6a44545302cae229b61054fe0672e0b252ba59cba47adeed831dc"),
            RemoteAsset("MOSS-TTS-Nano-100M-ONNX/moss_tts_local_shared.data","https://huggingface.co/OpenMOSS-Team/MOSS-TTS-Nano-100M-ONNX/resolve/main/moss_tts_local_shared.data","bae7782032c0fb12490ab42afe009f87ae6c75a0f0596fc7b5c08e4d5ee93916"),
            RemoteAsset("MOSS-Audio-Tokenizer-Nano-ONNX/codec_browser_onnx_meta.json","https://huggingface.co/OpenMOSS-Team/MOSS-Audio-Tokenizer-Nano-ONNX/resolve/main/codec_browser_onnx_meta.json","3e291c883bb7d11ff2fe8e964e3e495519760358859f35c951254c7741592731"),
            RemoteAsset("MOSS-Audio-Tokenizer-Nano-ONNX/moss_audio_tokenizer_decode_full.onnx","https://huggingface.co/OpenMOSS-Team/MOSS-Audio-Tokenizer-Nano-ONNX/resolve/main/moss_audio_tokenizer_decode_full.onnx","0fbbafe3fd4afa2a019af5c5ced204af6e2d1db044fa40f021525d2aee95b4ac"),
            RemoteAsset("MOSS-Audio-Tokenizer-Nano-ONNX/moss_audio_tokenizer_decode_shared.data","https://huggingface.co/OpenMOSS-Team/MOSS-Audio-Tokenizer-Nano-ONNX/resolve/main/moss_audio_tokenizer_decode_shared.data","e69d52e0f4e84ca27850557ee54face46632d3a5a16c89bd246c7c408466dcad"),
        )),
    ).forEach(::register) }
    val root = File(context.filesDir,"models").apply { mkdirs() }
    fun register(model:LocalModel) { synchronized(modelsById) { modelsById[model.id] = model } }
    fun model(id:String)=synchronized(modelsById) { modelsById[id] ?: error("Unknown model $id") }
    fun installed(id:String)=File(root,id).resolve(model(id).model).isFile
    fun installedIds():Set<String> = models.mapNotNull { model -> model.id.takeIf { File(root,it).resolve(model.model).isFile } }.toSet()
    fun delete(id:String):Boolean {
        val target=File(root,id).canonicalFile;require(target.parentFile==root.canonicalFile){"Unsafe model path"}
        return !target.exists()||target.deleteRecursively()
    }
    fun install(id:String, progress:(String)->Unit={},cancelled:()->Boolean={false}) {
        val model=model(id); val partial=File(context.cacheDir,"$id.part")
        val staging=File(root,".$id.staging").apply { if(model.url.isNotBlank())deleteRecursively();mkdirs() }
        if(model.url.isNotBlank()) {
            progress("Downloading $id");downloadVerified(model.url,partial,model.sha256,cancelled,"Model")
            if(cancelled())throw InterruptedException()
            progress("Extracting $id")
            TarArchiveInputStream(BZip2CompressorInputStream(partial.inputStream().buffered())).use { tar ->
                while(true){ val entry=tar.nextEntry ?: break; val relative=entry.name.substringAfter('/'); if(relative.isBlank()) continue
                    val target=File(staging,relative).canonicalFile; require(target.path.startsWith(staging.canonicalPath+File.separator)){"Unsafe model archive"}
                    if(entry.isDirectory) target.mkdirs() else { target.parentFile?.mkdirs();target.outputStream().use{tar.copyTo(it)} }
                }
            }
        }
        if(model.secondaryUrl.isNotBlank()) {
            progress("Downloading ${model.secondaryFile}")
            val secondary=File(staging,model.secondaryFile)
            downloadVerified(model.secondaryUrl,secondary,model.secondarySha256,cancelled,"Secondary model")
        }
        model.assets.forEach{asset->
            progress("Downloading ${asset.file}");val target=File(staging,asset.file).canonicalFile
            require(target.path.startsWith(staging.canonicalPath+File.separator)){"Unsafe model asset path"};target.parentFile?.mkdirs()
            downloadVerified(asset.url,target,asset.sha256,cancelled,"Asset ${asset.file}")
        }
        val destination=File(root,id); destination.deleteRecursively(); require(staging.renameTo(destination));partial.delete();progress("Installed $id")
    }
    private fun download(url:String,target:File,cancelled:()->Boolean) {
        val existing=target.length().takeIf{target.isFile}?:0L
        val connection=(URL(url).openConnection() as HttpURLConnection).apply{connectTimeout=15_000;readTimeout=60_000;instanceFollowRedirects=true;if(existing>0)setRequestProperty("Range","bytes=$existing-")}
        connection.connect();if(connection.responseCode==416&&existing>0)return
        require(connection.responseCode in 200..299){"Download failed: HTTP ${connection.responseCode}"}
        val append=existing>0&&connection.responseCode==HttpURLConnection.HTTP_PARTIAL
        if(!append&&target.exists())target.outputStream().use{}
        connection.inputStream.buffered().use{input->FileOutputStream(target,append).buffered().use{output->
            val buffer=ByteArray(128*1024);while(true){if(cancelled())throw InterruptedException();val count=input.read(buffer);if(count<0)break;output.write(buffer,0,count)}
        }}
    }
    private fun downloadVerified(url:String,target:File,expected:String,cancelled:()->Boolean,label:String) {
        download(url,target,cancelled)
        if(sha256(target)!=expected&&target.exists()) { target.delete();download(url,target,cancelled) }
        require(sha256(target)==expected){"$label checksum mismatch"}
    }
    private fun sha256(file:File):String {
        val digest=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(256*1024)
        file.inputStream().buffered().use{input->while(true){val count=input.read(buffer);if(count<0)break;digest.update(buffer,0,count)}}
        return digest.digest().joinToString(""){"%02x".format(it)}
    }
}
