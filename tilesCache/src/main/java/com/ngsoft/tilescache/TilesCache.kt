package com.ngsoft.tilescache

import android.content.Context
import com.ngsoft.tilescache.models.TilePkg
import java.time.LocalDateTime

class TilesCache(ctx: Context)  {

    private val dao: TilesDAO
    init {
        println("TilesCache init...")
        dao = TilesDatabase.connect(ctx).tilesDao()
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