package com.ngsoft.getapp.sdk

import com.ngsoft.getapp.sdk.models.MapDeployState
import com.ngsoft.getapp.sdk.models.MapImportState
import com.ngsoft.getapp.sdk.models.MapProperties
import com.ngsoft.getapp.sdk.models.StatusCode
import org.junit.Test

import org.junit.Assert.*
import org.junit.Before


//keeps as guidelines and see what fits 4 my needs

class GetMapServiceImplTest : TestBase() {

    @Test
    fun `test getCreateMapImportStatus with valid input`() {
        val reqId = "1148946338667298816"
        val result = api.getCreateMapImportStatus(reqId)
        assertNotNull(result)
        assertEquals(reqId, result?.importRequestId)
        assertEquals(StatusCode.SUCCESS, result?.statusCode?.statusCode)
        assertEquals(MapImportState.DONE, result?.state)
    }

    @Test
    fun `test getCreateMapImportStatus with null input`() {
        assertThrows(Exception::class.java) {
            api.getCreateMapImportStatus(null)
        }
    }

    @Test
    fun `test createMapImport with valid input`() {

        val inputProperties = MapProperties(
            "getmap:Ashdod2",
            "34.66529846191406,31.86120986938477,34.66958999633789,31.86344146728516",
            false
        )

        val result = api.createMapImport(inputProperties)
        assertNotNull(result)
        assertEquals(StatusCode.SUCCESS, result?.statusCode?.statusCode)
        assertEquals(MapImportState.START, result?.state)
    }

    @Test
    fun `test createMapImport with null input`() {
        assertThrows(Exception::class.java) {
            api.createMapImport(null)
        }
    }

    @Test
    fun `test setMapImportDeploy with valid input`() {
        val result = api.setMapImportDeploy("testId", MapDeployState.DONE)
        assertNotNull(result)
        assertEquals(MapDeployState.DONE, result)
    }

    @Test
    fun `test setMapImportDeploy with null inputRequestId`() {
        assertThrows(Exception::class.java) {
            api.setMapImportDeploy(null, MapDeployState.DONE)
        }
    }

    // TODO Add tests for other edge cases and scenarios

}