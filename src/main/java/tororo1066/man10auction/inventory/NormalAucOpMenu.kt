package tororo1066.man10auction.inventory

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.block.ShulkerBox
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.meta.BlockStateMeta
import org.bukkit.scheduler.BukkitTask
import tororo1066.man10auction.Man10Auction
import tororo1066.man10auction.Man10Auction.Companion.sendPrefixMsg
import tororo1066.man10auction.data.NormalAucData
import tororo1066.tororopluginapi.SJavaPlugin
import tororo1066.tororopluginapi.SStr
import tororo1066.tororopluginapi.defaultMenus.CategorySInventory
import tororo1066.tororopluginapi.defaultMenus.LargeSInventory
import tororo1066.tororopluginapi.defaultMenus.NumericInputInventory
import tororo1066.tororopluginapi.otherUtils.UsefulUtility
import tororo1066.tororopluginapi.otherUtils.UsefulUtility.Companion.toFormatString
import tororo1066.tororopluginapi.sInventory.SInventory
import tororo1066.tororopluginapi.sInventory.SInventoryItem
import tororo1066.tororopluginapi.sItem.SItem
import tororo1066.tororopluginapi.utils.DateType
import tororo1066.tororopluginapi.utils.toJPNDateStr
import java.text.SimpleDateFormat
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

class NormalAucOpMenu: LargeSInventory(SJavaPlugin.plugin, "§b通常オークション(OP)") {

    var task: BukkitTask? = null

    var sort = Sort.HIGH_PRICE
    var search = ""

    enum class Sort(val sortFunc: (MutableCollection<NormalAucData>)->List<NormalAucData>, val displayName: String, val next: Int){
        HIGH_PRICE({it.sortedByDescending { map -> map.nowPrice }},"高い順",1),
        LOW_PRICE({it.sortedBy { map -> map.nowPrice }},"安い順",2),
        ENDING_SOON({it.sortedBy { map -> map.endSuggest }},"終わり際順",3),
        START({it.sortedByDescending { map -> map.endSuggest }},"残り時間順",0)
    }

    init {
        setOnClick {
            it.isCancelled = true
        }
        setOnClose {
            task?.cancel()
        }
    }

    override fun renderMenu(p: Player): Boolean {
        val items = arrayListOf<SInventoryItem>()

        sort.sortFunc.invoke(Man10Auction.normalAucData.values).filter {
            if (search.isBlank())return@filter true
            ChatColor.stripColor(it.item.getDisplayName().lowercase())?.contains(search)?:false
        }.forEach {
            val item = it.item.clone()
                .addLore("",
                    "§e§l出品者：${it.sellerName}",
                    "§b§l値段：${it.nowPrice.toFormatString()}円",
                    "§a§l入札単位：${it.splitPrice.toFormatString()}円",
                    "§d§l残り時間：${it.getRemainingTime()
                        .toJPNDateStr(DateType.SECOND, DateType.YEAR,true)}",
                    "§c§l左クリックで情報を見る")
                .toSInventoryItem().setCanClick(false).setClickEvent { _ ->
                    val isTaskNow = AtomicBoolean(false)

                    val infoMenu = object : CategorySInventory(SJavaPlugin.plugin, "情報", "OPMenu") {
                        override fun renderMenu(p: Player): Boolean {
                            val infoItems = arrayListOf(
                                SInventoryItem(Material.PLAYER_HEAD).setDisplayName("§d出品者: ${it.sellerName}")
                                    .setSkullOwner(it.sellerUUID).setCanClick(false),
                                SInventoryItem(Material.LIME_STAINED_GLASS_PANE).setDisplayName("§dアイテム->")
                                    .setCanClick(false),
                                SInventoryItem(it.item.clone())
                                    .setCanClick(false),
                                SInventoryItem(Material.LIME_STAINED_GLASS_PANE).setDisplayName("§d<-アイテム")
                                    .setCanClick(false),
                                SInventoryItem(Material.GOLD_INGOT)
                                    .setDisplayName("§d初期の値段: ${UsefulUtility.doubleToFormatString(it.defaultPrice)}円")
                                    .setCanClick(false),
                                SInventoryItem(Material.GOLD_BLOCK)
                                    .setDisplayName("§d現在の価格: ${UsefulUtility.doubleToFormatString(it.nowPrice)}円")
                                    .setCanClick(false),
                                SInventoryItem(Material.EMERALD_BLOCK)
                                    .setDisplayName("§d入札単位: ${UsefulUtility.doubleToFormatString(it.splitPrice)}円")
                                    .setCanClick(false),
                                SInventoryItem(Material.PLAYER_HEAD).setDisplayName("§d最終入札者: ${it.lastBidName}")
                                    .apply { if (it.lastBidUUID != null) setSkullOwner(it.lastBidUUID!!) }
                                    .setCanClick(false),
                                SInventoryItem(Material.BOOK)
                                    .setDisplayName("§d出品日時: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(it.startDate)}")
                                    .setCanClick(false),
                                SInventoryItem(Material.LAPIS_BLOCK).setDisplayName("§d期間: ${it.activateDay}日")
                                    .setCanClick(false),
                                SInventoryItem(Material.DIAMOND_BLOCK).setDisplayName("§d遅延した時間: ${it.delayMinute}分")
                                    .setCanClick(false),
                                SInventoryItem(Material.REDSTONE_BLOCK)
                                    .setDisplayName("§d残り時間: ${it.getRemainingTime().toJPNDateStr(DateType.SECOND, DateType.YEAR,true)}")
                                    .setCanClick(false),
                                SInventoryItem(Material.CHEST).setDisplayName("§d終了済み: ${it.isEnd}")
                                    .setCanClick(false)
                            )

                            if (it.item.itemMeta is BlockStateMeta && (it.item.itemMeta as BlockStateMeta).blockState is ShulkerBox){
                                infoItems.add(
                                    SInventoryItem(Material.SHULKER_BOX).setDisplayName("§dシュルカーの中身を見る").setClickEvent second@ { _ ->
                                        val shulkerMeta = (it.item.itemMeta as BlockStateMeta).blockState as ShulkerBox
                                        val inv = object : SInventory(SJavaPlugin.plugin, "中身", 3){

                                            init {
                                                setOnClick { click ->
                                                    click.isCancelled = true
                                                }
                                            }
                                            override fun renderMenu(p: Player): Boolean {
                                                shulkerMeta.inventory.forEachIndexed { index, itemStack ->
                                                    setItem(index, SInventoryItem(itemStack?:return@forEachIndexed).setCanClick(false))
                                                }
                                                return true
                                            }
                                        }

                                        moveChildInventory(inv, p)
                                        return@second
                                    }.setCanClick(false)
                                )
                            }

                            val actionItems = arrayListOf(
                                SInventoryItem(Material.BARRIER).setDisplayName("§d出品を止める")
                                    .addLore("§cシフト左クリック->出品者がアイテムを受け取れる",
                                        "§cシフト右クリック->出品者がアイテムを受け取れない")
                                    .setCanClick(false)
                                    .setClickEvent second@ { e ->
                                        if (e.click == ClickType.SHIFT_LEFT){
                                            if (it.isEnd){
                                                p.sendPrefixMsg(SStr("&c既にキャンセル済みです"))
                                                return@second
                                            }
                                            if (isTaskNow.get())return@second

                                            isTaskNow.set(true)
                                            if (SJavaPlugin.mysql.asyncExecute("update normal_auction_data set isEnd = 'true', end_date = now() where auc_uuid = '${it.uuid}'")){
                                                it.isEnd = true
                                                p.closeInventory()
                                                SJavaPlugin.mysql.callbackExecute("insert into action_log (auc_uuid,action,uuid,name,price,date) values ('${it.uuid}','OP_CANCEL_SELL','${p.uniqueId}','${p.name}',${it.nowPrice},now())") {}
                                                allRenderMenu(p)
                                                p.inventory.addItem(it.item)
                                                if (it.lastBidUUID != null){
                                                    Man10Auction.bank.asyncDeposit(it.lastBidUUID!!, it.nowPrice, "Man10Auction cancel sell deposit(user: ${it.sellerUUID},${it.sellerName})","オークションで${it.item.itemMeta.displayName}がキャンセルされた"
                                                    ) {}
                                                }
                                                p.sendPrefixMsg(SStr("&aキャンセルに成功しました 一応アイテムもプレゼント"))
                                            } else {
                                                p.sendPrefixMsg(SStr("&c&lキャンセルに失敗しました"))
                                            }
                                            isTaskNow.set(false)
                                        }

                                        if (e.click == ClickType.SHIFT_RIGHT){
                                            if (isTaskNow.get())return@second

                                            isTaskNow.set(true)
                                            if (SJavaPlugin.mysql.asyncExecute("update normal_auction_data set isEnd = 'true', isReceived = 'true', end_date = now() where auc_uuid = '${it.uuid}'")){
                                                p.closeInventory()
                                                Man10Auction.normalAucData.remove(it.uuid)
                                                SJavaPlugin.mysql.callbackExecute("insert into action_log (auc_uuid,action,uuid,name,price,date) values ('${it.uuid}','OP_ALL_CANCEL_SELL','${p.uniqueId}','${p.name}',${it.nowPrice},now())") {}
                                                p.inventory.addItem(it.item)
                                                if (it.lastBidUUID != null){
                                                    Man10Auction.bank.asyncDeposit(it.lastBidUUID!!, it.nowPrice, "Man10Auction cancel sell deposit(user: ${it.sellerUUID},${it.sellerName})","オークションで${it.item.itemMeta.displayName}がキャンセルされた"
                                                    ) {}
                                                }
                                                p.sendPrefixMsg(SStr("&aキャンセルに成功しました 一応アイテムもプレゼント"))
                                            } else {
                                                p.sendPrefixMsg(SStr("&c&lキャンセルに失敗しました"))
                                            }
                                            isTaskNow.set(false)
                                        }
                                    },
                                SInventoryItem(Material.PLAYER_HEAD).setDisplayName("§d出品者をbanする")
                                    .setSkullOwner(it.sellerUUID)
                                    .setCanClick(false)
                                    .setClickEvent second@ { _ ->
                                        p.closeInventory()
                                        if (Man10Auction.bannedPlayer.contains(it.sellerUUID)){
                                            p.sendPrefixMsg(SStr("&c既にbanされています"))
                                            return@second
                                        }
                                        val list = SJavaPlugin.plugin.config.getStringList("bannedPlayers")
                                        list.add(it.sellerUUID.toString())
                                        SJavaPlugin.plugin.config.set("bannedPlayers",list)
                                        SJavaPlugin.plugin.saveConfig()
                                        Man10Auction.bannedPlayer.add(p.uniqueId)
                                        p.sendPrefixMsg(SStr("&cbanに成功しました"))
                                    }
                            )

                            setResourceItems(linkedMapOf("情報" to infoItems, "変更" to actionItems))
                            return true
                        }
                    }

                    moveChildInventory(infoMenu, p)
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

    override fun afterRenderMenu(p: Player) {

        fun sortDisplay(sortDis: Sort): String {
            return "${if (sortDis == sort) "§e" else "§7"}${sortDis.displayName}"
        }
        super.afterRenderMenu(p)

        setItem(46, SInventoryItem(Material.COMPASS).setDisplayName("§6§lソート順")
            .addLore(sortDisplay(Sort.HIGH_PRICE),sortDisplay(Sort.LOW_PRICE),sortDisplay(Sort.ENDING_SOON),sortDisplay(Sort.START)).setCanClick(false).setClickEvent {
                sort = Sort.values()[sort.next]
                allRenderMenu(p)
            }.uiSound())

        setItem(47, createInputItem(SItem(Material.OAK_SIGN).setDisplayName("§b§l検索").addLore("§6現在の値：§d${search}"),String::class.java,"§a/<検索ワード>") { str, _ ->
            search = str
            allRenderMenu(p)
        }.uiSound())
    }
}