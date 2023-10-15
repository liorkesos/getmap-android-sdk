package com.ngsoft.tilescache

import android.content.Context
import android.util.Log
import androidx.sqlite.db.SimpleSQLiteQuery
import com.ngsoft.tilescache.models.TilePkg
import java.time.LocalDateTime

class TilesCache(ctx: Context)  {
    private val TAG = "TilesCache"
    private val db: TilesDatabase
    private val dao: TilesDAO
    init {
        Log.d(TAG,"TilesCache init...")
        db = TilesDatabase.connect(ctx)
        dao = db.tilesDao()
    }

    fun nukeTable(){
        dao.nukeTable()
        //reset auto-increments
        db.runInTransaction { db.query(SimpleSQLiteQuery("DELETE FROM sqlite_sequence")) }
    }

    fun registerTilePkg(tilePkg: TilePkg) {
        dao.insert(tilePkg)
    }

    fun isTileInCache(prodName: String, x: Int, y: Int, zoom: Int, updDate: LocalDateTime) : Boolean {
        val tilePackages = dao.getByTile(x, y, zoom)
        tilePackages.forEach{ tilePkg ->
            if (tilePkg.prodName == prodName && isTilePkgUpdateDateValid(tilePkg, updDate))
                return true
        }
        return false
    }

    private fun isTilePkgUpdateDateValid(tilePkg: TilePkg, updDate: LocalDateTime): Boolean {
        return tilePkg.dateUpdated >= updDate
        //return tilePkg.dateUpdated.toEpochSecond(ZoneOffset.UTC) >= updDate.toEpochSecond(ZoneOffset.UTC)
    }

}