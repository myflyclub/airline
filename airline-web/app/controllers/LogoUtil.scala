package controllers

import java.nio.file.Path

import com.patson.data.AirlineSource
import javax.imageio.ImageIO

object LogoUtil {
  // Make initialization lazy and defensive: any failure while loading from DB or resources
  // should not blow up the class initializer (which causes NoClassDefFoundError).
  lazy val logos: scala.collection.mutable.Map[Int, Array[Byte]] = {
    try {
      collection.mutable.Map(AirlineSource.loadLogos().toSeq: _*)
    } catch {
      case e: Throwable =>
        // avoid failing class init; fall back to empty cache
        e.printStackTrace()
        collection.mutable.Map.empty[Int, Array[Byte]]
    }
  }

  lazy val blank: Array[Byte] = loadResourceSafely("/logo/blank.png").getOrElse(Array.emptyByteArray)

  lazy val rat: Array[Byte] = loadResourceSafely("/logo/bot-rat-logo.png").getOrElse(blank)

  val imageHeight = 12
  val imageWidth = 24
  
  def getLogo(airlineId : Int) : Array[Byte]= {
    if (airlineId < 30) {
      rat
    } else {
      logos.get(airlineId) match {
        case Some(logo) => logo
        case None => blank
      }
    }
  }
  
  def saveLogo(airlineId : Int, logo : Array[Byte]) = {
    AirlineSource.saveLogo(airlineId, logo)
    logos.put(airlineId, logo) //update cache
  }
  
  def validateUpload(logoFile : Path) : Option[String] = {
    val imageInputStream = ImageIO.createImageInputStream(logoFile.toFile)
    val readers = ImageIO.getImageReaders(imageInputStream)
    if (!readers.hasNext) {
      return Some("Cannot identify image format")
    }
    val reader = readers.next();
    val format = reader.getFormatName
    if (!format.equalsIgnoreCase("png")) {
      return Some(s"Invalid image format: $format")
    }

    val image = ImageIO.read(logoFile.toFile)
    if (image.getHeight() != imageHeight || image.getWidth() != imageWidth) {
      return Some("Image should be " + imageWidth + "px wide and " + imageHeight + "px tall")
    }
    return None
  }
  
  private def loadResourceSafely(path: String): Option[Array[Byte]] = {
    val isOpt = Option(play.Environment.simple().resourceAsStream(path))
    isOpt.flatMap { is =>
      try {
        val baos = new java.io.ByteArrayOutputStream()
        val buffer = new Array[Byte](8192)
        var read = is.read(buffer)
        while (read != -1) {
          baos.write(buffer, 0, read)
          read = is.read(buffer)
        }
        Some(baos.toByteArray)
      } catch {
        case e: Throwable =>
          e.printStackTrace()
          None
      } finally {
        try is.close() catch { case _: Throwable => }
      }
    }
  }
  
  def getBlankLogo() = {
    val is = play.Environment.simple().resourceAsStream("/logo/blank.png")

    val targetArray = new Array[Byte](is.available());
    is.read(targetArray);

    targetArray
  }

  def getRatLogo() = {
    val is = play.Environment.simple().resourceAsStream("/logo/bot-rat-logo.png")

    val targetArray = new Array[Byte](is.available());
    is.read(targetArray);

    targetArray
  }
}