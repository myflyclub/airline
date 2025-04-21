function plotMaintenanceLevelGauge(container, maintenanceLevelInput, onchangeFunction) {
	container.children(':FusionCharts').each((function(i) {
		  $(this)[0].dispose();
	}))
	
	var dataSource = { 
			"chart": {
		    	"theme": "fint",
		        "lowerLimit": "0",
		        "upperLimit": "100",
		        "showTickMarks": "0",
		        "showTickValues": "0",
		        "showborder": "0",
		        "showtooltip": "0",
		        "chartBottomMargin": "0",
		        "bgAlpha":"0",
		        "containerBackgroundOpacity" :'0',
		        "valueFontSize": "11",  
		        "valueFontBold": "0",
		        "animation": "0",
		        "editMode": "1",
		        "decimals": "0",
		        "baseFontColor": "#FFFFFF"
		    },
		    "pointers": {
		        //Multiple pointers defined here
		        "pointer": [
		            {
		                "bgColor": "#FFE62B",
		                "bgAlpha": "50",
		                "showValue": "0",
		                "sides" : "3",
		                "borderColor": "#FFE62B",
		                "borderAlpha": "20",
		                "value" : maintenanceLevelInput.val()
		            }
		        ]
		    },
		    "colorRange" : {
		    	"color": [
                      {
                    	  "minValue": "0",
                          "maxValue": "100",
                          "label": maintenanceLevelInput.val() + "%",
                          "code": "#6baa01"
                      }]
		    }
		}
	var chart = container.insertFusionCharts(
			{	
				type: 'hlineargauge',
				width: '100%',
		        height: '40px',
		        dataFormat: 'json',
			    dataSource: dataSource,
				events: {
		            //Event is raised when a real-time gauge or chart completes updating data.
		            //Where we can get the updated data and display the same.
		            "realTimeUpdateComplete" : function (evt, arg){
		                var newLevel = evt.sender.getData(1)
		                //take the floor
		                newLevel = Math.floor(newLevel)
		                dataSource["pointers"]["pointer"][0].value = newLevel
		                dataSource["colorRange"]["color"][0].label = newLevel + "%"
		                maintenanceLevelInput.val(newLevel)
		                container.updateFusionCharts({
		                	"dataSource": dataSource
		                });
		                onchangeFunction(newLevel)
		            }
		        }
			})
	
}

//unmodifiable seat configuration bar
function plotSeatConfigurationBar(container, configuration, maxSeats, spaceMultipliers, hideValues, height) {
    container.children(':FusionCharts').each((function(i) {
          $(this)[0].dispose();
    }))
    container.empty()

    var dataSource = {
        "chart": {
            "theme": "fint",
            "lowerLimit": "0",
            "upperLimit": "100",
            "showTickMarks": "0",
            "showTickValues": "0",
            "showborder": "0",
            "chartBottomMargin": "0",
            "bgAlpha":"0",
            "valueFontSize": "11",
            "valueFontBold": "0",
            "animation": "0",
            "editMode": "0",
            "containerBackgroundOpacity" :'0',
            "pointerBgAlpha":"0",
            "pointerBorderAlpha":"0",
            "chartLeftMargin": "0",
            "chartTopMargin": "0",
            "chartRightMargin": "0",
            "chartBottomMargin": "0",
            "baseFontColor": "#FFFFFF"
        }
    }


    var businessPosition = configuration.economy / maxSeats * 100
    var firstPosition = configuration.economy / maxSeats * 100 + configuration.business * spaceMultipliers.business / maxSeats * 100
    var emptyPosition = configuration.economy / maxSeats * 100 + configuration.business * spaceMultipliers.business / maxSeats * 100 + configuration.first * spaceMultipliers.first / maxSeats * 100

    var economyRange = {
                         "minValue": "0",
                         "maxValue": businessPosition,
                         "code": "#6baa01"
                       }
    var businessRange = {
                          "minValue": businessPosition,
                          "maxValue": firstPosition,
                          "code": "#0077CC"
                         }
    var firstRange = {
                      "minValue": firstPosition,
                      "maxValue": emptyPosition,
                      "code": "#FFE62B"
                      }
    var emptyRange = {
                       "minValue": emptyPosition,
                       "maxValue": "100",
                       "code": "#cccccc"
                     }
    if (!hideValues) {
        economyRange.label = "Y : " + configuration.economy
        businessRange.label = "J : " + configuration.business
        firstRange.label = "F : " + configuration.first
    }

    dataSource["colorRange"] = { "color": [economyRange, businessRange, firstRange, emptyRange] }

    if (!height) {
        height = "20px"
    }

    var chart = container.insertFusionCharts(
    {
        type: 'hlineargauge',
        width: '100%',
        height: height,
        dataFormat: 'json',
        dataSource: dataSource,
    })

}

function plotSeatConfigurationGauge(container, configuration, maxSeats, spaceMultipliers, callback) {
	container.children(':FusionCharts').each((function(i) {
		  $(this)[0].dispose();
	}))
	
	container.empty()

	var chartConfig = {
                      	    	"theme": "fint",
                      	        "lowerLimit": "0",
                      	        "upperLimit": "100",
                      	        "showTickMarks": "0",
                      	        "showTickValues": "0",
                      	        "showborder": "0",
                      	        "showtooltip": "0",
                      	        "chartBottomMargin": "0",
                      	        "bgAlpha":"0",
                      	        "valueFontSize": "11",
                      	        "valueFontBold": "0",
                      	        "animation": "0",
                      	        "editMode": "0",
                      	        "pointerBgAlpha":"0",
                                  "pointerBorderAlpha":"0",
                      	        containerBackgroundOpacity :'0',
                      	        "baseFontColor": "#FFFFFF"
                      	    }


	var dataSource = { 
		"chart": chartConfig
//	    ,
//	    "pointers": {
//	        //Multiple pointers defined here
//	        "pointer": [
//	            {
//	                "bgColor": "#FFE62B",
//	                "bgAlpha": "50",
//	                "showValue": "0",
//	                //"sides" : "4",
//	                "borderColor": "#FFE62B",
//	                "borderAlpha": "20",
//	            },
//	            {
//	                "bgColor": "#0077CC",
//	                "bgAlpha": "50",
//	                "showValue": "0",
//	                //"sides" : "3",
//	                "borderColor": "#0077CC",
//	                "borderAlpha": "20",
//	            }
//	        ]
//	    }
	}
	
	function updateDataSource(configuration) {
		var businessPosition = configuration.economy * spaceMultipliers.economy
		var firstPosition = businessPosition + configuration.business * spaceMultipliers.business
		var emptyPosition = firstPosition + configuration.first * spaceMultipliers.first
		dataSource["colorRange"] = {
            "color": [
                      {
                          "minValue": "0",
                          "maxValue": businessPosition,
                          "label": "Y : " + configuration.economy,
                          "tooltext": "Economy Class",
                          "code": "#6baa01"
                      },
                      {
                          "minValue": businessPosition,
                          "maxValue": firstPosition,
                          "label": "J : " + configuration.business,
                          "tooltext": "Business Class",
                          "code": "#0077CC"
                      },
                      {
                          "minValue": firstPosition,
                          "maxValue": emptyPosition,
                          "label": "F : " + configuration.first,
                          "tooltext": "First Class",
                          "code": "#FFE62B"
                      },
                      {
                          "minValue": emptyPosition,
                          "maxValue": "100",
                          "label": configuration.empty,
                          "tooltext": "Empty space",
                          "code": "#D9534F"
                    },
                  ]
              }
//	    dataSource["pointers"]["pointer"][0].value = firstPosition
//	    dataSource["pointers"]["pointer"][1].value = businessPosition
	}
	
	updateDataSource(configuration)

	var chart = container.insertFusionCharts(
	{	
		type: 'hlineargauge',
        width: '100%',
        height: '40px',
        dataFormat: 'json',
        containerBackgroundOpacity :'0',
	    dataSource: dataSource
//	    ,
//        "events": {
//            "realTimeUpdateComplete" : function (evt, arg){
//                var firstPosition = evt.sender.getData(1)
//                var businessPosition = evt.sender.getData(2)
//
//                var tinyAdjustment = 0.001 //the tiny adjustment is to avoid precision problem that causes floor to truncate number like 0.99999
//                configuration["first"] = Math.floor(tinyAdjustment + maxSeats * (100 - firstPosition) / 100 / spaceMultipliers.first)
//
//                if (firstPosition < businessPosition) {  //dragging first past business to the left => eliminate all business
//                	configuration["business"] = 0
//                } else {
//                	configuration["business"] = Math.floor(tinyAdjustment + (maxSeats * (100 - businessPosition) / 100 - configuration["first"] * spaceMultipliers.first) / spaceMultipliers.business)
//                }
//
//                if (businessPosition == 0) { //allow elimination of all economy seats
//                	configuration["economy"] = 0
//                } else {
//                	configuration["economy"] = Math.floor(tinyAdjustment + (maxSeats - configuration["first"] * spaceMultipliers.first - configuration["business"] * spaceMultipliers.business) / spaceMultipliers.economy)
//                }
//
//
//                //console.log(configuration)
//
//                updateDataSource(configuration)
//                callback(configuration)
//
//                container.updateFusionCharts({
//                	"dataSource": dataSource
//                });
//            }
//        }
	})
}

function plotAirportShares(airportShares, currentAirportId, container) {
	container.children(':FusionCharts').each((function(i) {
		  $(this)[0].dispose();
	}))
	
	var data = []
	$.each(airportShares, function(key, airportShare) {
		var entry = {
			label : airportShare.airportName,
			value : airportShare.share
		}
//		if (currentAirportId == airportShare.airportId) {
//			entry["sliced"] = true
//			entry["selected"] = true
//		}
		data.push(entry)
	})
	$("#cityPie").insertFusionCharts({
		type: 'pie2d',
	    width: '100%',
	    height: '195',
	    dataFormat: 'json',
	    containerBackgroundOpacity :'0',
		dataSource: {
	    	"chart": {
	    		"animation": "0",
	    		"pieRadius": "70",
	    		"showBorder":"0",
                "use3DLighting": "1",
                "showPercentInTooltip": "1",
                "decimals": "2",
                "toolTipBorderRadius": "2",
                "toolTipPadding": "5",
                "showHoverEffect":"1",
                "bgAlpha":"0",
                "showLabels":"0",
                "showValues":"0"
	    	},
			"data" : data
	    }
	})
}

var monthlyWeeksPerMark = 4
var quarterlyWeeksPerMark = 13
var monthlyMaxMark = 6
var quarterlyMaxMark = 8 //2 years
var plotUnitEnum = {
    MONTH : { value : 1,  maxWeek : monthlyMaxMark * monthlyWeeksPerMark, weeksPerMark : monthlyWeeksPerMark, maxMark : monthlyMaxMark},
    QUARTER : { value : 2,  maxWeek : quarterlyMaxMark * quarterlyWeeksPerMark, weeksPerMark : quarterlyWeeksPerMark, maxMark : quarterlyMaxMark}
}


function plotLinkProfit(linkConsumptions, container, plotUnit) {
	container.children(':FusionCharts').each((function(i) {
		  $(this)[0].dispose();
	}))
	
	var data = []
	var category = []
	 
	var profitByMark = {}
	var markOrder = []

	if (plotUnit === undefined) {
        plotUnit = plotUnitEnum.MONTH
    }

    var maxMark = plotUnit.maxMark
  	var xLabel = 'Period & Week'
  	var yLabel
  	// var weeksPerMark = plotUnit.weeksPerMark
   	switch (plotUnit.value) {
        case plotUnitEnum.MONTH.value:
            yLabel = 'Monthly Profit'
            break;
        case plotUnitEnum.QUARTER.value:
            yLabel = 'Quarterly Profit'
            break;
    }

	$.each(linkConsumptions, function(index, linkConsumption) {
		//group in months first
		var mark = getGameDate(linkConsumption.cycle)
		if (profitByMark[mark] === undefined) {
			profitByMark[mark] = linkConsumption.profit
			markOrder.push(mark)
		} else {
			profitByMark[mark] += linkConsumption.profit
		}
	})
	

	markOrder = markOrder.slice(0, maxMark)
	$.each(markOrder.reverse(), function(key, mark) {
		data.push({ value : profitByMark[mark] })
		category.push({ label : mark.toString() })
	})

	var chartConfig = {
                        "xAxisname": xLabel,
                        "yAxisName": yLabel,
                        "numberPrefix": "$",
                        "useroundedges": "1",
                        "animation": "0",
                        "showBorder":"0",
                        "showPlotBorder":"0",
                        "toolTipBorderRadius": "2",
                        "toolTipPadding": "5",
                        "bgAlpha": "0",
                        "showValues":"0"
                        }

    checkDarkTheme(chartConfig)

	var chart = container.insertFusionCharts({
		type: 'mscombi2d',
	    width: '100%',
	    height: '100%',
	    containerBackgroundOpacity :'0',
	    dataFormat: 'json',
		dataSource: {
	    	"chart": chartConfig,
	    	"categories" : [{ "category" : category}],
			"dataset" : [ {"data" : data}, {"renderas" : "Line", "data" : data} ]
	    	            
	    }
	})
}

function plotLinkConsumption(linkConsumptions, ridershipContainer, revenueContainer, priceContainer, plotUnit) {
	ridershipContainer.children(':FusionCharts').each((function(i) {
		  $(this)[0].dispose();
	}))
	
	revenueContainer.children(':FusionCharts').each((function(i) {
		  $(this)[0].dispose();
	}))
	
	priceContainer.children(':FusionCharts').each((function(i) {
		  $(this)[0].dispose();
	}))
	
	var emptySeatsData = []
	var cancelledSeatsData = []
	var soldSeatsData = {
			economy : [],
			business : [],
	        first : []
	}
	var revenueByClass = {
			economy : [],
			business : [],
	        first : []
	}
	
	var priceByClass = {
		economy : [],
		business : [],
        first : []
	}


		
	var category = []

    if (plotUnit === undefined) {
	    plotUnit = plotUnitEnum.MONTH
	}

	var maxWeek = plotUnit.maxWeek
	var weeksPerMark = plotUnit.weeksPerMark
	var xLabel = 'Period & Week'
	switch (plotUnit.value) {
      case plotUnitEnum.MONTH.value:
        break;
      case plotUnitEnum.QUARTER.value:
        break;
    }


	if (!jQuery.isEmptyObject(linkConsumptions)) {
		linkConsumptions = $(linkConsumptions).toArray().slice(0, maxWeek)
        var hasCapacity = {} //check if there's any capacity for this link class at all
        hasCapacity.economy = $.grep(linkConsumptions, function(entry, index) {
            return entry.capacity.economy > 0
        }).length > 0
        hasCapacity.business = $.grep(linkConsumptions, function(entry, index) {
            return entry.capacity.business > 0
        }).length > 0
        hasCapacity.first = $.grep(linkConsumptions, function(entry, index) {
            return entry.capacity.first > 0
        }).length > 0

		$.each(linkConsumptions.reverse(), function(key, linkConsumption) {
			var capacity = linkConsumption.capacity.economy + linkConsumption.capacity.business + linkConsumption.capacity.first
			var soldSeats = linkConsumption.soldSeats.economy + linkConsumption.soldSeats.business + linkConsumption.soldSeats.first
			var cancelledSeats = linkConsumption.cancelledSeats.economy + linkConsumption.cancelledSeats.business + linkConsumption.cancelledSeats.first
			emptySeatsData.push({ value : capacity - soldSeats - cancelledSeats  })
			cancelledSeatsData.push({ value : cancelledSeats  })
			
			soldSeatsData.economy.push({ value : linkConsumption.soldSeats.economy })
			soldSeatsData.business.push({ value : linkConsumption.soldSeats.business })
			soldSeatsData.first.push({ value : linkConsumption.soldSeats.first })
			
			revenueByClass.economy.push({ value : linkConsumption.price.economy * linkConsumption.soldSeats.economy })
			revenueByClass.business.push({ value : linkConsumption.price.business * linkConsumption.soldSeats.business })
			revenueByClass.first.push({ value : linkConsumption.price.first * linkConsumption.soldSeats.first })

			if (hasCapacity.economy) {
			    priceByClass.economy.push({ value : linkConsumption.price.economy })
			}
			if (hasCapacity.business) {
			    priceByClass.business.push({ value : linkConsumption.price.business })
            }
            if (hasCapacity.first) {
			    priceByClass.first.push({ value : linkConsumption.price.first })
			}

			var mark = getGameDate(linkConsumption.cycle)
			//var week = linkConsumption.cycle % 4 + 1
			category.push({ label : mark.toString()})
		})
	}

	var chartConfig = {
                      	    		"xAxisname": xLabel,
                      	    		"YAxisName": "Seats Consumption",
                      	    		//"sYAxisName": "Load Factor %",
                      	    		"sNumberSuffix" : "%",
                      	            "sYAxisMaxValue" : "100",
                      	            "transposeAxis":"1",
                      	    		"useroundedges": "1",
                      	    		"animation": "0",
                      	    		"showBorder":"0",
                                      "toolTipBorderRadius": "2",
                                      "toolTipPadding": "5",
                                      "plotBorderAlpha": "10",
                                      "usePlotGradientColor": "0",
                                      "paletteColors": "#007849,#0375b4,#ffce00,#D46A6A,#bbbbbb",
                                      "bgAlpha":"0",
                                      "showValues":"0",
                                      "canvasPadding":"0",
                                    "labelDisplay": "rotate",
      								"slantLabel": "1",
                                    "labelStep": weeksPerMark
                      	    	}

	checkDarkTheme(chartConfig, true)
	
	var ridershipChart = ridershipContainer.insertFusionCharts( {
		type: 'stackedarea2d',
	    width: '100%',
	    height: '100%',
	    dataFormat: 'json',
	    containerBackgroundOpacity :'0',
		dataSource: {
	    	"chart": chartConfig,
	    	"categories" : [{ "category" : category}],
			"dataset" : [
			              {"seriesName": "Sold Seats (Economy)", "data" : soldSeatsData.economy}
						 ,{"seriesName": "Sold Seats (Business)","data" : soldSeatsData.business}
						 ,{"seriesName": "Sold Seats (First)", "data" : soldSeatsData.first}
						 ,{ "seriesName": "Cancelled Seats", "data" : cancelledSeatsData}
						 ,{ "seriesName": "Empty Seats", "data" : emptySeatsData}
			            ]
	    }
	})

	chartConfig = {
	"xAxisname": xLabel,
    	    		"YAxisName": "Revenue",
    	    		//"sYAxisName": "Load Factor %",
    	    		"sYAxisMaxValue" : "100",
    	    		"transposeAxis":"1",
    	    		"useroundedges": "1",
    	    		"numberPrefix": "$",
    	    		"animation": "0",
    	    		"showBorder":"0",
                    "toolTipBorderRadius": "2",
                    "toolTipPadding": "5",
                    "plotBorderAlpha": "10",
                    "usePlotGradientColor": "0",
                    "paletteColors": "#007849,#0375b4,#ffce00",
                    "bgAlpha":"0",
                    "showValues":"0",
                    "canvasPadding":"0",
                    "labelDisplay": "rotate",
					"slantLabel": "1",
    	            "labelStep": weeksPerMark}
	checkDarkTheme(chartConfig, true)
	
	var revenueChart = revenueContainer.insertFusionCharts( {
    	type: 'stackedarea2d',
	    width: '100%',
	    height: '100%',
	    dataFormat: 'json',
	    containerBackgroundOpacity :'0',
		dataSource: {
	    	"chart": chartConfig,
	    	"categories" : [{ "category" : category}],
			"dataset" : [
			              {"seriesName": "Revenue (Economy)", "data" : revenueByClass.economy}
						 ,{"seriesName": "Revenue (Business)","data" : revenueByClass.business}
						 ,{"seriesName": "Revenue (First)", "data" : revenueByClass.first}
			            ]
	   }	
	})

	chartConfig =  {
                  	    		"xAxisname": xLabel,
                  	    		"YAxisName": "Ticket Price",
                  	    		//"sYAxisName": "Load Factor %",
                  	    		"numberPrefix": "$",
                  	    		"sYAxisMaxValue" : "100",
                  	    		"useroundedges": "1",
                  	    		"transposeAxis":"1",
                  	    		"animation": "0",
                  	    		"showBorder":"0",
                  	    		"drawAnchors": "0",
                                  "toolTipBorderRadius": "2",
                                  "toolTipPadding": "5",
                                  "paletteColors": "#007849,#0375b4,#ffce00",
                                  "bgAlpha":"0",
                                  "showValues":"0",
                                  "canvasPadding":"0",
                                  "formatNumberScale" : "0",
                                "labelDisplay": "rotate",
      							"slantLabel": "1",
                  	            "labelStep": weeksPerMark
                  	    	}
    checkDarkTheme(chartConfig, true)
	
	var priceChart = priceContainer.insertFusionCharts( {
    	type: 'msline',
	    width: '100%',
	    height: '100%',
	    dataFormat: 'json',
	    containerBackgroundOpacity :'0',
		dataSource: {
	    	"chart": chartConfig,
	    	"categories" : [{ "category" : category}],
			"dataset" : [
			              {"seriesName": "Price (Economy)", "data" : priceByClass.economy}
						 ,{"seriesName": "Price (Business)","data" : priceByClass.business}
						 ,{"seriesName": "Price (First)", "data" : priceByClass.first}
			            ]
	   }	
	})
}

function plotRivalHistory(rivalHistory, container, cycleHoverFunc, chartOutFunc) {
    container.children(':FusionCharts').each((function(i) {
		  $(this)[0].dispose();
	}))
	var dataByAirlineId = {}
	var airlineNameByAirlineId = {}
	var emptySeatsData = []
	var cancelledSeatsData = []
	var soldSeatsData = {
			economy : [],
			business : [],
	        first : []
	}

	var category = []
    var xLabel = 'Week'
	var maxCapacity = 0

	if (!jQuery.isEmptyObject(rivalHistory)) {
	    //collect airline ids involved
		$.each(rivalHistory, function(cycle, linkConsumptionByAirline) {
		    $.each(linkConsumptionByAirline, function(airlineId, entry) {
		        airlineNameByAirlineId[airlineId] = entry.airlineName
		    })
            category.push({ label : cycle, cycle : cycle })
		})
		$.each(airlineNameByAirlineId, function(airlineId, airlineName) {
            dataByAirlineId[airlineId] = []
        })

        $.each(rivalHistory, function(cycle, linkConsumptionByAirline) {
            $.each(airlineNameByAirlineId, function(airlineId, airlineName) {
                passenger = linkConsumptionByAirline[airlineId] ? linkConsumptionByAirline[airlineId].passenger : 0
                dataByAirlineId[airlineId].push({ value : passenger, cycle : cycle})
            })
        })
	}

    var dataset = []
    var paletteColors = []
    $.each(dataByAirlineId, function(airlineId, data) {
        var color
        if (airlineColors[airlineId]) {
            color = airlineColors[airlineId]
        } else {
            color = colorFromString(airlineNameByAirlineId[airlineId]) //find a random color but steady one
        }

        if (airlineId == activeAirline.id) { //always put own airline first (bottom)
            dataset.unshift({ seriesName: airlineNameByAirlineId[airlineId], "data" : data})
            paletteColors.unshift(color)
        } else {
            dataset.push({ seriesName: airlineNameByAirlineId[airlineId], "data" : data})
            paletteColors.push(color)
        }
    })

    var chartConfig = {
                        "xAxisname": xLabel,
                        "YAxisName": "Seats Consumption",
                        //"sYAxisName": "Load Factor %",
                        //"YAxisMaxValue" : maxCapacity * 1.2,
                        "transposeAxis":"1",
                        "useroundedges": "1",
                        "animation": "0",
                        "showBorder":"0",
                          "toolTipBorderRadius": "2",
                          "toolTipPadding": "5",
                          "plotBorderAlpha": "10",
                          "usePlotGradientColor": "0",
                          "bgAlpha":"0",
                          "showValues":"0",
                          "paletteColors": paletteColors.toString(),
                          "canvasPadding":"0",
                          "labelDisplay":"wrap",
                          "drawCrossLine": "1",
                          "crossLineColor": "#CDFCF6",
                          "crossLineAlpha": "50",
                          "showPlotBorder": "0",
                          "plotToolText": "$seriesName : $value"
                    }

	checkDarkTheme(chartConfig, true)

	//var ridershipChart = container.insertFusionCharts( {
	var ridershipChart = new FusionCharts( {
		type: 'stackedcolumn2d',
	    width: '100%',
	    height: '100%',
	    dataFormat: 'json',
	    renderAt: container[0].id,
	    containerBackgroundOpacity :'0',
		dataSource: {
	    	"chart": chartConfig,
	    	"categories" : [
            {
            "verticallinecolor": "666666",
            "verticallinethickness": "3",
            "alpha": "100",
            "category" : category}],
			"dataset" : dataset,

	    },
	    events : {
            "dataplotrollover": function (eventObj, dataObj) {
                if (cycleHoverFunc != null) {
                    cycleHoverFunc(category[dataObj.index].cycle)
                }

            },
            "chartRollOut": function (eventObj, dataObj) {
                if (chartOutFunc != null) {
                    chartOutFunc()
                }
            }
	    }
	}).render()
	return ridershipChart
}

function plotLinkEvent(linkConsumptions, linkEventContainer, cycleHoverFunc, chartOutFunc) {
	linkEventContainer.children(':FusionCharts').each((function(i) {
		  $(this)[0].dispose();
	}))
	var emptySeatsData = []
	var cancelledSeatsData = []
	var soldSeatsData = {
			economy : [],
			business : [],
	        first : []
	}


	var category = []
    var xLabel = 'Week'
	var maxCapacity = 0

	if (!jQuery.isEmptyObject(linkConsumptions)) {
		var hasCapacity = {} //check if there's any capacity for this link class at all
        hasCapacity.economy = $.grep(linkConsumptions, function(entry, index) {
            return entry.capacity.economy > 0
        }).length > 0
        hasCapacity.business = $.grep(linkConsumptions, function(entry, index) {
            return entry.capacity.business > 0
        }).length > 0
        hasCapacity.first = $.grep(linkConsumptions, function(entry, index) {
            return entry.capacity.first > 0
        }).length > 0


		$.each(linkConsumptions.reverse(), function(key, linkConsumption) {
			var capacity = linkConsumption.capacity.economy + linkConsumption.capacity.business + linkConsumption.capacity.first
			var soldSeats = linkConsumption.soldSeats.economy + linkConsumption.soldSeats.business + linkConsumption.soldSeats.first
			var cancelledSeats = linkConsumption.cancelledSeats.economy + linkConsumption.cancelledSeats.business + linkConsumption.cancelledSeats.first
			emptySeatsData.push({ value : capacity - soldSeats - cancelledSeats, cycle : linkConsumption.cycle  })
			cancelledSeatsData.push({ value : cancelledSeats, cycle : linkConsumption.cycle  })

			soldSeatsData.economy.push({ value : linkConsumption.soldSeats.economy, cycle : linkConsumption.cycle })
			soldSeatsData.business.push({ value : linkConsumption.soldSeats.business, cycle : linkConsumption.cycle })
			soldSeatsData.first.push({ value : linkConsumption.soldSeats.first, cycle : linkConsumption.cycle })

			if (capacity > maxCapacity) {
			    maxCapacity = capacity
			}

			//var mark = Math.floor(linkConsumption.cycle / weeksPerMark)
			//var week = linkConsumption.cycle % 4 + 1
			category.push({ label : linkConsumption.cycle.toString() , cycle : linkConsumption.cycle })



		})
	}

	var chartConfig = {
                      	    		"xAxisname": xLabel,
                      	    		"YAxisName": "Seats Consumption",
                      	    		//"sYAxisName": "Load Factor %",
                      	    		"YAxisMaxValue" : maxCapacity * 1.2,
                      	            "transposeAxis":"1",
                      	    		"useroundedges": "1",
                      	    		"animation": "0",
                      	    		"showBorder":"0",
                                      "toolTipBorderRadius": "2",
                                      "toolTipPadding": "5",
                                      "plotBorderAlpha": "10",
                                      "usePlotGradientColor": "0",
                                      "paletteColors": "#007849,#0375b4,#ffce00,#D46A6A,#bbbbbb",
                                      "bgAlpha":"0",
                                      "showValues":"0",
                                      "canvasPadding":"0",
                                      "labelDisplay":"wrap",
                                      "drawCrossLine": "1",
                                      "crossLineColor": "#CDFCF6",
                                      "crossLineAlpha": "50",
                                      "showLegend": "0",
                                      "showPlotBorder": "0",
                                      "plotToolText": "$seriesName : $value"
                      	    	}

	checkDarkTheme(chartConfig, true)

	var currentWeekHover = 0


	//var ridershipChart = linkEventContainer.insertFusionCharts( {
	var ridershipChart = new FusionCharts( {
		type: 'stackedcolumn2d',
	    width: '100%',
	    height: '100%',
	    dataFormat: 'json',
	    renderAt: linkEventContainer[0].id,
	    containerBackgroundOpacity :'0',
		dataSource: {
	    	"chart": chartConfig,
	    	"categories" : [
            {
            "verticallinecolor": "666666",
            "verticallinethickness": "3",
            "alpha": "100",
            "category" : category}],
			"dataset" : [
			              {"seriesName": "Sold Seats (Economy)", "type": "economy", "data" : soldSeatsData.economy} //type is just our custom attribute, nothing to do with fusioncharts
						 ,{"seriesName": "Sold Seats (Business)", "type": "business","data" : soldSeatsData.business}
						 ,{"seriesName": "Sold Seats (First)", "type": "first", "data" : soldSeatsData.first}
						 ,{ "seriesName": "Cancelled Seats", "type": "cancelled", "data" : cancelledSeatsData}
						 ,{ "seriesName": "Empty Seats", "type": "empty", "data" : emptySeatsData, "alpha":"10"}

			            //, {"seriesName": "Load Factor", "renderAs" : "line", "parentYAxis": "S", "data" : loadFactorData}
			            ],

	    },
	    events : {
            "dataplotrollover": function (eventObj, dataObj) {
                if (cycleHoverFunc != null) {
                    cycleHoverFunc(category[dataObj.index].cycle)
                }

            },
            "chartRollOut": function (eventObj, dataObj) {
                if (chartOutFunc != null) {
                    chartOutFunc()
                }
            }
	    }
	}).render()
	return ridershipChart
}


function toggleLinkEventBar(chart, cycle, on) {
    if (chart && chart.lastToggledCycle == cycle) {
        return
    }

    chart.lastToggledCycle = cycle
    //chart.setData()
    var jsonData = chart.getJSONData()
    $.each(jsonData.dataset, function(i, entry) {
      $.each(entry.data, function(i, entry) {
        if (entry.type == "empty") {
            return //do not change alpha on empty seat bar
        }
        if (on) { //toggle on the target cycle bar
            if (entry.cycle != cycle) { //all other cycle should then have lower alpha
              entry.alpha = "30"
            } else {
              entry.alpha = "100"
            }
        } else { //reset everyone
          entry.alpha = "100"
        }
      })
    })
    chart.setJSONData(jsonData)
}


function plotCumulativeCapacityChart(linkHistoryEntries, containerId) {
    const container = $(`#${containerId}`);
    container.children(':FusionCharts').each(function() {
        $(this)[0].dispose(); 
    });
    container.empty(); 

    if (!Array.isArray(linkHistoryEntries) || linkHistoryEntries.length === 0) {
        container.append("<div style='text-align: center; padding: 20px;'>No data available to plot cumulative capacity changes.</div>");
        return; 
    }

    const sortedEntries = [...linkHistoryEntries].sort((a, b) => a.cycleDelta - b.cycleDelta);

    const cumulativeCapacityByAirline = {};
    const airlineNames = {};
    const cycles = [];

    // Pass 1: Collect unique cycles and airline names, calculate net change per cycle per airline
    const netChangeByCycleAirline = {}; // { cycleDelta: { airlineId: net_change, ... }, ...}
    const uniqueCycles = new Set();

    sortedEntries.forEach(entry => {
        uniqueCycles.add(entry.cycleDelta);
        const cycle = entry.cycleDelta;
        const airlineId = entry.airlineId;

        if (!netChangeByCycleAirline[cycle]) {
            netChangeByCycleAirline[cycle] = {};
        }
        if (!netChangeByCycleAirline[cycle][airlineId]) {
            netChangeByCycleAirline[cycle][airlineId] = 0;
        }
        netChangeByCycleAirline[cycle][airlineId] += entry.capacityDelta ? entry.capacityDelta.total : 0;

        airlineNames[airlineId] = entry.airlineName; 
    });

    const sortedCycles = Array.from(uniqueCycles).sort((a, b) => a - b);

    const cycleIndices = sortedCycles.reduce((map, cycleDelta, index) => {
        map[cycleDelta] = index;
        return map;
    }, {});

    // Pass 2: Calculate cumulative capacity for each airline across cycles
    // Use an intermediate storage aligned by cycle index
    const cumulativeDataPointsByAirline = {};

    sortedCycles.forEach(cycleDelta => {
        const cycleText = window.getCycleDeltaText(cycleDelta);
        cycles.push({
            label: cycleText
        }); 

        const currentCycleNetChanges = netChangeByCycleAirline[cycleDelta] || {};

        // Initialize all known airlines for this cycle index if not already present
        Object.keys(airlineNames).forEach(airlineId => {
            if (!cumulativeDataPointsByAirline[airlineId]) {
                cumulativeDataPointsByAirline[airlineId] = new Array(sortedCycles.length).fill(null);
            }

            const currentCycleIndex = cycleIndices[cycleDelta];

            let previousCumulative = 0;
            if (currentCycleIndex > 0) {
                for (let i = currentCycleIndex - 1; i >= 0; i--) {
                    if (cumulativeDataPointsByAirline[airlineId][i] !== null) {
                        previousCumulative = cumulativeDataPointsByAirline[airlineId][i].value;
                        break;
                    }
                }
            }

            const netChange = currentCycleNetChanges[airlineId] !== undefined ?
                currentCycleNetChanges[airlineId] :
                0; 

            const currentCumulative = previousCumulative + netChange;

            cumulativeDataPointsByAirline[airlineId][currentCycleIndex] = {
                value: currentCumulative
            };
        });
    });

    // Filter out airlines with zero cumulative change throughout the period
    const relevantAirlineIds = Object.keys(airlineNames).filter(airlineId => {
        const dataPoints = cumulativeDataPointsByAirline[airlineId];
        return dataPoints.some(dp => dp !== null && dp.value !== 0);
    });

    const dataset = relevantAirlineIds.map(airlineId => {
        const airlineName = airlineNames[airlineId];
        const color = window.airlineColors && window.airlineColors[airlineId] ? window.airlineColors[airlineId] : (window.colorFromString ? window.colorFromString(airlineName) : undefined);

        // Map data points, using "" for null to create gaps in the line
        const dataWithGaps = cumulativeDataPointsByAirline[airlineId].map(dp => dp === null ? "" : dp.value);

        const series = {
            seriesname: airlineName,
            data: dataWithGaps.map(value => ({
                    value: value
                })) 
        };
        if (color) {
            series._colorHint = color;
        }
        return series;
    });

    const paletteColors = dataset.map(series => series._colorHint).filter(color => color);

    // Define chart configuration
    const chartConfig = {
        "caption": "Cumulative Capacity Change History", 
        "xAxisname": "Weeks Ago", 
        "yAxisName": "Cumulative Capacity Change (Seats)", 
        "useroundedges": "1",
        "animation": "1",
        "showBorder": "0",
        "toolTipBorderRadius": "2",
        "toolTipPadding": "5",
        "bgAlpha": "0", 
        "showValues": "0", // Hide values on data points
        "showZeroPlane": "1",
        "zeroPlaneColor": "#AAAAAA",
        "zeroPlaneThickness": "1",
        "drawAnchors": "0", // Hide data point anchors
        "legendPosition": "bottom", 
        "showLegend": "1",
        "legendAllowDrag": "1", 
        "toolTipBgColor": "#444444", // Dark theme friendly tooltip background
        "toolTipColor": "#DDDDDD", // Dark theme friendly tooltip text
        "height": "350", 
        "formatNumberScale": "0", // Prevent large number formatting like K, M, B
        "yaxisscalingmode": "adaptive", 
        "plottooltext": "$seriesName: $dataValue seats (Week $label ago)", // Improved tooltip

    };

    if (window.checkDarkTheme) {
        window.checkDarkTheme(chartConfig, true); 
    }

    if (paletteColors.length > 0) {
        chartConfig.paletteColors = paletteColors.join(",");
    } else if (window.checkDarkTheme) {
        window.checkDarkTheme(chartConfig, false);
    }


    // Render the chart
    if (window.FusionCharts) {
        new window.FusionCharts({
            type: 'msline', 
            renderAt: containerId,
            width: '100%',
            height: chartConfig.height, 
            dataFormat: 'json',
            containerBackgroundOpacity: '0',
            dataSource: {
                "chart": chartConfig,
                "categories": [{
                    "category": cycles
                }], 
                "dataset": dataset 
            }
        }).render();
    } else {
        console.error("FusionCharts object not found. Cannot render cumulative capacity chart.");
        container.empty().append("<div style='text-align: center; padding: 20px; color: red;'>Chart rendering failed (FusionCharts not available).</div>");
    }
}


function stringHashCode(s) {
  var h = 0, l = s.length, i = 0;
  if ( l > 0 )
    while (i < l)
      h = (h << 5) - h + s.charCodeAt(i++) | 0;
  return h;
};

var rgbMask = 0xff
function colorFromString(s) {
  var hashCode = stringHashCode(s)
  var r = (hashCode & rgbMask)
  hashCode = hashCode >> 2
  var g = (hashCode & rgbMask)
  hashCode = hashCode >> 2
  var b = (hashCode & rgbMask)
  //adjust
  if (r < 64 && g < 64 && b < 64) { //too dark
    r *= 2
    g *= 2
    b *= 2
  }

  r = r.toString(16)
  g = g.toString(16)
  b = b.toString(16)


  const rr = (r.length < 2 ? '0' : '') + r
  const gg = (g.length < 2 ? '0' : '') + g
  const bb = (b.length < 2 ? '0' : '') + b

    return `#${rr}${gg}${bb}`
}

var defaultPieColors = {
    "Traveler" : "#F2CA88",
    "Elite" : "#6e1996",
    "Business" : "#4267B2",
    "Tourist" : "#FF9800",
    "Olympics" : "#008080",
    "Budget": "#c8d143",
    "Carefree": "#b287a3",
    "Swift": "#ff5a5f",
    "Comprehensive": "#9bf3f0",
    "Brand Conscious": "#fc7a57",
    "Elite": "#2e287d",
    "departure/arrival passengers" : "#FC7A57",
    "transit passengers": "#9BF3F0",
}


function plotPie(dataSource, currentKey, container, keyName, valueName) {
	container.children(':FusionCharts').each((function(i) {
		  $(this)[0].dispose();
	}))
	
	var data = []

	$.each(dataSource, function(key, dataEntry) {
	    var keyLabel, dataValue

	    if (keyName && valueName) {
            keyLabel = dataEntry[keyName],
            dataValue = dataEntry[valueName]
        } else {
            keyLabel = key
            dataValue = dataEntry
        }

        var entry = { label : keyLabel, value : dataValue }

		if (dataEntry.color) {
			entry.color = dataEntry.color
		} else if (defaultPieColors[keyLabel]) {
		    entry.color = defaultPieColors[keyLabel]
		} else {
		    entry.color = colorFromString(keyLabel)
		}
		
		if (currentKey && keyLabel == currentKey) {
			entry.issliced = "1"
		}
		
//		if (currentAirportId == airportShare.airportId) {
//			entry["sliced"] = true
//			entry["selected"] = true
//		}
		data.push(entry)
	})

    //sort by value for less chaotic arrangement
	data.sort(function(a, b){ return a.value - b.value } );


	var ref = container.insertFusionCharts({
		type: 'pie2d',
	    width: '100%',
	    height: '160px',
	    dataFormat: 'json',
	    containerBackgroundOpacity :'0',
		dataSource: {
	    	"chart": {
	    		"animation": "0",
	    		"pieRadius": "65",
	    		"showBorder":"0",
                "use3DLighting": "1",
                "showPercentInTooltip": "1",
                "decimals": "2",
                "toolTipBorderRadius": "2",
                "toolTipPadding": "5",
                "showHoverEffect":"1",
                "bgAlpha":"0",
                "canvasBgAlpha" : "0",
                "showPlotBorder":"0",
                "showLabels":"0",
                "showValues":"0",
                "plottooltext": "$label - Passengers : $datavalue ($percentValue)"
	    	},
			"data" : data
	    }
	})
}

function plotIncomeChart(airlineIncomes, period, container) {
	container.children(':FusionCharts').each((function(i) {
		  $(this)[0].dispose();
	}))
	
	var data = {}
	data["total"] = []
	data["links"] = []
	data["transactions"] = []
	data["others"] = []
	data["stockPrice"] = []
	var category = []
	 
	var profitByMonth = {}
	var monthOrder = []
	
	$.each(airlineIncomes, function(key, airlineIncome) {
		data["total"].push({ value : airlineIncome.totalProfit })
		data["links"].push({ value : airlineIncome.linksProfit })
		data["transactions"].push({ value : airlineIncome.transactionsProfit })
		data["others"].push({ value : airlineIncome.othersProfit })
		data["stockPrice"].push({ value : airlineIncome.stockPrice.toFixed(2) })
		category.push({ "label" : getGameDate(airlineIncome.cycle) })
	})

	var chartConfig = {
                  	    		"xAxisname": "Week",
                  	    		"yAxisName": "Profit",
                  	    		"numberPrefix": "$",
                  	    		"useroundedges": "1",
                  	    		"animation": "1",
                  	    		"showBorder":"0",
                                  "toolTipBorderRadius": "2",
                                  "toolTipPadding": "5",
                                  "bgAlpha":"0",
                                  "showValues":"0",
                                  "showZeroPlane": "1",
                                  "zeroPlaneColor": "#222222",
                                  "zeroPlaneThickness": "2",
                  	    	}


    checkDarkTheme(chartConfig)
	
	var chart = container.insertFusionCharts({
		type: 'msline',
	    width: '100%',
	    height: '100%',
	    containerBackgroundOpacity :'0',
	    dataFormat: 'json',
		dataSource: {
	    	"chart": chartConfig,
	    	"categories" : [{ "category" : category}],
			"dataset" : [ 
				{ "seriesname": "Total Income", "data" : showEveryNthLabel(data["total"], 2), "visible" : "0"},
				{ "seriesname": "Flight Income", "data" : showEveryNthLabel(data["links"], 2)},
				{ "seriesname": "Transaction Income", "data" : showEveryNthLabel(data["transactions"], 2), "visible" : "0"},
				{ "seriesname": "Other Income", "data" : showEveryNthLabel(data["others"], 2), "visible" : "0"},
				{ "seriesname": "Stock Price", "data" : showEveryNthLabel(data["stockPrice"], 2), "visible" : "0"},
            ]
	    }
	})
}

function plotCashFlowChart(airlineCashFlows, period, container) {
	container.children(':FusionCharts').each((function(i) {
		  $(this)[0].dispose();
	}))
	
	var data = {}
	data["cashFlow"] = []
	var category = []
	 
	var profitByMonth = {}
	var monthOrder = []
	
	$.each(airlineCashFlows, function(key, airlineCashFlow) {
		data["cashFlow"].push({ value : airlineCashFlow.totalCashFlow })
		category.push({ "label" : getGameDate(airlineCashFlow.cycle) })
	})

	var chartConfig = {
        "xAxisname": "Period & Week",
        "yAxisName": "Profit",
        "numberPrefix": "$",
        "useroundedges": "1",
        "animation": "1",
        "showBorder":"0",
        "showLegend": "0",
        "toolTipBorderRadius": "2",
        "toolTipPadding": "5",
        "bgAlpha":"0",
        "showValues":"0",
        "showZeroPlane": "1",
        "zeroPlaneColor": "#222222",
        "zeroPlaneThickness": "2",
    }
    checkDarkTheme(chartConfig)
	
	var chart = container.insertFusionCharts({
		type: 'msline',
	    width: '100%',
	    height: '100%',
	    dataFormat: 'json',
	    containerBackgroundOpacity :'0',
		dataSource: {
	    	"chart": chartConfig,
	    	"categories" : [{ "category" : category}],
			"dataset" : [ 
				{ "seriesname": "Total CashFlow", "data" : showEveryNthLabel(data["cashFlow"], 2) }
			]
	    }
	})
}

function plotTotalValueChart(airlineValue, period, container) {
	container.children(':FusionCharts').each((function(i) {
		  $(this)[0].dispose();
	}))

	var data = {}
	data["totalValue"] = []
	var category = []

	var profitByMonth = {}
	var monthOrder = []

	$.each(airlineValue, function(key, airlineValue) {
		data["totalValue"].push({ value : airlineValue.totalValue })
		category.push({ "label" : getGameDate(airlineValue.cycle) })
	})

	var chartConfig = {
        "xAxisname": "Period & Week",
        "yAxisName": "Total Value",
        "numberPrefix": "$",
        "useroundedges": "1",
        "showLegend": "0",
        "animation": "1",
        "showBorder":"0",
        "toolTipBorderRadius": "2",
        "toolTipPadding": "5",
        "bgAlpha":"0",
        "showValues":"0",
        "showZeroPlane": "1",
        "zeroPlaneColor": "#222222",
        "zeroPlaneThickness": "2",
    }
    checkDarkTheme(chartConfig)

	var chart = container.insertFusionCharts({
		type: 'msline',
	    width: '100%',
	    height: '100%',
	    dataFormat: 'json',
	    containerBackgroundOpacity :'0',
		dataSource: {
	    	"chart": chartConfig,
	    	"categories" : [{ "category" : category}],
			"dataset" : [
				{ "seriesname": "Total Value", "data" : showEveryNthLabel(data["totalValue"], 2) }
			]
	    }
	})
}

function plotOpsChart(stats, container) {
	container.children(':FusionCharts').each(function () {
		$(this)[0].dispose();
	});

	const data = {
		RASK: [],
		CASK: [],
		satisfaction: [],
		loadFactor: [],
		onTime: [],
		hubDominance: []
	};
	const category = [];

	stats.forEach(stat => {
		data.RASK.push({ value: stat.RASK });
		data.CASK.push({ value: stat.CASK });
		data.satisfaction.push({ value: stat.satisfaction });
		data.loadFactor.push({ value: stat.loadFactor });
		data.onTime.push({ value: stat.onTime });
		data.hubDominance.push({ value: stat.hubDominance });
		category.push({ label: stat.cycle.toString() });
	});

	const chartConfig = {
		xAxisname: "Week",
		yAxisName: "Performance",
		numMinorDivLines: 1,
		divLineAlpha: 0,
		showValues: "0",
		showZeroPlane: "0",
		showBorder: "0",
		toolTipBorderRadius: "2",
		toolTipPadding: "5"
	};
	checkDarkTheme(chartConfig);

	const dataset = [
		{ seriesname: "RASK", data: showEveryNthLabel(data.RASK, 2) },
		{ seriesname: "CASK", data: showEveryNthLabel(data.CASK, 2) },
		{ seriesname: "Satisfaction", data: showEveryNthLabel(data.satisfaction, 2) },
		{ seriesname: "Load Factor", data: showEveryNthLabel(data.loadFactor, 2) },
		{ seriesname: "On Time", data: showEveryNthLabel(data.onTime, 2), "visible" : "0" },
		{ seriesname: "Hub Dominance", data: showEveryNthLabel(data.hubDominance, 2) }
	];

	container.insertFusionCharts({
		type: 'LogMSLine',
		width: '100%',
		height: '100%',
		dataFormat: 'json',
		containerBackgroundOpacity: '0',
		dataSource: {
			chart: chartConfig,
			categories: [{ category }],
			dataset
		}
	});
}

function plotAirlineStats(stats, container) {
    container.children(':FusionCharts').each(function () {
        $(this)[0].dispose();
    });

    const data = {
        total: [],
        traveler: [],
        tourists: [],
        elites: [],
        business: [],
        codeshares: []
    };
    const category = [];

    stats.forEach(stat => {
        data.total.push({ value: stat.total });
        data.traveler.push({ value: stat.traveler, color: defaultPieColors["Traveler"] });
        data.tourists.push({ value: stat.tourists, color: defaultPieColors["Tourist"] });
        data.elites.push({ value: stat.elites, color: defaultPieColors["Elite"] });
        data.business.push({ value: stat.business, color: defaultPieColors["Business"] });
        data.codeshares.push({ value: stat.codeshares });
        category.push({ label: stat.cycle.toString() });
    });

    const chartConfig = {
        xAxisname: "Week",
        yAxisName: "Passengers",
        numMinorDivLines: 1,
        divLineAlpha: 0,
        showValues: "0",
        showZeroPlane: "0",
        showBorder: "0",
        toolTipBorderRadius: "2",
        toolTipPadding: "5"
    };
    checkDarkTheme(chartConfig);

    const dataset = [
        { seriesname: "Total", data: showEveryNthLabel(data.total, 2) },
        { seriesname: "Tourist", data: showEveryNthLabel(data.tourists, 2) },
        { seriesname: "Elite", data: showEveryNthLabel(data.elites, 2) },
        { seriesname: "Business", data: showEveryNthLabel(data.business, 2) },
        { seriesname: "Traveler", data: showEveryNthLabel(data.traveler, 2), "visible" : "0" },
        { seriesname: "Codeshares", data: showEveryNthLabel(data.codeshares, 2) }
    ];

    container.insertFusionCharts({
        type: 'LogMSLine',
        width: '100%',
        height: '100%',
        dataFormat: 'json',
        containerBackgroundOpacity: '0',
        dataSource: {
            chart: chartConfig,
            categories: [{ category }],
            dataset
        }
    });
}

function plotOilPriceChart(oilPrices, container) {
	container.children(':FusionCharts').each((function(i) {
		  $(this)[0].dispose();
	}))
	
	var data = []
	var category = []
	var total = 0 
	var count = 0
	
	$.each(oilPrices, function(key, oilPrice) {
		data.push({ value : oilPrice.price })
		category.push({ "label" : oilPrice.cycle.toString() })
		total += oilPrice.price
		count ++;
	})
	
	var average
	if (count > 0)  {
		average = total / count
	} else {
		average = 0
	}

	var chartConfig = {
                      	    		"xAxisname": "Week",
                      	    		"yAxisName": "Oil Price Per Barrel",
                      	    		"numberPrefix": "$",
                      	    		"useroundedges": "1",
                      	    		"animation": "1",
                      	    		"showBorder":"0",
                      	    		"showValues": "0",
                                      "toolTipBorderRadius": "2",
                                      "toolTipPadding": "5",
                                      "bgAlpha":"0",
                                      "drawAnchors": "0",
                                      "setAdaptiveYMin":"1",
                                      "labelStep": "4"
                      	    	}
    checkDarkTheme(chartConfig)
	
	var chart = container.insertFusionCharts({
		type: 'msline',
	    width: '100%',
	    height: '100%',
	    containerBackgroundOpacity :'0',
	    dataFormat: 'json',
		dataSource: {
	    	"chart": chartConfig,
	    	"categories" : [{ "category" : category}],
			"dataset" : [{ "seriesname": "Price", "data" : data}],
			"trendlines": [{
	            "line": [
	                {
	                    "startvalue": average,
	                    "color": "#A1D490",
	                    "displayvalue": "Average",
	                    "valueOnRight": "1",
	                    "thickness": "2"
	                }
	            ]
	        }]
	    }
	})
}


function plotLoanInterestRatesChart(rates, container) {
	container.children(':FusionCharts').each((function(i) {
		  $(this)[0].dispose();
	}))

	var data = []
	var category = []
	var total = 0
	var count = 0

	$.each(rates, function(key, rate) {
	    var annualRate = rate.rate * 100 //to percentage based
		data.push({ value : annualRate.toFixed(1) })
		category.push({ "label" : rate.cycle.toString() })
		total += annualRate
		count ++;
	})

	var average
	if (count > 0)  {
		average = total / count
	} else {
		average = 0
	}

	var chartConfig = {
                      	    		"xAxisname": "Week",
                      	    		"yAxisName": "Base Annual Rate",
                      	    		"numberSuffix": "%",
                      	    		"useroundedges": "1",
                      	    		"animation": "1",
                      	    		"showBorder":"0",
                      	    		"showValues": "0",
                      	    		"drawAnchors": "0",
                                      "toolTipBorderRadius": "2",
                                      "toolTipPadding": "5",
                                      "bgAlpha":"0",
                                      "setAdaptiveYMin":"1",
                                      "labelStep": "4"
                      	    	}

    checkDarkTheme(chartConfig)
	var chart = container.insertFusionCharts({
		type: 'msline',
	    width: '100%',
	    height: '100%',
	    dataFormat: 'json',
	    containerBackgroundOpacity :'0',
		dataSource: {
	    	"chart": chartConfig,
	    	"categories" : [{ "category" : category}],
			"dataset" : [{ "seriesname": "Rate", "data" : data}],
			containerBackgroundOpacity :'0',
			"trendlines": [{
	            "line": [
	                {
	                    "startvalue": average.toFixed(1),
	                    "color": "#A1D490",
	                    "displayvalue": "Average",
	                    "valueOnRight": "1",
	                    "thickness": "2"
	                }
	            ]
	        }]
	    }
	})
}

function plotRivalHistoryChart(allRivalLinkConsumptions, priceContainer, linkClass, field, numberPrefix, currentAirlineId) {
	priceContainer.children(':FusionCharts').each((function(i) {
		  $(this)[0].dispose();
	}))

	var priceByAirline = {}

	var category = []

	var maxWeek = 24
	var weekCount = 0 //the rival with the most week count, usually this is just maxWeek

    var dataSet = []
    var maxValue = -1
    var minValue = 99999
    if (!jQuery.isEmptyObject(allRivalLinkConsumptions)) { //link consumptions is array (by each rival link) of array (by cycle),
	    $.each(allRivalLinkConsumptions, function(key, linkConsumptions) {
            if (linkConsumptions.length == 0) {
                return; //no consumptions yet
            }
            var newCategory = []
            var linkConsumptions = $(linkConsumptions).toArray().slice(0, maxWeek) //link consumptions for each rival link
            if (linkConsumptions.length > weekCount) { //check which rival has the longest history
                weekCount = linkConsumptions.length
            }

            var airlineName = linkConsumptions[0].airlineName
            var linkId = linkConsumptions[0].linkId
            priceHistory = []
            var lineColor = linkConsumptions[0].airlineId == currentAirlineId ? "#d84f4f" : "#f6bf1b"
            $.each(linkConsumptions.reverse(), function(key, linkConsumption) {
                var currentValue = linkConsumption[field][linkClass]
                if (currentValue > maxValue) {
                    maxValue = currentValue
                }
                if (currentValue < minValue) {
                    minValue = currentValue
                }
                priceHistory.push({ value : currentValue , color : lineColor})
                var month = Math.floor(linkConsumption.cycle / 4)
                //var week = linkConsumption.cycle % 4 + 1
                newCategory.push({ label : month.toString()})
            })
            dataSet.push({ "seriesName": airlineName, "data" : priceHistory})
            if (newCategory.length > category.length) { //take the longest length one
                category = newCategory
            }
       })

       //now pad at the beginning for those that have been around less than weekCount
       $.each(dataSet, function(index, dataEntry) {
            if (dataEntry["data"].length < weekCount) {
                var padLength = weekCount - dataEntry["data"].length
                for (i = 0; i < padLength; i++) {
                    dataEntry["data"].unshift({ "data" : ""})
                }
            }
       })
	}


     var yAxisMax = Math.round(maxValue * 1.1)
     var yAxisMin = Math.round(minValue * 0.8)

    var chartConfig = {
                      	    		"xAxisname": "Month",
                      	    		//"sYAxisName": "Load Factor %",
                      	    		"numberPrefix": numberPrefix,
                      	    		"sYAxisMaxValue" : "100",
                      	    		"useroundedges": "1",
                      	    		"transposeAxis":"1",
                      	    		"animation": "0",
                      	    		"showBorder":"0",
                      	    		"drawAnchors": "0",
                                      "toolTipBorderRadius": "2",
                                      "toolTipPadding": "5",
                                      "bgAlpha":"0",
                                      "showLegend": "0",
                                      "showValues":"0",
                                      "canvasPadding":"0",
                                      "labelDisplay":"wrap",
                      	            "labelStep": "4",
                      	            "formatNumber" : "0",
                      	            "formatNumberScale" : "0",
                      	            "yAxisMaxValue": yAxisMax,
                                      "yAxisMinValue": yAxisMin
                      	    	}
	checkDarkTheme(chartConfig)
	var priceChart = priceContainer.insertFusionCharts( {
    	type: 'msline',
	    width: '100%',
	    height: '100%',
	    dataFormat: 'json',
	    containerBackgroundOpacity :'0',
		dataSource: {
	    	"chart": chartConfig,
	    	"categories" : [{ "category" : category}],
			"dataset" : dataSet
	   }
	})
}

function plotLoyalistHistoryChart(loyalistHistory, container) {
	container.children(':FusionCharts').each((function(i) {
		  $(this)[0].dispose();
	}))

    if (jQuery.isEmptyObject(loyalistHistory)) {
        return
    }

	var category = []

	var dataSet = []
//    var maxValue = -1
//    var minValue = 99999
    var dataByAirlineId = {}
    var airlineNameByAirlineId = {}
    if (!jQuery.isEmptyObject(loyalistHistory)) { //link consumptions is array (by each rival link) of array (by cycle),

	    $.each(loyalistHistory, function(index, keyValue) {
            var cycle = keyValue[0]
            var cycleEntries = keyValue[1]
            category.push({ label : cycle.toString()})
            $.each(cycleEntries, function(index, entry) {
                var airlineId = entry.airlineId
                if (!dataByAirlineId[airlineId]) {
                    dataByAirlineId[airlineId] = []
                }
//                var lineColor = "#f6bf1b"
//                if (activeAirline && activeAirline.id == airlineId) {
//                    lineColor = "#d84f4f"
//                }
//                dataByAirlineId[airlineId].push({"value": entry.amount, "color": lineColor})
                dataByAirlineId[airlineId].push({"value": entry.amount})
                airlineNameByAirlineId[airlineId] = entry.airlineName
            })
        })

        $.each(airlineNameByAirlineId, function(airlineId, airlineName) {
            dataSet.push({ "seriesName": airlineName, "data" : dataByAirlineId[airlineId]})
        })
    }

//     var yAxisMax = Math.round(maxValue * 1.1)
//     var yAxisMin = Math.round(minValue * 0.8)
    var chartConfig = {
                      	    		"xAxisname": "Week",
                      	    		"yAxisName": "Loyalist Amount",
                      	    		"useroundedges": "1",
                      	    		"transposeAxis":"1",
                      	    		"animation": "0",
                      	    		"showBorder":"0",
                      	    		"drawAnchors": "0",
                                      "toolTipBorderRadius": "2",
                                      "toolTipPadding": "5",
                                      "bgAlpha":"0",
                      //                "showLegend": "0",
                                      "showplotBorder": "1",
                                       "plotHighlightEffect": "fadeout",
                                      "showValues":"0",
                                      "canvasPadding":"0",
                                      "labelDisplay":"wrap",
                      	            "labelStep": "4",
                      	            "formatNumber" : "0",
                      	            "formatNumberScale" : "0"
                      //	            "yAxisMaxValue": yAxisMax,
                      //                "yAxisMinValue": yAxisMin
                      	    	}
    checkDarkTheme(chartConfig)
	var loyalistHistoryChart = container.insertFusionCharts( {
    	type: 'logmsline',
	    width: '100%',
	    height: '100%',
	    dataFormat: 'json',
	    containerBackgroundOpacity :'0',
		dataSource: {
	    	"chart": chartConfig,
	    	"categories" : [{ "category" : category}],
			"dataset" : dataSet
	   }
	})
}

function plotMissionStatsGraph(stats, threshold, container) {
	container.children(':FusionCharts').each((function(i) {
		  $(this)[0].dispose();
	}))

	var data = []
	var category = []

	$.each(stats, function(i, entry) {
		data.push({ value : entry })
		category.push({ label : "week " + i})
	})


	var chartConfig = {
                      	    		"xAxisname": "Week",
                      	    		"yAxisName": "Achieved Value",
                      	    		"useroundedges": "1",
                      	    		"animation": "1",
                      	    		"showBorder":"0",
                      	    		"showValues": "0",
                                      "toolTipBorderRadius": "2",
                                      "toolTipPadding": "5",
                                      "bgAlpha":"0",
                                      "drawAnchors": "0",
                                      "setAdaptiveYMin":"1",
                                      "labelStep": "10"
                      	    	}
    checkDarkTheme(chartConfig)

    var dataSource = {
                        "chart": chartConfig,
                        "categories" : [{ "category" : category}],
                        "dataset" : [{ "seriesname": "Value", "data" : data}],
                    }
    if (threshold) {
        dataSource["trendlines"] = [{
                             	            "line": [
                             	                {
                             	                    "startvalue": threshold,
                             	                    "color": "#A1D490",
                             	                    "displayvalue": "Threshold",
                             	                    "valueOnRight": "1",
                             	                    "thickness": "2"
                             	                }
                             	            ]
                             	        }]
    }

	var chart = container.insertFusionCharts({
		type: 'msline',
	    width: '100%',
	    height: '100%',
	    containerBackgroundOpacity :'0',
	    dataFormat: 'json',
		dataSource: dataSource
	})
}


function checkDarkTheme(chartConfig, keepPallette) {
    if (document.documentElement.getAttribute("data-theme") === "dark") {
    //if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
            chartConfig.bgAlpha = "0"
            chartConfig.legendBgAlpha = "0"
            chartConfig.canvasBgAlpha = "10"
            chartConfig.canvasBgColor = "#DDDDDD"
            chartConfig.showAlternateHGridColor ="0"
            chartConfig.useRoundEdges = "0"
            if (!keepPallette) {
                chartConfig.palettecolors = "80CED7,FFF07C,80FF72,EEC0C6, 9067C6, E58C8A"
            }
            chartConfig.baseFontColor = "#DDDDDD"
            chartConfig.usePlotGradientColor = "0"
            chartConfig.legendBgColor = "#DDDDDD"
            chartConfig.legendBgAlpha = "10"
            chartConfig.toolTipBgColor = "#444444"
            chartConfig.toolTipColor = "#DDDDDD"
            chartConfig.valueFontColor = "#DDDDDD"


    //                "legendIconAlpha": "50",
    //                "legendIconBgAlpha": "30",
    //                "legendIconBorderColor": "#123456",
    //                "legendIconBorderThickness": "3"
    }
}

function showEveryNthLabel(rawData, displayInterval) {
	let data = [];
	for (let i = 0; i < rawData.length; i++) {
		const dataPoint = {
			value: rawData[i].value
		};
	
		// Check if this is the Nth item (using 1-based index logic)
		if ((i + 1) % displayInterval === 0) {
			// Add displayValue only for every 4th item
			dataPoint.showValue = "1"; // Or format as needed, e.g., "$" + rawData[i].value
		}
	
		data.push(dataPoint);
	}
	return data;
}
