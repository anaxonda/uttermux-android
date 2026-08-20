package io.uttermux.android.audio

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** A queue bounded by audio duration rather than an arbitrary chunk count. */
class PcmChunkQueue(sampleRate:Int,maxSeconds:Double=6.0) {
    private val queue=LinkedBlockingQueue<ByteArray>();private val bytes=AtomicLong();private val maximum=(sampleRate*2*maxSeconds).toLong().coerceAtLeast(4096)
    fun offer(chunk:ByteArray,timeoutMs:Long,cancelled:AtomicBoolean?=null):Boolean {
        if(chunk.isEmpty())return queue.offer(chunk,timeoutMs,TimeUnit.MILLISECONDS)
        val deadline=System.nanoTime()+timeoutMs*1_000_000
        while(cancelled?.get()!=true&&System.nanoTime()<deadline){
            val current=bytes.get();val limit=maximum.coerceAtLeast(chunk.size.toLong())
            // A provider may emit one chunk longer than the target queue window.
            // It already occupies memory, so permit it only while the queue is empty.
            if(current+chunk.size<=limit&&bytes.compareAndSet(current,current+chunk.size)){
                if(queue.offer(chunk))return true;bytes.addAndGet(-chunk.size.toLong())
            };Thread.sleep(5)
        };return false
    }
    fun poll(timeoutMs:Long):ByteArray?=queue.poll(timeoutMs,TimeUnit.MILLISECONDS)?.also{if(it.isNotEmpty())bytes.addAndGet(-it.size.toLong())}
    fun clear(){queue.clear();bytes.set(0)}
    val queuedBytes get()=bytes.get()
}
