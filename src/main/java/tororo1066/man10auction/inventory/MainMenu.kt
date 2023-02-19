package tororo1066.man10auction.inventory

import org.bukkit.Material
import org.bukkit.entity.Player
import tororo1066.tororopluginapi.SJavaPlugin
import tororo1066.tororopluginapi.sInventory.SInventory
import tororo1066.tororopluginapi.sInventory.SInventoryItem

class MainMenu: SInventory(SJavaPlugin.plugin, "MainMenu", 3) {

    init {
        setOnClick {
            it.isCancelled = true
        }
    }

    override fun renderMenu(p: Player): Boolean {
        setItem(1, SInventoryItem(Material.LAPIS_BLOCK).setDisplayName("§a通常のオークションを見る").setCanClick(false).setClickEvent {
            moveChildInventory(NormalAucMenu(),p)
        }.uiSound())

        setItem(4, SInventoryItem(Material.OAK_SIGN).setDisplayName("§b通常のオークションに出品する").setCanClick(false).setClickEvent {
            moveChildInventory(NormalAucSellMenu(),p)
        }.uiSound())

        setItem(7, SInventoryItem(Material.CHEST).setDisplayName("§eオークションで競り落としたアイテムを受け取る").setCanClick(false).setClickEvent {
            moveChildInventory(ReceiveMenu(),p)
        }.uiSound())

        setItem(20, SInventoryItem(Material.ENDER_CHEST).setDisplayName("§d出品しているアイテムを見る").setCanClick(false).setClickEvent {
            moveChildInventory(CancelSellMenu(),p)
        }.uiSound())

        return true
    }
}