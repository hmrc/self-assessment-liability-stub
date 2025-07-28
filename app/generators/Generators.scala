package generators

import org.scalacheck.Arbitrary.*
import org.scalacheck.Gen.*
import org.scalacheck.{Arbitrary, Gen, Shrink}

trait Generators extends ModelGenerators {
//  def intStringWithMaxLength(maxLength: Int): Gen[String] =
//    for {
//      length <- choose(1, maxLength)
//      int    <- listOfN(length, arbitrary[Int])
//    } yield int.mkString

  def generateCurrency(min: Double = 0.00, max: Double = 1000.00): Gen[Double] = {
    val minInt: Int = (min * 100).toInt
    val maxInt: Int = (max * 100).toInt
    
    for {
      rInt <- Gen.chooseNum(minInt, maxInt)
    } yield rInt.toDouble / 100 // Yield double with 2-decimal precision.
  }

  def generateChargeId: Gen[String] =
    for {
      chars <- listOfN(2, arbitrary[Char])
      ints  <- listOfN(7, arbitrary[Int])
    } yield s"$chars$ints"

  def generateChargeType: Gen[String] =
    for {
      chargeType <- oneOf("ITSA", "NICS", "VATC")
    } yield chargeType
}
