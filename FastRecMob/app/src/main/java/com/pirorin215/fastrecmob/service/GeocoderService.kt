package com.pirorin215.fastrecmob.service

import android.content.Context
import android.location.Address
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

/**
 * 緯度経度から住所を取得するサービス
 */
class GeocoderService(private val context: Context) {

    /**
     * 緯度経度から住所を取得する
     * @param latitude 緯度
     * @param longitude 経度
     * @return 住所文字列（取得できなかった場合はnull）
     */
    suspend fun getAddressFromLocation(latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) {
            return@withContext null
        }

        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses: List<Address>? = geocoder.getFromLocation(latitude, longitude, 1)

            if (addresses.isNullOrEmpty()) {
                return@withContext null
            }

            val address = addresses[0]
            buildAddressString(address)
        } catch (e: IOException) {
            // ネットワークエラーやその他のIOエラー
            e.printStackTrace()
            null
        } catch (e: Exception) {
            // その他のエラー
            e.printStackTrace()
            null
        }
    }

    /**
     * Addressオブジェクトから住所文字列を構築する
     * getAddressLine(0)から国名と郵便番号を削除して整形
     */
    private fun buildAddressString(address: Address): String {
        // getAddressLine(0) は「日本、〒305-0005 茨城県つくば市天久保２丁目１−１」のようなフルセットを返す
        val fullAddress = address.getAddressLine(0) ?: return buildFallbackAddress(address)

        // 表示をスッキリさせるため、国名と郵便番号を削る
        return fullAddress
            .replace("日本、", "")
            .replace("日本", "")
            .replace(Regex("〒\\d{3}-\\d{4}\\s*"), "")
            .trim()
    }

    /**
     * getAddressLine(0)がnullの場合のフォールバック
     */
    private fun buildFallbackAddress(address: Address): String {
        val addressParts = mutableListOf<String>()

        // 日本の住所フォーマット：都道府県 > 市区町村 > 町名 > 番地
        address.adminArea?.let { addressParts.add(it) } // 都道府県
        address.locality?.let { addressParts.add(it) } // 市区町村
        address.subLocality?.let { addressParts.add(it) } // 地域名
        address.thoroughfare?.let { addressParts.add(it) } // 町名
        address.subThoroughfare?.let { addressParts.add(it) } // 番地

        return if (addressParts.isNotEmpty()) {
            addressParts.joinToString("")
        } else {
            // どのフィールドも取得できなかった場合
            address.latitude?.let { lat ->
                address.longitude?.let { lon ->
                    "(${String.format("%.5f", lat)}, ${String.format("%.5f", lon)})"
                }
            } ?: "不明"
        }
    }

    companion object {
        /**
         * Geocoderが利用可能かどうかをチェック
         */
        fun isAvailable(context: Context): Boolean {
            return Geocoder.isPresent()
        }
    }
}
