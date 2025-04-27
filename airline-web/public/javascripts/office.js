var loadedIncomes = {}
var loadedCashFlows = {}
var officeSheetPage = 0;
var officePeriod;

var logoUploaderObj;
var liveryUploaderObj;

var companyValue = 0;

$( document ).ready(function() {
	loadLogoTemplates()
//	$('#colorpicker1').farbtastic($('#logoColor1'));
//	$('#colorpicker2').farbtastic($('#logoColor2'));
	
//	var $box = $('#colorPicker1');
//    $box.tinycolorpicker();
//    var picker = $('#colorPicker1').data("plugin_tinycolorpicker");
//    picker.setColor("#000000");
//    $box.bind("change", function() {
//        generateLogoPreview()
//    });
//
//    $box = $('#colorPicker2');
//    $box.tinycolorpicker();
//    picker = $('#colorPicker2').data("plugin_tinycolorpicker");
//    picker.setColor("#FFFFFF");
//
//    $box.bind("change", function() {
//		generateLogoPreview()
//    });

    $('#logoModal .picker.color1').val("#000000")
    $('#logoModal .picker.color2').val("#FFFFFF")

    $('#logoModal .picker').change(function() {
        generateLogoPreview()
    });

    
    var $airlineColorPicker = $('#officeCanvas .airlineColor .picker')
    $airlineColorPicker.change(function() {
        setAirlineColor($(this).val())
    });
})

function showOfficeCanvas() {
	setActiveDiv($("#officeCanvas"))
	highlightTab($('.officeCanvasTab'))

	updateAirlineDetails()
	loadSheets();
	updateResetAirlineInfo()
	//updateAirlineDelegateStatus($('#officeCanvas .delegateStatus'))
	updateAirlineAssets()
	updateCampaignSummary()
	updateChampionedCountriesDetails()
	updateChampionedAirportsDetails()
	updateServiceFundingDetails()
	updateMinimumRenewalBalanceDetails()
	updateAirplaneRenewalDetails()
	updateAirlineBases()
	updateAirlineColorPicker()
	updateHeadquartersMap($('#officeCanvas .headquartersMap'), activeAirline.id)
	updateLiveryInfo()
	loadSlogan(function(slogan) { $('#officeCanvas .slogan').val(slogan)})
}

function updateCampaignSummary() {
    $.ajax({
        type: 'GET',
        url: "airlines/" + activeAirline.id + "/campaigns?fullLoad=false",
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(result) {
            var $campaignSummary = $("#officeCanvas .campaignSummary")
            $campaignSummary.children("div.table-row").remove()

            $.each(result, function(index, campaign) {
                var row = $("<div class='table-row'></div>")
                row.append("<div class='cell'>" + getCountryFlagImg(campaign.principalAirport.countryCode) + getAirportText(campaign.principalAirport.city, campaign.principalAirport.iata) + "</div>")
                row.append("<div class='cell'>" + campaign.population + "</div>")

                $campaignSummary.append(row)
            });
            if (result.length == 0) {
                $campaignSummary.append("<div class='table-row'><div class='cell'>-</div><div class='cell'>-</div></div>")
            }
        },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
    });
}

function updateAirlineAssets() {
    $.ajax({
        type: 'GET',
        url: "airlines/" + activeAirline.id + "/airport-assets",
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(result) {
            var $assetList = $("#officeCanvas .assetList")
            $assetList.children("div.table-row").remove()

            $.each(result, function(index, asset) {
                var profit = asset.revenue - asset.expense
                var margin
                if (asset.expense == 0) {
                    margin = "-"
                } else {
                    margin = (profit * 100.0 / asset.revenue).toFixed(2)
                }
                var $row = $("<div class='table-row clickable'></div>")
                $row.append("<div class='cell'>" + getCountryFlagImg(asset.airport.countryCode) + asset.airport.iata + "</div>")
                $row.append("<div class='cell'>" + asset.name + "</div>")
                $row.append("<div class='cell' style='text-align: right;'>" + asset.level + "</div>")
                $row.append("<div class='cell' style='text-align: right;'>$" + commaSeparateNumber(profit) + "</div>")
                $row.append("<div class='cell' style='text-align: right;'>" + margin  + "%</div>")

                $row.click(function() {
                    showAssetModal(asset)
                    $("#airportAssetDetailsModal").data('postUpdateFunc', function() {
                        updateAirlineAssets()
                    })
                })
                $assetList.append($row)
            });
            if (result.length == 0) {
                $assetList.append("<div class='table-row'><div class='cell'>-</div><div class='cell'>-</div><div class='cell'>-</div><div class='cell'>-</div><div class='cell'>-</div></div>")
            }
        },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
    });
}



function updateAirlineBases() {
        $('#officeCanvas .bases').children('.table-row').remove()

    var airlineId = activeAirline.id
    	var url = "airlines/" + airlineId + "/office-capacity"
        $.ajax({
    		type: 'GET',
    		url: url,
    	    contentType: 'application/json; charset=utf-8',
    	    dataType: 'json',
    	    success: function(officeCapacity) {
    	    	 $(activeAirline.baseAirports).each(function(index, base) {
                    var row = $("<div class='table-row clickable' data-link='airport' onclick='showAirportDetails(" + base.airportId+ ")'></div>")
                    if (base.headquarter) {
                        row.append($("<div class='cell'><img src='assets/images/icons/building-hedge.png' class='pr-1'><span style='font-size: 130%;vertical-align: top;'>" + base.scale + "</span></div><div class='cell'>" + getCountryFlagImg(base.countryCode) + base.city + " " + base.airportCode + "</div>"))

                    } else {
                        row.append($("<div class='cell'><img src='assets/images/icons/building-low.png' class='pr-1'><span style='font-size: 130%;vertical-align: top;'>" + base.scale + "</span></div><div class='cell'>" + getCountryFlagImg(base.countryCode) + base.city + " " + base.airportCode + "</div>"))
                    }
                    var capacityInfo = officeCapacity[base.airportId]
                    var required = (capacityInfo.staffCapacity < capacityInfo.currentStaffRequired) ? "<span class='fatal'>" + capacityInfo.currentStaffRequired + "</span>" : capacityInfo.currentStaffRequired

                    if (capacityInfo.currentStaffRequired != capacityInfo.futureStaffRequired) {
                        row.append($("<div class='cell'>" + required + " | " + capacityInfo.staffCapacity + "<span>(future: " + capacityInfo.futureStaffRequired + ")</span></div>"))
                    } else {
                        row.append($("<div class='cell'>" + required + " | " + capacityInfo.staffCapacity + "</div>"))
                    }

                    var $overtimeCompensationDiv
                    if (capacityInfo.currentOvertimeCompensation == 0) {
                        $overtimeCompensationDiv = $("<div class='cell'>-</div>").appendTo(row)
                     } else {
                        $overtimeCompensationDiv = $("<div class='cell'>$" + commaSeparateNumber(capacityInfo.currentOvertimeCompensation) + "</div>").appendTo(row)
                     }

                     if (capacityInfo.currentOvertimeCompensation != capacityInfo.futureOvertimeCompensation) {
                        $overtimeCompensationDiv.append("<span>(future : $" + capacityInfo.futureOvertimeCompensation + ")</span>")
                     }

                    if (base.headquarter) {
                        $('#officeCanvas .bases .table-header').after(row)
                    } else {
                       $('#officeCanvas .bases').append(row)
                    }
                })
                if (!activeAirline.baseAirports || activeAirline.baseAirports.length == 0) {
                    $('#officeCanvas .bases').append("<div class='table-row'><div class='cell'></div></div>")
                }
                populateNavigation($('#officeCanvas .bases'))
    	    },
            error: function(jqXHR, textStatus, errorThrown) {
    	            console.log(JSON.stringify(jqXHR));
    	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
    	    }
    	});
}

function setProgressWidth(elemId, current, past, next){
    const percent = Math.max((current - past) / (next - past) * 100, 0)
    const red = Math.max((percent > 15 ? 250 - (percent - 15) * 4 : 250).toFixed(0), 0)
    const green = Math.min(percent * 3, 230)
    $(elemId).css({'width': percent + "%", 'background-color': "rgba("+red+","+green+",0,0.65)"})
}

function addProgressGrades(track, grades){
    const progressBar = document.querySelector(`#${track}Progress .threshold-bar`);
    const maxGrade = Math.max(...grades);
    grades.forEach((grade, index) => {
      const dot = document.createElement('div');
      dot.classList.add('dot');
      dot.title = `Level ${index + 1} at ${grade} ${track}`;

      const positionPercentage = (grade / maxGrade) * 100;
      dot.style.left = `${positionPercentage}%`;

      progressBar.appendChild(dot);
    });
}

function updateProgress(stats, stockPrice){
//    if(! stats && ! stockPrice){
//        return null
//    }
    const shortNumber = (number) => {
        if(number >= 10000){
            return (number / 1000).toFixed(0).toLocaleString() + "k"
        } else {
            return number.toFixed(0).toLocaleString()
        }
    }

    var $starBar = $(getGradeStarsImgs(activeAirline.gradeLevel - 2, 20))

    $('.reputationValueCurrent').text(activeAirline.reputation)
    $("#officeCanvas .reputationStars").html($starBar)

//    $('.stockValueCurrent').text("$" + stockPrice.toFixed(2))
    $('.touristsValueCurrent').text(shortNumber(activeAirline.tourists.tourists))
    $('.elitesValueCurrent').text(shortNumber(activeAirline.elites.elites))

    $('.reputationText').text(activeAirline.gradeDescription)
//    $('.stockText').text(activeAirline.stock.stockDescription)
    $('.touristsText').text(activeAirline.tourists.touristsDescription)
    $('.elitesText').text(activeAirline.elites.elitesDescription)

    $('.reputationLevel').text("Level " + activeAirline.gradeLevel)
//    $('.stockLevel').text("Level " + activeAirline.stock.stockLevel)
    $('.touristsLevel').text("Level " + activeAirline.tourists.touristsLevel)
    $('.elitesLevel').text("Level " + activeAirline.elites.elitesLevel)

    $('.reputationTrend').text((activeAirline.reputationBreakdowns.total).toFixed(0))
    $('.reputationValueNext').text(activeAirline.gradeCeiling)
//    $('.stockValueNext').text("$"+activeAirline.stock.stockCeiling)
    $('.touristsValueNext').text(activeAirline.tourists.touristsCeiling)
    $('.elitesValueNext').text(activeAirline.elites.elitesCeiling)
    $('.reputationValuePrev').text(activeAirline.gradeFloor)

    setProgressWidth("#reputationBar", activeAirline.reputation, activeAirline.gradeFloor, activeAirline.gradeCeiling)
//    setProgressWidth("#stockBar", stockPrice, 0, activeAirline.stock.grades.at(-1))
//    addProgressGrades("stock", activeAirline.stock.grades)
    setProgressWidth("#touristsBar", activeAirline.tourists.tourists, 0, activeAirline.tourists.grades.at(-1))
    addProgressGrades("tourists", activeAirline.tourists.grades)
    setProgressWidth("#elitesBar", activeAirline.elites.elites, 0, activeAirline.elites.grades.at(-1))
    addProgressGrades("elites", activeAirline.elites.grades)

//    updateMilestones(activeAirline.reputationBreakdowns.breakdowns)
}

function updateMilestones(breakdowns) {
  let total = 0;

  for (const breakdown of breakdowns) {
    if (breakdown.description.toLowerCase().includes("milestone")) {
      total += breakdown.value;
      if(breakdown.description === "Milestone Aircraft Types" && breakdown.value >= 0){
        if(breakdown.value >= 5){
            document.getElementById("m-aircraft1").src = "/assets/images/icons/tick.png"
        }
        if(breakdown.value >= 15){
            document.getElementById("m-aircraft2").src = "/assets/images/icons/tick.png"
        }
        if(breakdown.value >= 25){
            document.getElementById("m-aircraft3").src = "/assets/images/icons/tick.png"
        }
      } else if(breakdown.description === "Milestone Countries Served" && breakdown.value >= 0){
        if(breakdown.value >= 15){
            document.getElementById("m-country1").src = "/assets/images/icons/tick.png"
        }
        if(breakdown.value >= 30){
            document.getElementById("m-country2").src = "/assets/images/icons/tick.png"
        }
        if(breakdown.value >= 45){
            document.getElementById("m-country3").src = "/assets/images/icons/tick.png"
        }
        if(breakdown.value >= 75){
            document.getElementById("m-country4").src = "/assets/images/icons/tick.png"
        }
      } else if(breakdown.description === "Milestone Codeshares" && breakdown.value >= 0){
        if(breakdown.value >= 15){
            document.getElementById("m-codeshares1").src = "/assets/images/icons/tick.png"
        }
        if(breakdown.value >= 30){
            document.getElementById("m-codeshares2").src = "/assets/images/icons/tick.png"
        }
        if(breakdown.value >= 45){
            document.getElementById("m-codeshares3").src = "/assets/images/icons/tick.png"
        }
        if(breakdown.value >= 75){
            document.getElementById("m-codeshares4").src = "/assets/images/icons/tick.png"
        }
      } else if(breakdown.description === "Milestone Passenger Miles" && breakdown.value >= 0){
        if(breakdown.value >= 15){
            document.getElementById("m-pax1").src = "/assets/images/icons/tick.png"
        }
        if(breakdown.value >= 30){
            document.getElementById("m-pax2").src = "/assets/images/icons/tick.png"
        }
        if(breakdown.value >= 45){
            document.getElementById("m-pax3").src = "/assets/images/icons/tick.png"
        }
        if(breakdown.value >= 60){
            document.getElementById("m-pax4").src = "/assets/images/icons/tick.png"
        }
      }
    }
  }

  return total;
}

function updateAirlineColorPicker() {
    var $colorPicker = $('#officeCanvas .airlineColor .picker')
	if (airlineColors[activeAirline.id]) {
		$colorPicker.val(airlineColors[activeAirline.id]);
    } else {
    	$colorPicker.val("#FFFFFF");
	}
}

function updateAirlineDetails() {
    const airline = activeAirline
	var breakdownList = $("<ul></ul>")
	$.each(airline.reputationBreakdowns.breakdowns, function(index, breakdown) {
		if (!breakdown.description.toLowerCase().includes("milestone")) {
			breakdownList.append("<li>" + breakdown.description + ": <span class='rep-value'>" + breakdown.value.toFixed(2) + "</span></li>")
		}
	})

    const milestoneValue = updateMilestones(airline.reputationBreakdowns.breakdowns)
    breakdownList.append("<li>Milestones: <span class='rep-value'>" + milestoneValue.toFixed(2) + "</span></li>")
    $('#officeCanvas .reputationDetails').html(breakdownList)

    $('#officeCanvas .airlineName').text(airline.name)
    $('#officeCanvas .airlineType').text(airline.type)
    cancelAirlineRename()
    if (isPremium()) {
        if (airline.renameCooldown) {
            disableButton($('#officeCanvas .airlineNameDisplaySpan .editButton'), "Cannot rename yet. Cooldown: " + toReadableDuration(airline.renameCooldown))
        } else {
            enableButton($('#officeCanvas .airlineNameDisplaySpan .editButton'))
        }
    } else {
        disableButton($('#officeCanvas .airlineNameDisplaySpan .editButton'), "Airline rename is only available to Patreon members")
    }

    $('#airlineCode').text(airline.airlineCode)
    $('#airlineCodeInput').val(airline.airlineCode)
    $(".fuelTaxRate").text(airline.fuelTaxRate + "% ")
    // $('#destinations').text(airline.destinations)
    // $('#fleetSize').text(airline.fleetSize)
    // $('#fleetAge').text(getYearMonthText(airline.fleetAge))
//	    	$('#assets').text('$' + commaSeparateNumber(airline.assets))
    // $('#officeCanvas .linkCount').text(airline.linkCount)
    $('#minimumRenewalBalance').text('$' + commaSeparateNumber(airline.minimumRenewalBalance))
}


function loadSheets() {
	var airlineId = activeAirline.id
	//reset values
	loadedIncomes = {}
	loadedIncomes['WEEKLY'] = []
	loadedIncomes['QUARTER'] = []
	loadedIncomes['PERIOD'] = []

	loadedCashFlows = {}
    loadedCashFlows['WEEKLY'] = []
    loadedCashFlows['QUARTER'] = []
    loadedCashFlows['PERIOD'] = []

	officeSheetPage = 0
	officePeriod = 'WEEKLY'
	$('#officeCanvas select.period').val(officePeriod)

		
	$.ajax({
		type: 'GET',
		url: "airlines/" + airlineId + "/finances",
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(data) {
	    	var airlineIncomes = data.incomes
	    	//group by period
	    	$.each(airlineIncomes, function(index, airlineIncome) {
	    		loadedIncomes[airlineIncome.period].push(airlineIncome)
	    	})
	    	
	    	var totalPages = loadedIncomes[officePeriod].length
	    	if (totalPages > 0) {
	    		officeSheetPage = totalPages - 1
	    		updateIncomeSheet(loadedIncomes[officePeriod][officeSheetPage])
	    	}
	    	
	    	updateIncomeChart()
	    	
	    	var airlineCashFlows = data.cashFlows
	    	//group by period
	    	$.each(airlineCashFlows, function(index, airlineCashFlow) {
	    		loadedCashFlows[airlineCashFlow.period].push(airlineCashFlow)
	    	})
	    	
	    	totalPages = loadedCashFlows[officePeriod].length
	    	if (totalPages > 0) {
	    		officeSheetPage = totalPages - 1
	    		updateCashFlowSheet(loadedCashFlows[officePeriod][officeSheetPage])
	    	}
	    	
	    	updateCashFlowChart()

			data.airlineStats.forEach(stat => {
				stat.traveler = stat.total - (stat.tourists + stat.elites + stat.business);
			});

	    	plotAirlineStats(data.airlineStats, $("#officeCanvas #airlineStatsChart"))
			plotOpsChart(data.airlineStats, $("#officeCanvas #opsChart"))


	    	var stockPrice = loadedIncomes['WEEKLY'].length > 0 ? loadedIncomes['WEEKLY'][loadedIncomes['WEEKLY'].length - 1].stockPrice : null
            const airlineStat = data.airlineStats[data.airlineStats.length - 1] ?? null
			updateOpsDataSheet(airlineStat)
			updateAirlineStatsSheet(airlineStat)
            updateProgress(airlineStat, stockPrice)
	    },
	    error: function(jqXHR, textStatus, errorThrown) {
            console.log(JSON.stringify(jqXHR));
            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

function updateIncomeChart() {
	plotIncomeChart(loadedIncomes[officePeriod], officePeriod, $("#officeCanvas #totalProfitChart"))
}

function updateCashFlowChart() {
	plotCashFlowChart(loadedCashFlows[officePeriod], officePeriod, $("#officeCanvas #totalCashFlowChart"))
	plotTotalValueChart(loadedIncomes[officePeriod], officePeriod, $("#officeCanvas #totalValueChart"))
}

function officeHistoryStep(step) {
    var type = $('#officeCanvas .sheetOptions').find('.cell.selected').data('type')
    var totalPages = loadedIncomes[officePeriod].length //income and cash flow should have same # of pages - just pick income arbitrarily
    if (officeSheetPage + step < 0) {
        officeSheetPage = 0
    } else if (officeSheetPage + step >= totalPages) {
        officeSheetPage = totalPages - 1
    } else {
        officeSheetPage = officeSheetPage + step
    }

    if (type === 'income') {
        updateIncomeSheet(loadedIncomes[officePeriod][officeSheetPage])
    } else if (type === 'cashFlow') {
    	updateCashFlowSheet(loadedCashFlows[officePeriod][officeSheetPage])
    }
}

function changeOfficePeriod(period, type) {
    var type = $('#officeCanvas .sheetOptions').find('.cell.selected').data('type')
    officePeriod = period
    if (type === 'income') {
        var totalPages = loadedIncomes[officePeriod].length
        officeSheetPage = totalPages - 1
        updateIncomeSheet(loadedIncomes[officePeriod][officeSheetPage])
        updateIncomeChart()
    } else if (type === 'cashFlow') {
        var totalPages = loadedCashFlows[officePeriod].length
    	officeSheetPage = totalPages - 1
    	updateCashFlowSheet(loadedCashFlows[officePeriod][officeSheetPage])
    	updateCashFlowChart()
    }
}

function updateIncomeSheet(airlineIncome) {
	if (airlineIncome) {		
		$("#officeCycleText").text(getGameDate(airlineIncome.cycle, airlineIncome.period))
		$("#totalProfit").text('$' + commaSeparateNumber(airlineIncome.totalProfit))
        $("#totalRevenue").text('$' + commaSeparateNumber(airlineIncome.totalRevenue))
        $("#totalExpense").text('$' + commaSeparateNumber(airlineIncome.totalExpense))
        $("#linksProfit").text('$' + commaSeparateNumber(airlineIncome.linksProfit))
        $("#linksRevenue").text('$' + commaSeparateNumber(airlineIncome.linksRevenue))
        $("#linksExpense").text('$' + commaSeparateNumber(airlineIncome.linksExpense))
        $("#linksTicketRevenue").text('$' + commaSeparateNumber(airlineIncome.linksTicketRevenue))
        $("#linksAirportFee").text('$' + commaSeparateNumber(airlineIncome.linksAirportFee))
        $("#linksFuelCost").text('$' + commaSeparateNumber(airlineIncome.linksFuelCost))
        $("#linksFuelTax").text('$' + commaSeparateNumber(airlineIncome.linksFuelTax))
        $("#linksCrewCost").text('$' + commaSeparateNumber(airlineIncome.linksCrewCost))
        $("#linksInflightCost").text('$' + commaSeparateNumber(airlineIncome.linksInflightCost))
        $("#linksMaintenance").text('$' + commaSeparateNumber(airlineIncome.linksMaintenanceCost))
        $("#linksLoungeCost").text('$' + commaSeparateNumber(airlineIncome.linksLoungeCost))
        $("#linksDepreciation").text('$' + commaSeparateNumber(airlineIncome.linksDepreciation))
        $("#linksDelayCompensation").text('$' + commaSeparateNumber(airlineIncome.linksDelayCompensation))
        $("#transactionsProfit").text('$' + commaSeparateNumber(airlineIncome.transactionsProfit))
        $("#transactionsRevenue").text('$' + commaSeparateNumber(airlineIncome.transactionsRevenue))
        $("#transactionsExpense").text('$' + commaSeparateNumber(airlineIncome.transactionsExpense))
        $("#transactionsCapitalGain").text('$' + commaSeparateNumber(airlineIncome.transactionsCapitalGain))
        //$("#transactionsCreateLink").text('$' + commaSeparateNumber(airlineIncome.transactionsCreateLink))
        $("#othersProfit").text('$' + commaSeparateNumber(airlineIncome.othersProfit))
        $("#othersRevenue").text('$' + commaSeparateNumber(airlineIncome.othersRevenue))
        $("#othersExpense").text('$' + commaSeparateNumber(airlineIncome.othersExpense))
        $("#othersLoanInterest").text('$' + commaSeparateNumber(airlineIncome.othersLoanInterest))
        $("#othersBaseUpkeep").text('$' + commaSeparateNumber(airlineIncome.othersBaseUpkeep))
        $("#othersOvertimeCompensation").text('$' + commaSeparateNumber(airlineIncome.othersOvertimeCompensation))
        $("#othersLoungeUpkeep").text('$' + commaSeparateNumber(airlineIncome.othersLoungeUpkeep))
        $("#othersLoungeCost").text('$' + commaSeparateNumber(airlineIncome.othersLoungeCost))
        $("#othersLoungeIncome").text('$' + commaSeparateNumber(airlineIncome.othersLoungeIncome))
        $("#othersAssetExpense").text('$' + commaSeparateNumber(airlineIncome.othersAssetExpense))
        $("#othersAssetRevenue").text('$' + commaSeparateNumber(airlineIncome.othersAssetRevenue))
        $("#othersServiceInvestment").text('$' + commaSeparateNumber(airlineIncome.othersServiceInvestment))
        $("#othersAdvertisement").text('$' + commaSeparateNumber(airlineIncome.othersAdvertisement))
        $("#othersFuelProfit").text('$' + commaSeparateNumber(airlineIncome.othersFuelProfit))
        $("#othersDepreciation").text('$' + commaSeparateNumber(airlineIncome.othersDepreciation))
	}
}


function changeCashFlowPeriod(period) {
	officePeriod = period
	var totalPages = loadedCashFlows[officePeriod].length
	officeSheetPage = totalPages - 1
	// updateAirlineStatsSheet(loadedIncomes[officePeriod][officeSheetPage].airlineStats)
	updateCashFlowSheet(loadedCashFlows[officePeriod][officeSheetPage])
	updateCashFlowChart()
}


function updateCashFlowSheet(airlineCashFlow) {
	if (airlineCashFlow) {
		$("#officeCycleText").text(getGameDate(airlineCashFlow.cycle, airlineCashFlow.period))
		$("#cashFlowSheet .totalCashFlow").text('$' + commaSeparateNumber(airlineCashFlow.totalCashFlow))
        $("#cashFlowSheet .operation").text('$' + commaSeparateNumber(airlineCashFlow.operation))
        $("#cashFlowSheet .loanInterest").text('$' + commaSeparateNumber(airlineCashFlow.loanInterest))
        $("#cashFlowSheet .loanPrincipal").text('$' + commaSeparateNumber(airlineCashFlow.loanPrincipal))
        $("#cashFlowSheet .baseConstruction").text('$' + commaSeparateNumber(airlineCashFlow.baseConstruction))
        $("#cashFlowSheet .buyAirplane").text('$' + commaSeparateNumber(airlineCashFlow.buyAirplane))
        $("#cashFlowSheet .sellAirplane").text('$' + commaSeparateNumber(airlineCashFlow.sellAirplane))
        $("#cashFlowSheet .createLink").text('$' + commaSeparateNumber(airlineCashFlow.createLink))
        $("#cashFlowSheet .facilityConstruction").text('$' + commaSeparateNumber(airlineCashFlow.facilityConstruction))
        $("#cashFlowSheet .oilContract").text('$' + commaSeparateNumber(airlineCashFlow.oilContract))
        $("#cashFlowSheet .assetTransactions").text('$' + commaSeparateNumber(airlineCashFlow.assetTransactions))
	}
}

function updateAirlineStatsSheet(airlineStats) {
    if (airlineStats) {
        Object.keys(airlineStats).forEach((key) => {
            const element = document.querySelector(`#airlineStatsSheet .${key}`);
            if (element) {
                element.textContent = commaSeparateNumber(airlineStats[key]);
            }
        });
    }
}

function updateOpsDataSheet(opsData) {
    if (opsData) {
        Object.keys(opsData).forEach((key) => {
            const element = document.querySelector(`#opsSheet .${key}`);
            if (element) {
                element.textContent = commaSeparateNumber(opsData[key]);
            }
        });
    }
    if (activeAirline.stats) {
	Object.keys(activeAirline.stats).forEach((key) => {
		const element = document.querySelector(`#opsSheet .${key}`);
		if (element) {
			element.textContent = commaSeparateNumber(activeAirline.stats[key]);
		}
	});
    }
}

function setTargetServiceQuality(targetServiceQuality) {
	var airlineId = activeAirline.id
	var url = "airlines/" + airlineId + "/target-service-quality"
	if (!checkTargetServiceQualityInput(targetServiceQuality)) { //if invalid, then return
	    return;
	}

    var data = { "targetServiceQuality" : parseInt(targetServiceQuality) }
	$.ajax({
		type: 'PUT',
		url: url,
	    data: JSON.stringify(data),
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(result) {
	    	activeAirline.targetServiceQuality = result.targetServiceQuality
	    	updateServiceFundingDetails()
	    },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

function checkTargetServiceQualityInput(input) {
    var value = parseInt(input)
    if (value === undefined) {
        $("#serviceFundingInputSpan .warning").show()
        return false;
    } else {
        if (input < 0 || input > 100) {
            $("#serviceFundingInputSpan .warning").show()
            return false;
        } else { //ok
            $("#serviceFundingInputSpan .warning").hide()
            return true;
        }
    }
}

function setMinimumRenewalBalance(minimumRenewalBalance) {
	var airlineId = activeAirline.id
	var url = "airlines/" + airlineId + "/minimum-renewal-balance"
	if (!checkEditMinimumRenewalBalanceInput(minimumRenewalBalance)) { //if invalid, then return
	    return;
	}

    var data = { "minimumRenewalBalance" : parseInt(minimumRenewalBalance) }
	$.ajax({
		type: 'PUT',
		url: url,
	    data: JSON.stringify(data),
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(result) {
	    	activeAirline.minimumRenewalBalance = result.minimumRenewalBalance
	    	updateMinimumRenewalBalanceDetails()
	    },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

function checkEditMinimumRenewalBalanceInput(input) {

	var value = parseInt(input)

	if (Number.isNaN(value)) {
		$("#minimumRenewalBalanceInputSpan .warning").show()
		return false;
	} else {
		if (value > 999999999999 || value < 0) {
            $("#minimumRenewalBalanceInputSpan .warning").show()
            return false;
		} else {
            $("#minimumRenewalBalanceInputSpan .warning").hide()
            return true;	
		}
	}

}

function setAirplaneRenewal(threshold) {
	var airlineId = activeAirline.id
	var url = "airlines/" + airlineId + "/airplane-renewal"
	var data
	if (threshold) {
		data = { "threshold" : parseInt(threshold) }
	} else {
		data = { "threshold" : -1 } //disable
	} 
		
	
		
	$.ajax({
		type: 'PUT',
		url: url,
	    data: JSON.stringify(data),
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(result) {
	    	updateAirplaneRenewalDetails()
	    },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

function editAirlineCode() {
	$('#airlineCodeDisplaySpan').hide()
	$('#airlineCodeInputSpan').show()
}

function validateAirlineCode(airlineCode) {
	if (/[^a-zA-Z]/.test(airlineCode) || airlineCode.length != 2) {
		$('#airlineCodeInputSpan .warning').show()
	} else {
		$('#airlineCodeInputSpan .warning').hide()
	}
}

function setAirlineCode(airlineCode) {
	var airlineId = activeAirline.id
	var url = "airlines/" + airlineId + "/airline-code"
    var data = { "airlineCode" : airlineCode }
	$.ajax({
		type: 'PUT',
		url: url,
	    data: JSON.stringify(data),
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(airline) {
	    	activeAirline = airline
	    	$('#airlineCode').text(airline.airlineCode)
	    	$('#airlineCodeInputSpan').hide()
	    	$('#airlineCodeDisplaySpan').show()
	    },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    },
        beforeSend: function() {
            $('body .loadingSpinner').show()
        },
        complete: function(){
            $('body .loadingSpinner').hide()
        }
	});
}

const airlineNameInput = document.getElementById('airlineNameInput');
const debouncedValidateAirlineName = debounce(validateAirlineName, 400);

airlineNameInput.addEventListener('input', (event) => {
    debouncedValidateAirlineName(event.target.value);
});

function editAirlineName() {
	$('#officeCanvas .airlineNameDisplaySpan').hide()
	$('#officeCanvas .airlineNameInput').val(activeAirline.name)
	validateAirlineName(activeAirline.name)
	$('#officeCanvas .airlineNameInputSpan').show()
}

function validateAirlineName(airlineName) {
    var airlineId = activeAirline.id
    var url = "airlines/" + airlineId + "/airline-name"
    var data = { "airlineName" : airlineName }
    $.ajax({
        type: 'PUT',
        url: url,
        data: JSON.stringify(data),
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(result) {
            if (result.ok) {
                document.querySelector('#officeCanvas .airlineNameInputSpan .confirm').disabled = false
                $('#officeCanvas .airlineNameInputSpan .warning').hide()
            } else {
                document.querySelector('#officeCanvas .airlineNameInputSpan .confirm').disabled = true
                $('#officeCanvas .airlineNameInputSpan .warning').text(result.rejection)
                $('#officeCanvas .airlineNameInputSpan .warning').show()
            }
        },
        error: function(jqXHR, textStatus, errorThrown) {
                console.log(JSON.stringify(jqXHR));
                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    })
}

function confirmAirlineRename(airlineName) {
    promptConfirm("Change airline name to <b>" + airlineName + "</b>? Can only rename every 30 days.", function() {
        var airlineId = activeAirline.id
        var url = "airlines/" + airlineId + "/airline-name"
        var data = { "airlineName" : airlineName }
        $.ajax({
            type: 'PUT',
            url: url,
            data: JSON.stringify(data),
            contentType: 'application/json; charset=utf-8',
            dataType: 'json',
            success: function(airline) {
                loadUser(false)
                showOfficeCanvas()
            },
            error: function(jqXHR, textStatus, errorThrown) {
                    console.log(JSON.stringify(jqXHR));
                    console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
            },
            beforeSend: function() {
                $('body .loadingSpinner').show()
            },
            complete: function(){
                $('body .loadingSpinner').hide()
            }
        });
    })
}

function cancelAirlineRename() {
    $('#officeCanvas .airlineNameDisplaySpan').show()
	$('#officeCanvas .airlineNameInputSpan').hide()
}





function loadLogoTemplates() {
	$('#logoTemplates').empty()
	$.ajax({
		type: 'GET',
		url: "logos/templates",
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(templates) {
	    	//group by period
	    	$.each(templates, function(index, templateIndex) {
	    		$('#logoTemplates').append('<div style="padding: 3px; margin: 3px; float: left;" class="clickable" onclick="selectLogoTemplate(' + templateIndex + ')"><img src="logos/templates/' + templateIndex + '"></div>')
	    	})
	    	
	    	
	    },
	    error: function(jqXHR, textStatus, errorThrown) {
            console.log(JSON.stringify(jqXHR));
            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

function editAirlineLogo() {
	var modal = $('#logoModal')
	$('#logoTemplateIndex').val(0)
	generateLogoPreview()
	modal.fadeIn(200)
}

function selectLogoTemplate(templateIndex) {
	$('#logoTemplateIndex').val(templateIndex)
	generateLogoPreview()
}

function generateLogoPreview() {
	var logoTemplate = $('#logoTemplateIndex').val()

	var color1 = $('#logoModal .picker.color1').val()
    var color2 = $('#logoModal .picker.color2').val()

	var url = "logos/preview?templateIndex=" + logoTemplate + "&color1=" + encodeURIComponent(color1) + "&color2=" + encodeURIComponent(color2)
	$('#logoPreview').empty();
	$('#logoPreview').append('<img src="' + url + '">')
}

function setAirlineLogo() {
	var logoTemplate = $('#logoTemplateIndex').val() 
	var color1 = $('#logoModal .picker.color1').val()
    var color2 = $('#logoModal .picker.color2').val()
	
	var url = "airlines/" + activeAirline.id + "/set-logo?templateIndex=" + logoTemplate + "&color1=" + encodeURIComponent(color1) + "&color2=" + encodeURIComponent(color2)
    $.ajax({
		type: 'GET',
		url: url,
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(dummy) {
	    	updateAirlineLogo()
	    },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

function setAirlineColor(color) {
	var url = "airlines/" + activeAirline.id + "/set-color?color=" + encodeURIComponent(color)
    $.ajax({
		type: 'GET',
		url: url,
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(dummy) {
	    	updateAirlineColors()
	    },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

function showUploadLogo() {
	if (activeAirline.reputation >= 40) {
		updateLogoUpload()
		$('#uploadLogoModal .uploadForbidden').hide()
		$('#uploadLogoModal .uploadPanel').show()
	} else {
		$('#uploadLogoModal .uploadForbidden .warning').text('You may only upload airline banner at Reputation 40 or above')
		$('#uploadLogoModal .uploadForbidden').show()
		$('#uploadLogoModal .uploadPanel').hide()
	}
	
	$('#uploadLogoModal').fadeIn(200)
}


function updateLogoUpload() {
	$('#uploadLogoModal .uploadPanel .warning').hide()
	if (logoUploaderObj) {
		logoUploaderObj.reset()
	}
	
	logoUploaderObj = $("#uploadLogoModal .uploadPanel .fileuploader").uploadFile({
		url:"airlines/" + activeAirline.id + "/logo",
		multiple:false,
		dragDrop:false,
		acceptFiles:"image/png",
		fileName:"logoFile",
		maxFileSize:100*1024,
		onSuccess:function(files,data,xhr,pd)
		{
			if (data.success) {
				$('#uploadLogoModal .uploadPanel .warning').hide()
				closeModal($('#uploadLogoModal'))
				updateAirlineLogo()
			} else if (data.error) {
				$('#uploadLogoModal .uploadPanel .warning').text(data.error)
				$('#uploadLogoModal .uploadPanel .warning').show()
			}
			
		}
	});
}

function updateLiveryInfo() {
    $('#officeCanvas img.livery').attr('src', 'airlines/' + activeAirline.id + "/livery?dummy=" + Math.random())
}

function showUploadLivery() {
	if (activeAirline.reputation >= 40) {
		updateLiveryUpload()
		$('#uploadLiveryModal .uploadForbidden').hide()
		$('#uploadLiveryModal .uploadPanel').show()
	} else {
		$('#uploadLiveryModal .uploadForbidden .warning').text('You may only upload airline livery at Reputation 40 or above')
		$('#uploadLiveryModal .uploadForbidden').show()
		$('#uploadLiveryModal .uploadPanel').hide()
	}

	$('#uploadLiveryModal').fadeIn(200)
}


function updateLiveryUpload() {
	$('#uploadLiveryModal .uploadPanel .warning').hide()
	if (liveryUploaderObj) {
		liveryUploaderObj.reset()
	}

	liveryUploaderObj = $("#uploadLiveryModal .uploadPanel .fileuploader").uploadFile({
		url:"airlines/" + activeAirline.id + "/livery",
		multiple:false,
		dragDrop:false,
		acceptFiles:"image/png",
		fileName:"liveryFile",
		maxFileSize:1 * 1024 * 1024,
		onSuccess:function(files,data,xhr,pd)
		{
			if (data.success) {
				$('#uploadLiveryModal .uploadPanel .warning').hide()
				closeModal($('#uploadLiveryModal'))
				updateLiveryInfo()
			} else if (data.error) {
				$('#uploadLiveryModal .uploadPanel .warning').text(data.error)
				$('#uploadLiveryModal .uploadPanel .warning').show()
			}

		}
	});
}

function deleteLivery() {
    var url = "airlines/" + activeAirline.id + "/livery"
    $.ajax({
		type: 'DELETE',
		url: url,
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(dummy) {
	    	updateLiveryInfo()
	    },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

function saveSlogan() {
    var url = "airlines/" + activeAirline.id + "/slogan"
    $.ajax({
		type: 'PUT',
		url: url,
		contentType: 'application/json; charset=utf-8',
        dataType: 'json',
	    data: JSON.stringify({ 'slogan' : $('#officeCanvas .slogan').val() }) ,


	    success: function(result) {
	    	$('#officeCanvas .slogan').val(result.slogan)
	    },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

function loadSlogan(callback) {
    var url = "airlines/" + activeAirline.id + "/slogan"
    $.ajax({
		type: 'GET',
		url: url,
	    dataType: 'json',
	    success: function(result) {
	    	callback(result.slogan)
	    },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}




function editTargetServiceQuality() {
	$('#serviceFundingDisplaySpan').hide()
	$('#serviceFundingInputSpan').show()
}

function editMinimumRenewalBalance() {
	$('#minimumRenewalBalanceDisplaySpan').hide()
	$('#minimumRenewalBalanceInputSpan').show()
}


function updateServiceFundingDetails() {
	$('#currentServiceQuality').text(activeAirline.serviceQuality)
	
	$('#targetServiceQuality').text(activeAirline.targetServiceQuality)
	$('#targetServiceQualityInput').val(activeAirline.targetServiceQuality)
	
	$('#serviceFundingDisplaySpan').show()
	$('#serviceFundingInputSpan').hide()

	$('#fundingProjection').text('...')
	$.ajax({
		type: 'GET',
		url: "airlines/" + activeAirline.id + "/service-funding-projection",
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(result) {
	    	$('#fundingProjection').text(commaSeparateNumber(result.fundingProjection))
	    },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
	
}

function updateMinimumRenewalBalanceDetails() {
	$('#minimumRenewalBalance').text('$' + commaSeparateNumber(activeAirline.minimumRenewalBalance))
	$('#minimumRenewalBalanceInput').val(activeAirline.minimumRenewalBalance)

	$('#minimumRenewalBalanceDisplaySpan').show()
	$('#minimumRenewalBalanceInputSpan').hide()
}

function editAirplaneRenewal() {
	$('#airplaneRenewalDisplaySpan').hide()
	$('#airplaneRenewalInputSpan').show()
}


function updateAirplaneRenewalDetails() {
	$.ajax({
		type: 'GET',
		url: "airlines/" + activeAirline.id + "/airplane-renewal",
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(airplaneRenewal) {
	    	if (airplaneRenewal.threshold) {
	    		$('#airplaneRenewal').text('Below ' + airplaneRenewal.threshold + "%")
	    		$('#airplaneRenewalInput').val(airplaneRenewal.threshold)
	    	} else {
	    		$('#airplaneRenewal').text('-')
	    		$('#airplaneRenewalInput').val(40)
	    	}
	    	$('#airplaneRenewalDisplaySpan').show()
	    	$('#airplaneRenewalInputSpan').hide()
	    },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

function updateChampionedCountriesDetails() {
	$('#championedCountriesList').children('div.table-row').remove()

    	$.ajax({
    		type: 'GET',
    		url: "airlines/" + activeAirline.id + "/championed-countries",
    	    contentType: 'application/json; charset=utf-8',
    	    dataType: 'json',
    	    success: function(championedCountries) {
    	    	$(championedCountries).each(function(index, championDetails) {
    	    		var country = championDetails.country
    	    		var row = $("<div class='table-row clickable' data-link='country' onclick=\"showCountryView('" + country.countryCode + "');\"></div>")
    	    		row.append("<div class='cell'>" + getRankingImg(championDetails.ranking) + "</div>")
    	    		row.append("<div class='cell'>" + getCountryFlagImg(country.countryCode) + country.name + "</div>")
    	    		$('#championedCountriesList').append(row)
    	    	})

    	    	populateNavigation($('#championedCountriesList'))

    	    	if ($(championedCountries).length == 0) {
    	    		var row = $("<div class='table-row'></div>")
    	    		row.append("<div class='cell'>-</div>")
    	    		row.append("<div class='cell'>-</div>")
    	    		$('#championedCountriesList').append(row)
    	    	}
    	    },
            error: function(jqXHR, textStatus, errorThrown) {
    	            console.log(JSON.stringify(jqXHR));
    	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
    	    }
    	});

}

function updateChampionedAirportsDetails() {
	$('#championedAirportsList').children('div.table-row').remove()
	
	$.ajax({
		type: 'GET',
		url: "airlines/" + activeAirline.id + "/championed-airports",
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(championedAirports) {
	    	$(championedAirports).each(function(index, championDetails) {
	    		var row = $("<div class='table-row clickable' data-link='airport' onclick=\"showAirportDetails('" + championDetails.airportId + "');\"></div>")
	    		row.append("<div class='cell'>" + getRankingImg(championDetails.ranking) + "</div>")
	    		row.append("<div class='cell'>" + getCountryFlagImg(championDetails.countryCode) + championDetails.airportText + "</div>")
	    		row.append("<div class='cell' style='text-align: right;'>" + commaSeparateNumber(championDetails.loyalistCount) + "</div>")
	    		row.append("<div class='cell' style='text-align: right;'>" + championDetails.reputationBoost + "</div>")
	    		$('#championedAirportsList').append(row)
	    	})

	    	populateNavigation($('#championedAirportsList'))
	    	
	    	if ($(championedAirports).length == 0) {
	    		var row = $("<div class='table-row'></div>")
	    		row.append("<div class='cell'>-</div>")
	    		row.append("<div class='cell'>-</div>")
	    		row.append("<div class='cell'>-</div>")
	    		row.append("<div class='cell'>-</div>")
	    		$('#championedAirportsList').append(row)
	    	}
	    },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
	
}


function selectSheet(tab, sheet) {
    tab.siblings().removeClass("selected")
	tab.addClass("selected")
    var type = tab.data('type')
    if (type === 'income') {
        updateIncomeSheet(loadedIncomes[officePeriod][officeSheetPage])
        updateIncomeChart()
    } else if (type === 'cashFlow') {
        updateCashFlowSheet(loadedCashFlows[officePeriod][officeSheetPage])
    	updateCashFlowChart()
    }

	sheet.siblings(".sheet").hide()
	sheet.show()
}

function updateResetAirlineInfo() {
	var airlineId = activeAirline.id
	var url = "airlines/" + airlineId + "/reset-consideration"
    $.ajax({
		type: 'GET',
		url: url,
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(result) {
	        companyValue = result.overall;

	    	if (result.rebuildRejection) {
	    		disableButton($("#officeCanvas .button.resetAirline.rebuild"), result.rebuildRejection)
	    	} else {
	    	    enableButton($("#officeCanvas .button.resetAirline.rebuild"))
	    	}

            if (result.bankruptRejection) {
                disableButton($("#officeCanvas .button.resetAirline.bankrupt"), result.bankruptRejection)
            } else {
                enableButton($("#officeCanvas .button.resetAirline.bankrupt"))
            }

	    	if (result.overall >= 0) {
	    		$("#officeCanvas #resetBalance").text(commaSeparateNumber(result.overall))
	    	} else {
	    		$("#officeCanvas #resetBalance").text('-' + commaSeparateNumber(result.overall * -1)) //to avoid the () negative number which could be confusing
	    	}
	    	
	    	$('#popover-reset .airplanes').text(commaSeparateNumber(result.airplanes))
	    	$('#popover-reset .bases').text(commaSeparateNumber(result.bases))
	    	$('#popover-reset .assets').text(commaSeparateNumber(result.assets))
	    	$('#popover-reset .loans').text(commaSeparateNumber(result.loans))
	    	$('#popover-reset .oilContracts').text(commaSeparateNumber(result.oilContracts))
	    	$('#popover-reset .cash').text(commaSeparateNumber(result.existingBalance))
	    	$('#popover-reset .overall').text(commaSeparateNumber(result.overall))

	    },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

function resetAirline(keepAssets) {
	$.ajax({
		type: 'GET',
		url: "airlines/" + activeAirline.id + "/reset?keepAssets=" + keepAssets,
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function() {
	    	updateAllPanels(activeAirline.id)
	    	selectedLink = undefined
	    	showWorldMap()
	    },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
	
}
