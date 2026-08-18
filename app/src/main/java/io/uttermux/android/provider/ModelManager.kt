package io.uttermux.android.provider

import android.content.Context
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.*
import java.net.URL
import java.security.MessageDigest

data class LocalModel(val id:String,val engine:String,val url:String,val sha256:String,val model:String,val tokens:String="tokens.txt",val voices:String="",val dataDir:String="espeak-ng-data",val lexicon:String="")

class ModelManager(private val context: Context) {
    val models = listOf(
        LocalModel("kokoro-multi-lang-v1_0","kokoro","https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-multi-lang-v1_0.tar.bz2","c133d26353d776da730870dac7da07dbfc9a5e3bc80cc5e8e83ab6e823be7046","model.onnx",voices="voices.bin",lexicon="lexicon-us-en.txt"),
        LocalModel("kitten-nano-en-v0_8-int8","kitten","https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kitten-nano-en-v0_8-int8.tar.bz2","6fa5be852612ce761094ba74ee6123b4fc4acfefa79bf64dc63acae4a83af2fd","model.int8.onnx",voices="voices.bin"),
        LocalModel("kitten-nano-en-v0_1-fp16","kitten","https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kitten-nano-en-v0_1-fp16.tar.bz2","f35dac93754fe2ac97c66e1f468311d0d2130f7f0f5a89bfa1197e09a0cbdec5","model.fp16.onnx",voices="voices.bin"),
        LocalModel("vits-piper-en_US-lessac-medium","vits","https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-lessac-medium.tar.bz2","9e3febfacf0abf4270172d2958bcec246032b7e88efc2720840cc80c93de334e","en_US-lessac-medium.onnx"),
        LocalModel("vits-inflect-en-nano-v2","vits","https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-inflect-en-nano-v2.tar.bz2","9a6b1188b5f3be8813e0552056328495b4e88ffd1ef18ff837272bde7b3bc136","model.onnx"),
    )
    val root = File(context.filesDir,"models").apply { mkdirs() }
    fun model(id:String)=models.first { it.id==id }
    fun installed(id:String)=File(root,id).resolve(model(id).model).isFile
    fun install(id:String, progress:(String)->Unit={}) {
        val model=model(id); val partial=File(context.cacheDir,"$id.part")
        progress("Downloading $id")
        URL(model.url).openStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
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
        val destination=File(root,id); destination.deleteRecursively(); require(staging.renameTo(destination));partial.delete();progress("Installed $id")
    }
}
