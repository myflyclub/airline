var configStore

function showAirplaneModelConfigurationsFromPlanLink(modelId) {
    showAirplaneModelConfigurations(modelId)
    $('#modelConfigurationModal').data('closeCallback', function() {
        planLink($("#planLinkFromAirportId").val(), $("#planLinkToAirportId").val(), true)
    })
}

function showAirplaneModelConfigurations(modelId) {
    var airlineId = activeAirline.id
    $.ajax({
        type: 'GET',
        url: "airlines/" + airlineId + "/configurations?modelId=" + modelId,
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(result) {
            loadedModelConfigInfo = result
            showAirplaneModelConfigurationsModal(result)
        },
        error: function(jqXHR, textStatus, errorThrown) {
                console.log(JSON.stringify(jqXHR));
                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    });
}

function refreshConfigurationAfterAirplaneUpdate() {
    //loadAirplaneModelOwnerInfo() //refresh the whole model screen - as the table might change
    loadAirplaneModelOwnerInfoByModelId(selectedModelId) //refresh the loaded airplanes on the selected model
    showAirplaneModelConfigurations(selectedModelId)
}

function showAirplaneModelConfigurationsModal(modelConfigurationInfo) {
    $("#modelConfigurationModal .configContainer").empty()
    var spaceMultipliers = modelConfigurationInfo.spaceMultipliers
    var model = modelConfigurationInfo.model
    configStore = modelConfigurationInfo.configurations[0]

    $("#modelConfigurationModal .modelName").text(model.name)
    $("#modelConfigurationModal .modelCapacity").text(model.capacity)
    $("#modelConfigurationModal .modelMaxSeats").text(model.maxSeats)

    $.each(modelConfigurationInfo.configurations, function(index, configuration) {
        var configurationDiv = $(`<div style='min-height : 130px;' class='config' id='config-${configuration.id}'></div>`)
        configurationDiv.data("configuration", configuration)

        addAirplaneInventoryDivByConfiguration(configurationDiv, model.id)
        configurationDiv.attr("ondragover", "allowAirplaneIconDragOver(event)")
        configurationDiv.attr("ondrop", "onAirplaneIconConfigurationDrop(event, " + configuration.id + ")");
        $("#modelConfigurationModal .configContainer").append(configurationDiv)

        const ComponentAircraftConfig = window.ComponentAircraftConfig;
        config = modelConfigurationInfo.configurations[index]
        config = {...config, ...modelConfigurationInfo.model}
        config.id = configuration.id
        config.original = {economy: config.economy, business: config.business, first: config.first}
        config.airplaneCount = getAssignedAirplanesCount("configurationId", configuration.id, model.id)
        console.log(config)

        new ComponentAircraftConfig({
            target: document.getElementById(`config-${configuration.id}`),
            props: {
                config
            }
        });
    })

    for (i = 0 ; i < modelConfigurationInfo.maxConfigurationCount - modelConfigurationInfo.configurations.length; i ++) { //pad the rest with empty div
        var configurationDiv = $("<div style='width : 98%; min-height : 130px; position: relative;' class='config'></div>")
        var promptDiv = ("<div style='position: absolute; top: 50%; left: 50%; transform: translate(-50%,-50%);'><button class='button' onclick='toggleNewConfiguration(selectedModel, " + (modelConfigurationInfo.configurations.length == 0 ? "true" : "false") + ")'><img src='assets/images/icons/24px/plus.png' title='Add new configuration'><div style='float:right'><h3 class='pl-2'>Add New Configuration</h3></div></span></div>")

        configurationDiv.append(promptDiv)
        $("#modelConfigurationModal .configContainer").append(configurationDiv)
    }
    toggleUtilizationRate($("#modelConfigurationModal"), $("#modelConfigurationModal .toggleUtilizationRateBox"))
    toggleCondition($("#modelConfigurationModal"), $("#modelConfigurationModal .toggleConditionBox"))

    $('#modelConfigurationModal').fadeIn(200)
}

function addAirplaneInventoryDivByConfiguration(configurationDiv, modelId) {
    var airplanesDiv = $("<div style='max-height: 60px; min-height: 20px; overflow: auto; box-shadow: inset 0 0 2px #ffffff82, 0 0 3px rgba(0, 0, 0, 0.2); padding: 3px 6px; border-radius: 12px; justify-content: center; display: flex; flex-wrap: wrap;'></div>")
    var configurationId = configurationDiv.data("configuration").id
    var info = loadedModelsById[modelId]
    if (!info.isFullLoad) {
        loadAirplaneModelOwnerInfoByModelId(modelId) //refresh to get the utility rate
    }

    var allAirplanes = $.merge($.merge($.merge([], info.assignedAirplanes), info.availableAirplanes), info.constructingAirplanes)
    $.each(allAirplanes, function( key, airplane ) {
        if (airplane.configurationId == configurationId) {
            var airplaneId = airplane.id
            var li = $("<div class='clickable' onclick='loadOwnedAirplaneDetails(" + airplaneId + ", $(this), refreshConfigurationAfterAirplaneUpdate)'></div>").appendTo(airplanesDiv)
            var airplaneIcon = getAirplaneIcon(airplane, gameConstants.aircraft.conditionBad)
            enableAirplaneIconDrag(airplaneIcon, airplaneId)
            enableAirplaneIconDrop(airplaneIcon, airplaneId, "refreshConfigurationAfterAirplaneUpdate")
            li.append(airplaneIcon)
         }
    });


    configurationDiv.append(airplanesDiv)
}

function toggleNewConfiguration(model, isDefault) {
       var configuration = { "id" : 0, "model" : model, "economy" : configStore.economy, "business" : configStore.business, "first" : configStore.first , "isDefault" : isDefault}
       saveConfiguration(configuration)
}

function saveAndSetDefaultConfiguration(configuration) {
    configuration.isDefault = true
    saveConfiguration(configuration)
}

function saveConfiguration(configuration) {
    var airlineId = activeAirline.id

    $.ajax({
            type: 'PUT',
            url: "airlines/" + airlineId + "/configurations?modelId=" + configuration.model.id + "&configurationId=" + configuration.id + "&economy=" + configuration.economy + "&business=" + configuration.business + "&first=" + configuration.first + "&isDefault=" + configuration.isDefault,
            contentType: 'application/json; charset=utf-8',
            dataType: 'json',
            success: function(result) {
                showAirplaneModelConfigurations(configuration.model.id)
            },
            error: function(jqXHR, textStatus, errorThrown) {
                    console.log(JSON.stringify(jqXHR));
                    console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
            }
        });
}

function deleteConfiguration(configuration) {
    var airlineId = activeAirline.id

    $.ajax({
            type: 'DELETE',
            url: "airlines/" + airlineId + "/configurations/" + configuration.id,
            contentType: 'application/json; charset=utf-8',
            dataType: 'json',
            success: function(result) {
                refreshConfigurationAfterAirplaneUpdate()
            },
            error: function(jqXHR, textStatus, errorThrown) {
                    console.log(JSON.stringify(jqXHR));
                    console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
            }
        });
}

function onAirplaneIconConfigurationDrop(event, configurationId) {
  event.preventDefault();
  var airplaneId = event.dataTransfer.getData("airplane-id")
  if (airplaneId) {
    $.ajax({
              type: 'PUT',
              url: "airlines/" + activeAirline.id + "/airplanes/" + airplaneId + "/configuration/" + configurationId,
              contentType: 'application/json; charset=utf-8',
              dataType: 'json',
              async: false,
              success: function(result) {
                  refreshConfigurationAfterAirplaneUpdate()
              },
              error: function(jqXHR, textStatus, errorThrown) {
                      console.log(JSON.stringify(jqXHR));
                      console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
              }
          });
  }
}