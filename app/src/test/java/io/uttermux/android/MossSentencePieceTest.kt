package io.uttermux.android

import io.uttermux.android.provider.MossSentencePiece
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class MossSentencePieceTest {
    @Test fun scoreOrderedBpeAndByteFallbackMatchSentencePieceContract(){
        val pieces=listOf(
            Piece("<unk>",0f,2),Piece("▁",0f),Piece("H",0f),Piece("i",0f),
            Piece("▁H",1f),Piece("▁Hi",2f),Piece("<0xC3>",0f,6),Piece("<0xA9>",0f,6),
        )
        val model=File.createTempFile("moss-tokenizer",".model").apply{writeBytes(message(pieces));deleteOnExit()}
        val tokenizer=MossSentencePiece(model)
        assertArrayEquals(intArrayOf(5),tokenizer.encode("  Hi  "))
        assertArrayEquals(intArrayOf(1,6,7),tokenizer.encode("é"))
    }
    @Test fun unknownLengthDelimitedMetadataDoesNotDisplaceReader(){
        val encoded=message(listOf(Piece("<unk>",0f,2),Piece("▁",0f),Piece("x",0f),Piece("▁x",1f)))
        val model=File.createTempFile("moss-tokenizer-metadata",".model").apply{
            writeBytes(ByteArrayOutputStream().apply{fieldBytes(2,byteArrayOf(8,1,18,3,97,98,99));write(encoded)}.toByteArray());deleteOnExit()
        }
        assertArrayEquals(intArrayOf(3),MossSentencePiece(model).encode("x"))
    }
    private data class Piece(val text:String,val score:Float,val type:Int=1)
    private fun message(pieces:List<Piece>)=ByteArrayOutputStream().apply{pieces.forEach{piece->val child=ByteArrayOutputStream().apply{
        fieldBytes(1,piece.text.toByteArray());write((2 shl 3) or 5);fixed32(piece.score.toRawBits());write((3 shl 3) or 0);varint(piece.type)
    }.toByteArray();fieldBytes(1,child)}}.toByteArray()
    private fun ByteArrayOutputStream.fieldBytes(field:Int,value:ByteArray){write((field shl 3) or 2);varint(value.size);write(value)}
    private fun ByteArrayOutputStream.varint(value:Int){var current=value;while(true){if(current and 127.inv()==0){write(current);return};write((current and 127) or 128);current=current ushr 7}}
    private fun ByteArrayOutputStream.fixed32(value:Int){repeat(4){write(value ushr (it*8) and 255)}}
}

