package tororo1066.man10auction.data

import org.bukkit.entity.Player
import tororo1066.man10auction.Man10Auction
import tororo1066.man10auction.Man10Auction.Companion.sendPrefixMsg
import tororo1066.tororopluginapi.SJavaPlugin
import tororo1066.tororopluginapi.SStr
import tororo1066.tororopluginapi.SStr.Companion.toSStr
import tororo1066.tororopluginapi.mysql.SMySQLResultSet
import tororo1066.tororopluginapi.otherUtils.UsefulUtility.Companion.toFormatString
import tororo1066.tororopluginapi.sItem.SItem
import tororo1066.tororopluginapi.utils.toDate
import java.sql.Connection
import java.sql.Statement
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicBoolean

class NormalAucData: Cloneable {

    lateinit var uuid: UUID
    lateinit var sellerUUID: UUID
    var sellerName = ""
    lateinit var startDate: Date
    var endSuggest = 0L
    var activateDay = 0
    lateinit var item: SItem
    var nowPrice = 0.0
    var defaultPrice = 0.0
    var isBidding = AtomicBoolean(false)
    var isEnd = false

    var lastBidUUID: UUID? = null
    var lastBidName = ""

    var splitPrice = 1.0

    var delayMinute = 0

    fun bid(p: Player, price: Double): Boolean {
        fun bidEnd(bool: Boolean): Boolean {
            isBidding.set(false)
            return bool
        }
        if (isEnd){
            p.sendPrefixMsg(SStr("&4既にこのオークションは終了しています"))
            return false
        }
        if (isBidding.get()){
            p.sendPrefixMsg(SStr("&4現在入札中です 少し待ってから再度試してください"))
            return false
        }
        isBidding.set(true)
        try {
            return Man10Auction.es.submit(Callable {
                val connection: Connection
                val statement: Statement
                try {
                    connection = SJavaPlugin.mysql.open()
                } catch (e: Exception) {
                    e.printStackTrace()
                    return@Callable false
                }
                try {
                    connection.autoCommit = false
                    statement = connection.createStatement()
                    val result = statement.executeQuery("select * from normal_auction_data where auc_uuid = '${uuid}' for update")
                    if (result.next()){

                        lastBidUUID = result.getString("last_bid_uuid")?.let { UUID.fromString(it) }
                        lastBidName = result.getString("last_bid_name")?:""
                        nowPrice = result.getDouble("now_price")
                        delayMinute = result.getInt("delay_minute")
                        result.close()
                        statement.close()

                        if (!Man10Auction.pluginEnabled){
                            if (!p.hasPermission("mauction.op")){
                                p.sendPrefixMsg(SStr("&4現在使用できません"))
                                return@Callable bidEnd(false)
                            }
                        }
                        if (sellerUUID == p.uniqueId){
                            p.sendPrefixMsg(SStr("&4自分の出品に入札することはできません"))
                            return@Callable bidEnd(false)
                        }
                        if (Man10Auction.bannedPlayer.contains(p.uniqueId)){
                            p.sendPrefixMsg(SStr("&4あなたはオークションbanをされています"))
                            return@Callable bidEnd(false)
                        }
                        if (nowPrice >= price){
                            p.sendPrefixMsg(SStr("&4${nowPrice.toFormatString()}円より高い値段で入札してください"))
                            return@Callable bidEnd(false)
                        }
                        if (price % splitPrice != 0.0){
                            p.sendPrefixMsg(SStr("&4${splitPrice.toFormatString()}円刻みでしか入札できません"))
                            return@Callable bidEnd(false)
                        }

                        if (!SJavaPlugin.vault.withdraw(p.uniqueId, price)){
                            p.sendPrefixMsg(SStr("&4金額が足りません"))
                            return@Callable bidEnd(false)
                        }

                        val delayUpdate = getRemainingTime() <= 300
                        if (delayUpdate){
                            delayMinute += 5
                        }
                        val sql = "update normal_auction_data set now_price = $price, last_bid_uuid = '${p.uniqueId}'" +
                                ", last_bid_name = '${p.name}'${if (delayUpdate) ", delay_minute = ${delayMinute + 5}" else ""}" +
                                " where auc_uuid = '${uuid}'"
                        connection.createStatement().use {
                            it.execute(sql)
                        }

                        connection.commit()
                        p.sendPrefixMsg(SStr("&a入札に成功しました！"))
                        Man10Auction.prefix.append(item.displayName().hoverEvent(item).toSStr()).append(
                            SStr("&aに${price.toFormatString()}円の入札！")).broadcast()
                        if (lastBidUUID != null){
                            Man10Auction.es.execute {
                                Man10Auction.bank.deposit(lastBidUUID!!, nowPrice, "Man10Auction other player bid item","オークションで他のプレイヤーがアイテムに入札した")
                            }
                        }
                        lastBidUUID = p.uniqueId
                        lastBidName = p.name
                        nowPrice = price
                        SJavaPlugin.mysql.callbackExecute("insert into action_log (auc_uuid,action,uuid,name,price,date) values ('${uuid}','BID','${p.uniqueId}','${p.name}',${price},now())") {}
                        bidEnd(true)
                    } else {
                        result.close()
                        statement.close()
                        connection.close()
                        p.sendPrefixMsg(SStr("&4入札に失敗しました 1"))
                        return@Callable bidEnd(false)
                    }
                } catch (e: Exception){
                    connection.rollback()
                    e.printStackTrace()
                    p.sendPrefixMsg(SStr("&4入札に失敗しました 2"))
                    return@Callable bidEnd(false)
                } finally {
                    connection.close()
                }
            }).get()
        } catch (e: Exception){
            e.printStackTrace()
            p.sendPrefixMsg(SStr("&4入札に失敗しました 3"))
            return false
        }

    }

    //秒で取得
    fun getRemainingTime(): Long {
        return (endSuggest + delayMinute * 60000 - Date().time) / 1000
    }

    override fun clone(): NormalAucData {
        val clone = super.clone() as NormalAucData
        clone.item = item.clone()
        clone.isBidding = AtomicBoolean(false)
        return clone
    }

    companion object{
        fun load(result: SMySQLResultSet): NormalAucData? {
            if (result.getBoolean("isReceived"))return null
            val data = NormalAucData()
            data.uuid = UUID.fromString(result.getString("auc_uuid"))
            data.sellerUUID = UUID.fromString(result.getString("seller_uuid"))
            data.sellerName = result.getString("seller_name")
            data.item = SItem.fromBase64(result.getString("item"))!!
            data.startDate = result.getDate("start_date").toDate()
            data.activateDay = result.getInt("activate_day")
            data.nowPrice = result.getDouble("now_price")
            data.defaultPrice = result.getDouble("default_price")
            data.endSuggest = (data.startDate.time + data.activateDay * 86400000L)
            data.lastBidUUID = result.getNullableString("last_bid_uuid")?.let { UUID.fromString(it) }
            data.lastBidName = result.getNullableString("last_bid_name")?:""
            data.isEnd = result.getBoolean("isEnd")
            data.splitPrice = result.getDouble("split_money")
            data.delayMinute = result.getNullableInt("delay_minute")?:0

            return data
        }
    }
}