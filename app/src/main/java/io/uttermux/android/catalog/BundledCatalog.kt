package io.uttermux.android.catalog

import io.uttermux.android.config.ModelCatalogEntry

object BundledCatalog {
    val researchModels=listOf(
        ModelCatalogEntry("moss-tts-nano-100m","moss","MOSS-TTS-Nano",setOf("multilingual"),"blocked",120,"Apache-2.0","Official Android ONNX demo exists, but arbitrary text still requires a production SentencePiece tokenizer.","https://github.com/OpenMOSS/MOSS-TTS-Nano"),
        ModelCatalogEntry("qwen3-tts-0.6b-customvoice-q5","qwen","Qwen3-TTS 0.6B CustomVoice Q5/MXFP4",setOf("multilingual"),"benchmark",869,"Apache-2.0","Heavyweight GGUF candidate; runtime and long-form stability must pass device benchmarks.","https://github.com/QwenLM/Qwen3-TTS"),
        ModelCatalogEntry("qwen3-tts-0.6b-base-q5","qwen","Qwen3-TTS 0.6B Base Q5/MXFP4",setOf("multilingual"),"benchmark",869,"Apache-2.0","Voice-cloning variant; runtime not bundled.","https://github.com/QwenLM/Qwen3-TTS"),
        ModelCatalogEntry("audio8-tts-0.6b-int4","audio8","Audio8 TTS 0.6B INT4",setOf("multilingual"),"benchmark",968,"research","Official ONNX package; Android memory and RTF are not validated.","https://github.com/Audio8-AI/Audio8_TTS"),
        ModelCatalogEntry("chatterbox-nano","chatterbox","Chatterbox Nano",setOf("en"),"watchlist",110,"research","Community mobile runtime only.","https://huggingface.co/ResembleAI/chatterbox-nano"),
        ModelCatalogEntry("neutts-nano","neutts","NeuTTS Nano",setOf("en","fr","de","es"),"watchlist",229,"research","GGUF backbone plus ONNX codec; dedicated runtime required.","https://github.com/neuphonic/neutts"),
        ModelCatalogEntry("x-voice-0.4b","x-voice","X-Voice 0.4B",setOf("multilingual"),"incompatible",400,"CC-BY-NC-4.0","No credible Android/ONNX runtime and non-commercial weights.","https://github.com/sunnyxrxrx/X-Voice"),
        ModelCatalogEntry("lemas-tts","lemas","LEMAS-TTS",setOf("multilingual"),"watchlist",300,"research","ONNX assets but no mature Android deployment.",""),
        ModelCatalogEntry("omnivoice","omnivoice","OmniVoice",setOf("multilingual"),"watchlist",800,"research","No validated Android deployment.",""),
    )
}
