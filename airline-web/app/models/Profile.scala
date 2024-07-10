package models

import com.patson.model.Loan
import com.patson.model.Airport
import com.patson.model.airplane.Airplane

case class Profile(name : String, description : String, cash : Int, airport : Airport, reputation : Int = 0, quality : Int = 10, airplanes : List[Airplane] = List.empty, loan : Option[Loan] = None)
