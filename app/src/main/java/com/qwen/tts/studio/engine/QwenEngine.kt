package com.qwen.tts.studio.engine

/** Thin, lifecycle-owned wrapper around the pinned qwen3-tts.cpp C/JNI ABI. */
class QwenEngine : AutoCloseable {
    private var nativePtr = 0L

    class NativeParams(
        val languageId:Int = -1,
        val instruction:String? = null,
        val speaker:String? = null,
        val maxAudioTokens:Int = 1024,
    )
    class NativeResult(
        val audio:FloatArray?, val sampleRate:Int, val success:Boolean,
        val errorMsg:String?, val timeMs:Long,
    )
    class NativeCapabilities(
        val loaded:Boolean, val supportsCloning:Boolean,
        val supportsNamedSpeakers:Boolean, val supportsInstruction:Boolean,
        val speakerEmbeddingDim:Int, val modelKind:Int, val speakerCount:Int,
    )
    fun interface AudioChunkCallback {
        fun onAudioChunk(
            samples:FloatArray, sampleRate:Int, startSample:Long, endSample:Long,
            startFrame:Int, endFrame:Int, startTextByte:Int, endTextByte:Int,
            textAlignmentKind:Int, confidence:Float,
        ):Boolean
    }

    init { System.loadLibrary("qwen3_tts_jni");nativePtr=nativeInit();check(nativePtr!=0L){"Qwen runtime initialization failed"} }
    fun loadModels(directory:String,modelName:String?=null)=nativeLoadModels(nativePtr,directory,modelName)
    fun stream(text:String,referenceWav:String?=null,speakerEmbedding:String?=null,iclPrompt:String?=null,
               params:NativeParams=NativeParams(),chunkSeconds:Float=.5f,leftContextSeconds:Float=2f,
               callback:AudioChunkCallback):NativeResult = nativeSynthesizeStreaming(nativePtr,text,referenceWav,
        speakerEmbedding,iclPrompt,params,chunkSeconds,leftContextSeconds,false,callback)
    fun extractSpeakerEmbedding(referenceWav:String,outputPath:String)=nativeExtractSpeakerEmbedding(nativePtr,referenceWav,outputPath)
    fun extractIclPrompt(referenceWav:String,referenceText:String,outputPath:String)=nativeExtractIclPrompt(nativePtr,referenceWav,referenceText,outputPath)
    fun capabilities()=nativeGetModelCapabilities(nativePtr)
    fun lastError()=nativeGetLastError(nativePtr)
    fun setCpuThreads(count:Int)=nativeSetCpuThreads(count)
    fun activeBackend()=nativeGetActiveBackendName()
    override fun close(){if(nativePtr!=0L){nativeFree(nativePtr);nativePtr=0}}

    private external fun nativeInit():Long
    private external fun nativeFree(ptr:Long)
    private external fun nativeLoadModels(ptr:Long,directory:String,modelName:String?):Boolean
    private external fun nativeSynthesizeStreaming(ptr:Long,text:String,referenceWav:String?,speakerEmbedding:String?,iclPrompt:String?,params:NativeParams?,chunkSeconds:Float,leftContextSeconds:Float,collectAudio:Boolean,callback:AudioChunkCallback):NativeResult
    private external fun nativeExtractSpeakerEmbedding(ptr:Long,referenceWav:String,outputPath:String):Boolean
    private external fun nativeExtractIclPrompt(ptr:Long,referenceWav:String,referenceText:String,outputPath:String):Boolean
    private external fun nativeGetModelCapabilities(ptr:Long):NativeCapabilities?
    private external fun nativeGetLastError(ptr:Long):String?
    private external fun nativeSetCpuThreads(count:Int):Boolean
    private external fun nativeGetActiveBackendName():String?
}
