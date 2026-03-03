package com.patson.util

import com.patson.data.airplane.ModelSource
import com.patson.model.RegionalAirline
import com.patson.model.airplane.Model


object AirplaneModelCache {
  val allModels = ModelSource.loadAllModels().map(model => (model.id, model)).toMap

  def getModel(modelId : Int) : Option[Model] = {
    allModels.get(modelId)
  }
}



