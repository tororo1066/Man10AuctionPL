package tororo1066.man10auction.inventory

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import tororo1066.man10auction.Man10Auction
import tororo1066.man10auction.Man10Auction.Companion.sendPrefixMsg
import tororo1066.tororopluginapi.SJavaPlugin
import tororo1066.tororopluginapi.SStr
import tororo1066.tororopluginapi.defaultMenus.LargeSInventory
import tororo1066.tororopluginapi.defaultMenus.NumericInputInventory
import tororo1066.tororopluginapi.otherUtils.UsefulUtility
import tororo1066.tororopluginapi.otherUtils.UsefulUtility.Companion.toFormatString
import tororo1066.tororopluginapi.sInventory.SInventoryItem
import tororo1066.tororopluginapi.utils.DateType
import tororo1066.tororopluginapi.utils.toJPNDateStr
import java.util.function.Consumer

class NormalAucMenu: LargeSInventory(SJavaPlugin.plugin, "§b通常オークション") {

    var task: BukkitTask? = null

    init {
        setOnClose {
            task?.cancel()
        }
    }

    override fun renderMenu(p: Player): Boolean {
        val items = arrayListOf<SInventoryItem>()
        Man10Auction.normalAucData.values.forEach {
            if (it.isEnd || it.sellerUUID == p.uniqueId)return@forEach
            val item = it.item.clone()
                .addLore("","§e§l出品者：${it.sellerName}","§b§l値段：${it.nowPrice.toFormatString()}円","§a§l入札単位：${it.splitPrice.toFormatString()}円","§d§l残り時間：${it.getRemainingTime().toJPNDateStr(DateType.SECOND,DateType.YEAR)}")
                .toSInventoryItem().setCanClick(false).setClickEvent { e ->
                    val inputInv = NumericInputInventory(SJavaPlugin.plugin,"§a入札金額")
                    inputInv.onConfirm = Consumer { int ->
                        if (!Man10Auction.normalAucData.containsKey(it.uuid)){
                            p.sendPrefixMsg(SStr("&4出品が存在しません"))
                            return@Consumer
                        }
                        p.closeInventory()
                        Man10Auction.normalAucData[it.uuid]!!.bid(p,int.toDouble())
                    }
                    moveChildInventory(inputInv, p)
                }
            items.add(item)
        }
        if (task == null){
            task = Bukkit.getScheduler().runTaskTimer(SJavaPlugin.plugin, Runnable {
                allRenderMenu(p)
            },20,20)
        }
        setResourceItems(items)
        return true
    }
}