package tororo1066.man10auction.inventory

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.scheduler.BukkitTask
import red.man10.man10bank.Bank
import tororo1066.man10auction.Man10Auction
import tororo1066.man10auction.Man10Auction.Companion.sendPrefixMsg
import tororo1066.tororopluginapi.SJavaPlugin
import tororo1066.tororopluginapi.SStr
import tororo1066.tororopluginapi.defaultMenus.LargeSInventory
import tororo1066.tororopluginapi.defaultMenus.NumericInputInventory
import tororo1066.tororopluginapi.otherUtils.UsefulUtility.Companion.toFormatString
import tororo1066.tororopluginapi.sInventory.SInventoryItem
import tororo1066.tororopluginapi.utils.DateType
import tororo1066.tororopluginapi.utils.toJPNDateStr
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

class CancelSellMenu: LargeSInventory(SJavaPlugin.plugin, "§b自分が出品したアイテムを見る") {


    var task: BukkitTask? = null

    init {
        setOnClick {
            it.isCancelled = true
        }
        setOnClose {
            task?.cancel()
        }
    }

    var isTaskNow = AtomicBoolean(false)

    override fun renderMenu(p: Player): Boolean {
        val items = arrayListOf<SInventoryItem>()
        Man10Auction.normalAucData.values.forEach {
            if (it.sellerUUID != p.uniqueId)return@forEach
            if (it.lastBidUUID == null && it.isEnd){
                val item = it.item.clone()
                    .addLore("","§e§l出品時の値段：${it.defaultPrice.toFormatString()}円","§a§l入札単位：${it.splitPrice.toFormatString()}円","§d§l残り時間：0秒","§cシフト左クリックで受け取り")
                    .toSInventoryItem().setCanClick(false).setClickEvent { e ->
                        if (e.click != ClickType.SHIFT_LEFT)return@setClickEvent
                        if (!Man10Auction.normalAucData.containsKey(it.uuid))return@setClickEvent
                        if (p.inventory.firstEmpty() == -1){
                            p.closeInventory()
                            p.sendPrefixMsg(SStr("&c&lインベントリに空きを作ってください"))
                            return@setClickEvent
                        }
                        if (isTaskNow.get())return@setClickEvent

                        isTaskNow.set(true)
                        if (SJavaPlugin.mysql.asyncExecute("update normal_auction_data set isReceived = 'true' where auc_uuid = '${it.uuid}'")){
                            Man10Auction.normalAucData.remove(it.uuid)
                            SJavaPlugin.mysql.callbackExecute("insert into action_log (auc_uuid,action,uuid,name,price,date) values ('${it.uuid}','RECEIVED_FAILED_SELL','${p.uniqueId}','${p.name}',${it.nowPrice},now())") {}
                            allRenderMenu(p)
                            p.inventory.addItem(it.item)
                            p.sendPrefixMsg(SStr("&aアイテムを受け取りました"))
                        } else {
                            p.sendPrefixMsg(SStr("&c&lアイテムの受け取りに失敗しました"))
                        }
                        isTaskNow.set(false)
                    }
                items.add(item)
            } else if (!it.isEnd) {
                val item = it.item.clone()
                    .addLore("","§e§l出品時の値段：${it.defaultPrice.toFormatString()}円","§b§l現在の値段：${it.nowPrice.toFormatString()}円","§a§l入札単位：${it.splitPrice.toFormatString()}円","§d§l残り時間：${it.getRemainingTime().toJPNDateStr(
                        DateType.SECOND,
                        DateType.YEAR,true)}","§cシフト左クリックで取り消し")
                    .toSInventoryItem().setCanClick(false).setClickEvent { e ->
                        if (e.click != ClickType.SHIFT_LEFT)return@setClickEvent
                        if (!Man10Auction.normalAucData.containsKey(it.uuid))return@setClickEvent
                        if (Man10Auction.normalAucData[it.uuid]!!.isEnd)return@setClickEvent
                        if (p.inventory.firstEmpty() == -1){
                            p.closeInventory()
                            p.sendPrefixMsg(SStr("&c&lインベントリに空きを作ってください"))
                            return@setClickEvent
                        }
                        if (isTaskNow.get())return@setClickEvent

                        isTaskNow.set(true)
                        if (SJavaPlugin.mysql.asyncExecute("update normal_auction_data set isEnd = 'true', isReceived = 'true', end_date = now() where auc_uuid = '${it.uuid}'")){
                            Man10Auction.normalAucData.remove(it.uuid)
                            SJavaPlugin.mysql.callbackExecute("insert into action_log (auc_uuid,action,uuid,name,price,date) values ('${it.uuid}','RECEIVED_CANCEL_SELL','${p.uniqueId}','${p.name}',${it.nowPrice},now())") {}
                            allRenderMenu(p)
                            p.inventory.addItem(it.item)
                            if (it.lastBidUUID != null){
                                Man10Auction.bank.asyncDeposit(it.lastBidUUID!!, it.nowPrice, "Man10Auction cancel sell deposit(user: ${it.sellerUUID},${it.sellerName})","オークションで${it.item.itemMeta.displayName}がキャンセルされた"
                                ) { _, _, _ -> }
                            }
                            p.sendPrefixMsg(SStr("&aアイテムを受け取りました"))
                        } else {
                            p.sendPrefixMsg(SStr("&c&lアイテムの受け取りに失敗しました"))
                        }
                        isTaskNow.set(false)
                    }
                items.add(item)
            }

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