package io.uttermux.android.provider

import android.content.Context
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.*
import java.net.URL
import java.net.HttpURLConnection
import java.security.MessageDigest

data class LocalModel(val id:String,val engine:String,val url:String,val sha256:String,val model:String,val tokens:String="tokens.txt",val voices:String="",val dataDir:String="espeak-ng-data",val lexicon:String="",
    val secondaryUrl:String="",val secondarySha256:String="",val secondaryFile:String="")

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
        LocalModel("sherpa-onnx-pocket-tts-int8-2026-01-26","pocket","https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/sherpa-onnx-pocket-tts-int8-2026-01-26.tar.bz2","2f3b88823cbbb9bf0b2477ec8ae7b3fec417b3a87b6bb5f256dba66f2ad967cb","lm_flow.int8.onnx"),
        LocalModel("sherpa-onnx-zipvoice-distill-int8-zh-en-emilia","zipvoice","https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/sherpa-onnx-zipvoice-distill-int8-zh-en-emilia.tar.bz2","77219c8b40f4ee8d73a7f902305ff6c1128ef9b54461c41b4ca6ed890b6c2803","encoder.int8.onnx"),
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
        progress("Downloading $id")
        download(model.url,partial,cancelled)
        if(cancelled())throw InterruptedException()
        val actual=MessageDigest.getInstance("SHA-256").digest(partial.readBytes()).joinToString(""){"%02x".format(it)}
        require(actual==model.sha256){"Model checksum mismatch"}
        progress("Extracting $id")
        val staging=File(root,".$id.staging").apply { deleteRecursively();mkdirs() }
        TarArchiveInputStream(BZip2CompressorInputStream(partial.inputStream().buffered())).use { tar ->
            while(true){ val entry=tar.nextEntry ?: break; val relative=entry.name.substringAfter('/'); if(relative.isBlank()) continue
                val target=File(staging,relative).canonicalFile; require(target.path.startsWith(staging.canonicalPath+File.separator)){"Unsafe model archive"}
                if(entry.isDirectory) target.mkdirs() else { target.parentFile?.mkdirs();target.outputStream().use{tar.copyTo(it)} }
            }
        }
        if(model.secondaryUrl.isNotBlank()) {
            progress("Downloading ${model.secondaryFile}")
            val secondary=File(staging,model.secondaryFile)
            download(model.secondaryUrl,secondary,cancelled)
            val secondaryActual=MessageDigest.getInstance("SHA-256").digest(secondary.readBytes()).joinToString(""){"%02x".format(it)}
            require(secondaryActual==model.secondarySha256){"Secondary model checksum mismatch"}
        }
        val destination=File(root,id); destination.deleteRecursively(); require(staging.renameTo(destination));partial.delete();progress("Installed $id")
    }
    private fun download(url:String,target:File,cancelled:()->Boolean) {
        val existing=target.length().takeIf{target.isFile}?:0L
        val connection=(URL(url).openConnection() as HttpURLConnection).apply{connectTimeout=15_000;readTimeout=60_000;instanceFollowRedirects=true;if(existing>0)setRequestProperty("Range","bytes=$existing-")}
        connection.connect();val append=existing>0&&connection.responseCode==HttpURLConnection.HTTP_PARTIAL
        if(!append&&target.exists())target.outputStream().use{}
        connection.inputStream.buffered().use{input->FileOutputStream(target,append).buffered().use{output->
            val buffer=ByteArray(128*1024);while(true){if(cancelled())throw InterruptedException();val count=input.read(buffer);if(count<0)break;output.write(buffer,0,count)}
        }}
    }
}
