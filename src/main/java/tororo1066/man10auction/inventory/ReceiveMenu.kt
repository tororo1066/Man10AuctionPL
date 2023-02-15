package tororo1066.man10auction.inventory

import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import tororo1066.man10auction.Man10Auction
import tororo1066.man10auction.Man10Auction.Companion.sendPrefixMsg
import tororo1066.tororopluginapi.SJavaPlugin
import tororo1066.tororopluginapi.SStr
import tororo1066.tororopluginapi.defaultMenus.LargeSInventory
import tororo1066.tororopluginapi.sInventory.SInventoryItem
import java.util.concurrent.atomic.AtomicBoolean

class ReceiveMenu: LargeSInventory(SJavaPlugin.plugin, "§b§l競り落としたアイテムを受け取る") {

    var isTaskNow = AtomicBoolean(false)

    init {
        setOnClick {
            it.isCancelled = true
        }
    }

    override fun renderMenu(p: Player): Boolean {
        val items = arrayListOf<SInventoryItem>()
        Man10Auction.normalAucData.values.forEach {
            if (!it.isEnd || it.lastBidUUID != p.uniqueId)return@forEach
            items.add(SInventoryItem(it.item).addLore("","§eシフト左クリックで受け取る").setCanClick(false).setClickEvent { e ->
                if (e.click != ClickType.SHIFT_LEFT)return@setClickEvent
                if (p.inventory.firstEmpty() == -1){
                    throughClose(p)
                    p.sendPrefixMsg(SStr("&4インベントリに空きを作ってください"))
                    return@setClickEvent
                }
                if (isTaskNow.get())return@setClickEvent
                isTaskNow.set(true)
                if (SJavaPlugin.mysql.asyncExecute("update normal_auction_data set isReceived = 'true' where auc_uuid = '${it.uuid}'")){
                    Man10Auction.normalAucData.remove(it.uuid)
                    allRenderMenu(p)
                    p.inventory.addItem(it.item)
                    p.sendPrefixMsg(SStr("&aアイテムを受け取りました！"))
                } else {
                    p.sendPrefixMsg(SStr("&4アイテムの受け取りに失敗しました"))
                }

                isTaskNow.set(false)

            })
        }

        setResourceItems(items)
        return true
    }
}