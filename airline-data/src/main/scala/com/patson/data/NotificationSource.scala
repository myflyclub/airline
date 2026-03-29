package com.patson.data

import com.patson.data.Constants._
import com.patson.model.{Notification, NotificationCategory}

import java.sql.{ResultSet, Statement}

object NotificationSource {
  val RETENTION_LIMIT = 100

  def insertNotification(n: Notification): Unit = insertNotificationReturning(n)

  def insertNotificationReturning(n: Notification): Notification = {
    doInsert(n)
    purgeOldNotifications(n.airlineId)
    n
  }

  // Insert many notifications without a per-row purge; purges once per affected airline at the end.
  def insertNotificationsBulk(notifications: List[Notification]): Unit = {
    notifications.foreach(doInsert)
    notifications.map(_.airlineId).distinct.foreach(purgeOldNotifications)
  }

  private def doInsert(n: Notification): Unit = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        s"INSERT INTO $NOTIFICATION_TABLE (airline, category, message, cycle, is_read, target_id, expiry_cycle) VALUES (?,?,?,?,?,?,?)",
        Statement.RETURN_GENERATED_KEYS
      )
      try {
        statement.setInt(1, n.airlineId)
        statement.setString(2, n.category.toString)
        statement.setString(3, n.message)
        statement.setInt(4, n.cycle)
        statement.setInt(5, if (n.isRead) 1 else 0)
        n.targetId match {
          case Some(tid) => statement.setString(6, tid)
          case None      => statement.setNull(6, java.sql.Types.VARCHAR)
        }
        n.expiryCycle match {
          case Some(ec) => statement.setInt(7, ec)
          case None     => statement.setNull(7, java.sql.Types.INTEGER)
        }
        statement.execute()
        val generatedKeys = statement.getGeneratedKeys
        if (generatedKeys.next()) {
          n.id = generatedKeys.getInt(1)
        }
      } finally {
        statement.close()
      }
    } finally {
      connection.close()
    }
  }

  private def purgeOldNotifications(airlineId: Int): Unit = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        s"DELETE FROM $NOTIFICATION_TABLE WHERE airline = ? AND category != '${NotificationCategory.LINK_CANCELLATION}' AND id NOT IN (SELECT id FROM (SELECT id FROM $NOTIFICATION_TABLE WHERE airline = ? AND category != '${NotificationCategory.LINK_CANCELLATION}' ORDER BY id DESC LIMIT $RETENTION_LIMIT) AS t)"
      )
      try {
        statement.setInt(1, airlineId)
        statement.setInt(2, airlineId)
        statement.executeUpdate()
      } finally {
        statement.close()
      }
    } finally {
      connection.close()
    }
  }

  private def rowToNotification(rs: ResultSet): Notification = Notification(
    airlineId   = rs.getInt("airline"),
    category    = NotificationCategory.withName(rs.getString("category")),
    message     = rs.getString("message"),
    cycle       = rs.getInt("cycle"),
    isRead      = rs.getInt("is_read") == 1,
    id          = rs.getInt("id"),
    targetId    = Option(rs.getString("target_id")),
    expiryCycle = Option(rs.getInt("expiry_cycle")).filter(_ => !rs.wasNull())
  )

  def loadNotificationsByAirline(airlineId: Int, limit: Int = 50): List[Notification] = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        s"SELECT * FROM $NOTIFICATION_TABLE WHERE airline = ? ORDER BY id DESC LIMIT ?"
      )
      try {
        statement.setInt(1, airlineId)
        statement.setInt(2, limit)
        val rs = statement.executeQuery()
        val result = scala.collection.mutable.ListBuffer[Notification]()
        while (rs.next()) result += rowToNotification(rs)
        result.toList
      } finally {
        statement.close()
      }
    } finally {
      connection.close()
    }
  }

  def loadAllByCategory(category: NotificationCategory.Value): List[Notification] = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        s"SELECT * FROM $NOTIFICATION_TABLE WHERE category = ?"
      )
      try {
        statement.setString(1, category.toString)
        val rs = statement.executeQuery()
        val result = scala.collection.mutable.ListBuffer[Notification]()
        while (rs.next()) result += rowToNotification(rs)
        result.toList
      } finally {
        statement.close()
      }
    } finally {
      connection.close()
    }
  }

  def deleteNotifications(notifications: List[Notification]): Unit = {
    if (notifications.isEmpty) return
    val connection = Meta.getConnection()
    try {
      val placeholders = notifications.map(_ => "?").mkString(", ")
      val statement = connection.prepareStatement(
        s"DELETE FROM $NOTIFICATION_TABLE WHERE id IN ($placeholders)"
      )
      try {
        notifications.zipWithIndex.foreach { case (n, i) => statement.setInt(i + 1, n.id) }
        statement.executeUpdate()
      } finally {
        statement.close()
      }
    } finally {
      connection.close()
    }
  }

  def loadUnreadByCategory(airlineId: Int, category: NotificationCategory.Value): List[Notification] =
    loadUnreadByCategories(airlineId, Seq(category))

  def loadUnreadByCategories(airlineId: Int, categories: Seq[NotificationCategory.Value]): List[Notification] = {
    if (categories.isEmpty) return List.empty
    val placeholders = categories.map(_ => "?").mkString(", ")
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        s"SELECT * FROM $NOTIFICATION_TABLE WHERE airline = ? AND is_read = 0 AND category IN ($placeholders)"
      )
      try {
        statement.setInt(1, airlineId)
        categories.zipWithIndex.foreach { case (cat, i) => statement.setString(i + 2, cat.toString) }
        val rs = statement.executeQuery()
        val result = scala.collection.mutable.ListBuffer[Notification]()
        while (rs.next()) result += rowToNotification(rs)
        result.toList
      } finally {
        statement.close()
      }
    } finally {
      connection.close()
    }
  }

  def countUnreadByAirline(airlineId: Int): Int = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        s"SELECT COUNT(*) FROM $NOTIFICATION_TABLE WHERE airline = ? AND is_read = 0"
      )
      try {
        statement.setInt(1, airlineId)
        val rs = statement.executeQuery()
        if (rs.next()) rs.getInt(1) else 0
      } finally {
        statement.close()
      }
    } finally {
      connection.close()
    }
  }

  def markAllRead(airlineId: Int): Unit = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        s"UPDATE $NOTIFICATION_TABLE SET is_read = 1 WHERE airline = ?"
      )
      try {
        statement.setInt(1, airlineId)
        statement.executeUpdate()
      } finally {
        statement.close()
      }
    } finally {
      connection.close()
    }
  }

  def markSingleRead(airlineId: Int, notifId: Int): Unit = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        s"UPDATE $NOTIFICATION_TABLE SET is_read = 1 WHERE id = ? AND airline = ?"
      )
      try {
        statement.setInt(1, notifId)
        statement.setInt(2, airlineId)
        statement.executeUpdate()
      } finally {
        statement.close()
      }
    } finally {
      connection.close()
    }
  }

  def markCategoryRead(airlineId: Int, category: NotificationCategory.Value): Unit = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        s"UPDATE $NOTIFICATION_TABLE SET is_read = 1 WHERE airline = ? AND category = ?"
      )
      try {
        statement.setInt(1, airlineId)
        statement.setString(2, category.toString)
        statement.executeUpdate()
      } finally {
        statement.close()
      }
    } finally {
      connection.close()
    }
  }

  def deleteAllRead(airlineId: Int): Unit = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        s"DELETE FROM $NOTIFICATION_TABLE WHERE airline = ? AND is_read = 1"
      )
      try {
        statement.setInt(1, airlineId)
        statement.executeUpdate()
      } finally {
        statement.close()
      }
    } finally {
      connection.close()
    }
  }

  def deleteNotification(airlineId: Int, notifId: Int): Unit = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        s"DELETE FROM $NOTIFICATION_TABLE WHERE id = ? AND airline = ?"
      )
      try {
        statement.setInt(1, notifId)
        statement.setInt(2, airlineId)
        statement.executeUpdate()
      } finally {
        statement.close()
      }
    } finally {
      connection.close()
    }
  }
}
