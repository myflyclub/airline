package controllers

import java.awt.Color

import com.patson.util.LogoGenerator
import javax.inject.Inject
import play.api.libs.json.Json
import play.api.mvc._
import scala.jdk.CollectionConverters._

class LogoApplication @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

  def getTemplates() = Action {
     val templateIndices: List[Int] = (0 until LogoGenerator.getTemplateCount()).toList
     Ok(Json.toJson(templateIndices))
       .withHeaders(
         CACHE_CONTROL -> "public, max-age=2419200",
         ETAG -> s""""$currentApiVersion"""",
         EXPIRES -> java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
           .format(java.time.ZonedDateTime.now().plusWeeks(4))
       )
  }

  def getTemplate(id : Int) = Action {
     // Generate a template preview using standard colors (Black and White)
     val bytes = LogoGenerator.generateLogo(id, Color.BLACK.getRGB, Color.WHITE.getRGB)
     Ok(bytes).as("image/png")
  }
  
  def getPreview(templateIndex : Int, color1 : String, color2 : String) = Action {
     Ok(LogoGenerator.generateLogo(templateIndex, Color.decode(color1).getRGB, Color.decode(color2).getRGB)).as("img/png")
  }	 
}
