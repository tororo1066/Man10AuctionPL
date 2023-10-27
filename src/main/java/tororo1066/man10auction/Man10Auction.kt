package tororo1066.man10auction

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import red.man10.man10bank.BankAPI
import tororo1066.man10auction.command.AuctionCommand
import tororo1066.man10auction.data.NormalAucData
import tororo1066.man10auction.tasks.csvTask
import tororo1066.tororopluginapi.SInput
import tororo1066.tororopluginapi.SJavaPlugin
import tororo1066.tororopluginapi.SStr
import tororo1066.tororopluginapi.utils.toPlayer
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class Man10Auction: SJavaPlugin(UseOption.MySQL,UseOption.Vault,UseOption.SConfig) {

    companion object{
        val normalAucData = HashMap<UUID,NormalAucData>()
        val bannedPlayer = ArrayList<UUID>()//オークションをバンされたプレイヤー
        var pluginEnabled = true

        val prefix = SStr("&f[&dMan10&aAuction&f]&r")

        lateinit var bank: BankAPI
        lateinit var sInput: SInput
        lateinit var es: ExecutorService
        lateinit var notificationManager: NotificationManager

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
        es = Executors.newCachedThreadPool()
        notificationManager = NotificationManager()

        reloadConfigs()

        val rs = mysql.sQuery("select * from normal_auction_data where isReceived = 'false'")
        rs.forEach { result ->
            val data = NormalAucData.load(result)?:return@forEach
            normalAucData[data.uuid] = data
        }

        AuctionCommand()

        Bukkit.getScheduler().runTaskTimer(this, Runnable {
            val date = Date()
            normalAucData.values.forEach {
                if (it.isEnd)return@forEach
                if (it.endSuggest + it.delayMinute * 60000 <= date.time){
                    it.isEnd = true

                    Bukkit.getScheduler().runTaskAsynchronously(this, Runnable second@ {
                        if (it.isBidding.get()){
                            while (it.isBidding.get()){
                                Thread.sleep(50)
                            }
                            it.isEnd = false
                            return@second
                        }

                        try {
                            mysql.open().use { connection ->
                                connection.autoCommit = false
                                if (it.lastBidUUID == null){
                                    connection.createStatement().use { stmt ->
                                        stmt.execute("update normal_auction_data set isEnd = 'true', end_date = now() where auc_uuid = '${it.uuid}'")
                                    }
                                    mysql.callbackExecute("insert into action_log (auc_uuid,action,uuid,name,price,date) values " +
                                            "('${it.uuid}','FAILED_SELL','${it.sellerUUID}','${it.sellerName}',${it.nowPrice},now())") {}
                                    it.sellerUUID.toPlayer()?.sendMessage(prefix.toPaperComponent().append(it.item.displayName()).hoverEvent(it.item).append(
                                        Component.text("§7は入札されませんでした")))
                                    return@second
                                }

                                connection.createStatement().use { stmt ->
                                    stmt.execute("update normal_auction_data set isEnd = 'true', end_date = now() where auc_uuid = '${it.uuid}'")
                                }
                                mysql.callbackExecute("insert into action_log (auc_uuid,action,uuid,name,price,date) values ('${it.uuid}','SUCCEED_SELL','${it.sellerUUID}','${it.sellerName}',${it.nowPrice},now())") {}

                                connection.commit()
                                it.sellerUUID.toPlayer()?.sendMessage(prefix.toPaperComponent().append(it.item.displayName()).hoverEvent(it.item).append(
                                    Component.text("§aが§e${it.nowPrice}円§aで競り落とされました！")))
                                it.lastBidUUID?.toPlayer()?.sendMessage(prefix.toPaperComponent().append(it.item.displayName()).hoverEvent(it.item).append(
                                    Component.text("§aを§e${it.nowPrice}円§aで落札しました！")))
                                es.execute {
                                    bank.deposit(it.sellerUUID,it.nowPrice,"Man10Auction sold item","オークションでアイテムが売れた")
                                }
                            }

                        } catch (e: Exception){
                            e.printStackTrace()
                        }

                        csvTask()
                    })
                }
            }
        },20,20)

    }

    override fun onDisable() {
        notificationManager.stop()
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
                "\t`item_info` LONGTEXT NULL DEFAULT NULL COLLATE 'utf8mb4_0900_ai_ci',\n" +
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