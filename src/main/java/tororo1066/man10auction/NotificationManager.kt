package tororo1066.man10auction

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import net.kyori.adventure.text.Component
import tororo1066.man10auction.data.NormalAucData
import tororo1066.tororopluginapi.SJavaPlugin
import tororo1066.tororopluginapi.SStr
import tororo1066.tororopluginapi.SStr.Companion.toSStr
import tororo1066.tororopluginapi.otherUtils.UsefulUtility.Companion.toFormatString
import java.net.InetSocketAddress
import java.util.*


class NotificationManager {

    private val server = HttpServer.create(InetSocketAddress(8080), 0)
    init {
        server.createContext("/notice", NotificationHandler())
        server.start()
    }

    fun stop(){
        server.stop(0)
    }

    private class NotificationHandler : HttpHandler {

        override fun handle(exchange: HttpExchange) {

            fun close(response: String){
                exchange.sendResponseHeaders(200, response.length.toLong())
                val os = exchange.responseBody
                os.write(response.toByteArray())
                os.close()
                exchange.close()
            }

            // ここで通知を受け取る処理を実装

            val requestBody = exchange.requestBody.bufferedReader().use {
                it.readText()
            }
            val json = Gson().fromJson(requestBody, JsonObject::class.java)
            val aucUUID = json.get("auc_uuid")?.asString
            if (aucUUID == null) {
                close("auc_uuid is null")
                return
            }
            SJavaPlugin.mysql.callbackQuery("select * from normal_auction_data where auc_uuid = '$aucUUID'") { rs ->
                val result = rs.firstOrNull() ?: run {
                    close("auc_data is not found")
                    return@callbackQuery
                }
                val data = NormalAucData.load(result) ?: run {
                    close("auc_data failed to load")
                    return@callbackQuery
                }
                Man10Auction.normalAucData[UUID.fromString(aucUUID)] = data
                Man10Auction.prefix.append(data.item.displayName().hoverEvent(data.item).toSStr())
                    .append(SStr("&aに${data.nowPrice.toFormatString()}円の入札！")).broadcast()
            }

            val response = "Notification Received!"
            exchange.sendResponseHeaders(200, response.length.toLong())
            val os = exchange.responseBody
            os.write(response.toByteArray())
            os.close()
            exchange.close()


        }
    }
}

