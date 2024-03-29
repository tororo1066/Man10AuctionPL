package tororo1066.man10auction.command

import org.bukkit.Bukkit
import tororo1066.man10auction.Man10Auction
import tororo1066.man10auction.Man10Auction.Companion.sendPrefixMsg
import tororo1066.man10auction.inventory.MainMenu
import tororo1066.man10auction.inventory.NormalAucOpMenu
import tororo1066.tororopluginapi.SJavaPlugin
import tororo1066.tororopluginapi.SStr
import tororo1066.tororopluginapi.annotation.SCommandBody
import tororo1066.tororopluginapi.sCommand.SCommand
import tororo1066.tororopluginapi.sCommand.SCommandArg
import tororo1066.tororopluginapi.sCommand.SCommandArgType
import kotlin.random.Random

class AuctionCommand : SCommand("mauction",Man10Auction.prefix.toString(),"mauction.user") {

    @SCommandBody
    val mainMenu = command().setPlayerExecutor {
        if (!Man10Auction.pluginEnabled){
            if (!it.sender.hasPermission("mauction.op")){
                it.sender.sendPrefixMsg(SStr("&c&l現在使用できません"))
                return@setPlayerExecutor
            }
        }
        if (Man10Auction.bannedPlayer.contains(it.sender.uniqueId)){
            it.sender.sendPrefixMsg(SStr("&c&lあなたはオークションbanをされています"))
            return@setPlayerExecutor
        }
        MainMenu().open(it.sender)
    }

    @SCommandBody
    val connectWeb = command().addArg(SCommandArg("web")).setPlayerExecutor {
        val data = SJavaPlugin.mysql.asyncQuery("select * from clients where uuid = '${it.sender.uniqueId}'").firstOrNull()
        if (data?.getNullableString("pass") != null){
            it.sender.sendPrefixMsg(SStr("&a&lあなたは既に連携されています"))
            return@setPlayerExecutor
        }

        val linkId = Random.nextInt(10000,99999)
        if (!SJavaPlugin.mysql.asyncExecute("insert into clients (uuid,linkId) values ('${it.sender.uniqueId}',$linkId)")){
            it.sender.sendPrefixMsg(SStr("&c&l連携に失敗しました"))
            return@setPlayerExecutor
        }

        it.sender.sendPrefixMsg(SStr("&a&l以下のURLから連携を行ってください"))
        it.sender.sendPrefixMsg(SStr("&c&lあなたのリンクIDは${linkId}です(他言厳禁)"))
        it.sender.sendPrefixMsg(SStr("&a&lhttps://???"))
    }

    @SCommandBody("mauction.op")
    val opMenu = command().addArg(SCommandArg("opMenu"))
        .setPlayerExecutor {
            NormalAucOpMenu().open(it.sender)
        }

    @SCommandBody("mauction.op")
    val enabled = command().addArg(SCommandArg("enabled")).addArg(SCommandArg(SCommandArgType.BOOLEAN)).setPlayerExecutor {
        Man10Auction.pluginEnabled = it.args[1].toBoolean()
        SJavaPlugin.plugin.config.set("pluginEnabled",Man10Auction.pluginEnabled)
        SJavaPlugin.plugin.saveConfig()
        it.sender.sendPrefixMsg(SStr("&a${Man10Auction.pluginEnabled}にしました"))
    }

    @SCommandBody("mauction.op")
    val reload = command().addArg(SCommandArg("reload")).setPlayerExecutor {
        Man10Auction.reloadConfigs()
        it.sender.sendPrefixMsg(SStr("&aリロードしました"))
    }

    @SCommandBody("mauction.op")
    val infoBanPlayer = command().addArg(SCommandArg("ban")).addArg(SCommandArg("info")).addArg(SCommandArg(SCommandArgType.STRING).addAlias("プレイヤー名")).setPlayerExecutor {
        val p = Bukkit.getOfflinePlayerIfCached(it.args[2])
        if (p == null){
            it.sender.sendPrefixMsg(SStr("&c&lプレイヤーが存在しません"))
            return@setPlayerExecutor
        }

        if (Man10Auction.bannedPlayer.contains(p.uniqueId)){
            it.sender.sendPrefixMsg(SStr("&a${p.name}はbanされています"))
        } else {
            it.sender.sendPrefixMsg(SStr("&a${p.name}はbanされていません"))
        }
    }

    @SCommandBody("mauction.op")
    val addBanPlayer = command().addArg(SCommandArg("ban")).addArg(SCommandArg("add")).addArg(SCommandArg(SCommandArgType.STRING).addAlias("プレイヤー名")).setPlayerExecutor {
        val p = Bukkit.getOfflinePlayerIfCached(it.args[2])
        if (p == null){
            it.sender.sendPrefixMsg(SStr("&c&lプレイヤーが存在しません"))
            return@setPlayerExecutor
        }

        val list = SJavaPlugin.plugin.config.getStringList("bannedPlayers")
        list.add(p.uniqueId.toString())
        SJavaPlugin.plugin.config.set("bannedPlayers",list)
        SJavaPlugin.plugin.saveConfig()
        Man10Auction.bannedPlayer.add(p.uniqueId)

        it.sender.sendPrefixMsg(SStr("&a${p.name}を追加しました"))
    }

    @SCommandBody("mauction.op")
    val removeBanPlayer = command().addArg(SCommandArg("ban")).addArg(SCommandArg("remove")).addArg(SCommandArg(SCommandArgType.STRING).addAlias("プレイヤー名")).setPlayerExecutor {
        val p = Bukkit.getOfflinePlayerIfCached(it.args[2])
        if (p == null){
            it.sender.sendPrefixMsg(SStr("&c&lプレイヤーが存在しません"))
            return@setPlayerExecutor
        }

        val list = SJavaPlugin.plugin.config.getStringList("bannedPlayers")
        list.remove(p.uniqueId.toString())
        SJavaPlugin.plugin.config.set("bannedPlayers",list)
        SJavaPlugin.plugin.saveConfig()
        Man10Auction.bannedPlayer.remove(p.uniqueId)

        it.sender.sendPrefixMsg(SStr("&a${p.name}を削除しました"))
    }

}