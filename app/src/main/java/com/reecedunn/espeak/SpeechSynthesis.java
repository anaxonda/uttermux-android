package com.reecedunn.espeak;

/** Minimal embedding API built from the official eSpeak NG Android JNI source. */
public final class SpeechSynthesis {
    public interface Callback { void onAudio(byte[] pcm); void onComplete(); void onWord(int position,int length,int frame); }
    static { System.loadLibrary("ttsespeak"); nativeClassInit(); }
    private final Callback callback; private final int sampleRate;
    public SpeechSynthesis(String dataParent, Callback callback) { this.callback=callback; sampleRate=nativeCreate(dataParent); if(sampleRate<=0)throw new IllegalStateException("eSpeak NG initialization failed"); }
    public int sampleRate(){return sampleRate;} public String[] voices(){return nativeGetAvailableVoices();}
    public boolean setVoice(String value){return nativeSetVoiceByName(value);} public boolean setRate(int value){return nativeSetParameter(1,value);}
    public boolean setPitch(int value){return nativeSetParameter(3,value);} public boolean synthesize(String text){return nativeSynthesize(text,false);} public boolean stop(){return nativeStop();}
    @SuppressWarnings("unused") private void nativeSynthCallback(byte[] data){if(data==null)callback.onComplete();else callback.onAudio(data);}
    @SuppressWarnings("unused") private void nativeSynthWordCallback(int position,int length,int frame){callback.onWord(position,length,frame);}
    private static native boolean nativeClassInit(); private native int nativeCreate(String path); private static native String nativeGetVersion();
    private native String[] nativeGetAvailableVoices(); private native boolean nativeSetVoiceByName(String name); private native boolean nativeSetVoiceByProperties(String language,int gender,int age);
    private native boolean nativeSetParameter(int parameter,int value); private native int nativeGetParameter(int parameter,int current); private native boolean nativeSetPunctuationCharacters(String characters);
    private native boolean nativeSynthesize(String text,boolean ssml); private native boolean nativeStop();
}
