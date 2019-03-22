package com.lykke.trade.volume.monitoring.service.process.impl

import com.lykke.me.subscriber.incoming.events.ExecutionEvent
import com.lykke.trade.volume.monitoring.service.entity.EventTradeVolumesWrapper
import com.lykke.trade.volume.monitoring.service.process.EventProcessLoggerFactory
import com.lykke.trade.volume.monitoring.service.process.ExecutionEventListener
import com.lykke.trade.volume.monitoring.service.process.ExecutionEventProcessor
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executor

class ExecutionEventListenerImpl(private val id: Long,
                                 private val executor: Executor,
                                 private val inputQueue: BlockingQueue<ExecutionEvent>,
                                 private val processor: ExecutionEventProcessor,
                                 private val outputQueue: BlockingQueue<EventTradeVolumesWrapper>) : ExecutionEventListener {

    companion object {
        private val LOGGER = EventProcessLoggerFactory.getLogger(ExecutionEventListenerImpl::class.java.name)
    }

    override fun startProcessingExecutionEvents() {
        executor.execute {
            while (true) {
                try {
                    processEvent(inputQueue.take())
                } catch (e: Exception) {
                    LOGGER.error(null, "Unable to take and process event: ${e.message}", e)
                }
            }
        }
        LOGGER.info(null, "Started, id: $id")
    }

    private fun processEvent(event: ExecutionEvent) {
        try {
            val tradeVolumes = processor.process(event)
            if (tradeVolumes.tradeVolumes.isEmpty()) {
                LOGGER.debug(event.sequenceNumber, "No trades in event")
                return
            }
            outputQueue.put(tradeVolumes)
        } catch (e: Exception) {
            LOGGER.error(event.sequenceNumber,
                    "Unable to process incoming execution event: ${e.message}",
                    e)
        }
    }
}