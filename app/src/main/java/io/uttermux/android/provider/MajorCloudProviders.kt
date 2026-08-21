package io.uttermux.android.provider

import android.content.Context
import android.util.Base64
import io.uttermux.android.audio.CompressedAudioDecoder
import io.uttermux.android.config.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private data class AwsCredentials(val accessKey:String,val secretKey:String,val sessionToken:String="",val expiresAt:Long=Long.MAX_VALUE)

class AwsPollyProvider(private val secure:SecureStore):TtsProvider {
    override val id=ProviderIds.AWS
    override val descriptor=ProviderDescriptor(id,"Amazon Polly",credentialFields=listOf(
        CredentialField("aws_auth_mode","Authentication mode",false,"direct",listOf("direct","cognito","proxy")),
        CredentialField("aws_access_key","Access key ID",false),CredentialField("aws_secret_key","Secret access key"),
        CredentialField("aws_region","AWS region",false,"us-east-1"),CredentialField("aws_identity_pool","Cognito identity pool ID",false),
        CredentialField("aws_proxy","Proxy base URL",false),CredentialField("aws_token","Proxy bearer token"),
    ),note="Direct BYOK is supported. Use a dedicated IAM identity limited to polly:DescribeVoices and polly:SynthesizeSpeech.")
    @Volatile private var catalog=listOf(
        voice("Joanna","en-US","female","neural"),voice("Amy","en-GB","female","neural"),voice("Lea","fr-FR","female","neural"),
    )
    @Volatile private var temporary:AwsCredentials?=null
    override val voices get()=catalog
    private fun mode()=secure.get("aws_auth_mode").ifBlank{"direct"}
    private fun region()=secure.get("aws_region").ifBlank{"us-east-1"}
    private fun voice(name:String,locale:String,gender:String,engine:String)=VoiceRecord(
        "$id/$name/$engine@$locale","$name · Polly ${engine.replaceFirstChar(Char::uppercase)}",Locale.forLanguageTag(locale),id,"Polly $engine",setOf(locale),true,
        "$gender · AWS metered service",gender=gender,performanceClass="cloud",capabilities=setOf("streaming"),
    )
    override fun isAvailable(voice:VoiceRecord)=when(mode()){
        "proxy"->CloudContracts.validHttps(secure.get("aws_proxy"));"cognito"->secure.get("aws_identity_pool").isNotBlank()
        else->secure.get("aws_access_key").isNotBlank()&&secure.get("aws_secret_key").isNotBlank()
    }
    override fun refresh(){
        if(!isAvailable(catalog.first()))return
        if(mode()=="proxy"){refreshProxy();return}
        val found=mutableListOf<VoiceRecord>();var next=""
        do{
            val url="https://polly.${region()}.amazonaws.com/v1/voices".toHttpUrl().newBuilder().apply{if(next.isNotBlank())addQueryParameter("NextToken",next)}.build()
            val response=JSONObject(String(HttpAudio.executeBytes(AwsSigV4.request("GET",url.toString(),ByteArray(0),credentials(),region(),"polly"))))
            val array=response.optJSONArray("Voices")?:JSONArray()
            for(i in 0 until array.length()){
                val item=array.getJSONObject(i);val name=item.getString("Id");val locale=item.optString("LanguageCode","en-US");val gender=item.optString("Gender").lowercase()
                val engines=item.optJSONArray("SupportedEngines")?:JSONArray().put("standard")
                for(j in 0 until engines.length())found+=voice(name,locale,gender,engines.getString(j))
            }
            next=response.optString("NextToken")
        }while(next.isNotBlank())
        if(found.isNotEmpty())catalog=found.distinctBy{it.id}
    }
    private fun refreshProxy(){
        val base=CloudContracts.requireHttps(secure.get("aws_proxy"),"AWS proxy");val array=JSONArray(String(HttpAudio.get("$base/v1/voices",proxyHeaders())))
        val found=(0 until array.length()).map{array.getJSONObject(it)}.map{voice(it.getString("id"),it.optString("language","en-US"),it.optString("gender"),it.optString("model","neural"))}
        if(found.isNotEmpty())catalog=found
    }
    override fun strategy(voice:VoiceRecord)=StreamStrategy.DIRECT_STREAM
    override fun stream(session:PreparedSession,text:String,speed:Float,pitch:Float,cancelled:AtomicBoolean,emit:(AudioChunk)->Boolean){
        if(mode()=="proxy"){
            val base=CloudContracts.requireHttps(secure.get("aws_proxy"),"AWS proxy");val body=JSONObject().put("text",text).put("voice",externalVoice(session.voice)).put("model",engine(session.voice)).put("language",session.language).put("speed",speed)
            var sequence=0;HttpAudio.postStream("$base/v1/synthesize",body,proxyHeaders(),cancelled){emit(AudioChunk(it,24_000,TextRange(0,text.length),sequence++))};return
        }
        val voiceId=externalVoice(session.voice)
        val payload=JSONObject().put("Text",text).put("TextType","text").put("OutputFormat","pcm").put("SampleRate","16000")
            .put("VoiceId",voiceId).put("Engine",engine(session.voice))
        if(voiceId=="Aditi"&&session.language.lowercase().startsWith("hi"))payload.put("LanguageCode","hi-IN")
        val body=payload.toString().toByteArray()
        val request=AwsSigV4.request("POST","https://polly.${region()}.amazonaws.com/v1/speech",body,credentials(),region(),"polly","application/json")
        var sequence=0;HttpAudio.executeStream(request,cancelled){emit(AudioChunk(it,16_000,TextRange(0,text.length),sequence++))}
    }
    private fun externalVoice(voice:VoiceRecord)=voice.id.substringAfter('/').substringBefore('/')
    private fun engine(voice:VoiceRecord)=voice.id.substringAfter('/').substringAfter('/').substringBefore('@')
    private fun proxyHeaders()=secure.get("aws_token").takeIf(String::isNotBlank)?.let{mapOf("Authorization" to "Bearer $it")}.orEmpty()
    private fun credentials():AwsCredentials {
        if(mode()!="cognito")return AwsCredentials(secure.get("aws_access_key"),secure.get("aws_secret_key"))
        temporary?.takeIf{it.expiresAt-System.currentTimeMillis()>60_000}?.let{return it}
        val endpoint="https://cognito-identity.${region()}.amazonaws.com/"
        fun call(target:String,body:JSONObject)=JSONObject(String(HttpAudio.postRaw(endpoint,body.toString().toByteArray(),"application/x-amz-json-1.1",mapOf("X-Amz-Target" to "AWSCognitoIdentityService.$target"))))
        val pool=secure.get("aws_identity_pool");require(pool.isNotBlank()){ "Cognito identity pool is not configured" }
        val identity=call("GetId",JSONObject().put("IdentityPoolId",pool)).getString("IdentityId")
        val value=call("GetCredentialsForIdentity",JSONObject().put("IdentityId",identity)).getJSONObject("Credentials")
        return AwsCredentials(value.getString("AccessKeyId"),value.getString("SecretKey"),value.getString("SessionToken"),value.optLong("Expiration",System.currentTimeMillis()/1000+1800)*1000).also{temporary=it}
    }
}

private object AwsSigV4 {
    fun request(method:String,url:String,body:ByteArray,credentials:AwsCredentials,region:String,service:String,contentType:String="application/json"):Request {
        require(credentials.accessKey.isNotBlank()&&credentials.secretKey.isNotBlank()){ "AWS credentials are not configured" }
        val now=Date();val timestamp=format("yyyyMMdd'T'HHmmss'Z'",now);val day=format("yyyyMMdd",now);val parsed=url.toHttpUrl();val payloadHash=sha256(body)
        val headers=sortedMapOf("host" to parsed.host,"x-amz-content-sha256" to payloadHash,"x-amz-date" to timestamp)
        if(method!="GET")headers["content-type"]=contentType
        if(credentials.sessionToken.isNotBlank())headers["x-amz-security-token"]=credentials.sessionToken
        val canonicalHeaders=headers.entries.joinToString(""){"${it.key}:${it.value.trim()}\n"};val signedHeaders=headers.keys.joinToString(";")
        val query=(0 until parsed.querySize).map{encode(parsed.queryParameterName(it)) to encode(parsed.queryParameterValue(it).orEmpty())}.sortedWith(compareBy({it.first},{it.second})).joinToString("&"){"${it.first}=${it.second}"}
        val canonical=listOf(method,parsed.encodedPath.ifBlank{"/"},query,canonicalHeaders,signedHeaders,payloadHash).joinToString("\n")
        val scope="$day/$region/$service/aws4_request";val toSign="AWS4-HMAC-SHA256\n$timestamp\n$scope\n${sha256(canonical.toByteArray())}"
        val signing=hmac(hmac(hmac(hmac(("AWS4"+credentials.secretKey).toByteArray(),day),region),service),"aws4_request")
        val signature=hmac(signing,toSign).joinToString(""){"%02x".format(it)}
        val builder=Request.Builder().url(parsed).method(method,if(method=="GET")null else body.toRequestBody(contentType.toMediaType()))
        headers.forEach(builder::header);builder.header("Authorization","AWS4-HMAC-SHA256 Credential=${credentials.accessKey}/$scope, SignedHeaders=$signedHeaders, Signature=$signature")
        return builder.build()
    }
    private fun format(pattern:String,date:Date)=SimpleDateFormat(pattern,Locale.US).apply{timeZone=TimeZone.getTimeZone("UTC")}.format(date)
    private fun sha256(value:ByteArray)=MessageDigest.getInstance("SHA-256").digest(value).joinToString(""){"%02x".format(it)}
    private fun hmac(key:ByteArray,value:String)=Mac.getInstance("HmacSHA256").apply{init(SecretKeySpec(key,"HmacSHA256"))}.doFinal(value.toByteArray())
    private fun encode(value:String)=URLEncoder.encode(value,"UTF-8").replace("+","%20").replace("%7E","~")
}

class GoogleCloudProvider(private val context:Context,private val secure:SecureStore):TtsProvider {
    override val id=ProviderIds.GOOGLE
    override val descriptor=ProviderDescriptor(id,"Google Cloud TTS",credentialFields=listOf(
        CredentialField("google_auth_mode","Authentication mode",false,"direct",listOf("direct","proxy")),CredentialField("google_api_key","Restricted API key"),
        CredentialField("google_proxy","Proxy base URL",false),CredentialField("google_token","Proxy bearer token"),
    ),note="Restrict direct keys to the Cloud Text-to-Speech API and set billing quotas.")
    @Volatile private var catalog=listOf(voice("en-US-Chirp3-HD-Charon","en-US","male"),voice("fr-FR-Chirp3-HD-Aoede","fr-FR","female"))
    override val voices get()=catalog
    private fun mode()=secure.get("google_auth_mode").ifBlank{"direct"}
    private fun voice(name:String,locale:String,gender:String)=VoiceRecord("$id/$name@$locale","$name · Google",Locale.forLanguageTag(locale),id,model(name),setOf(locale),true,"$gender · Google metered service",gender=gender,performanceClass="cloud")
    private fun model(name:String)=when{ "Chirp3" in name->"Chirp 3 HD";"Neural2" in name->"Neural2";"Wavenet" in name||"WaveNet" in name->"WaveNet";else->"Standard" }
    override fun isAvailable(voice:VoiceRecord)=CloudContracts.configured(id){secure.get(it)}
    override fun refresh(){
        if(!isAvailable(catalog.first()))return
        val bytes=if(mode()=="proxy")HttpAudio.get("${CloudContracts.requireHttps(secure.get("google_proxy"),"Google proxy")}/v1/voices",proxyHeaders()) else HttpAudio.get("https://texttospeech.googleapis.com/v1/voices?key=${URLEncoder.encode(secure.get("google_api_key"),"UTF-8")}",androidKeyHeaders())
        val root=runCatching{JSONObject(String(bytes)).optJSONArray("voices")}.getOrNull()?:JSONArray(String(bytes));val found=mutableListOf<VoiceRecord>()
        for(i in 0 until root.length()){
            val item=root.getJSONObject(i);val name=item.getString("name");val gender=item.optString("ssmlGender",item.optString("gender")).lowercase();val languages=item.optJSONArray("languageCodes")
            if(languages!=null)for(j in 0 until languages.length())found+=voice(name,languages.getString(j),gender) else found+=voice(name,item.optString("language","en-US"),gender)
        }
        if(found.isNotEmpty())catalog=found.distinctBy{it.id}
    }
    override fun strategy(voice:VoiceRecord)=StreamStrategy.BUFFERED
    override fun stream(session:PreparedSession,text:String,speed:Float,pitch:Float,cancelled:AtomicBoolean,emit:(AudioChunk)->Boolean){
        val name=session.voice.id.substringAfter('/').substringBefore('@')
        if(mode()=="proxy"){
            val body=JSONObject().put("text",text).put("voice",name).put("language",session.language).put("speed",speed)
            var sequence=0;HttpAudio.postStream("${CloudContracts.requireHttps(secure.get("google_proxy"),"Google proxy")}/v1/synthesize",body,proxyHeaders(),cancelled){emit(AudioChunk(it,24_000,TextRange(0,text.length),sequence++))};return
        }
        val body=JSONObject().put("input",JSONObject().put("text",text)).put("voice",JSONObject().put("name",name).put("languageCode",session.language))
            .put("audioConfig",JSONObject().put("audioEncoding","LINEAR16").put("sampleRateHertz",24000).put("speakingRate",speed.coerceIn(.25f,2f)))
        val bytes=HttpAudio.post("https://texttospeech.googleapis.com/v1/text:synthesize?key=${URLEncoder.encode(secure.get("google_api_key"),"UTF-8")}",body,androidKeyHeaders(),cancelled)
        val wav=Base64.decode(JSONObject(String(bytes)).getString("audioContent"),Base64.DEFAULT);val audio=CompressedAudioDecoder.decode(context,wav,"wav")
        emit(AudioChunk(audio.pcm16,audio.sampleRate,TextRange(0,text.length),0))
    }
    private fun proxyHeaders()=secure.get("google_token").takeIf(String::isNotBlank)?.let{mapOf("Authorization" to "Bearer $it")}.orEmpty()
    private fun androidKeyHeaders():Map<String,String> {
        @Suppress("DEPRECATION")
        val certificate=when {
            android.os.Build.VERSION.SDK_INT>=33->context.packageManager.getPackageInfo(context.packageName,android.content.pm.PackageManager.PackageInfoFlags.of(android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES.toLong())).signingInfo?.apkContentsSigners?.firstOrNull()
            android.os.Build.VERSION.SDK_INT>=28->context.packageManager.getPackageInfo(context.packageName,android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners?.firstOrNull()
            else->context.packageManager.getPackageInfo(context.packageName,android.content.pm.PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
        }?.toByteArray()?:return emptyMap()
        val sha1=MessageDigest.getInstance("SHA-1").digest(certificate).joinToString(""){"%02X".format(it)}
        return mapOf("X-Android-Package" to context.packageName,"X-Android-Cert" to sha1)
    }
}
