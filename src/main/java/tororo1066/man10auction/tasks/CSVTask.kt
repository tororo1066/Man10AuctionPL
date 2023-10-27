package tororo1066.man10auction.tasks

import tororo1066.man10auction.Man10Auction
import tororo1066.tororopluginapi.SJavaPlugin
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*

fun csvTask(){
    val folder = File(SJavaPlugin.plugin.dataFolder.path + "/csv/")
    if (!folder.exists()){
        folder.mkdir()
    }
    val file = File(SJavaPlugin.plugin.dataFolder.path + "/csv/auction.csv")
    if (file.exists()){
        file.delete()
    }
    file.createNewFile()
    val writer = PrintWriter(file)
    writer.println("アイテム名,出品者,値段,入札単位,終了時刻")
    Man10Auction.normalAucData.values
        .filter { !it.isEnd }
        .sortedBy { it.getRemainingTime() }
        .take(30).forEach { data ->
            val displayName = if (data.item.getDisplayName() == "") data.item.i18NDisplayName else data.item.getDisplayName()
            val dateFormat = SimpleDateFormat("yyyy/MM/dd kk:mm:ss")
            writer.println("${displayName},${data.sellerName},${data.nowPrice}" +
                    ",${data.splitPrice}" +
                    ",${dateFormat.format(Date((data.endSuggest + data.delayMinute * 60000)))}")
        }
    writer.close()
}