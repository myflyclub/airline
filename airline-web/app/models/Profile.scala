package models

import com.patson.model.{AirlineType, Airport, Loan}
import com.patson.model.airplane.Airplane

case class Profile(
  name : String,
  airlineType : AirlineType,
  difficulty : String = "Normal",
  description : String,
  rule : List[String] = List(""),
  cash : Long,
  airport : Airport,
  reputation : Int = 0,
  quality : Int = 35,
  airplanes : List[Airplane] = List.empty,
  loan: Option[Loan] = None
)
