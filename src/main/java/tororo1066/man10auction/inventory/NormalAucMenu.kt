package tororo1066.man10auction.inventory

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.block.ShulkerBox
import org.bukkit.entity.Player
import org.bukkit.inventory.meta.BlockStateMeta
import org.bukkit.scheduler.BukkitTask
import tororo1066.man10auction.Man10Auction
import tororo1066.man10auction.Man10Auction.Companion.sendPrefixMsg
import tororo1066.man10auction.data.NormalAucData
import tororo1066.tororopluginapi.SJavaPlugin
import tororo1066.tororopluginapi.SStr
import tororo1066.tororopluginapi.defaultMenus.LargeSInventory
import tororo1066.tororopluginapi.defaultMenus.NumericInputInventory
import tororo1066.tororopluginapi.frombukkit.SBukkit
import tororo1066.tororopluginapi.otherUtils.UsefulUtility.Companion.toFormatString
import tororo1066.tororopluginapi.sInventory.SInventory
import tororo1066.tororopluginapi.sInventory.SInventoryItem
import tororo1066.tororopluginapi.sItem.SItem
import tororo1066.tororopluginapi.utils.DateType
import tororo1066.tororopluginapi.utils.toJPNDateStr
import java.util.function.Consumer

class NormalAucMenu: LargeSInventory(SJavaPlugin.plugin, "§b通常オークション") {

    private var task: BukkitTask? = null

    private var sort = Sort.HIGH_PRICE
    private var search = ""

    enum class Sort(val sortFunc: (MutableCollection<NormalAucData>)->List<NormalAucData>, val displayName: String, val next: Int){
        HIGH_PRICE({it.sortedByDescending { map -> map.nowPrice }},"高い順",1),
        LOW_PRICE({it.sortedBy { map -> map.nowPrice }},"安い順",2),
        ENDING_SOON({it.sortedBy { map -> map.getRemainingTime() }},"終わり際順",3),
        START({it.sortedByDescending { map -> map.getRemainingTime() }},"残り時間順",0)
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
            if (it.isEnd)return@forEach
            val item = it.item.clone()
                .addLore("","§e§l出品者：${it.sellerName}","§b§l値段：${it.nowPrice.toFormatString()}円","§a§l入札単位：${it.splitPrice.toFormatString()}円","§d§l残り時間：${it.getRemainingTime().toJPNDateStr(DateType.SECOND,DateType.YEAR,true)}")
                .toSInventoryItem().setCanClick(false).setClickEvent { e ->
                    if (e.isRightClick && it.item.itemMeta is BlockStateMeta && (it.item.itemMeta as BlockStateMeta).blockState is ShulkerBox){
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
                        return@setClickEvent
                    }
                    val inputInv = NumericInputInventory(SJavaPlugin.plugin,"§a入札金額")
                    inputInv.onConfirm = Consumer { int ->
                        if (!Man10Auction.normalAucData.containsKey(it.uuid)){
                            p.sendPrefixMsg(SStr("&c&l出品が存在しません"))
                            return@Consumer
                        }
                        p.closeInventory()
                        Man10Auction.normalAucData[it.uuid]!!.bid(p,int.toDouble())
                    }
                    moveChildInventory(inputInv, p)
                }
            if (it.item.itemMeta is BlockStateMeta && (it.item.itemMeta as BlockStateMeta).blockState is ShulkerBox){
                item.addLore("§c右クリックで中身を見る")
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