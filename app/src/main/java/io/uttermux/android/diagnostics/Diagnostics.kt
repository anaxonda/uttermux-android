package io.uttermux.android.diagnostics

import android.util.Log
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

data class DiagnosticEvent(val requestId:Long,val atMillis:Long,val name:String,val detail:String="")

object Diagnostics {
    private val ids=AtomicLong();private val events=ArrayDeque<DiagnosticEvent>();private const val LIMIT=500
    fun request(detail:String):Long=ids.incrementAndGet().also{record(it,"request",detail)}
    @Synchronized fun record(id:Long,name:String,detail:String="") {
        events.addLast(DiagnosticEvent(id,System.currentTimeMillis(),name,detail))
        while(events.size>LIMIT)events.removeFirst()
        Log.i("UtterMuxTiming","#$id $name $detail")
    }
    @Synchronized fun recent():List<DiagnosticEvent> = events.toList()
    @Synchronized fun clear(){events.clear()}
    private fun safe(value:String)=value
        .replace(Regex("(?i)(api[-_ ]?key|token|secret|authorization)([=: ]+)[^\\s,;]+"),"\$1\$2[redacted]")
        .take(300)
    fun report():String=recent().joinToString("\n"){"${it.atMillis}\t#${it.requestId}\t${it.name}\t${safe(it.detail)}"}
}
