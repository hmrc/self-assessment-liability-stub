package generators

import models.*
import org.scalacheck.Arbitrary.*
import org.scalacheck.Gen.*
import org.scalacheck.{Arbitrary, Gen, Shrink}

trait ModelGenerators {
  self: Generators =>

  private val balanceDetails: Arbitrary[BalanceDetails] = Arbitrary {
    for {
      totalOverdueBalance <- generateCurrency()
      totalPayableBalance <- totalOverdueBalance
      payableDueDate <- "???"
      totalPendingBalance <- generateCurrency()
      pendingDueDate <- "???"
      totalBalance <- totalPayableBalance + totalPendingBalance
      totalCodedOut <- generateCurrency(max = totalBalance)
      totalCreditAvailable <- generateCurrency(max = totalBalance)
    } yield BalanceDetails(
      totalOverdueBalance,
      totalPayableBalance,
      payableDueDate,
      totalPendingBalance,
      pendingDueDate,
      totalBalance,
      totalCodedOut,
      totalCreditAvailable
    )
  }
  private val chargeDetails: Arbitrary[Option[Set[ChargeDetails]]] = Arbitrary {
    
  }
  private val refundDetails: Arbitrary[Option[Set[RefundDetails]] = Arbitrary {
    
  }
  private val paymentHistoryDetails: Arbitrary[Option[Set[PaymentHistoryDetails]]] = Arbitrary {
    
  }

  val hipResponse: Arbitrary[String] = Arbitrary {
    for {
      balanceDetails <- balanceDetails
      chargeDetails <- chargeDetails
      refundDetails <- refundDetails
      paymentHistoryDetails <- paymentHistoryDetails
    } yield HipResponse(
      balanceDetails,
      chargeDetails,
      refundDetails,
      paymentHistoryDetails
    )
  }
}
