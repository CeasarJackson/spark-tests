package org.hammerlab.spark.test.suite

import grizzled.slf4j.Logging
import hammerlab.test.Suite
import org.apache.spark.{ SparkConf, SparkContext, SparkException }
import org.hammerlab.spark.{ Context, SparkConfBase }

/**
 * Base for tests that initialize [[SparkConf]]s (and [[SparkContext]]s, though that is left to subclasses).
 */
trait SparkSuiteBase
  extends Suite
    with SparkConfBase
    with TestConfs
    with Logging
{

  protected implicit var sc: SparkContext = _
  protected implicit var ctx: Context = _

  def makeSparkContext: SparkContext =
    Option(sc) match {
      case Some(_) ⇒
        throw SparkContextAlreadyInitialized
      case None ⇒
        val sparkConf = makeSparkConf

        sc =
          try {
            new SparkContext(sparkConf)
          } catch {
            case e: SparkException if e.getMessage.contains("Only one SparkContext may be running in this JVM") ⇒
              val sc = SparkContext.getOrCreate()
              warn("Previous SparkContext still active! Shutting down; here is the caught error:")
              warn(e)
              sc.stop()
              new SparkContext(sparkConf)
          }
        val checkpointsDir = tmpDir()
        sc.setCheckpointDir(checkpointsDir.toString)

        ctx = sc
        sc
    }

  def clear(): Unit =
    if (sc != null) {
      sc.stop()
      sc = null
      ctx = null
    } else
      throw NoSparkContextToClear

  override def sparkConf(confs: (String, String)*): Unit =
    Option(sc) match {
      case Some(_) ⇒
        throw SparkConfigAfterInitialization(confs)
      case None ⇒
        super.sparkConf(confs: _*)
    }
}

case object SparkContextAlreadyInitialized extends IllegalStateException

case class SparkConfigAfterInitialization(confs: Seq[(String, String)])
  extends IllegalStateException(
    s"Attempting to configure SparkContext after initialization:\n" +
      (
        for {
          (k, v) ← confs
        } yield
          s"$k:\t$v"
      )
      .mkString("\t", "\n\t", "")
  )

case object NoSparkContextToClear extends IllegalStateException
