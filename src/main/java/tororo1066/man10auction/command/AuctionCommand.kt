package tororo1066.man10auction.command

import org.bukkit.Bukkit
import tororo1066.man10auction.Man10Auction
import tororo1066.man10auction.Man10Auction.Companion.sendPrefixMsg
import tororo1066.man10auction.inventory.MainMenu
import tororo1066.tororopluginapi.SJavaPlugin
import tororo1066.tororopluginapi.SStr
import tororo1066.tororopluginapi.annotation.SCommandBody
import tororo1066.tororopluginapi.sCommand.SCommand
import tororo1066.tororopluginapi.sCommand.SCommandArg
import tororo1066.tororopluginapi.sCommand.SCommandArgType

class AuctionCommand : SCommand("mauction",Man10Auction.pluginEnabled.toString(),"mauction.user") {

    @SCommandBody
    val mainMenu = command().setPlayerExecutor {
        if (!Man10Auction.pluginEnabled){
            it.sender.sendPrefixMsg(SStr("&4現在使用できません"))
            return@setPlayerExecutor
        }
        if (Man10Auction.bannedPlayer.contains(it.sender.uniqueId)){
            it.sender.sendPrefixMsg(SStr("&4あなたはオークションbanをされています"))
            return@setPlayerExecutor
        }
        MainMenu().open(it.sender)
    }

    @SCommandBody("mauction.op")
    val enabled = command().addArg(SCommandArg("enabled")).addArg(SCommandArg(SCommandArgType.BOOLEAN)).setPlayerExecutor {
        Man10Auction.pluginEnabled = it.args[1].toBoolean()
        SJavaPlugin.plugin.config.set("pluginEnabled",Man10Auction.pluginEnabled)
        SJavaPlugin.plugin.saveConfig()
        it.sender.sendPrefixMsg(SStr("&a${Man10Auction.pluginEnabled}にしました"))
    }

    @SCommandBody("mauction.op")
    val infoBanPlayer = command().addArg(SCommandArg("ban")).addArg(SCommandArg("info")).addArg(SCommandArg(SCommandArgType.STRING).addAlias("プレイヤー名")).setPlayerExecutor {
        val p = Bukkit.getOfflinePlayerIfCached(it.args[2])
        if (p == null){
            it.sender.sendPrefixMsg(SStr("&4プレイヤーが存在しません"))
            return@setPlayerExecutor
        }

        if (SJavaPlugin.plugin.config.getStringList("bannedPlayers").contains(p.uniqueId.toString())){
            it.sender.sendPrefixMsg(SStr("&a${p.name}はbanされています"))
        } else {
            it.sender.sendPrefixMsg(SStr("&a${p.name}はbanされていません"))
        }
    }

    @SCommandBody("mauction.op")
    val addBanPlayer = command().addArg(SCommandArg("ban")).addArg(SCommandArg("add")).addArg(SCommandArg(SCommandArgType.STRING).addAlias("プレイヤー名")).setPlayerExecutor {
        val p = Bukkit.getOfflinePlayerIfCached(it.args[2])
        if (p == null){
            it.sender.sendPrefixMsg(SStr("&4プレイヤーが存在しません"))
            return@setPlayerExecutor
        }

        val list = SJavaPlugin.plugin.config.getStringList("bannedPlayers")
        list.add(p.uniqueId.toString())
        SJavaPlugin.plugin.config.set("bannedPlayers",list)
        SJavaPlugin.plugin.saveConfig()

        it.sender.sendPrefixMsg(SStr("&a${p.name}を追加しました"))
    }

    @SCommandBody("mauction.op")
    val removeBanPlayer = command().addArg(SCommandArg("ban")).addArg(SCommandArg("remove")).addArg(SCommandArg(SCommandArgType.STRING).addAlias("プレイヤー名")).setPlayerExecutor {
        val p = Bukkit.getOfflinePlayerIfCached(it.args[2])
        if (p == null){
            it.sender.sendPrefixMsg(SStr("&4プレイヤーが存在しません"))
            return@setPlayerExecutor
        }

        val list = SJavaPlugin.plugin.config.getStringList("bannedPlayers")
        list.remove(p.uniqueId.toString())
        SJavaPlugin.plugin.config.set("bannedPlayers",list)
        SJavaPlugin.plugin.saveConfig()

        it.sender.sendPrefixMsg(SStr("&a${p.name}を削除しました"))
    }


}