package tororo1066.man10auction

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import red.man10.man10bank.BankAPI
import red.man10.man10bank.Man10Bank
import tororo1066.man10auction.data.NormalAucData
import tororo1066.tororopluginapi.SInput
import tororo1066.tororopluginapi.SJavaPlugin
import tororo1066.tororopluginapi.SStr
import tororo1066.tororopluginapi.utils.sendMessage
import tororo1066.tororopluginapi.utils.toPlayer
import java.util.Date
import java.util.UUID

class Man10Auction: SJavaPlugin(UseOption.MySQL,UseOption.Vault,UseOption.SConfig) {

    companion object{
        val normalAucData = HashMap<UUID,NormalAucData>()
        val bannedPlayer = ArrayList<UUID>()//オークションをバンされたプレイヤー
        var pluginEnabled = true

        val prefix = SStr("&f[&dMan10&aAuction&f]&r")

        lateinit var bank: BankAPI
        lateinit var sInput: SInput

        fun CommandSender.sendPrefixMsg(sStr: SStr){
            this.sendMessage(prefix.toPaperComponent().append(sStr.toPaperComponent()))
        }

        var MIN_SELL_MONEY = 0L
        var MAX_DAYS = 0L
        var MAX_SELL = 0

        fun reloadConfigs(){
            plugin.reloadConfig()
            MIN_SELL_MONEY = plugin.config.getLong("minSellMoney",1)
            MAX_DAYS = plugin.config.getLong("maxDays",7)
            MAX_SELL = plugin.config.getInt("maxSell",3)
            pluginEnabled = plugin.config.getBoolean("pluginEnabled",true)

            plugin.config.getStringList("bannedPlayers").forEach {
                bannedPlayer.add(UUID.fromString(it))
            }
        }
    }

    override fun onStart() {

        createTables()
        sInput = SInput(this)
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
                            mysql.execute("update normal_auction_data set isEnd = 'true', end_date = now() where auc_uuid = '${it.uuid}'")
                            mysql.callbackExecute("insert into action_log (auc_uuid,action,uuid,name,price,date) values " +
                                    "('${it.uuid}','FAILED_SELL','${it.sellerUUID}','${it.sellerName}',${it.nowPrice},now())") {}
                            it.sellerUUID.toPlayer()?.sendMessage(prefix.toPaperComponent().append(it.item.displayName()).hoverEvent(it.item).append(
                                Component.text("§7は入札されませんでした")))
                            return@second
                        }

                        bank.deposit(it.sellerUUID,it.nowPrice,"Man10Auction sold item(${it.item.getDisplayName()})","オークションでアイテム(${it.item.getDisplayName()})が売れた")

                        mysql.execute("update normal_auction_data set isEnd = 'true', end_date = now() where auc_uuid = '${it.uuid}'")
                        mysql.callbackExecute("insert into action_log (auc_uuid,action,uuid,name,price,date) values ('${it.uuid}','SUCCEED_SELL','${it.sellerUUID}','${it.sellerName}',${it.nowPrice},now())") {}

                        it.sellerUUID.toPlayer()?.sendMessage(prefix.toPaperComponent().append(it.item.displayName()).hoverEvent(it.item).append(
                            Component.text("§aが§e${it.nowPrice}円§aで競り落とされました！")))
                        it.lastBidUUID?.toPlayer()?.sendMessage(prefix.toPaperComponent().append(it.item.displayName()).hoverEvent(it.item).append(
                            Component.text("§aを§e${it.nowPrice}円§aで落札しました！")))

                    })
                }
            }
        },20,20)
    }

    fun createTables(){
        mysql.execute("CREATE TABLE IF NOT EXISTS `normal_auction_data` (\n" +
                "\t`id` INT(10) NOT NULL AUTO_INCREMENT,\n" +
                "\t`auc_uuid` VARCHAR(36) NULL DEFAULT NULL COLLATE 'utf8mb4_0900_ai_ci',\n" +
                "\t`seller_uuid` VARCHAR(36) NOT NULL COLLATE 'utf8mb4_0900_ai_ci',\n" +
                "\t`seller_name` VARCHAR(16) NOT NULL COLLATE 'utf8mb4_0900_ai_ci',\n" +
                "\t`item` LONGTEXT NOT NULL COLLATE 'utf8mb4_0900_ai_ci',\n" +
                "\t`start_date` DATETIME NULL DEFAULT NULL,\n" +
                "\t`activate_day` INT(10) NOT NULL DEFAULT '0',\n" +
                "\t`end_date` DATETIME NULL DEFAULT NULL,\n" +
                "\t`now_price` DOUBLE NULL DEFAULT NULL,\n" +
                "\t`default_price` DOUBLE NULL DEFAULT NULL,\n" +
                "\t`isEnd` VARCHAR(5) NULL DEFAULT 'false' COLLATE 'utf8mb4_0900_ai_ci',\n" +
                "\t`isReceived` VARCHAR(5) NULL DEFAULT 'false' COLLATE 'utf8mb4_0900_ai_ci',\n" +
                "\t`last_bid_uuid` VARCHAR(36) NULL DEFAULT NULL COLLATE 'utf8mb4_0900_ai_ci',\n" +
                "\t`last_bid_name` VARCHAR(16) NULL DEFAULT NULL COLLATE 'utf8mb4_0900_ai_ci',\n" +
                "\t`split_money` DOUBLE NULL DEFAULT NULL,\n" +
                "\t`delay_minute` INT NULL DEFAULT '0',\n" +
                "\tPRIMARY KEY (`id`) USING BTREE,\n" +
                "\tUNIQUE INDEX `auc_uuid` (`auc_uuid`) USING BTREE,\n" +
                "\tINDEX `seller_uuid` (`seller_uuid`) USING BTREE,\n" +
                "\tINDEX `seller_name` (`seller_name`) USING BTREE\n" +
                ")\n" +
                "COLLATE='utf8mb4_0900_ai_ci'\n" +
                "ENGINE=InnoDB\n" +
                ";")
        mysql.execute("CREATE TABLE IF NOT EXISTS `action_log` (\n" +
                "\t`id` INT(10) NOT NULL AUTO_INCREMENT,\n" +
                "\t`auc_uuid` VARCHAR(36) NULL DEFAULT NULL COLLATE 'utf8mb4_0900_ai_ci',\n" +
                "\t`action` VARCHAR(20) NULL DEFAULT NULL COLLATE 'utf8mb4_0900_ai_ci',\n" +
                "\t`uuid` VARCHAR(36) NULL DEFAULT NULL COLLATE 'utf8mb4_0900_ai_ci',\n" +
                "\t`name` VARCHAR(16) NULL DEFAULT NULL COLLATE 'utf8mb4_0900_ai_ci',\n" +
                "\t`price` DOUBLE NULL DEFAULT NULL,\n" +
                "\t`date` DATETIME NULL DEFAULT NULL,\n" +
                "\tPRIMARY KEY (`id`) USING BTREE,\n" +
                "\tINDEX `auc_uuid` (`auc_uuid`) USING BTREE\n" +
                ")\n" +
                "COLLATE='utf8mb4_0900_ai_ci'\n" +
                "ENGINE=InnoDB\n" +
                ";")
    }

}