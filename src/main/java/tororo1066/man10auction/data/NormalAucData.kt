package tororo1066.man10auction.data

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder
import red.man10.man10bank.Bank
import tororo1066.man10auction.Man10Auction
import tororo1066.man10auction.Man10Auction.Companion.sendPrefixMsg
import tororo1066.tororopluginapi.SJavaPlugin
import tororo1066.tororopluginapi.SStr
import tororo1066.tororopluginapi.mysql.SMySQLResultSet
import tororo1066.tororopluginapi.otherUtils.UsefulUtility.Companion.toFormatString
import tororo1066.tororopluginapi.sItem.SItem
import tororo1066.tororopluginapi.utils.toDate
import java.io.ByteArrayInputStream
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class NormalAucData {

    lateinit var uuid: UUID
    lateinit var sellerUUID: UUID
    var sellerName = ""
    lateinit var startDate: Date
    var endSuggest = 0L
    var endDate: Date? = null
    var activateDay = 0
    lateinit var item: SItem
    var nowPrice = 0.0
    var defaultPrice = 0.0
    var isBidding = AtomicBoolean(false)
    var isEnd = false

    var lastBidUUID: UUID? = null
    var lastBidName = ""

    var splitPrice = 1.0

    fun bid(p: Player, price: Double): Boolean {
        fun bidEnd(bool: Boolean): Boolean {
            isBidding.set(false)
            return bool
        }
        if (isBidding.get()){
            p.sendPrefixMsg(SStr("&4現在入札中です 少し待ってから再度試してください"))
            return false
        }
        isBidding.set(true)
        if (sellerUUID == p.uniqueId){
            p.sendPrefixMsg(SStr("&4自分の出品に入札することはできません"))
            return bidEnd(false)
        }
        if (Man10Auction.bannedPlayer.contains(p.uniqueId)){
            p.sendPrefixMsg(SStr("&4あなたはオークションbanをされています"))
            return bidEnd(false)
        }
        if (nowPrice >= price){
            p.sendPrefixMsg(SStr("&4${nowPrice.toFormatString()}円より高い値段で入札してください"))
            return bidEnd(false)
        }
        if (price % splitPrice != 0.0){
            p.sendPrefixMsg(SStr("&4${splitPrice.toFormatString()}円刻みでしか入札できません"))
            return bidEnd(false)
        }

        if (!SJavaPlugin.vault.withdraw(p.uniqueId, price)){
            p.sendPrefixMsg(SStr("&4金額が足りません"))
            return bidEnd(false)
        }

        return if (SJavaPlugin.mysql.asyncExecute("update normal_auction_data set now_price = $price, last_bid_uuid = '${p.uniqueId}', last_bid_name = '${p.name}' where auc_uuid = '${uuid}'")){
            p.sendPrefixMsg(SStr("&a入札に成功しました！"))
            if (lastBidUUID != null){
                Man10Auction.bank.asyncDeposit(lastBidUUID!!, nowPrice, "Man10Auction other player bid item(${item.getDisplayName()})","オークションで他のプレイヤーがアイテム(${item.getDisplayName()})に入札した") { _, _, _ -> }

            }
            lastBidUUID = p.uniqueId
            lastBidName = p.name
            nowPrice = price
            bidEnd(true)
        } else {
            p.sendPrefixMsg(SStr("&4入札に失敗しました"))
            SJavaPlugin.vault.deposit(p.uniqueId, price)
            bidEnd(false)
        }
    }

    //秒で取得
    fun getRemainingTime(): Long {
        return (endSuggest - Date().time) / 1000
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


            return data
        }
    }
}