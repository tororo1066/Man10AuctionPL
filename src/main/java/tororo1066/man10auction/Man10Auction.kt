package tororo1066.man10auction

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import red.man10.man10bank.BankAPI
import red.man10.man10bank.Man10Bank
import tororo1066.man10auction.data.NormalAucData
import tororo1066.tororopluginapi.SJavaPlugin
import tororo1066.tororopluginapi.SStr
import tororo1066.tororopluginapi.utils.sendMessage
import java.util.Date
import java.util.UUID

class Man10Auction: SJavaPlugin(UseOption.MySQL,UseOption.Vault,UseOption.SConfig) {

    companion object{
        val normalAucData = HashMap<UUID,NormalAucData>()
        val bannedPlayer = ArrayList<UUID>()//オークションをバンされたプレイヤー
        var pluginEnabled = true

        val prefix = SStr("&f[&dMan10&aAuction&f]&r")

        lateinit var bank: BankAPI

        fun CommandSender.sendPrefixMsg(sStr: SStr){
            this.sendMessage(prefix.toPaperComponent().append(sStr.toPaperComponent()))
        }

        var MIN_SELL_MONEY = 0L
        var MAX_DAYS = 0L
        var MAX_SELL = 0

        fun reloadConfigs(){
            MIN_SELL_MONEY = plugin.config.getLong("minSellMoney",1)
            MAX_DAYS = plugin.config.getLong("maxDays",7)
            MAX_SELL = plugin.config.getInt("maxSell",3)
            pluginEnabled = plugin.config.getBoolean("pluginEnabled",true)
        }

        fun addItem(p: Player, item: ItemStack){
            if (p.inventory.firstEmpty() == -1){
                p.world.dropItem(p.location,item){
                    it.owner = p.uniqueId
                    it.setCanMobPickup(false)
                }
            } else {
                p.inventory.addItem(item)
            }
        }
    }

    override fun onStart() {
        bank = BankAPI(this)

        reloadConfigs()

        val rs = mysql.sQuery("select * from normal_auction_data where isReceived = 'false'")
        rs.forEach { result ->
            val data = NormalAucData.load(result)?:return@forEach
            normalAucData[data.uuid] = data
        }

        Bukkit.getScheduler().runTaskTimer(this, Runnable {
            val date = Date()
            normalAucData.values.forEach {
                if (it.isEnd)return@forEach
                if (it.endSuggest <= date.time){
                    it.isEnd = true

                    Bukkit.getScheduler().runTaskAsynchronously(this, Runnable second@ {
                        while (it.isBidding.get()){
                            Thread.sleep(50)
                        }

                        if (it.lastBidUUID == null){
                            mysql.execute("update normal_auction_data set isEnd = 'true' where auc_uuid = '${it.uuid}'")
                            return@second
                        }

                        bank.deposit(it.sellerUUID,it.nowPrice,"Man10Auction sold item(${it.item.getDisplayName()})","オークションでアイテム(${it.item.getDisplayName()})が売れた")

                        mysql.execute("update normal_auction_data set isEnd = 'true' where auc_uuid = '${it.uuid}'")

                    })
                }
            }
        },20,20)
    }
}