package io.uttermux.android

import io.uttermux.android.audio.AdaptiveBufferPolicy
import io.uttermux.android.audio.PcmChunkQueue
import io.uttermux.android.audio.TextSegmenter
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test

class StreamingArchitectureTest {
    @Test fun segmentationPreservesEveryCharacterAndExactRanges(){
        val text=("First sentence. Second sentence with more words! Third clause; ").repeat(20)
        val segments=TextSegmenter.split(text)
        assertEquals(text,segments.joinToString(""){it.text})
        segments.forEach{assertEquals(it.text,text.substring(it.range.start,it.range.endExclusive));assertTrue(it.text.length<=360)}
        assertTrue(segments.first().text.length<=360)
    }

    @Test fun pocketSizedSegmentationBreaksTheReaderPassageAtSentenceBoundaries(){
        val text="MISTER HANEDA WAS senior to Mister Omochi, who was senior to Mister Saito, who was senior to Miss Mori, who was senior to me, I was senior to no one. You could put this another way. I took orders from Miss Mori, who took orders from Mister Saito, and so on up the ladder; of course, orders that came down could jump a level or two"
        val segments=TextSegmenter.split(text,firstTarget=100,nextTarget=120,maxChars=165)
        assertEquals(text,segments.joinToString(""){it.text})
        assertTrue(segments.size>=3)
        assertTrue(segments[0].text.endsWith("no one."))
        assertTrue(segments.all{it.text.length<=165})
    }

    @Test fun adaptivePolicyTradesLatencyForUnderrunProtection(){
        val fast=AdaptiveBufferPolicy.startupMillis("automatic",0,.3,0)
        val slow=AdaptiveBufferPolicy.startupMillis("automatic",0,1.4,2)
        assertTrue(slow>fast)
        assertTrue(AdaptiveBufferPolicy.startupMillis("smooth",0,.3,0)>AdaptiveBufferPolicy.startupMillis("low",0,.3,0))
        assertEquals(1234,AdaptiveBufferPolicy.startupMillis("manual",1234,4.0,4))
    }

    @Test fun durationQueueAllowsOneOversizedProviderChunkWithoutDeadlock(){
        val queue=PcmChunkQueue(sampleRate=10,maxSeconds=.1)
        val chunk=ByteArray(100)
        assertTrue(queue.offer(chunk,50,AtomicBoolean()))
        assertEquals(100,queue.queuedBytes)
        assertArrayEquals(chunk,queue.poll(50))
        assertEquals(0,queue.queuedBytes)
    }

}
