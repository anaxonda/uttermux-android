package io.uttermux.android.provider

import ai.onnxruntime.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

data class MossPcm(val samples:FloatArray,val sampleRate:Int,val generatedNanos:Long)

/** Production wrapper derived from OpenMOSS's official Android ONNX example.
 * Token generation and codec decoding use separate sessions and a bounded
 * frame queue, allowing decoding/output to overlap autoregressive generation. */
class MossRuntime(private val root:File,threads:Int=4):Closeable {
    private val env=OrtEnvironment.getEnvironment();private val manifestFile=File(root,"MOSS-TTS-Nano-100M-ONNX/browser_poc_manifest.json")
    private val manifest=JSONObject(manifestFile.readText());private val ttsDir=manifestFile.parentFile!!;private val codecDir=File(root,"MOSS-Audio-Tokenizer-Nano-ONNX")
    private val ttsMeta=JSONObject(File(ttsDir,manifest.getJSONObject("model_files").getString("tts_meta")).readText())
    private val codecMeta=JSONObject(File(codecDir,"codec_browser_onnx_meta.json").readText());private val cfg=manifest.getJSONObject("tts_config")
    private val options=OrtSession.SessionOptions().apply{setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);setIntraOpNumThreads(threads.coerceIn(1,6));setInterOpNumThreads(1)}
    private val prefill=session(ttsDir,ttsMeta.getJSONObject("files").getString("prefill"));private val decode=session(ttsDir,ttsMeta.getJSONObject("files").getString("decode_step"))
    private val localFrame=session(ttsDir,ttsMeta.getJSONObject("files").getString("local_fixed_sampled_frame"));private val codec=session(codecDir,codecMeta.getJSONObject("files").getString("decode_full"))
    private val tokenizer=MossSentencePiece(File(ttsDir,manifest.getJSONObject("model_files").getString("tokenizer_model")))
    val voices:List<String> = (manifest.optJSONArray("builtin_voices")?:JSONArray()).objects().map{it.optString("voice")}.filter{it.isNotBlank()&&it!="Trump"}
    val sampleRate=codecMeta.getJSONObject("codec_config").getInt("sample_rate")

    fun stream(text:String,voice:String,cancelled:AtomicBoolean,emit:(MossPcm)->Boolean){
        val ids=tokenizer.encode(text);require(ids.isNotEmpty()){ "MOSS received empty text" };val inputs=buildRows(ids,voice);val initial=prefill(inputs)
        val queue=ArrayBlockingQueue<IntArray>(64);val finished=AtomicBoolean();val failure=AtomicReference<Throwable?>()
        val producer=Thread({try{decode(initial,cancelled){frame->while(!cancelled.get())if(queue.offer(frame,50,TimeUnit.MILLISECONDS))return@decode true;false}}catch(t:Throwable){failure.set(t)}finally{finished.set(true)}},"uttermux-moss-generator").apply{start()}
        val frames=mutableListOf<IntArray>();var emittedSamples=0;var last=System.nanoTime()
        try{
            while(!finished.get()||queue.isNotEmpty()){
                val frame=queue.poll(50,TimeUnit.MILLISECONDS);if(frame!=null)frames+=frame
                val shouldDecode=frames.isNotEmpty()&&(frames.size%16==0||finished.get()&&queue.isEmpty())
                if(shouldDecode){val audio=decodeAudio(frames);if(audio.size>emittedSamples){val now=System.nanoTime();val chunk=audio.copyOfRange(emittedSamples,audio.size);emittedSamples=audio.size;if(!emit(MossPcm(chunk,sampleRate,now-last))){cancelled.set(true);break};last=now}}
                if(cancelled.get())throw InterruptedException()
            }
            failure.get()?.let{throw it};require(emittedSamples>0){"MOSS generated no audio"}
        }finally{cancelled.takeIf{it.get()}?.let{producer.interrupt()};producer.join(1000)}
    }

    override fun close(){codec.close();localFrame.close();decode.close();prefill.close();options.close()}
    private fun session(dir:File,name:String):OrtSession{val file=File(dir,name);require(file.isFile){"Missing MOSS file: ${file.name}"};return env.createSession(file.absolutePath,options)}
    private data class Inputs(val rows:Array<IntArray>,val mask:IntArray)
    private data class Initial(val hidden:OnnxTensor,val length:Int,val past:OrtSession.Result)
    private fun buildRows(text:IntArray,voice:String):Inputs{
        val n=cfg.getInt("n_vq");val width=n+1;val pad=cfg.getInt("audio_pad_token_id");val rows=mutableListOf<IntArray>()
        fun textRows(values:IntArray)=values.forEach{token->rows+=IntArray(width){if(it==0)token else pad}}
        val templates=manifest.getJSONObject("prompt_templates");textRows(templates.getJSONArray("user_prompt_prefix_token_ids").ints()+cfg.getInt("audio_start_token_id"))
        val selected=(manifest.getJSONArray("builtin_voices").objects().firstOrNull{it.optString("voice")==voice}?:manifest.getJSONArray("builtin_voices").getJSONObject(0)).getJSONArray("prompt_audio_codes")
        for(i in 0 until selected.length()){val source=selected.getJSONArray(i);rows+=IntArray(width){index->when{index==0->cfg.optInt("audio_user_slot_token_id",8);index-1<min(source.length(),n)->source.getInt(index-1);else->pad}}}
        textRows(intArrayOf(cfg.getInt("audio_end_token_id"))+templates.getJSONArray("user_prompt_after_reference_token_ids").ints()+text+templates.getJSONArray("assistant_prompt_prefix_token_ids").ints()+cfg.getInt("audio_start_token_id"))
        return Inputs(rows.toTypedArray(),IntArray(rows.size){1})
    }
    private fun prefill(input:Inputs):Initial{
        val width=input.rows[0].size;val flat=IntArray(input.rows.size*width);var offset=0;input.rows.forEach{row->row.forEach{flat[offset++]=it}}
        OnnxTensor.createTensor(env,IntBuffer.wrap(flat),longArrayOf(1,input.rows.size.toLong(),width.toLong())).use{ids->OnnxTensor.createTensor(env,IntBuffer.wrap(input.mask),longArrayOf(1,input.mask.size.toLong())).use{mask->
            val result=prefill.run(mapOf("input_ids" to ids,"attention_mask" to mask));return Initial(lastHidden(result.tensor("global_hidden")),input.rows.size,result)
        }}
    }
    private fun decode(initial:Initial,cancelled:AtomicBoolean,onFrame:(IntArray)->Boolean){
        val n=cfg.getInt("n_vq");val width=n+1;val pad=cfg.getInt("audio_pad_token_id");val seen=Array(n){HashSet<Int>()};val inputNames=ttsMeta.getJSONObject("onnx").getJSONArray("decode_input_names").strings().drop(2);val outputNames=ttsMeta.getJSONObject("onnx").getJSONArray("decode_output_names").strings().drop(1)
        var hidden=initial.hidden;var past:OrtSession.Result?=initial.past;var length=initial.length;val random=java.util.Random(1234);val max=manifest.optJSONObject("generation_defaults")?.optInt("max_new_frames",375)?:375
        try{for(step in 0 until max){if(cancelled.get())throw InterruptedException();val frame=sampleFrame(hidden,seen,random)?:break;if(!onFrame(frame))break
            val row=IntArray(width){if(it==0)cfg.getInt("audio_assistant_slot_token_id") else pad};frame.forEachIndexed{i,value->row[i+1]=value;seen[i]+=value}
            OnnxTensor.createTensor(env,IntBuffer.wrap(row),longArrayOf(1,1,width.toLong())).use{ids->OnnxTensor.createTensor(env,IntBuffer.wrap(intArrayOf(length)),longArrayOf(1)).use{pastLength->
                val previous=past?:error("Missing MOSS KV cache");val feeds=linkedMapOf<String,OnnxTensorLike>("input_ids" to ids,"past_valid_lengths" to pastLength);inputNames.indices.forEach{feeds[inputNames[it]]=previous.tensor(outputNames[it])}
                val next=decode.run(feeds);val nextHidden=lastHidden(next.tensor("global_hidden"));hidden.close();previous.close();past=next;hidden=nextHidden;length++
            }}
        }}finally{hidden.close();past?.close()}
    }
    private fun sampleFrame(hidden:OnnxTensor,seen:Array<HashSet<Int>>,random:java.util.Random):IntArray?{
        val n=cfg.getInt("n_vq");val size=cfg.getJSONArray("audio_codebook_sizes").optInt(0,1024);val mask=IntArray(n*size);seen.forEachIndexed{i,set->set.forEach{if(it in 0 until size)mask[i*size+it]=1}}
        val assistant=floatArrayOf(random.nextDouble().coerceIn(1e-6,1.0-1e-6).toFloat());val audio=FloatArray(n){random.nextDouble().coerceIn(1e-6,1.0-1e-6).toFloat()}
        OnnxTensor.createTensor(env,IntBuffer.wrap(mask),longArrayOf(1,n.toLong(),size.toLong())).use{seenTensor->OnnxTensor.createTensor(env,FloatBuffer.wrap(assistant),longArrayOf(1)).use{assistantTensor->OnnxTensor.createTensor(env,FloatBuffer.wrap(audio),longArrayOf(1,n.toLong())).use{audioTensor->
            localFrame.run(mapOf("global_hidden" to hidden,"repetition_seen_mask" to seenTensor,"assistant_random_u" to assistantTensor,"audio_random_u" to audioTensor)).use{result->if(result.tensor("should_continue").ints().firstOrNull()!=1)return null;return result.tensor("frame_token_ids").ints()}
        }}}
    }
    private fun decodeAudio(frames:List<IntArray>):FloatArray{
        val n=cfg.getInt("n_vq");val flat=IntArray(frames.size*n);var offset=0;frames.forEach{row->repeat(n){flat[offset++]=row[it]}}
        OnnxTensor.createTensor(env,IntBuffer.wrap(flat),longArrayOf(1,frames.size.toLong(),n.toLong())).use{codes->OnnxTensor.createTensor(env,IntBuffer.wrap(intArrayOf(frames.size)),longArrayOf(1)).use{lengths->
            codec.run(mapOf("audio_codes" to codes,"audio_code_lengths" to lengths)).use{result->
                val raw=result.tensor("audio").value as Array<*>;val batch=raw[0] as Array<*>;val channels=batch.map{it as FloatArray};val reported=result.tensor("audio_lengths").ints().first();val length=min(reported,channels.minOf{it.size});return FloatArray(length){i->channels.sumOf{it[i].toDouble()}.toFloat()/channels.size}
            }
        }}
    }
    private fun lastHidden(tensor:OnnxTensor):OnnxTensor{val shape=tensor.info.shape;val hidden=if(shape.size==2)(tensor.value as Array<*>)[0] as FloatArray else ((tensor.value as Array<*>)[0] as Array<*>).last() as FloatArray;return OnnxTensor.createTensor(env,FloatBuffer.wrap(hidden.copyOf()),longArrayOf(1,hidden.size.toLong()))}
    private fun OrtSession.Result.tensor(name:String)=get(name).orElseThrow{IllegalStateException("Missing MOSS output $name")} as OnnxTensor
    private fun OnnxTensor.ints():IntArray{val out=mutableListOf<Int>();fun add(value:Any?){when(value){is Int->out+=value;is Long->out+=value.toInt();is IntArray->out+=value.toList();is LongArray->value.forEach{out+=it.toInt()};is Array<*>->value.forEach(::add)}};add(value);return out.toIntArray()}
    private fun JSONArray.ints()=IntArray(length()){getInt(it)}
    private fun JSONArray.strings()=List(length()){getString(it)}
    private fun JSONArray.objects()=List(length()){getJSONObject(it)}
}
