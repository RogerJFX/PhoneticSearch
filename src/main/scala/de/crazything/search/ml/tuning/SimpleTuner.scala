package de.crazything.search.ml.tuning

import java.util.concurrent.atomic.AtomicReference

import scala.util.Random

class SimpleTuner(config: TunerConfig) extends Tuner {

  private val boostStep = config.boostStep

  private val oldNotification = new AtomicReference[Notification[Float]](null)

  override val vector: Array[Float] = Array.fill[Float](config.querySize)(config.initialBoost)

  private val random = new Random()

  override def tune(terms: Seq[String], delta: Int): Unit = {
    val old = oldNotification.getAndSet(Notification(terms, delta, vector.clone()))
    if (old != null && old.delta < delta) {
      for (i <- vector.indices) {
        vector(i) = old.tuning(i)
      }
    }
    if (random.nextBoolean()) {
      vector(random.nextInt(config.querySize)) += boostStep
    } else {
      vector(random.nextInt(config.querySize)) -= boostStep
    }
  }

  override def boostAt(index: Int): Float = vector(index)

  override val threshold: Int = config.threshold

  override def reset(): Unit = {
    oldNotification.set(null)
    for (i <- vector.indices) {
      vector(i) = config.initialBoost
    }
  }
}
