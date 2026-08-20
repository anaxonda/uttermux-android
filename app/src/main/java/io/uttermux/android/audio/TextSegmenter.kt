package io.uttermux.android.audio

import io.uttermux.android.config.TextRange

data class TextSegment(val text:String,val range:TextRange)

object TextSegmenter {
    fun split(text:String,firstTarget:Int=120,nextTarget:Int=260,maxChars:Int=360,boundaries:String=".!?;:\n"):List<TextSegment> {
        if(text.length<=maxChars)return listOf(TextSegment(text,TextRange(0,text.length)))
        val result=mutableListOf<TextSegment>();var start=0;var target=firstTarget
        while(start<text.length) {
            val hardEnd=minOf(text.length,start+maxChars)
            val desired=minOf(text.length,start+target)
            var end=findBoundary(text,start,desired,hardEnd,boundaries)
            if(end<=start)end=hardEnd
            result+=TextSegment(text.substring(start,end),TextRange(start,end))
            start=end;target=nextTarget
        }
        return result
    }
    private fun findBoundary(text:String,start:Int,desired:Int,hardEnd:Int,boundaries:String):Int {
        if(hardEnd==text.length)return hardEnd
        for(i in hardEnd downTo desired)if(text[i-1] in boundaries)return i
        for(i in hardEnd downTo desired)if(text[i-1].isWhitespace())return i
        for(i in desired downTo start+1)if(text[i-1] in boundaries||text[i-1].isWhitespace())return i
        return hardEnd
    }
}
