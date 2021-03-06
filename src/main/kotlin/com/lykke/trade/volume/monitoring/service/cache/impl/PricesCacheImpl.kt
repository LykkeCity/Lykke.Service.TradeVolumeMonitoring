package com.lykke.trade.volume.monitoring.service.cache.impl

import com.lykke.trade.volume.monitoring.service.cache.PricesCache
import com.lykke.trade.volume.monitoring.service.entity.Rate
import com.lykke.trade.volume.monitoring.service.loader.RatesLoader
import com.lykke.trade.volume.monitoring.service.utils.divideWithMaxScale
import com.lykke.utils.logging.MetricsLogger
import com.lykke.utils.logging.ThrottlingLogger
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class PricesCacheImpl(private val ratesLoader: RatesLoader,
                      override val updateInterval: Long) : PricesCache {

    companion object {
        private val LOGGER = ThrottlingLogger.getLogger(PricesCacheImpl::class.java.name)
        private val METRICS_LOGGER = MetricsLogger.getLogger()
    }

    @Volatile
    private var midPricesByAssetPairId: ConcurrentMap<String, BigDecimal> = ConcurrentHashMap<String, BigDecimal>()

    override fun update() {
        val ratesByAssetPairId = try {
            ratesLoader.loadRatesByAssetPairIdMap()
        } catch (e: Exception) {
            val message = "Unable to load rates"
            LOGGER.error(message, e)
            METRICS_LOGGER.logError(message, e)
            return
        }
        val midPricesByAssetPairId = convertToMidPricesByAssetPairIdMap(ratesByAssetPairId)
        this.midPricesByAssetPairId = midPricesByAssetPairId
        LOGGER.debug("Loaded ${ratesByAssetPairId.size} rates (mid prices: ${midPricesByAssetPairId.size})")
    }

    override fun getPrice(assetPairId: String): BigDecimal? {
        var midPrice = midPricesByAssetPairId[assetPairId]
        if (midPrice == null) {
            val rate = ratesLoader.loadRate(assetPairId)
            midPrice = rate?.let { calculateMidPrice(rate) }
            if (midPrice != null) {
                midPricesByAssetPairId[assetPairId] = midPrice
            }
        }
        return midPrice?.stripTrailingZeros()
    }

    private fun convertToMidPricesByAssetPairIdMap(ratesByAssetPairId: Map<String, Rate>): ConcurrentMap<String, BigDecimal> {
        val midPricesByAssetPairId = ConcurrentHashMap<String, BigDecimal>()
        ratesByAssetPairId.forEach { assetPairId, rate ->
            calculateMidPrice(rate)?.let { midPrice ->
                midPricesByAssetPairId[assetPairId] = midPrice
            }
        }
        return midPricesByAssetPairId
    }

    private fun calculateMidPrice(rate: Rate): BigDecimal? {
        return if (rate.ask?.signum() == 1 && rate.bid?.signum() == 1)
            divideWithMaxScale(rate.ask + rate.bid, BigDecimal.valueOf(2))
        else null
    }

    init {
        update()
        LOGGER.info("Loaded ${midPricesByAssetPairId.size} mid prices initially")
    }
}

