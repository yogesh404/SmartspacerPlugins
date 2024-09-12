package utils.icons

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.annotation.XmlRes
import org.xmlpull.v1.XmlPullParser

// TODO: move elsewhere if we implement other icon packs
data class IconPackInfo(
    val name: String,
    val packageName: String,
    val hasWeatherIcons: Boolean,
    val drawableFilter: Map<String, String> = emptyMap()
)

class BreezyIconProvider(
    private val context: Context
) {

    companion object {
        private const val TAG = "BreezyIconProvider"

        private const val ICON_PROVIDER_ACTION = "org.breezyweather.ICON_PROVIDER"
        private const val PROVIDER_CONFIG_META_DATA = "org.breezyweather.PROVIDER_CONFIG"
        private const val DRAWABLE_FILTER_META_DATA = "org.breezyweather.DRAWABLE_FILTER"
    }

    // TODO: do we need this?
    private data class IconPackConfig(
        val hasWeatherIcons: Boolean
        // Add other attributes as needed
    )

    fun getInstalledIconPacks(): List<IconPackInfo> {
        val packageManager = context.packageManager
        val providers = packageManager.queryIntentActivities(
            Intent(ICON_PROVIDER_ACTION),
            PackageManager.GET_RESOLVED_FILTER
        )

        Log.d(TAG, "providers: $providers")

        return providers.mapNotNull { resolveInfo ->
            getIconPackByPackageName(resolveInfo.activityInfo.packageName)
        }
    }

    fun getIconPackByPackageName(packageName: String): IconPackInfo? {
        try {
            val packageManager = context.packageManager
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val metaData = appInfo.metaData
            val configResId = metaData.getInt(PROVIDER_CONFIG_META_DATA)
            val config = parseConfig(packageManager.getResourcesForApplication(packageName), configResId)

            val drawableFilterResId = metaData.getInt(DRAWABLE_FILTER_META_DATA, 0)
            val drawableFilter = if (drawableFilterResId != 0) {
                parseFilter(packageManager.getResourcesForApplication(packageName), drawableFilterResId)
            } else {
                emptyMap()
            }

            return IconPackInfo(
                name = packageManager.getApplicationLabel(appInfo).toString(),
                packageName = packageName,
                hasWeatherIcons = config.hasWeatherIcons,
                drawableFilter = drawableFilter
            )
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Failed to get icon pack info for $packageName", e)

            return null
        }
    }

    fun getWeatherIcon(
        iconPack: IconPackInfo,
        weatherCode: String,
        isDay: Boolean
    ): Drawable? {
        val resources = try {
            context.packageManager.getResourcesForApplication(iconPack.packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Failed to get resources for ${iconPack.packageName}", e)
            return null
        }

        val fileName = getWeatherIconFileName(weatherCode, isDay, iconPack.drawableFilter)
        val drawableId = resources.getIdentifier(fileName, "drawable", iconPack.packageName)

        return if (drawableId != 0) {
            resources.getDrawable(drawableId)
        } else {
            null
        }
    }

    private fun parseConfig(resources: Resources, @XmlRes configResId: Int): IconPackConfig {
        val parser = resources.getXml(configResId)
        var type = parser.eventType
        while (type != XmlPullParser.END_DOCUMENT) {
            if (type == XmlPullParser.START_TAG && "config" == parser.name) {
                return IconPackConfig(
                    parser.getAttributeBooleanValue(
                        null,
                        "hasWeatherIcons",
                        false
                    )
                )
            }
            type = parser.next()
        }

        return IconPackConfig(false)
    }

    private fun parseFilter(resources: Resources, @XmlRes filterResId: Int): Map<String, String> {
        val map: MutableMap<String, String> = HashMap()
        val parser = resources.getXml(filterResId)
        var type = parser.eventType
        while (type != XmlPullParser.END_DOCUMENT) {
            if (type == XmlPullParser.START_TAG && "item" == parser.name) {
                map[parser.getAttributeValue(null, "name")] =
                    parser.getAttributeValue(null, "value")
            }
            type = parser.next()
        }
        return map
    }

    private fun getWeatherIconFileName(
        weatherCode: String,
        isDay: Boolean,
        drawableFilter: Map<String, String>
    ): String {
        val standardFileName = "${weatherCode}_${if (isDay) "day" else "night"}"

        return drawableFilter[standardFileName] ?: standardFileName
    }

    fun getBreezyWeatherCode(conditionCode: Int?): String? {
        return when (conditionCode) {
            200, 201, 202 -> "weather_thunderstorm"
            210, 211, 212 -> "shortcuts_thunder"
            221, 230, 231, 232 -> "weather_thunderstorm"
            300, 301, 302, 310, 311, 312, 313, 314, 321 -> "weather_rain"
            500, 501, 502, 503, 504 -> "weather_rain"
            511 -> "weather_sleet"
            600, 601, 602 -> "weather_snow"
            611, 612, 613, 614, 615, 616 -> "weather_sleet"
            620, 621, 622 -> "weather_snow"
            701, 711, 721, 731 -> "weather_haze"
            741 -> "weather_fog"
            751, 761, 762 -> "weather_haze"
            771, 781 -> "weather_wind"
            800 -> "weather_clear"
            801, 802 -> "weather_partly_cloudy"
            803, 804 -> "weather_cloudy"

            else -> null
        }
    }
}