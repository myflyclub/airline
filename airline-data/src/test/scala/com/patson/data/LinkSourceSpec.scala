package com.patson.data

import com.patson.model.{Link, LinkClassValues, TransportType}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.util.Using

class LinkSourceSpec extends AnyWordSpec with Matchers {
  private case class PriceChange(
    economy: Int,
    business: Int,
    first: Int,
    economyDelta: Int,
    businessDelta: Int,
    firstDelta: Int)

  "LinkSource.updateLink" should {
    "save one price change and none when the price is unchanged" in {
      assume(
        sys.env.get("RUN_LINK_SOURCE_DB_TESTS").contains("1"),
        "Set RUN_LINK_SOURCE_DB_TESTS=1 and point mysqldb.* at an initialized disposable database")

      val originalLink = loadFixtureLink()
      val originalPrice = originalLink.price
      val historyBaseline = latestHistoryId(originalLink.id)
      val updatedPrice = LinkClassValues.getInstance(
        originalPrice.economyVal + 1,
        originalPrice.businessVal,
        originalPrice.firstVal,
        originalPrice.discountVal)
      val updatedLink = originalLink.copy(price = updatedPrice)
      originalLink.getAssignedModel().foreach(updatedLink.setAssignedModel)

      try {
        LinkSource.updateLink(updatedLink) shouldBe 1

        val changesAfterUpdate = loadPriceChanges(originalLink.id, historyBaseline)
        changesAfterUpdate should have size 1
        changesAfterUpdate.head shouldBe PriceChange(
          economy = updatedPrice.economyVal,
          business = updatedPrice.businessVal,
          first = updatedPrice.firstVal,
          economyDelta = 1,
          businessDelta = 0,
          firstDelta = 0)

        LinkSource.updateLink(updatedLink) shouldBe 1
        loadPriceChanges(originalLink.id, historyBaseline) should have size 1
      } finally {
        restorePrice(originalLink)
        deleteHistoryAfter(originalLink.id, historyBaseline)
      }
    }
  }

  private def loadFixtureLink(): Link = {
    val linkId = Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("SELECT id FROM link WHERE transport_type = ? ORDER BY id LIMIT 1")) { statement =>
        statement.setInt(1, TransportType.FLIGHT.id)
        Using.resource(statement.executeQuery()) { resultSet =>
          if (resultSet.next()) resultSet.getInt("id")
          else fail("The disposable database must contain at least one flight link")
        }
      }
    }
    LinkSource.loadFlightLinkById(linkId).getOrElse(fail(s"Unable to load fixture link $linkId"))
  }

  private def latestHistoryId(linkId: Int): Int =
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("SELECT COALESCE(MAX(id), 0) AS id FROM link_change_history WHERE link = ?")) { statement =>
        statement.setInt(1, linkId)
        Using.resource(statement.executeQuery()) { resultSet =>
          resultSet.next()
          resultSet.getInt("id")
        }
      }
    }

  private def loadPriceChanges(linkId: Int, afterId: Int): List[PriceChange] =
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement(
        "SELECT price_economy, price_business, price_first, price_economy_delta, price_business_delta, price_first_delta " +
          "FROM link_change_history WHERE link = ? AND id > ? ORDER BY id")) { statement =>
        statement.setInt(1, linkId)
        statement.setInt(2, afterId)
        Using.resource(statement.executeQuery()) { resultSet =>
          val changes = List.newBuilder[PriceChange]
          while (resultSet.next()) {
            changes += PriceChange(
              economy = resultSet.getInt("price_economy"),
              business = resultSet.getInt("price_business"),
              first = resultSet.getInt("price_first"),
              economyDelta = resultSet.getInt("price_economy_delta"),
              businessDelta = resultSet.getInt("price_business_delta"),
              firstDelta = resultSet.getInt("price_first_delta"))
          }
          changes.result()
        }
      }
    }

  private def restorePrice(link: Link): Unit =
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement(
        "UPDATE link SET price_economy = ?, price_business = ?, price_first = ? WHERE id = ?")) { statement =>
        statement.setInt(1, link.price.economyVal)
        statement.setInt(2, link.price.businessVal)
        statement.setInt(3, link.price.firstVal)
        statement.setInt(4, link.id)
        statement.executeUpdate()
      }
    }

  private def deleteHistoryAfter(linkId: Int, afterId: Int): Unit =
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("DELETE FROM link_change_history WHERE link = ? AND id > ?")) { statement =>
        statement.setInt(1, linkId)
        statement.setInt(2, afterId)
        statement.executeUpdate()
      }
    }
}
