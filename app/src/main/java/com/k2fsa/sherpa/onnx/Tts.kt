// API-compatible wrapper for sherpa-onnx v1.13.4 (Apache-2.0).
package com.k2fsa.sherpa.onnx

data class OfflineTtsVitsModelConfig(var model:String="",var lexicon:String="",var tokens:String="",var dataDir:String="",var dictDir:String="",var noiseScale:Float=.667f,var noiseScaleW:Float=.8f,var lengthScale:Float=1f)
data class OfflineTtsMatchaModelConfig(var acousticModel:String="",var vocoder:String="",var lexicon:String="",var tokens:String="",var dataDir:String="",var dictDir:String="",var noiseScale:Float=1f,var lengthScale:Float=1f)
data class OfflineTtsKokoroModelConfig(var model:String="",var voices:String="",var tokens:String="",var dataDir:String="",var lexicon:String="",var lang:String="",var dictDir:String="",var lengthScale:Float=1f)
data class OfflineTtsZipVoiceModelConfig(var tokens:String="",var encoder:String="",var decoder:String="",var vocoder:String="",var dataDir:String="",var lexicon:String="",var featScale:Float=.1f,var tShift:Float=.5f,var targetRms:Float=.1f,var guidanceScale:Float=1f)
data class OfflineTtsKittenModelConfig(var model:String="",var voices:String="",var tokens:String="",var dataDir:String="",var lengthScale:Float=1f)
data class OfflineTtsPocketModelConfig(var lmFlow:String="",var lmMain:String="",var encoder:String="",var decoder:String="",var textConditioner:String="",var vocabJson:String="",var tokenScoresJson:String="",var voiceEmbeddingCacheCapacity:Int=50)
data class OfflineTtsSupertonicModelConfig(var durationPredictor:String="",var textEncoder:String="",var vectorEstimator:String="",var vocoder:String="",var ttsJson:String="",var unicodeIndexer:String="",var voiceStyle:String="")
data class OfflineTtsModelConfig(var vits:OfflineTtsVitsModelConfig=OfflineTtsVitsModelConfig(),var matcha:OfflineTtsMatchaModelConfig=OfflineTtsMatchaModelConfig(),var kokoro:OfflineTtsKokoroModelConfig=OfflineTtsKokoroModelConfig(),var zipvoice:OfflineTtsZipVoiceModelConfig=OfflineTtsZipVoiceModelConfig(),var kitten:OfflineTtsKittenModelConfig=OfflineTtsKittenModelConfig(),var pocket:OfflineTtsPocketModelConfig=OfflineTtsPocketModelConfig(),var supertonic:OfflineTtsSupertonicModelConfig=OfflineTtsSupertonicModelConfig(),var numThreads:Int=1,var debug:Boolean=false,var provider:String="cpu")
data class OfflineTtsConfig(var model:OfflineTtsModelConfig=OfflineTtsModelConfig(),var ruleFsts:String="",var ruleFars:String="",var maxNumSentences:Int=1,var silenceScale:Float=.2f)
data class GenerationConfig(var silenceScale:Float=.2f,var speed:Float=1f,var sid:Int=0,var referenceAudio:FloatArray?=null,var referenceSampleRate:Int=0,var referenceText:String?=null,var numSteps:Int=5,var extra:Map<String,String>?=null)
class GeneratedAudio(val samples:FloatArray,val sampleRate:Int)

class OfflineTts(var config: OfflineTtsConfig) {
    private var ptr = newFromFile(config)
    fun sampleRate()=getSampleRate(ptr)
    fun generateWithConfig(text:String, config:GenerationConfig)=generateWithConfigImpl(ptr,text,config,null)
    fun release(){ if(ptr!=0L){ delete(ptr);ptr=0 } }
    private external fun newFromFile(config:OfflineTtsConfig):Long
    private external fun delete(ptr:Long)
    private external fun getSampleRate(ptr:Long):Int
    private external fun generateWithConfigImpl(ptr:Long,text:String,config:GenerationConfig,callback:((FloatArray)->Int)?):GeneratedAudio
    companion object { init { System.loadLibrary("sherpa-onnx-jni") } }
}
