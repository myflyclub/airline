
package com.patson.patch

import com.mchange.v2.c3p0.ComboPooledDataSource
import com.patson.data.Constants.{DATABASE_CONNECTION, DATABASE_PASSWORD, DATABASE_USER, DB_DRIVER, AIRLINE_INFO_TABLE}
import com.patson.data.Meta
import com.patson.init.actorSystem
import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
  * patcher to create/recreate the prestige table
  */
object PrestigePatcher extends App {
  mainFlow()

  def mainFlow() = {
    patchSchema()

    Await.result(actorSystem.terminate(), Duration.Inf)
  }


  def patchSchema() = {
    Class.forName(DB_DRIVER)
    val dataSource = new ComboPooledDataSource()
    dataSource.setUser(DATABASE_USER)
    dataSource.setPassword(DATABASE_PASSWORD)
    dataSource.setJdbcUrl(DATABASE_CONNECTION)
    dataSource.setMaxPoolSize(100)
    val connection = dataSource.getConnection
    Meta.createPrestige(connection)

    val preparedStatement = connection.prepareStatement("ALTER TABLE " + Constants.AIRLINE_INFO_TABLE + " ADD COLUMN prestige_points INTEGER UNSIGNED DEFAULT 0")
    preparedStatement.execute()
    preparedStatement.close()

    connection.close
  }
}
