package io.uttermux.android.provider

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object HttpAudio {
    val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    val json = "application/json".toMediaType()
    fun post(url: String, body: JSONObject, headers: Map<String, String>, cancelled: AtomicBoolean? = null): ByteArray {
        val builder = Request.Builder().url(url).post(body.toString().toRequestBody(json))
        headers.forEach(builder::header)
        val call = client.newCall(builder.build())
        val result = AtomicReference<Result<ByteArray>>(); val done = CountDownLatch(1)
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, error: java.io.IOException) { result.set(Result.failure(error)); done.countDown() }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) = response.use {
                result.set(runCatching {
                    if (!it.isSuccessful) throw ProviderException(it.code, it.body.string().take(1000))
                    it.body.bytes()
                }); done.countDown()
            }
        })
        while (!done.await(50, TimeUnit.MILLISECONDS)) {
            if (cancelled?.get() == true) { call.cancel(); throw InterruptedException() }
        }
        if (cancelled?.get() == true) throw InterruptedException()
        return result.get().getOrThrow()
    }
    fun postStream(url:String, body:JSONObject, headers:Map<String,String>, cancelled:AtomicBoolean,
                   emit:(ByteArray)->Boolean) {
        postStreamRaw(url,body.toString().toByteArray(),"application/json",headers,cancelled,emit)
    }
    fun postStreamRaw(url:String,body:ByteArray,contentType:String,headers:Map<String,String>,cancelled:AtomicBoolean,
                      emit:(ByteArray)->Boolean) {
        val builder=Request.Builder().url(url).post(body.toRequestBody(contentType.toMediaType()));headers.forEach(builder::header)
        val call=client.newCall(builder.build());val finished=AtomicBoolean()
        Thread({
            while(!finished.get()&&!cancelled.get()) Thread.sleep(25)
            if(cancelled.get()) call.cancel()
        },"uttermux-http-cancel").apply{isDaemon=true;start()}
        try {
            call.execute().use { response ->
                if(!response.isSuccessful) throw ProviderException(response.code,response.body.string().take(1000))
                val input=response.body.byteStream();val buffer=ByteArray(16384);var carry=-1
                while(!cancelled.get()) {
                    val start=if(carry>=0)1 else 0;val count=input.read(buffer,start,buffer.size-start)
                    if(count<0) break
                    if(carry>=0) buffer[0]=carry.toByte()
                    val total=count+start;val even=total-total%2
                    if(even>0&&!emit(buffer.copyOf(even))) return
                    carry=if(total%2==1) buffer[total-1].toInt() and 255 else -1
                }
                if(cancelled.get()) throw InterruptedException()
            }
        } finally { finished.set(true) }
    }
    fun postRaw(url:String,body:ByteArray,contentType:String,headers:Map<String,String>,cancelled:AtomicBoolean?=null):ByteArray {
        val builder=Request.Builder().url(url).post(body.toRequestBody(contentType.toMediaType()));headers.forEach(builder::header)
        val call=client.newCall(builder.build())
        call.execute().use{response->
            if(cancelled?.get()==true){call.cancel();throw InterruptedException()}
            if(!response.isSuccessful)throw ProviderException(response.code,response.body.string().take(1000))
            return response.body.bytes()
        }
    }
    fun get(url: String, headers: Map<String, String> = emptyMap()): ByteArray {
        val builder = Request.Builder().url(url)
        headers.forEach(builder::header)
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw ProviderException(response.code, response.body.string().take(1000))
            return response.body.bytes()
        }
    }
}

class ProviderException(val status: Int, detail: String) : RuntimeException("Provider HTTP $status: $detail")
