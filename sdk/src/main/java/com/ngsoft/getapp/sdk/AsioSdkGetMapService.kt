package com.ngsoft.getapp.sdk

import GetApp.Client.models.PrepareDeliveryResDto
import android.content.Context
import android.util.Log
import com.ngsoft.getapp.sdk.models.MapDownloadData
import com.ngsoft.getapp.sdk.models.MapDeliveryState
import com.ngsoft.getapp.sdk.models.MapImportState
import com.ngsoft.getapp.sdk.models.MapProperties
import java.util.Timer
import java.util.concurrent.TimeUnit
import kotlin.concurrent.timer
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource


internal class AsioSdkGetMapService (private val appCtx: Context) : DefaultGetMapService(appCtx) {

    private val _tag = "AsioSdkGetMapService"

    private var deliveryTimeoutMinutes: Int = 5
    private var downloadTimeoutMinutes: Int = 5



    override fun init(configuration: Configuration): Boolean {
        super.init(configuration)
        deliveryTimeoutMinutes = configuration.deliveryTimeout
        downloadTimeoutMinutes = configuration.downloadTimeout

        return true
    }

    override fun downloadMap(mp: MapProperties, downloadStatusHandler: (MapDownloadData) -> Unit){
        Log.d(_tag, "downloadMap for -> $mp")
        var tmr: Timer? = null
        val downloadData: MapDownloadData

        try{
            downloadData = getDownloadData(mp)
        }catch (e: Exception){
            downloadStatusHandler.invoke(MapDownloadData(
                deliveryStatus=MapDeliveryState.ERROR,
                errorContent = e.toString()))

            Log.e(_tag, "downloadMap - getDownloadData: ${e.toString()}")
            return
        }
        Log.i(_tag, "downloadMap-> download-data: $downloadData")
        if (downloadData.deliveryStatus != MapDeliveryState.START) {
            downloadStatusHandler.invoke(downloadData)
            return
        }
        downloadData.fileName = PackageDownloader.getFileNameFromUri(downloadData.url!!)
        downloadData.deliveryStatus = MapDeliveryState.DOWNLOAD;
        downloadStatusHandler.invoke(downloadData)

        val downloadCompletionHandler: (Long) -> Unit = {
            Log.d(_tag,"processing download ID=$it completion event...")
            Log.d(_tag,"stopping progress watcher...")
            tmr?.cancel()
            downloadData.deliveryStatus = MapDeliveryState.DOWNLOAD;
            downloadData.downloadProgress = 100
            downloadStatusHandler.invoke(downloadData);
        }


        val downloadId: Long
        try{
            downloadId = downloader.downloadFile(downloadData.url!!, downloadCompletionHandler)
        }catch (e: Exception){
            downloadData.deliveryStatus = MapDeliveryState.ERROR;
            downloadData.errorContent = e.toString();
            Log.e(_tag, "downloadMap - downloadFile: ${e.toString()} " )
            return
        }

        Log.d(_tag, "downloadMap -> downloadId: $downloadId")


        tmr = timer(initialDelay = 100, period = 500 ) {
            val fileProgress = downloader.queryProgress(downloadId)
            if(fileProgress.second > 0) {
                val progress = (fileProgress.first * 100 / fileProgress.second).toInt()
                Log.d(_tag, "downloadId: $downloadId -> process: $progress ")

                downloadData.deliveryStatus = MapDeliveryState.DOWNLOAD;
                downloadData.downloadProgress = progress
                downloadStatusHandler.invoke(downloadData)
            }
        }

    }

    private fun getDownloadData(mp: MapProperties,): MapDownloadData {
        val mapDownloadData = MapDownloadData(deliveryStatus=MapDeliveryState.START);

        val retCreate = createMapImport(mp)

        when(retCreate?.state){
            MapImportState.IN_PROGRESS -> Log.d(_tag,"deliverTile - createMapImport => IN_PROGRESS")
            MapImportState.START -> if( !checkImportStatus(retCreate.importRequestId!!)){
                mapDownloadData.deliveryStatus = MapDeliveryState.ERROR;
//                TODO get the error message
                return mapDownloadData
            }
            MapImportState.DONE -> Log.d(_tag,"deliverTile - createMapImport => DONE")
            MapImportState.CANCEL -> {
                Log.w(_tag,"getDownloadData - createMapImport => CANCEL")
                mapDownloadData.deliveryStatus = MapDeliveryState.CANCEL;
                return mapDownloadData
            }
            else -> {
                Log.e(_tag,"getDownloadData - createMapImport failed: ${retCreate?.state}")
                mapDownloadData.deliveryStatus = MapDeliveryState.ERROR;
//                TODO get the error message
                return mapDownloadData
            }
        }

        val retDelivery = setMapImportDeliveryStart(retCreate.importRequestId!!)
        when(retDelivery?.state){
            MapDeliveryState.DONE -> Log.d(_tag,"deliverTile - setMapImportDeliveryStart => DONE")
            MapDeliveryState.START -> if( !checkDeliveryStatus(retCreate.importRequestId!!)) {
                mapDownloadData.deliveryStatus = MapDeliveryState.ERROR;
//                TODO get the error message
                return mapDownloadData
            }
            MapDeliveryState.DOWNLOAD,
            MapDeliveryState.CONTINUE,
            MapDeliveryState.PAUSE ->  Log.d(_tag,"deliverTile - setMapImportDeliveryStart => ${retDelivery.state}")
            MapDeliveryState.CANCEL -> {
                Log.w(_tag,"getDownloadData - setMapImportDeliveryStart => CANCEL")
                mapDownloadData.deliveryStatus = MapDeliveryState.CANCEL;
                return mapDownloadData
            }
            else -> {
                Log.e(_tag,"getDownloadData - setMapImportDeliveryStart failed: ${retDelivery?.state}")
//                downloadStatus.invoke(DownloadHebStatus.FAILED, 0)
                mapDownloadData.deliveryStatus = MapDeliveryState.ERROR;
//                TODO get the error message
                return mapDownloadData
            }
        }

        val deliveryStatus = client.deliveryApi.deliveryControllerGetPreparedDeliveryStatus(retCreate.importRequestId!!)
        if(deliveryStatus.status != PrepareDeliveryResDto.Status.done) {
            Log.e(_tag,"getDownloadData - prepared delivery status => ${deliveryStatus.status} is not done!")
            mapDownloadData.deliveryStatus = MapDeliveryState.ERROR;
//                TODO get the error message
            return mapDownloadData
        }
        if (deliveryStatus.url == null){
            Log.e(_tag, "getDownloadData - download url is null", )
            mapDownloadData.deliveryStatus = MapDeliveryState.ERROR;
            mapDownloadData.errorContent = "getDownloadData - download url is null"
            return mapDownloadData


        }

        mapDownloadData.url = deliveryStatus.url
        return mapDownloadData
    }

    @OptIn(ExperimentalTime::class)
    private fun checkImportStatus(requestId: String) : Boolean {
        var stat = getCreateMapImportStatus(requestId)
        val timeoutTime = TimeSource.Monotonic.markNow() + deliveryTimeoutMinutes.minutes
        while (stat?.state!! != MapImportState.DONE){
            TimeUnit.SECONDS.sleep(1)
            stat = getCreateMapImportStatus(requestId)

            when(stat?.state){
                MapImportState.ERROR -> {
                    Log.e(_tag,"checkImportStatus - MapImportState.ERROR")
                    return false
                }
                MapImportState.CANCEL -> {
                    Log.w(_tag,"checkImportStatus - MapImportState.CANCEL")
                    return false
                }
                else -> {}
            }

            if(timeoutTime.hasPassedNow()){
                Log.w(_tag,"checkImportStatus - timed out")
                return false
            }
        }
        return true
    }

    @OptIn(ExperimentalTime::class)
    private fun checkDeliveryStatus(requestId: String) : Boolean {
        var stat = getMapImportDeliveryStatus(requestId)
        val timeoutTime = TimeSource.Monotonic.markNow() + deliveryTimeoutMinutes.minutes
        while (stat?.state != MapDeliveryState.DONE){
            TimeUnit.SECONDS.sleep(1)
            stat = getMapImportDeliveryStatus(requestId)
            when(stat?.state){
                MapDeliveryState.ERROR -> {
                    Log.e(_tag,"checkDeliveryStatus - MapDeliveryState.ERROR")
                    return false
                }
                MapDeliveryState.CANCEL -> {
                    Log.w(_tag,"checkDeliveryStatus - MapDeliveryState.CANCEL")
                    return false
                }
                else -> {}
            }
            if(timeoutTime.hasPassedNow()){
                Log.w(_tag,"checkDeliveryStatus - timed out")
                return false
            }
        }

        return true
    }
}