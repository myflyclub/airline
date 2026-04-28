package com.patson.data

import java.sql.Connection
import java.sql.PreparedStatement
import com.patson.data.Constants._
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

object Meta {
  private val hikariConfig = new HikariConfig()
  hikariConfig.setJdbcUrl(DATABASE_CONNECTION)
  hikariConfig.setUsername(DATABASE_USER)
  hikariConfig.setPassword(DATABASE_PASSWORD)
  hikariConfig.setMaximumPoolSize(
    if (Constants.configFactory.hasPath("hikari.maxPoolSize"))
      Constants.configFactory.getInt("hikari.maxPoolSize")
    else 20
  )
  hikariConfig.setIdleTimeout(300_000)
  hikariConfig.setMaxLifetime(3_600_000)
  hikariConfig.setConnectionTimeout(
    if (Constants.configFactory.hasPath("hikari.connectionTimeout"))
      Constants.configFactory.getLong("hikari.connectionTimeout")
    else 10_000
  )
  hikariConfig.setLeakDetectionThreshold(30000)

  val dataSource = new HikariDataSource(hikariConfig)

  // Attempt to close the pool gracefully on JVM exit
  sys.addShutdownHook {
    if (!dataSource.isClosed) {
        dataSource.close()
    }
  }

  def getConnection(): java.sql.Connection = {
    dataSource.getConnection()
  }

  def createSchema() {
    val connection = getConnection()
    var statement: PreparedStatement = null

    statement = connection.prepareStatement("SET FOREIGN_KEY_CHECKS = 0")
    statement.execute()
    statement.close()
    
    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + CYCLE_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + CYCLE_PHASE_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + CITY_TABLE)
    statement.execute()
    statement.close()
    
    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + COUNTRY_TABLE)
    statement.execute()
    statement.close()
    
    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + COUNTRY_AIRLINE_RELATIONSHIP_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + AIRLINE_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + AIRLINE_INFO_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + AIRLINE_BASE_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + AIRPORT_CITY_SHARE_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + AIRPORT_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + TRANSIT_CONSUMPTION_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + LINK_CONSUMPTION_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + LINK_ASSIGNMENT_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + LINK_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + AIRPLANE_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + AIRPLANE_MODEL_META_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + AIRPLANE_MODEL_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + USER_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + USER_SECRET_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + USER_AIRLINE_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + CYCLE_TABLE + "(cycle INTEGER PRIMARY KEY)")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + CYCLE_PHASE_TABLE + "(cycle_phase_length INTEGER PRIMARY KEY, cycle_phase_index INTEGER)")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + CITY_TABLE + "(id INTEGER PRIMARY KEY AUTO_INCREMENT, name VARCHAR(256) CHARACTER SET 'utf8', latitude DOUBLE, longitude DOUBLE, country_code VARCHAR(256) CHARACTER SET 'utf8', population INTEGER, income INTEGER)")
    statement.execute()
    statement.close()
    
    statement = connection.prepareStatement("CREATE TABLE " + COUNTRY_TABLE + "(code CHAR(2) PRIMARY KEY, name VARCHAR(256) CHARACTER SET 'utf8', airport_population INTEGER, income INTEGER, openness INTEGER, gini DOUBLE)")
    statement.execute()
    statement.close()
    
    statement = connection.prepareStatement("CREATE TABLE " + AIRPORT_TABLE + "( id INTEGER PRIMARY KEY AUTO_INCREMENT, iata VARCHAR(256), icao VARCHAR(256), name VARCHAR(256) CHARACTER SET 'utf8', latitude DOUBLE, longitude DOUBLE, country_code VARCHAR(256), city VARCHAR(256) CHARACTER SET 'utf8', zone VARCHAR(256), airport_size INTEGER, income BIGINT, population BIGINT, pop_middle_income BIGINT, pop_elite BIGINT, runway_length SMALLINT)")
    statement.execute()
    statement.close()
    
    statement = connection.prepareStatement("CREATE INDEX " + AIRPORT_INDEX_1 + " ON " + AIRPORT_TABLE + "(country_code)")
    statement.execute()
    statement.close()
    

    statement = connection.prepareStatement("CREATE TABLE " + AIRLINE_TABLE + "( id INTEGER PRIMARY KEY AUTO_INCREMENT, name VARCHAR(256), airline_type TINYINT(1))")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE INDEX " + COUNTRY_AIRLINE_RELATIONSHIP_INDEX_1 + " ON " + AIRLINE_TABLE + "(id)")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE INDEX " + COUNTRY_AIRLINE_RELATIONSHIP_INDEX_2 + " ON " + COUNTRY_TABLE + "(code)")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + AIRLINE_INFO_TABLE + "(" +
      "airline INTEGER PRIMARY KEY, " +
      "balance LONG," +
      "action_point DECIMAL(5,1) DEFAULT 0," +
      "service_quality DECIMAL(5,2)," +
      "target_service_quality INTEGER," +
      "shares_outstanding INTEGER DEFAULT 0," +
      "stock_price DOUBLE DEFAULT 0," +
      "reputation DECIMAL(6,2)," +
      "country_code CHAR(2)," +
      "initialized TINYINT," +
      "minimum_renewal_balance INTEGER DEFAULT 0," +
      "prestige_points INTEGER UNSIGNED," +
      "dividends BIGINT NOT NULL DEFAULT 0," +
      "FOREIGN KEY(airline) REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")

    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + AIRLINE_BASE_TABLE + "(" +
      "airport INTEGER, " +
      "airline INTEGER, " +
      "scale INTEGER," +
      "founded_cycle INTEGER," +
      "headquarter INTEGER," +
      "country CHAR(2) NOT NULL, " +
      "PRIMARY KEY (airport, airline)," +
      "FOREIGN KEY(airport) REFERENCES " + AIRPORT_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE," +
      "FOREIGN KEY(airline) REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE," +
      "FOREIGN KEY(country) REFERENCES " + COUNTRY_TABLE + "(code) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE INDEX " + AIRLINE_BASE_INDEX_1 + " ON " + AIRPORT_TABLE + "(id)")
    statement.execute()
    statement.close()
    statement = connection.prepareStatement("CREATE INDEX " + AIRLINE_BASE_INDEX_2 + " ON " + AIRLINE_TABLE + "(id)")
    statement.execute()
    statement.close()
      statement = connection.prepareStatement("CREATE INDEX " + AIRLINE_BASE_INDEX_3 + " ON " + COUNTRY_TABLE + "(code)")
      statement.execute()
      statement.close()

    createLinkStats(connection)
    createTransitConsumption(connection)
    createPassengerHistoryTables(connection)
    createAirlineLedger(connection)
    createIncome(connection)
    createLoan(connection)
    createCountryMarketShare(connection)
    createAirlineStats(connection)
    createCountryAirlineTitle(connection)
    createAirlineMeta(connection)
    createAirlineNameHistory(connection)
    createAirplaneRenewal(connection)
    createAirplaneConfiguration(connection)
    createAlliance(connection)
    createLounge(connection)
    createLoungeConsumption(connection)
    createOil(connection)
    createLoanInterestRate(connection)
    createResetUser(connection)
    createEvent(connection)
    createSantaClaus(connection)
    createAirportAirlineBonus(connection)
    createLinkChangeHistory(connection)
    createGoogleResource(connection)
    createDelegate(connection)
    createAirportRunway(connection)
    createChatMessage(connection)
    createLoyalist(connection)
    createCampaign(connection)
    createLinkNegotiation(connection)
    createTutorial(connection)
    createAirportChampion(connection)
    createAirlineBaseSpecialization(connection)
    createAirlineBaseSpecializationLastUpdate(connection)
    createReputationBreakdown(connection)
    createIp(connection)
    createAdminLog(connection)
    createUserUuid(connection)
    createAirlineModifier(connection)
    createAirlineModifierProperty(connection)
    createAirlineDividendsCoolDown(connection)
    createUserModifier(connection)
    createAllianceLabelColor(connection)
    createAllianceStats(connection)
    createDestinations(connection)
    createNotes(connection)
    createAirportStatistics(connection)
    createWorldStatistics(connection)
    createRankingLeaderboard(connection)
    createPrestige(connection)
    createNotification(connection)

    statement = connection.prepareStatement("CREATE TABLE " + AIRPORT_CITY_SHARE_TABLE + "(" +
      "airport INTEGER," +
      "city INTEGER," +
      "share DOUBLE," +
      "PRIMARY KEY (airport, city)," +
      "FOREIGN KEY(airport) REFERENCES " + AIRPORT_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE," +
      "FOREIGN KEY(city) REFERENCES " + CITY_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE INDEX " + AIRPORT_CITY_SHARE_INDEX_1 + " ON " + AIRPORT_CITY_SHARE_TABLE + "(airport)")
    statement.execute()
    statement.close()
    statement = connection.prepareStatement("CREATE INDEX " + AIRPORT_CITY_SHARE_INDEX_2 + " ON " + AIRPORT_CITY_SHARE_TABLE + "(city)")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + LINK_TABLE + "(" +
      "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
      "from_airport INTEGER, " +
      "to_airport INTEGER, " +
      "airline INTEGER, " +
      "price_economy INTEGER, " +
      "price_business INTEGER, " +
      "price_first INTEGER, " +
      "distance DOUBLE, " +
      "capacity_economy INTEGER, " +
      "capacity_business INTEGER, " +
      "capacity_first INTEGER, " +
      "quality INTEGER, " +
      "duration INTEGER, " +
      "frequency INTEGER," +
      "flight_number INTEGER," +
      "airplane_model SMALLINT," +
      "from_country CHAR(2)," +
      "to_country CHAR(2)," +
      "transport_type SMALLINT," +
      "last_update DATETIME DEFAULT CURRENT_TIMESTAMP," +
      "FOREIGN KEY(from_airport) REFERENCES " + AIRPORT_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE," +
      "FOREIGN KEY(to_airport) REFERENCES " + AIRPORT_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")

    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE UNIQUE INDEX " + LINK_INDEX_1 + " ON " + LINK_TABLE + "(from_airport, to_airport, airline)")
    statement.execute()
    statement.close()
    statement = connection.prepareStatement("CREATE INDEX " + LINK_INDEX_2 + " ON " + LINK_TABLE + "(from_airport)")
    statement.execute()
    statement.close()
    statement = connection.prepareStatement("CREATE INDEX " + LINK_INDEX_3 + " ON " + LINK_TABLE + "(to_airport)")
    statement.execute()
    statement.close()
    statement = connection.prepareStatement("CREATE INDEX " + LINK_INDEX_4 + " ON " + LINK_TABLE + "(airline)")
    statement.execute()
    statement.close()
    statement = connection.prepareStatement("CREATE INDEX " + LINK_INDEX_5 + " ON " + LINK_TABLE + "(from_country)")
    statement.execute()
    statement.close()
    statement = connection.prepareStatement("CREATE INDEX " + LINK_INDEX_6 + " ON " + LINK_TABLE + "(to_country)")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + LINK_CONSUMPTION_TABLE + "(" +
      "link INTEGER, " +
      "price_economy INTEGER, " +
      "price_business INTEGER, " +
      "price_first INTEGER, " +
      "capacity_economy INTEGER, " +
      "capacity_business INTEGER, " +
      "capacity_first INTEGER, " +
      "sold_seats_economy INTEGER, " +
      "sold_seats_business INTEGER, " +
      "sold_seats_first INTEGER, " +
      "quality SMALLINT, " +
      "fuel_cost INTEGER, " +
      "fuel_tax INTEGER, " +
      "crew_cost INTEGER, " +
      "airport_fees INTEGER, " +
      "inflight_cost INTEGER, " +
      "delay_compensation INTEGER, " +
      "maintenance_cost INTEGER, " +
      "lounge_cost INTEGER, " +
      "depreciation INTEGER, " +
      "revenue INTEGER, " +
      "profit INTEGER, " +
      "minor_delay_count SMALLINT, " +
      "major_delay_count SMALLINT, " +
      "cancellation_count SMALLINT, " +
      "from_airport INTEGER, " +
      "to_airport INTEGER, " +
      "airline INTEGER, " +
      "distance INTEGER, " +
      "frequency SMALLINT, " +
      "duration SMALLINT, " +
      "flight_number SMALLINT, " +
      "airplane_model SMALLINT, " +
      "raw_quality SMALLINT, " +
      "satisfaction DECIMAL(5,4), " +
      "cycle INTEGER, " +
      "PRIMARY KEY (cycle, link))")

    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE INDEX " + LINK_CONSUMPTION_INDEX_2 + " ON " + LINK_CONSUMPTION_TABLE + "(airline, cycle)")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + LINK_ASSIGNMENT_TABLE + "(" +
      "link INTEGER, " +
      "airplane INTEGER, " +
      "frequency INTEGER, " +
      "flight_minutes INTEGER, " +
      "PRIMARY KEY (link, airplane)," +
      "FOREIGN KEY(link) REFERENCES " + LINK_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE," +
      "FOREIGN KEY(airplane) REFERENCES " + AIRPLANE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE INDEX " + LINK_ASSIGNMENT_INDEX_1 + " ON " + LINK_ASSIGNMENT_TABLE + "(link)")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE INDEX " + LINK_ASSIGNMENT_INDEX_2 + " ON " + LINK_ASSIGNMENT_TABLE + "(airplane)")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + AIRPLANE_MODEL_TABLE + "(" +
      "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
      "name VARCHAR(256), " +
      "family VARCHAR(256), " +
      "capacity INTEGER, " +
      "quality INTEGER, " +
      "ascent_burn DOUBLE, " +
      "cruise_burn DOUBLE, " +
      "speed INTEGER, " +
      "fly_range INTEGER, " +
      "price INTEGER, " +
      "lifespan INTEGER, " +
      "construction_time INTEGER, " +
      "country_code CHAR(2), " +
      "manufacturer VARCHAR(256)," +
      "image_url VARCHAR(256)," +
      "runway_requirement BIGINT)")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + AIRPLANE_TABLE + "(" +
      "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
      "model INTEGER, " +
      "owner INTEGER, " +
      "constructed_cycle INTEGER, " +
      "purchased_cycle INTEGER, " +
      "airplane_condition DECIMAL(7,4), " +
      "purchase_price INTEGER NOT NULL DEFAULT 0," +
      "is_sold TINYINT(1)," +
      "home INTEGER," +
      "version INTEGER DEFAULT 0," +
      "FOREIGN KEY(model) REFERENCES " + AIRPLANE_MODEL_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE," +
      "FOREIGN KEY(owner) REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")

    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE INDEX " + AIRPLANE_INDEX_1 + " ON " + AIRPLANE_TABLE + "(owner)")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE INDEX " + AIRPLANE_INDEX_2 + " ON " + AIRPLANE_TABLE + "(model)")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + AIRPLANE_MODEL_META_TABLE + "(" +
      "airplane_model INT PRIMARY KEY, " +
      "launch_customer VARCHAR(256) NOT NULL, " +
      "FOREIGN KEY(airplane_model) REFERENCES " + AIRPLANE_MODEL_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + USER_TABLE + "(" +
      "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
      "user_name VARCHAR(100) UNIQUE, " +
      "email VARCHAR(256) NOT NULL, " +
      "status  VARCHAR(256) NOT NULL, " +
      "admin_status VARCHAR(256), " +
      "creation_time DATETIME DEFAULT CURRENT_TIMESTAMP, " +
      "level INTEGER NOT NULL DEFAULT 0, " +
      "last_active DATETIME DEFAULT CURRENT_TIMESTAMP)")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + USER_SECRET_TABLE + "(" +
      "user_name VARCHAR(100) PRIMARY KEY," +
      "digest VARCHAR(32) NOT NULL, " +
      "salt VARCHAR(32) NOT NULL," +
      "FOREIGN KEY(user_name) REFERENCES " + USER_TABLE + "(user_name) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + USER_AIRLINE_TABLE + "(" +
      "airline INTEGER PRIMARY KEY," +
      "user_name VARCHAR(100)," +
      "FOREIGN KEY(airline) REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE," +
      "FOREIGN KEY(user_name) REFERENCES " + USER_TABLE + "(user_name) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()
  }

  def createPassengerHistoryTables(connection: Connection): Unit = {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + PASSENGER_ROUTE_HISTORY_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + PASSENGER_ROUTE_HISTORY_TABLE + "(" +
      "route_id INTEGER PRIMARY KEY," +
      "passenger_count INTEGER," +
      "home_country CHAR(2) NOT NULL DEFAULT ''," +
      "home_airport INT(11)," +
      "destination_airport INT(11)," +
      "passenger_type TINYINT," +
      "preference_type TINYINT," +
      "preferred_link_class CHAR(1)," +
      "route_cost INT" +
      ")")

    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE INDEX idx_route_airports ON " + PASSENGER_ROUTE_HISTORY_TABLE + "(home_airport, destination_airport)")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE INDEX idx_home_pass_count ON " + PASSENGER_ROUTE_HISTORY_TABLE + "(home_airport, passenger_count DESC)")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + PASSENGER_LINK_HISTORY_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + PASSENGER_LINK_HISTORY_TABLE + "(" +
      "route_id INTEGER NOT NULL," +
      "link INTEGER NOT NULL," +
      "link_class CHAR(1)," +
      "inverted TINYINT," +
      "cost DOUBLE," +
      "satisfaction DOUBLE," +
      "PRIMARY KEY (route_id, link)" +
      ")")

    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE INDEX idx_link_history ON " + PASSENGER_LINK_HISTORY_TABLE + "(link)")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + PASSENGER_MISSED_DEMAND_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + PASSENGER_MISSED_DEMAND_TABLE + "(" +
      "from_airport INT NOT NULL, " +
      "to_airport INT NOT NULL, " +
      "passenger_type TINYINT NOT NULL, " +
      "preference_type TINYINT NOT NULL, " +
      "preferred_link_class CHAR(1) NOT NULL, " +
      "passenger_count INT NOT NULL, " +
      "PRIMARY KEY (from_airport, to_airport, passenger_type, preference_type, preferred_link_class)" +
      ")"
    )

    statement.execute()
    statement.close()

    val idxStmt = connection.prepareStatement(
      "CREATE INDEX idx_missed_from ON " + PASSENGER_MISSED_DEMAND_TABLE + "(from_airport)"
    )
    idxStmt.execute()
    idxStmt.close()
  }

  def createLinkStats(connection: Connection): Unit = {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + LINK_STATISTICS_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + LINK_STATISTICS_TABLE + "(" +
      "from_airport INTEGER, " +
      "to_airport INTEGER, " +
      "is_departure INTEGER, " +
      "is_destination INTEGER, " +
      "passenger_count INTEGER, " +
      "premium_count INTEGER, " +
      "airline INTEGER, " +
      "cycle INTEGER)")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE INDEX " + LINK_STATISTICS_INDEX_1 + " ON " + LINK_STATISTICS_TABLE + "(from_airport)")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE INDEX " + LINK_STATISTICS_INDEX_2 + " ON " + LINK_STATISTICS_TABLE + "(to_airport)")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE INDEX " + LINK_STATISTICS_INDEX_3 + " ON " + LINK_STATISTICS_TABLE + "(airline)")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE INDEX " + LINK_STATISTICS_INDEX_4 + " ON " + LINK_STATISTICS_TABLE + "(cycle)")
    statement.execute()
    statement.close()
  }

  def createTransitConsumption(connection: Connection): Unit = {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + TRANSIT_CONSUMPTION_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + TRANSIT_CONSUMPTION_TABLE + "(" +
      "link INTEGER, " +
      "sold_seats_economy INTEGER, " +
      "cycle INTEGER, " +
      "PRIMARY KEY (cycle, link))")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE INDEX transit_consumption_index_1 ON " + TRANSIT_CONSUMPTION_TABLE + "(link)")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE INDEX transit_consumption_index_2 ON " + TRANSIT_CONSUMPTION_TABLE + "(cycle DESC)")
    statement.execute()
    statement.close()
  }

  def createAirlineLedger(connection : Connection) {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + AIRLINE_LEDGER_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + AIRLINE_LEDGER_TABLE + "(" +
      "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
      "airline INTEGER, " +
      "cycle INTEGER, " +
      "entry_type INTEGER, " +
      "amount BIGINT(20)," +
      "description VARCHAR(200) NULL," +
      "FOREIGN KEY(airline) REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE" +
      ")")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE INDEX " + AIRLINE_LEDGER_INDEX_1 + " ON " + AIRLINE_LEDGER_TABLE + "(airline)")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE INDEX " + AIRLINE_LEDGER_INDEX_2 + " ON " + AIRLINE_LEDGER_TABLE + "(cycle)")
    statement.execute()
    statement.close()
  }

  def createAirlineMeta(connection : Connection) {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + AIRLINE_META_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + AIRLINE_META_TABLE + "(" +
      "airline INTEGER, " +
      "slogan VARCHAR(256), " +
      "founded_cycle INTEGER, " +
      "airline_code CHAR(2) DEFAULT NULL, " +
      "color CHAR(7) DEFAULT NULL, " +
      "skip_tutorial TINYINT NOT NULL DEFAULT 0, " +
      "notified_level INT NOT NULL DEFAULT -1, " +
      "notified_loyalist_level INT NOT NULL DEFAULT 0, " +
      "PRIMARY KEY (airline)," +
      "FOREIGN KEY(airline) REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()
  }

  def createAirlineNameHistory(connection : Connection) {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + AIRLINE_NAME_HISTORY_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + AIRLINE_NAME_HISTORY_TABLE + "(" +
      "airline INTEGER, " +
      "name VARCHAR(256), " +
      "update_timestamp DATETIME DEFAULT CURRENT_TIMESTAMP," +
      "PRIMARY KEY (airline,name,update_timestamp)," +
      "FOREIGN KEY(airline) REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()
  }

  def createIncome(connection : Connection) {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + BALANCE_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + BALANCE_TABLE + "(" +
      "airline INTEGER, " +
      "income LONG, " +
      "normalized_operating_income LONG, " +
      "cash_on_hand LONG," +
      "total_value LONG," +
      "stock_price DOUBLE," +
      "period INTEGER," +
      "cycle INTEGER," +
      "PRIMARY KEY (airline, period, cycle)" +
      ")")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + BALANCE_DETAILS_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + BALANCE_DETAILS_TABLE + "(" +
      "airline INTEGER, " +
      "ticket_revenue LONG, " +
      "lounge_revenue LONG, " +
      "staff LONG," +
      "staff_overtime LONG," +
      "flight_crew LONG," +
      "fuel LONG," +
      "fuel_tax LONG," +
      "fuel_normalized LONG," +
      "deprecation LONG," +
      "airport_rentals LONG," +
      "inflight_service LONG," +
      "delay LONG," +
      "maintenance LONG," +
      "lounge LONG, " +
      "advertising LONG," +
      "loan_interest LONG," +
      "dividends BIGINT NOT NULL DEFAULT 0," +
      "period INTEGER," +
      "cycle INTEGER," +
      "PRIMARY KEY (airline, period, cycle)" +
      ")")
    statement.execute()
    statement.close()
  }

  def createLoan(connection : Connection) {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + LOAN_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + LOAN_TABLE + "(" +
      "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
      "airline INTEGER, " +
      "principal LONG, " +
      "annual_rate DECIMAL(7,6), " +
      "creation_cycle INTEGER," +
      "last_payment_cycle INTEGER," +
      "term LONG," +
      "FOREIGN KEY(airline) REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()
  }

  def createCountryMarketShare(connection : Connection) {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + COUNTRY_MARKET_SHARE_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + COUNTRY_MARKET_SHARE_TABLE + "(country CHAR(2), airline INTEGER, passenger_count BIGINT(20)," +
                                            "PRIMARY KEY (country, airline)," +
                                            "FOREIGN KEY(country) REFERENCES " + COUNTRY_TABLE + "(code) ON DELETE CASCADE ON UPDATE CASCADE," +
                                            "FOREIGN KEY(airline) REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE)")
    statement.execute()
    statement.close()
  }

  def createCountryAirlineTitle(connection : Connection) {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + COUNTRY_AIRLINE_TITLE_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + COUNTRY_AIRLINE_TITLE_TABLE + "(country CHAR(2), airline INT(11), title TINYINT," +
      "PRIMARY KEY (country, airline)," +
      "FOREIGN KEY(country) REFERENCES " + COUNTRY_TABLE + "(code) ON DELETE CASCADE ON UPDATE CASCADE," +
      "FOREIGN KEY(airline) REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE)")
    statement.execute()
    statement.close()
  }

  def createAirplaneRenewal(connection : Connection) {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + AIRPLANE_RENEWAL_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + AIRPLANE_RENEWAL_TABLE + "(" +
      "airline INTEGER, " +
      "threshold INTEGER, " +
      "PRIMARY KEY (airline)," +
      "FOREIGN KEY(airline) REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()
  }

  def createAirplaneConfiguration(connection : Connection) {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + AIRPLANE_CONFIGURATION_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + AIRPLANE_CONFIGURATION_TEMPLATE_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + AIRPLANE_CONFIGURATION_TEMPLATE_TABLE + "(" +
      "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
      "airline INTEGER, " +
      "model INTEGER, " +
      "economy INTEGER, " +
      "business INTEGER, " +
      "first INTEGER, " +
      "is_default TINYINT(1), " +
      "FOREIGN KEY(model) REFERENCES " + AIRPLANE_MODEL_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE, " +
      "FOREIGN KEY(airline) REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE INDEX " + AIRPLANE_CONFIGURATION_TEMPLATE_INDEX_1 + " ON " + AIRPLANE_CONFIGURATION_TEMPLATE_TABLE + "(airline)")
    statement.execute()
    statement.close()
    statement = connection.prepareStatement("CREATE INDEX " + AIRPLANE_CONFIGURATION_TEMPLATE_INDEX_2 + " ON " + AIRPLANE_CONFIGURATION_TEMPLATE_TABLE + "(model)")
    statement.execute()
    statement.close()



    statement = connection.prepareStatement("CREATE TABLE " + AIRPLANE_CONFIGURATION_TABLE + "(" +
      "airplane INTEGER, " +
      "configuration INTEGER, " +
      "PRIMARY KEY (airplane)," +
      "FOREIGN KEY(configuration) REFERENCES " + AIRPLANE_CONFIGURATION_TEMPLATE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE, " +
      "FOREIGN KEY(airplane) REFERENCES " + AIRPLANE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()
  }

  def createAlliance(connection : Connection) {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + ALLIANCE_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + ALLIANCE_TABLE + "(" +
      "id INTEGER PRIMARY KEY AUTO_INCREMENT," +
      "name VARCHAR(256), " +
      "creation_cycle INTEGER" +
      ")")
    statement.execute()

    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + ALLIANCE_MEMBER_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + ALLIANCE_MEMBER_TABLE + "(" +
      "alliance INTEGER," +
      "airline INTEGER, " +
      "role VARCHAR(256), " +
      "joined_cycle INTEGER, " +
      "PRIMARY KEY (alliance, airline)," +
      "FOREIGN KEY(airline) REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE, " +
      "FOREIGN KEY(alliance) REFERENCES " + ALLIANCE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()

    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + ALLIANCE_HISTORY_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + ALLIANCE_HISTORY_TABLE + "(" +
      "id INTEGER PRIMARY KEY AUTO_INCREMENT," +
      "cycle INTEGER," +
      "airline INTEGER, " +
      "alliance_name VARCHAR(256)," +
      "event VARCHAR(256), " +
      "FOREIGN KEY(airline) REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()

    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + ALLIANCE_META_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + ALLIANCE_META_TABLE + "(" +
      "alliance INTEGER," +
      "alliance_slogan VARCHAR(512)," +
      "PRIMARY KEY (alliance)," +
      "FOREIGN KEY(alliance) REFERENCES " + ALLIANCE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()



    statement.close()
  }

  def createLounge(connection : Connection) {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + LOUNGE_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + LOUNGE_TABLE + "(" +
      "airport INTEGER, " +
      "airline INTEGER, " +
      "name VARCHAR(256), " +
      "level INTEGER," +
      "status VARCHAR(16)," +
      "founded_cycle INTEGER," +
      "PRIMARY KEY (airport, airline), " +
      "FOREIGN KEY(airport) REFERENCES " + AIRPORT_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE," +
      "FOREIGN KEY(airline) REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()
  }

  def createLoungeConsumption(connection : Connection) {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + LOUNGE_CONSUMPTION_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + LOUNGE_CONSUMPTION_TABLE + "(" +
      "airport INTEGER, " +
      "airline INTEGER, " +
      "self_visitors INTEGER," +
      "alliance_visitors INTEGER," +
      "cycle INTEGER," +
      "PRIMARY KEY (airport, airline), " +
      "FOREIGN KEY(airport) REFERENCES " + AIRPORT_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE," +
      "FOREIGN KEY(airline) REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()
  }

  def createEvent(connection : Connection) {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + OLYMPIC_CANDIDATE_TABLE)
    statement.execute()
    statement.close()
    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + OLYMPIC_AFFECTED_AIRPORT_TABLE)
    statement.execute()
    statement.close()
    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + OLYMPIC_AIRLINE_VOTE_TABLE)
    statement.execute()
    statement.close()
    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + OLYMPIC_VOTE_ROUND_TABLE)
    statement.execute()
    statement.close()
    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + OLYMPIC_COUNTRY_STATS_TABLE)
    statement.execute()
    statement.close()
    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + OLYMPIC_AIRLINE_STATS_TABLE)
    statement.execute()
    statement.close()
    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + OLYMPIC_AIRLINE_GOAL_TABLE)
    statement.execute()
    statement.close()
    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + EVENT_PICKED_REWARD_TABLE)
    statement.execute()
    statement.close()
    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + EVENT_TABLE)
    statement.execute()
    statement.close()


    statement = connection.prepareStatement("CREATE TABLE " + EVENT_TABLE + "(" +
      "id INTEGER PRIMARY KEY AUTO_INCREMENT," +
      "event_type INTEGER," +
      "start_cycle INTEGER, " +
      "duration INTEGER" +
      ")")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + OLYMPIC_CANDIDATE_TABLE + "(" +
      "event INTEGER," +
      "airport INTEGER," +
      "PRIMARY KEY (event, airport), " +
      "FOREIGN KEY(airport) REFERENCES " + AIRPORT_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE," +
      "FOREIGN KEY(event) REFERENCES " + EVENT_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + OLYMPIC_AFFECTED_AIRPORT_TABLE + "(" +
      "event INTEGER," +
      "principal_airport INTEGER," +
      "affected_airport INTEGER," +
      "PRIMARY KEY (event, principal_airport, affected_airport), " +
      "FOREIGN KEY(principal_airport) REFERENCES " + AIRPORT_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE," +
      "FOREIGN KEY(affected_airport) REFERENCES " + AIRPORT_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE," +
      "FOREIGN KEY(event) REFERENCES " + EVENT_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + OLYMPIC_AIRLINE_VOTE_TABLE + "(" +
      "event INTEGER," +
      "airline INTEGER," +
      "airport INTEGER," +
      "vote_weight INTEGER," +
      "precedence INTEGER," +
      "PRIMARY KEY (event, airport, airline), " +
      "FOREIGN KEY(airport) REFERENCES " + AIRPORT_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE," +
      "FOREIGN KEY(airline) REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE," +
      "FOREIGN KEY(event) REFERENCES " + EVENT_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()


    statement = connection.prepareStatement("CREATE TABLE " + OLYMPIC_VOTE_ROUND_TABLE + "(" +
      "event INTEGER," +
      "airport INTEGER," +
      "round INTEGER," +
      "vote INTEGER," +
      "PRIMARY KEY (event, airport, round), " +
      "FOREIGN KEY(airport) REFERENCES " + AIRPORT_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE," +
      "FOREIGN KEY(event) REFERENCES " + EVENT_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + OLYMPIC_COUNTRY_STATS_TABLE + "(" +
      "event INTEGER," +
      "cycle INTEGER," +
      "country_code CHAR(2)," +
      "transported INTEGER," +
      "total INTEGER," +
      "PRIMARY KEY (event, cycle, country_code), " +
      "FOREIGN KEY(country_code) REFERENCES " + COUNTRY_TABLE + "(code) ON DELETE CASCADE ON UPDATE CASCADE," +
      "FOREIGN KEY(event) REFERENCES " + EVENT_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + OLYMPIC_AIRLINE_STATS_TABLE + "(" +
      "event INTEGER," +
      "cycle INTEGER," +
      "airline INTEGER," +
      "score DECIMAL(15,2)," +
      "PRIMARY KEY (event, cycle, airline), " +
      "FOREIGN KEY(airline) REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE," +
      "FOREIGN KEY(event) REFERENCES " + EVENT_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + OLYMPIC_AIRLINE_GOAL_TABLE + "(" +
      "event INTEGER," +
      "airline INTEGER," +
      "goal INTEGER," +
      "PRIMARY KEY (event, airline), " +
      "FOREIGN KEY(airline) REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE," +
      "FOREIGN KEY(event) REFERENCES " + EVENT_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + EVENT_PICKED_REWARD_TABLE + "(" +
      "event INTEGER," +
      "airline INTEGER," +
      "reward_category INTEGER," +
      "reward_option INTEGER," +
      "PRIMARY KEY (event, airline, reward_category), " +
      "FOREIGN KEY(airline) REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE," +
      "FOREIGN KEY(event) REFERENCES " + EVENT_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()
  }

  def createAirportAirlineBonus(connection : Connection): Unit = {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + AIRPORT_AIRLINE_APPEAL_BONUS_TABLE)
    statement.execute()
    statement.close()

    //case class AirlineAppealBonus(loyalty : Double, awareness : Double, bonusType: BonusType.Value, expirationCycle : Option[Int])
    statement = connection.prepareStatement("CREATE TABLE " + AIRPORT_AIRLINE_APPEAL_BONUS_TABLE + "(" +
      "airline INTEGER," +
      "airport INTEGER," +
      "bonus_type INTEGER," +
      "loyalty_bonus DECIMAL(5,2), " +
      "awareness_bonus DECIMAL(5,2)," +
      "expiration_cycle INTEGER," +
      "INDEX " + AIRPORT_AIRLINE_APPEAL_BONUS_INDEX_1 + " (airline,airport,bonus_type)," +
      "FOREIGN KEY(airport) REFERENCES " + AIRPORT_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE," +
      "FOREIGN KEY(airline) REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()
  }

  def createSantaClaus(connection : Connection) {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + SANTA_CLAUS_INFO_TABLE)
    statement.execute()
    statement.close()

    //case class SantaClausInfo(airport : Airport, airline : Airline, attemptsLeft : Int, guesses : List[SantaClausGuess], found : Boolean, pickedAward : Option[SantaClausAwardType.Value], var id : Int = 0)
    statement = connection.prepareStatement("CREATE TABLE " + SANTA_CLAUS_INFO_TABLE + "(" +
      "id INTEGER PRIMARY KEY AUTO_INCREMENT," +
      "airline INTEGER, " +
      "airport  INTEGER," +
      "attempts_left INTEGER," +
      "found TINYINT(1)," +
      "picked_award INTEGER," +
      "FOREIGN KEY(airline) REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()


    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + SANTA_CLAUS_GUESS_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + SANTA_CLAUS_GUESS_TABLE + "(" +
      "id INTEGER PRIMARY KEY AUTO_INCREMENT," +
      "airline INTEGER, " +
      "airport  INTEGER," +
      "FOREIGN KEY(airline) REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()

  }

  def createResetUser(connection : Connection) {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + RESET_USER_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + RESET_USER_TABLE + "(" +
      "user_name VARCHAR(100) PRIMARY KEY, " +
      "token VARCHAR(256) NOT NULL, " +
      "FOREIGN KEY(user_name) REFERENCES " + USER_TABLE + "(user_name) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()
  }

  def createOil(connection : Connection) {
    //airline, price, volume, cost, start_cycle, duration
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + OIL_CONTRACT_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + OIL_CONTRACT_TABLE + "(" +
      "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
      "airline INTEGER, " +
      "price DOUBLE, " +
      "volume INTEGER," +
      "start_cycle INTEGER," +
      "duration INTEGER," +
      "FOREIGN KEY(airline) REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + OIL_PRICE_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + OIL_PRICE_TABLE + "(" +
      "price DOUBLE, " +
      "cycle INTEGER," +
      "PRIMARY KEY (cycle)" +
      ")")
    statement.execute()
    statement.close()


    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + OIL_CONSUMPTION_HISTORY_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + OIL_CONSUMPTION_HISTORY_TABLE + "(" +
      "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
      "airline INTEGER, " +
      "price DOUBLE, " +
      "volume INTEGER," +
      "consumption_type INTEGER," +
      "cycle INTEGER," +
      "FOREIGN KEY(airline) REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + OIL_INVENTORY_POLICY_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + OIL_INVENTORY_POLICY_TABLE + "(" +
      "airline INTEGER, " +
      "factor DOUBLE," +
      "start_cycle INTEGER," +
      "PRIMARY KEY (airline), " +
      "FOREIGN KEY(airline) REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()
  }

  def createLoanInterestRate(connection : Connection) {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + LOAN_INTEREST_RATE_TABLE)
    statement.execute()
    statement.close()


    statement = connection.prepareStatement("CREATE TABLE " + LOAN_INTEREST_RATE_TABLE + "(" +
      "rate DECIMAL(5,2), " +
      "cycle INTEGER," +
      "PRIMARY KEY (cycle)" +
      ")")
    statement.execute()
    statement.close()
  }

  def createLinkChangeHistory(connection: Connection) = {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + LINK_CHANGE_HISTORY_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + LINK_CHANGE_HISTORY_TABLE + "(" +
      "id INTEGER PRIMARY KEY AUTO_INCREMENT," +
      "link INTEGER, " +
      "price_economy INTEGER, " +
      "price_business INTEGER, " +
      "price_first INTEGER, " +
      "price_economy_delta INTEGER, " +
      "price_business_delta INTEGER, " +
      "price_first_delta INTEGER, " +
      "capacity_economy INTEGER, " +
      "capacity_business INTEGER, " +
      "capacity_first INTEGER, " +
      "capacity INTEGER, " +
      "capacity_economy_delta INTEGER, " +
      "capacity_business_delta INTEGER, " +
      "capacity_first_delta INTEGER, " +
      "capacity_delta INTEGER, " +
      "from_airport INTEGER, " +
      "to_airport INTEGER, " +
      "from_country CHAR(2), " +
      "to_country CHAR(2), " +
      "airline INTEGER, " +
      "alliance INTEGER, " +
      "frequency SMALLINT, " +
      "flight_number SMALLINT, " +
      "airplane_model SMALLINT, " +
      "raw_quality SMALLINT, " +
      "cycle INTEGER," +
      "INDEX " + LINK_CHANGE_HISTORY_INDEX_PREFIX + 1 + " (from_airport)," +
      "INDEX " + LINK_CHANGE_HISTORY_INDEX_PREFIX + 2 + " (to_airport)," +
      "INDEX " + LINK_CHANGE_HISTORY_INDEX_PREFIX + 3 + " (capacity_delta)," +
      "INDEX " + LINK_CHANGE_HISTORY_INDEX_PREFIX + 4 + " (from_country)," +
      "INDEX " + LINK_CHANGE_HISTORY_INDEX_PREFIX + 5 + " (to_country)," +
      "INDEX " + LINK_CHANGE_HISTORY_INDEX_PREFIX + 6 + " (airline)," +
      "INDEX " + LINK_CHANGE_HISTORY_INDEX_PREFIX + 7 + " (alliance)," +
      "INDEX " + LINK_CHANGE_HISTORY_INDEX_PREFIX + 8 + " (capacity)," +
      "INDEX " + LINK_CHANGE_HISTORY_INDEX_PREFIX + 9 + " (cycle)" +
      ")")

    statement.execute()
    statement.close()
  }

  def createGoogleResource(connection: Connection) = {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + GOOGLE_RESOURCE_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + GOOGLE_RESOURCE_TABLE + "(" +
      "resource_id INTEGER," +
      "resource_type INTEGER, " +
      "url VARCHAR(1024)," +
      "max_age_deadline BIGINT(20)," +
      "caption VARCHAR(255)," +
      "PRIMARY KEY (resource_id, resource_type)" +
      ")")

    statement.execute()
    statement.close()
  }


  def createDelegate(connection : Connection) {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + AIRCRAFT_MODEL_DELEGATE_TASK_TABLE)
    statement.execute()
    statement.close()
    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + COUNTRY_DELEGATE_TASK_TABLE)
    statement.execute()
    statement.close()
    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + LINK_NEGOTIATION_TASK_TABLE)
    statement.execute()
    statement.close()
    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + BUSY_DELEGATE_TABLE)
    statement.execute()
    statement.close()


    statement = connection.prepareStatement("CREATE TABLE " + BUSY_DELEGATE_TABLE + "(" +
      "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
      "airline INTEGER, " +
      "task_type TINYINT," +
      "available_cycle INTEGER, " +
      "FOREIGN KEY(airline) REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")

    statement.execute()
    statement.close()



    statement = connection.prepareStatement("CREATE TABLE " + COUNTRY_DELEGATE_TASK_TABLE + "(" +
      "delegate INTEGER PRIMARY KEY, " +
      "country_code CHAR(2), " +
      "start_cycle INTEGER, " +
      "FOREIGN KEY(delegate) REFERENCES " + BUSY_DELEGATE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()


    statement = connection.prepareStatement("CREATE TABLE " + LINK_NEGOTIATION_TASK_TABLE + "(" +
      "delegate INTEGER PRIMARY KEY , " +
      "from_airport INTEGER, " +
      "to_airport INTEGER, " +
      "start_cycle INTEGER, " +
      "FOREIGN KEY(delegate) REFERENCES " + BUSY_DELEGATE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()

  }

  def createAirportRunway(connection : Connection): Unit = {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + AIRPORT_RUNWAY_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + AIRPORT_RUNWAY_TABLE + "(" +
      "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
      "airport INTEGER," +
      "code VARCHAR(16)," +
      "runway_type VARCHAR(256)," +
      "length SMALLINT," +
      "lighted TINYINT," +
      "FOREIGN KEY(airport) REFERENCES " + AIRPORT_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()
  }

  def createChatMessage(connection : Connection) {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + CHAT_MESSAGE_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + CHAT_MESSAGE_TABLE + "(" +
      "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
      "airline INTEGER, " +
      "user INTEGER, " +
      "room_id INTEGER, " +
      "text VARCHAR(512) CHARACTER SET 'utf8mb4'," +
      "time VARCHAR(128)" +
      ")")

    statement.execute()
    statement.close()

    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + LAST_CHAT_ID_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + LAST_CHAT_ID_TABLE + "(" +
      "user INTEGER PRIMARY KEY, " +
      "last_chat_id BIGINT" +
    ")")
    statement.execute()
    statement.close()

  }

  def createLoyalist(connection : Connection) {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + LOYALIST_TABLE)
    statement.execute()
    statement.close()


    statement = connection.prepareStatement("CREATE TABLE " + LOYALIST_TABLE + "(" +
                                             "airport INTEGER REFERENCES " + AIRPORT_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE, " +
                                             "airline INTEGER REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE, " +
                                             "amount INTEGER," +
                                             "PRIMARY KEY (airport, airline)" +
                                             ")")

    statement.execute()
    statement.close()

    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + LOYALIST_HISTORY_TABLE)
    statement.execute()
    statement.close()


    statement = connection.prepareStatement("CREATE TABLE " + LOYALIST_HISTORY_TABLE + "(" +
      "airport INTEGER REFERENCES " + AIRPORT_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE, " +
      "airline INTEGER REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE, " +
      "amount INTEGER," +
      "cycle INTEGER, " +
      "INDEX " + LOYALIST_HISTORY_INDEX_PREFIX + 1 + " (airline)," +
      "PRIMARY KEY (airport, airline, cycle)" +
      ")")

    statement.execute()
    statement.close()
  }


  def createCampaign(connection : Connection) {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + CAMPAIGN_TABLE)
    statement.execute()
    statement.close()


    statement = connection.prepareStatement("CREATE TABLE " + CAMPAIGN_TABLE + "(" +
      "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
      "airline INTEGER REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE, " +
      "principal_airport INTEGER REFERENCES " + AIRPORT_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE, " +
      "population_coverage BIGINT, " +
      "radius INTEGER" +
      ")")

    statement.execute()
    statement.close()

    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + CAMPAIGN_AREA_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + CAMPAIGN_AREA_TABLE + "(" +
      "campaign INTEGER REFERENCES " + CAMPAIGN_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE, " +
      "airport INTEGER REFERENCES " + AIRPORT_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE, " +
      "PRIMARY KEY (campaign, airport)" +
      ")")

    statement.execute()
    statement.close()

    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + CAMPAIGN_DELEGATE_TASK_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + CAMPAIGN_DELEGATE_TASK_TABLE + "(" +
      "delegate INTEGER PRIMARY KEY, " +
      "campaign INTEGER REFERENCES " + CAMPAIGN_TABLE + "(id) ON DELETE RESTRICT ON UPDATE CASCADE, " +
      "start_cycle INTEGER, " +
      "FOREIGN KEY(delegate) REFERENCES " + BUSY_DELEGATE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + AIRCRAFT_MODEL_DELEGATE_TASK_TABLE + "(" +
      "delegate INTEGER PRIMARY KEY, " +
      "aircraft_model_id INTEGER, " +
      "start_cycle INTEGER, " +
      "FOREIGN KEY(delegate) REFERENCES " + BUSY_DELEGATE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()
  }

  def createLinkNegotiation(connection : Connection) = {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + LINK_NEGOTIATION_COOL_DOWN_TABLE)
    statement.execute()
    statement.close()


    statement = connection.prepareStatement(s"CREATE TABLE $LINK_NEGOTIATION_COOL_DOWN_TABLE (" +
      s"airline INTEGER REFERENCES $AIRLINE_TABLE(id) ON DELETE CASCADE ON UPDATE CASCADE, " +
      s"from_airport INTEGER REFERENCES $AIRPORT_TABLE(id) ON DELETE CASCADE ON UPDATE CASCADE, " +
      s"to_airport INTEGER REFERENCES $AIRPORT_TABLE(id) ON DELETE CASCADE ON UPDATE CASCADE, " +
      "expiration_cycle INTEGER, " +
      "PRIMARY KEY (airline, from_airport, to_airport)" +
      ")")

    statement.execute()
    statement.close()

    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + LINK_NEGOTIATION_DISCOUNT_TABLE)
    statement.execute()
    statement.close()


    statement = connection.prepareStatement(s"CREATE TABLE $LINK_NEGOTIATION_DISCOUNT_TABLE (" +
      "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
      s"airline INTEGER REFERENCES $AIRLINE_TABLE(id) ON DELETE CASCADE ON UPDATE CASCADE, " +
      s"from_airport INTEGER REFERENCES $AIRPORT_TABLE(id) ON DELETE CASCADE ON UPDATE CASCADE, " +
      s"to_airport INTEGER REFERENCES $AIRPORT_TABLE(id) ON DELETE CASCADE ON UPDATE CASCADE, " +
      "discount DECIMAL(3,2), " +
      "expiration_cycle INTEGER" +
      ")")

    statement.execute()
    statement.close()

  }



  def createTutorial(connection : Connection) = {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + COMPLETED_TUTORIAL_TABLE)
    statement.execute()
    statement.close()


    statement = connection.prepareStatement("CREATE TABLE " + COMPLETED_TUTORIAL_TABLE + "(" +
      "airline INTEGER REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE, " +
      "category VARCHAR(256)," +
      "id VARCHAR(256), " +
      "PRIMARY KEY (airline, category, id)" +
      ")")

    statement.execute()
    statement.close()

  }

  def createNotification(connection : Connection) = {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + NOTIFICATION_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + NOTIFICATION_TABLE + "(" +
      "id INTEGER PRIMARY KEY AUTO_INCREMENT," +
      "airline INTEGER NOT NULL REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE, " +
      "category VARCHAR(64) NOT NULL, " +
      "message VARCHAR(512) NOT NULL, " +
      "cycle INTEGER NOT NULL, " +
      "is_read TINYINT NOT NULL DEFAULT 0, " +
      "target_id VARCHAR(256) DEFAULT NULL, " +
      "expiry_cycle INTEGER DEFAULT NULL" +
      ")")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE INDEX notification_index_1 ON " + NOTIFICATION_TABLE + "(category)")
    statement.execute()
    statement.close()
  }

  def createAirportChampion(connection : Connection) {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + AIRPORT_CHAMPION_TABLE)
    statement.execute()
    statement.close()


    statement = connection.prepareStatement("CREATE TABLE " + AIRPORT_CHAMPION_TABLE + "(" +
      "airport INTEGER REFERENCES " + AIRPORT_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE, " +
      "airline INTEGER REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE, " +
      "loyalist INTEGER," +
      "ranking INTEGER," +
      "reputation_boost DECIMAL(5,2)," +
      "PRIMARY KEY (airport, airline)" +
      ")")

    statement.execute()
    statement.close()
  }

  def createAirlineBaseSpecialization(connection : Connection): Unit = {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + AIRLINE_BASE_SPECIALIZATION_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + AIRLINE_BASE_SPECIALIZATION_TABLE + "(" +
      "airport INTEGER, " +
      "airline INTEGER, " +
      "specialization_type VARCHAR(256), " +
      "PRIMARY KEY (airport, airline, specialization_type)," +
      "FOREIGN KEY(airport) REFERENCES " + AIRPORT_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE," +
      "FOREIGN KEY(airline) REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()
  }

  def createAirlineBaseSpecializationLastUpdate(connection : Connection): Unit = {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + AIRLINE_BASE_SPECIALIZATION_LAST_UPDATE_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + AIRLINE_BASE_SPECIALIZATION_LAST_UPDATE_TABLE + "(" +
      "airport INTEGER, " +
      "airline INTEGER, " +
      "update_cycle INTEGER, " +
      "PRIMARY KEY (airport, airline)," +
      "FOREIGN KEY(airport) REFERENCES " + AIRPORT_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE," +
      "FOREIGN KEY(airline) REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()
  }

  def createReputationBreakdown(connection : Connection): Unit = {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + AIRLINE_REPUTATION_BREAKDOWN)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + AIRLINE_REPUTATION_BREAKDOWN + "(" +
      "airline INTEGER, " +
      "reputation_type VARCHAR(256), " +
      "rep_value DECIMAL(10, 2), " +
      "quantity_value INTEGER UNSIGNED, " +
      "PRIMARY KEY (airline, reputation_type)," +
      "FOREIGN KEY(airline) REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()
  }

  def createIp(connection : Connection) {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + USER_IP_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + USER_IP_TABLE + "(" +
      "user INTEGER, " +
      "ip VARCHAR(256) NOT NULL, " +
      "occurrence INT NULL DEFAULT 0, " +
      "last_update DATETIME DEFAULT CURRENT_TIMESTAMP," +
      "PRIMARY KEY(user, ip)," +
      "FOREIGN KEY(user) REFERENCES " + USER_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + BANNED_IP_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + BANNED_IP_TABLE + "(" +
      "ip VARCHAR(256) PRIMARY KEY" +
      ")")
    statement.execute()
    statement.close()
  }

  def createUserUuid(connection : Connection) {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + USER_UUID_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + USER_UUID_TABLE + "(" +
      "user INTEGER, " +
      "uuid VARCHAR(256) NOT NULL, " +
      "occurrence INT NULL DEFAULT 0, " +
      "last_update DATETIME DEFAULT CURRENT_TIMESTAMP," +
      "PRIMARY KEY(user, uuid)," +
      "FOREIGN KEY(user) REFERENCES " + USER_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()
  }

  def createAdminLog(connection : Connection) {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + ADMIN_LOG_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + ADMIN_LOG_TABLE + "(" +
      "admin_user VARCHAR(256) NOT NULL, " +
      "admin_action VARCHAR(256) NOT NULL, " +
      "user_id INTEGER, " +
      "action_time DATETIME DEFAULT CURRENT_TIMESTAMP" +
      ")")
    statement.execute()
    statement.close()
  }

  def createAirlineModifier(connection : Connection) {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + AIRLINE_MODIFIER_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + AIRLINE_MODIFIER_TABLE + "(" +
      "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
      "airline INTEGER, " +
      "modifier_name CHAR(20), " +
      "creation INTEGER," +
      "expiry INTEGER," +
      "INDEX " + AIRLINE_MODIFIER_INDEX_PREFIX + 1 + " (airline)," +
      "FOREIGN KEY(airline) REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")"
    )
    statement.execute()
    statement.close()
  }

  def createAirlineModifierProperty(connection : Connection) {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + AIRLINE_MODIFIER_PROPERTY_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + AIRLINE_MODIFIER_PROPERTY_TABLE + "(" +
      "id INTEGER," +
      "name VARCHAR(256), " +
      "value INTEGER," +
      "PRIMARY KEY(id, name)," +
      "FOREIGN KEY(id) REFERENCES " + AIRLINE_MODIFIER_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")"
    )
    statement.execute()
    statement.close()
  }

  def createAirlineDividendsCoolDown(connection : Connection): Unit = {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + AIRLINE_DIVIDENDS_COOL_DOWN_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + AIRLINE_DIVIDENDS_COOL_DOWN_TABLE + "(" +
      "airline INT NOT NULL," +
      "expiration_cycle INT NOT NULL," +
      "PRIMARY KEY (airline)," +
      "FOREIGN KEY(airline) REFERENCES " + AIRLINE_INFO_TABLE + "(airline) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")"
    )
    statement.execute()
    statement.close()
  }

  def createUserModifier(connection : Connection) {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + USER_MODIFIER_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + USER_MODIFIER_TABLE + "(" +
      "user INTEGER, " +
      "modifier_name CHAR(20), " +
      "creation INTEGER," +
      "PRIMARY KEY (user, modifier_name)," +
      "FOREIGN KEY(user) REFERENCES " + USER_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")"
    )
    statement.execute()
    statement.close()
  }

  def createAirlineStats(connection : Connection): Unit = {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + AIRLINE_STATISTICS_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + AIRLINE_STATISTICS_TABLE + "(" +
      "airline INTEGER, " +
      "cycle INTEGER, " +
      "period INTEGER, " +
      "tourists INTEGER, " +
      "elites INTEGER, " +
      "business INTEGER, " +
      "total INTEGER, " +
      "codeshares INTEGER, " +
      "rask DOUBLE, " +
      "cask DOUBLE, " +
      "satisfaction DOUBLE, " +
      "load_factor DOUBLE, " +
      "on_time DOUBLE, " +
      "cash_on_hand INTEGER, " +
      "eps DOUBLE, " +
      "link_count INTEGER, " +
      "rep_total DOUBLE, " +
      "rep_leaderboards DOUBLE, " +
      "dividends_per_share DOUBLE NOT NULL DEFAULT 0," +
      "PRIMARY KEY (airline, period, cycle)," +
      "FOREIGN KEY(airline) REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()
  }

  /**
   * Destinations – could work beyond the airport
   *
   * @param connection
   */
  def createDestinations(connection : Connection): Unit = {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + DESTINATIONS_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + DESTINATIONS_TABLE + "(" +
      "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
      "airport INTEGER, " +
      "name VARCHAR(256), " +
      "destination_type VARCHAR(256), " +
      "strength INTEGER, " +
      "description VARCHAR(256), " +
      "latitude DOUBLE, " +
      "longitude DOUBLE, " +
      "country_code CHAR(2)" +
      ")")
    statement.execute()
    statement.close()
  }

  def createAllianceLabelColor(connection : Connection) {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + ALLIANCE_LABEL_COLOR_BY_ALLIANCE_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + ALLIANCE_LABEL_COLOR_BY_AIRLINE_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + ALLIANCE_LABEL_COLOR_BY_AIRLINE_TABLE + "(" +
      "airline INTEGER, " +
      "target_alliance INTEGER," +
      "color CHAR(20)," +
      "PRIMARY KEY (airline, target_alliance)," +
      "FOREIGN KEY(airline) REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE," +
      "FOREIGN KEY(target_alliance) REFERENCES " + ALLIANCE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")"
    )
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + ALLIANCE_LABEL_COLOR_BY_ALLIANCE_TABLE + "(" +
      "alliance INTEGER, " +
      "target_alliance INTEGER," +
      "color CHAR(20)," +
      "PRIMARY KEY (alliance, target_alliance)," +
      "FOREIGN KEY(alliance) REFERENCES " + ALLIANCE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE," +
      "FOREIGN KEY(target_alliance) REFERENCES " + ALLIANCE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")"
    )
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + ALLIANCE_META_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + ALLIANCE_META_TABLE + "(" +
      "alliance INTEGER, " +
      "alliance_slogan VARCHAR(256), " +
      "FOREIGN KEY(alliance) REFERENCES " + ALLIANCE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")"
    )
    statement.execute()
    statement.close()
  }

  def createAllianceStats(connection : Connection): Unit = {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + ALLIANCE_STATS_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + ALLIANCE_STATS_TABLE + "(" +
      "alliance INTEGER, " +
      "cycle INTEGER, " +
      "period INTEGER, " +
      "traveler_pax INT UNSIGNED, " +
      "business_pax INT UNSIGNED, " +
      "elite_pax INT UNSIGNED, " +
      "tourist_pax INT UNSIGNED, " +
      "total_airport_rep INT, " +
      "total_airline_market_cap DOUBLE, " +
      "total_lounge_visit INT UNSIGNED, " +
      "total_profit BIGINT, " +
      "PRIMARY KEY (alliance, period, cycle)," +
      "FOREIGN KEY(alliance) REFERENCES " + ALLIANCE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")")
    statement.execute()
    statement.close()
  }

  def createNotes(connection : Connection): Unit = {

    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + NOTES_AIRLINE_TABLE)
    statement.execute()
    statement.close()
    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + NOTES_AIRPORT_TABLE)
    statement.execute()
    statement.close()
    statement = connection.prepareStatement("DROP TABLE IF EXISTS " + NOTES_LINK_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + NOTES_AIRLINE_TABLE + "(" +
      "airline INTEGER PRIMARY KEY, " +
      "notes TEXT, " +
      "FOREIGN KEY(airline) REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")"
    )
    statement.execute()
    statement.close()
    statement = connection.prepareStatement("CREATE TABLE " + NOTES_LINK_TABLE + "(" +
      "link INTEGER PRIMARY KEY, " +
      "airline INTEGER, " +
      "notes TEXT, " +
      "FOREIGN KEY(link) REFERENCES " + LINK_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE," +
      "FOREIGN KEY(airline) REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")"
    )
    statement.execute()
    statement.close()
    statement = connection.prepareStatement("CREATE TABLE " + NOTES_AIRPORT_TABLE + "(" +
      "airport INTEGER, " +
      "airline INTEGER, " +
      "notes TEXT, " +
      "PRIMARY KEY (airport, airline)," +
      "FOREIGN KEY(airport) REFERENCES " + AIRPORT_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE," +
      "FOREIGN KEY(airline) REFERENCES " + AIRLINE_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")"
    )
    statement.execute()
    statement.close()
  }

  def createAirportStatistics(connection : Connection): Unit = {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + AIRPORT_STATISTICS_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + AIRPORT_STATISTICS_TABLE + "(" +
      "airport INTEGER PRIMARY KEY, " +
      "baseline_demand INTEGER, " +
      "from_pax INTEGER, " +
      "congestion DOUBLE, " +
      "reputation DOUBLE, " +
      "travel_rate DOUBLE, " +
      "FOREIGN KEY(airport) REFERENCES " + AIRPORT_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")"
    )
    statement.execute()
    statement.close()
  }

  def createWorldStatistics(connection : Connection): Unit = {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + WORLD_STATISTICS_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + WORLD_STATISTICS_TABLE + "(" +
      "week INTEGER, " +
      "period INTEGER, " +
      "total_pax INTEGER, " +
      "missed_pax INTEGER, " +
      "load_factor DOUBLE, " +
      "PRIMARY KEY (week, period)" +
      ")"
    )
    statement.execute()
    statement.close()
  }

  def createRankingLeaderboard(connection : Connection): Unit = {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + RANKING_LEADERBOARD_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + RANKING_LEADERBOARD_TABLE + "(" +
      "cycle INTEGER, " +
      "ranking_type VARCHAR(64), " +
      "key_hash CHAR(64) NOT NULL, " +
      "ranking_key VARCHAR(256), " +
      "entry VARCHAR(512), " +
      "ranking INTEGER, " +
      "ranked_value DOUBLE, " +
      "movement INTEGER DEFAULT 0, " +
      "reputation_prize INTEGER DEFAULT 0, " +
      "PRIMARY KEY (cycle, ranking_type, key_hash), " +
      "INDEX ranking_leaderboard_cycle_idx (cycle, ranking_type, ranking)" +
      ")"
    )
    statement.execute()
    statement.close()
  }

  def createPrestige(connection: Connection): Unit = {
    var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + PRESTIGE_TABLE)
    statement.execute()
    statement.close()

    statement = connection.prepareStatement("CREATE TABLE " + PRESTIGE_TABLE + "(" +
      "id INTEGER PRIMARY KEY AUTO_INCREMENT," +
      "airline INTEGER, " +
      "airport INTEGER, " +
      "airline_name VARCHAR(256), " +
      "prestige_points INTEGER UNSIGNED, " +
      "cycle INTEGER, " +
      "FOREIGN KEY(airport) REFERENCES " + AIRPORT_TABLE + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
      ")"
    )

    statement.execute()
    statement.close()
  }

  def isTableExist(connection : Connection, tableName : String): Boolean = {
    val tables = connection.getMetaData.getTables(null, null, tableName, null)
    tables.next()
  }

}
