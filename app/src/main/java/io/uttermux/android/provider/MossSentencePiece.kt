package io.uttermux.android.provider

import java.io.File
import java.text.Normalizer

/** Minimal SentencePiece BPE reader used by MOSS. It intentionally supports
 * the model features present in the pinned tokenizer: NMT-NFKC normalization,
 * dummy prefix, whitespace escaping, byte fallback, and score-ordered BPE. */
class MossSentencePiece(modelFile:File) {
    private data class Piece(val id:Int,val text:String,val score:Float,val type:Int)
    private val pieces:List<Piece>;private val byText:Map<String,Piece>
    init {pieces=parseModel(modelFile.readBytes());require(pieces.isNotEmpty()){ "MOSS tokenizer has no pieces" };byText=pieces.associateBy{it.text}}

    fun encode(text:String):IntArray {
        val normalized=Normalizer.normalize(text,Normalizer.Form.NFKC).trim().replace(Regex("\\s+")," ")
        if(normalized.isEmpty())return IntArray(0)
        val symbols=mutableListOf<String>();val escaped="▁"+normalized.replace(' ','▁')
        var offset=0
        while(offset<escaped.length){
            val cp=escaped.codePointAt(offset);val symbol=String(Character.toChars(cp));offset+=Character.charCount(cp)
            if(byText.containsKey(symbol))symbols+=symbol else symbol.toByteArray(Charsets.UTF_8).forEach{byte->symbols+="<0x${"%02X".format(byte.toInt() and 255)}>"}
        }
        while(symbols.size>1){
            var bestIndex=-1;var bestPiece:Piece?=null
            for(i in 0 until symbols.lastIndex){val candidate=byText[symbols[i]+symbols[i+1]]?:continue;if(bestPiece==null||candidate.score>bestPiece!!.score){bestPiece=candidate;bestIndex=i}}
            if(bestIndex<0)break;symbols[bestIndex]=bestPiece!!.text;symbols.removeAt(bestIndex+1)
        }
        val unknown=pieces.firstOrNull{it.type==2}?.id?:0
        return symbols.map{byText[it]?.id?:unknown}.toIntArray()
    }

    private fun parseModel(bytes:ByteArray):List<Piece>{
        val reader=ProtoReader(bytes);val result=mutableListOf<Piece>()
        while(reader.more()){val tag=reader.varint().toInt();val field=tag ushr 3;val wire=tag and 7
            if(field==1&&wire==2){val nested=ProtoReader(reader.bytes());var text="";var score=0f;var type=1
                while(nested.more()){val child=nested.varint().toInt();when(child ushr 3){1->text=String(nested.bytes(),Charsets.UTF_8);2->score=Float.fromBits(nested.fixed32());3->type=nested.varint().toInt();else->nested.skip(child and 7)}}
                result+=Piece(result.size,text,score,type)
            }else reader.skip(wire)
        };return result
    }

    private class ProtoReader(private val data:ByteArray){var position=0;fun more()=position<data.size
        fun varint():Long{var result=0L;var shift=0;while(position<data.size){val value=data[position++].toInt() and 255;result=result or ((value and 127).toLong() shl shift);if(value and 128==0)return result;shift+=7};error("Truncated protobuf varint")}
        fun bytes():ByteArray{val size=varint().toInt();require(size>=0&&position+size<=data.size){"Invalid protobuf length"};return data.copyOfRange(position,position+size).also{position+=size}}
        fun fixed32():Int{require(position+4<=data.size);return (data[position++].toInt() and 255) or ((data[position++].toInt() and 255) shl 8) or ((data[position++].toInt() and 255) shl 16) or ((data[position++].toInt() and 255) shl 24)}
        fun skip(wire:Int){when(wire){
            0->varint();1->position+=8
            // Keep the position read after varint(): using `position += varint()`
            // evaluates the old position first and lands before the real end.
            2->{val size=varint().toInt();position+=size}
            3->while(true){val nestedWire=varint().toInt() and 7;if(nestedWire==4)break;skip(nestedWire)}
            5->position+=4
            else->error("Unsupported protobuf wire type $wire")
        };require(position<=data.size){"Truncated protobuf field"}}
    }
}

