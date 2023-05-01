package tororo1066.man10auction.inventory

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent.ShowItem
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Server
import org.bukkit.entity.Player
import tororo1066.man10auction.Man10Auction
import tororo1066.man10auction.Man10Auction.Companion.sendPrefixMsg
import tororo1066.man10auction.data.NormalAucData
import tororo1066.tororopluginapi.SJavaPlugin
import tororo1066.tororopluginapi.SStr
import tororo1066.tororopluginapi.defaultMenus.NumericInputInventory
import tororo1066.tororopluginapi.otherUtils.UsefulUtility.Companion.toFormatString
import tororo1066.tororopluginapi.sInventory.SInventory
import tororo1066.tororopluginapi.sInventory.SInventoryItem
import tororo1066.tororopluginapi.sItem.SItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID
import java.util.function.Consumer
import kotlin.math.log

class NormalAucSellMenu: SInventory(SJavaPlugin.plugin, "§1アイテムを出品する",3) {

    var defaultSellMoney = 1L
    var splitMoney = 1L
    var days = 1L
    var noDrop = false

    init {
        savePlaceItems(true)

        setOnClose {
            if (!noDrop){
                val p = it.player
                val item = getItem(13)?:return@setOnClose
                if (p.inventory.firstEmpty() == -1){
                    p.world.dropItem(p.location,item) { dropItem ->
                        dropItem.owner = p.uniqueId
                        dropItem.setCanMobPickup(false)
                    }
                } else {
                    p.inventory.addItem(item)
                }
            }
        }
    }

    override fun renderMenu(p: Player): Boolean {

        fillItem(SInventoryItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE).setDisplayName(" ").setCanClick(false))

        removeItem(13)

        setItem(2, SInventoryItem(Material.LIGHT_WEIGHTED_PRESSURE_PLATE).setDisplayName("§e§l最低額を設定する").addLore("§d現在の値：${defaultSellMoney}円").setCanClick(false).setClickEvent {
            val inputInv = NumericInputInventory(SJavaPlugin.plugin, "§e§l最低額を設定する")
            inputInv.onConfirm = Consumer { long ->
                if (long < Man10Auction.MIN_SELL_MONEY){
                    p.sendPrefixMsg(SStr("&c&l最低額は${Man10Auction.MIN_SELL_MONEY.toDouble().toFormatString()}円からです"))
                    return@Consumer
                }

                defaultSellMoney = long
                p.closeInventory()
            }

            inputInv.onCancel = Consumer { p.closeInventory() }

            moveChildInventory(inputInv, p)
        })

        setItem(6, SInventoryItem(Material.GOLD_INGOT).setDisplayName("§6§l何円単位で入札するか設定する").addLore("§d現在の値：${splitMoney}円").setCanClick(false).setClickEvent {
            val inputInv = NumericInputInventory(SJavaPlugin.plugin, "§6§l何円単位で入札するか設定する")
            inputInv.onConfirm = Consumer { long ->
                if (long < 1){
                    p.sendPrefixMsg(SStr("&c&l1円から設定できます"))
                    return@Consumer
                }

                splitMoney = long
                p.closeInventory()
            }

            inputInv.onCancel = Consumer { p.closeInventory() }

            moveChildInventory(inputInv, p)
        })

        setItem(19, SInventoryItem(Material.RED_TERRACOTTA).setDisplayName("§c§l期間を設定する").addLore("§d現在の値：${days}日").setCanClick(false).setClickEvent {
            val inputInv = NumericInputInventory(SJavaPlugin.plugin, "§c§l期間を設定する")
            inputInv.onConfirm = Consumer { long ->
                if (long > Man10Auction.MAX_DAYS){
                    p.sendPrefixMsg(SStr("&c&l${Man10Auction.MAX_DAYS}日まで設定できます"))
                    return@Consumer
                }

                days = long
                p.closeInventory()
            }

            inputInv.onCancel = Consumer { p.closeInventory() }

            moveChildInventory(inputInv, p)
        })

        setItem(22, SInventoryItem(Material.LIME_STAINED_GLASS_PANE).setDisplayName("§a§l出品").setCanClick(false).setClickEvent {

            val item = getItem(13)?:return@setClickEvent
            if (Man10Auction.MAX_SELL <= Man10Auction.normalAucData.filter { map -> map.value.sellerUUID == p.uniqueId && !map.value.isEnd }.size){
                p.closeInventory()
                p.sendPrefixMsg(SStr("&c&lアイテムの出品は${Man10Auction.MAX_SELL}個までです"))

                return@setClickEvent
            }
            noDrop = true
            val date = Date()
            val uuid = UUID.randomUUID()
            p.closeInventory()
            if (SJavaPlugin.mysql.asyncExecute("insert into normal_auction_data (auc_uuid,seller_uuid,seller_name,item,start_date,activate_day,now_price,default_price,split_money,delay_minute) values('${uuid}','${p.uniqueId}','${p.name}','${SItem(item).toBase64()}','${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date)}',${days},${defaultSellMoney},${defaultSellMoney},${splitMoney},0)")){
                val data = NormalAucData()
                data.uuid = uuid
                data.sellerUUID = p.uniqueId
                data.sellerName = p.name
                data.item = SItem(item)
                data.startDate = date
                data.activateDay = days.toInt()
                data.nowPrice = defaultSellMoney.toDouble()
                data.defaultPrice = defaultSellMoney.toDouble()
                data.endSuggest = (data.startDate.time + data.activateDay * 86400000L)
                data.splitPrice = splitMoney.toDouble()

                Man10Auction.normalAucData[data.uuid] = data
                p.sendPrefixMsg(SStr("&a出品に成功しました！"))
                Bukkit.broadcast(Man10Auction.prefix.toPaperComponent().append(Component.text("§e${p.name}§dが").append(item.displayName().hoverEvent(item)).append(
                    Component.text("§dを出品しました！"))),Server.BROADCAST_CHANNEL_USERS)
                SJavaPlugin.mysql.callbackExecute("insert into action_log (auc_uuid,action,uuid,name,price,date) values ('${data.uuid}','SELL_ITEM','${p.uniqueId}','${p.name}',${data.nowPrice},now())") {}
            } else {
                p.world.dropItem(p.location,item) { ite ->
                    ite.setCanMobPickup(false)
                    ite.owner = p.uniqueId
                }
                p.sendPrefixMsg(SStr("&c&l出品に失敗しました"))
            }
        })



        return true
    }
}