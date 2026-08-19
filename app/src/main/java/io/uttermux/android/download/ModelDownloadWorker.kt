package io.uttermux.android.download

import android.content.Context
import androidx.work.*
import io.uttermux.android.UtterMuxApp

class ModelDownloadWorker(context:Context,params:WorkerParameters):Worker(context,params){
    override fun doWork():Result {
        val id=inputData.getString(KEY_MODEL)?:return Result.failure(workDataOf("error" to "Missing model ID"))
        return runCatching{
            UtterMuxApp.instance.models.install(id,{message->setProgressAsync(workDataOf("message" to message))},{isStopped})
            Result.success(workDataOf("model" to id))
        }.getOrElse{error->if(isStopped)Result.retry()else Result.failure(workDataOf("error" to (error.message?:"Download failed")))}
    }
    companion object{const val KEY_MODEL="model"}
}

object ModelDownloads{
    fun enqueue(context:Context,id:String){
        val request=OneTimeWorkRequestBuilder<ModelDownloadWorker>().setInputData(workDataOf(ModelDownloadWorker.KEY_MODEL to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresStorageNotLow(true).build()).build()
        WorkManager.getInstance(context).enqueueUniqueWork("model-$id",ExistingWorkPolicy.KEEP,request)
    }
}
