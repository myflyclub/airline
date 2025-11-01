package com.patson.model.google

case class GoogleResource(resourceId : Int, resourceType : ResourceType.Value, url : String, maxAgeDeadline : Option[Long], caption: String)

object ResourceType extends Enumeration {
  val CITY_IMAGE, AIRPORT_IMAGE = Value
}