var logoModalConfirm = function() {}
var _financesEtag = null
var loadedIncomes = {}
var loadedLedger = []
var loadedAirlineOps = {}
var loadedAirlineStats = {}
var loadedAirlineReputation = {}
var officePeriod;
var companyValue = 0;

const minPositiveLog = 0.2 //manually defined lower bound for log

// --- Unified sheet system ---
const SHEET_CONFIG = {
	income: {
		pageGroup: 'financial',
		getData: () => loadedIncomes[officePeriod] ?? [],
		updateSheet: data => updateIncomeSheet(data),
		updateChart: () => updateIncomeChart(),
		sheetId: 'incomeSheet',
		chartIds: ['totalProfitChart'],
	},
	ledger: {
		pageGroup: 'ledger',
		getData: () => loadedLedger,
		updateSheet: data => updateLedgerSheet(data),
		updateChart: () => updateAssetChart(),
		sheetId: 'ledgerSheet',
		chartIds: ['assetChart'],
	},
	airlineStat: {
		pageGroup: 'stats',
		getData: () => loadedAirlineStats[officePeriod] ?? [],
		updateSheet: data => updateAirlineStatSheet(data),
		updateChart: () => updateAirlineStatChart(),
		sheetId: 'airlineStatsSheet',
		chartIds: ['airlineStatsChart'],
	},
	airlineOp: {
		pageGroup: 'stats',
		getData: () => loadedAirlineStats[officePeriod] ?? [],
		updateSheet: data => updateAirlineOpSheet(data),
		updateChart: () => updateOpsChart(),
		sheetId: 'opsSheet',
		chartIds: ['opsChart'],
	},
	airlineReputation: {
		pageGroup: 'stats',
		getData: () => loadedAirlineStats[officePeriod] ?? [],
		updateSheet: data => updateAirlineReputationSheet(data),
		updateChart: () => updateAirlineReputationChart(),
		sheetId: 'airlineReputationSheet',
		chartIds: ['airlineReputationChart'],
	},
}

const allSheetChartIds = new Set(Object.values(SHEET_CONFIG).flatMap(c => c.chartIds))
const sheetPages = { financial: 0, ledger: 0, stats: 0 }

function getActiveSheetType() {
	return document.querySelector('#officeCanvas .sheetOptions .cell.selected')?.dataset.type
}
function getSheetPage(type) {
	return sheetPages[SHEET_CONFIG[type].pageGroup]
}
function setSheetPage(type, page) {
	sheetPages[SHEET_CONFIG[type].pageGroup] = page
}

function getPeriodLabel() {
	return { WEEKLY: 'Week', QUARTER: 'Quarter', YEAR: 'Year' }[officePeriod] ?? officePeriod
}

function renderSheetNav(type) {
	if (!type) return
	const config = SHEET_CONFIG[type]
	const data = config.getData()
	const page = getSheetPage(type)
	const entry = data[page]
	const sheet = document.getElementById(config.sheetId)
	if (!sheet) return

	const label = type === 'ledger' ? 'Week' : getPeriodLabel()

	let nav = sheet.querySelector('.sheet-nav')
	if (!nav) {
		nav = document.createElement('div')
		nav.className = 'sheet-nav'

		const prev = document.createElement('a')
		prev.className = 'button-text prev'
		prev.textContent = `\u2190 Prev ${label}`
		prev.addEventListener('click', () => officeHistoryStep(-1))

		const cycleEl = document.createElement('span')
		cycleEl.className = 'officeCycleText text-xs'

		const next = document.createElement('a')
		next.className = 'button-text next'
		next.textContent = `Next ${label} \u2192`
		next.addEventListener('click', () => officeHistoryStep(1))

		nav.append(prev, cycleEl, next)

		const chart = sheet.querySelector('[id$="Chart"]')
		if (chart) {
			chart.after(nav)
		} else {
			sheet.prepend(nav)
		}
	}

	// Update state
	nav.querySelector('.button-text.prev').textContent = `\u2190 Prev ${label}`
	nav.querySelector('.button-text.next').textContent = `Next ${label} \u2192`
	nav.querySelector('.button-text.prev').classList.toggle('disabled', page <= 0)
	nav.querySelector('.button-text.next').classList.toggle('disabled', page >= data.length - 1)
	nav.querySelector('.officeCycleText').textContent = entry ? getGameDate(entry.cycle, entry.period, true) : ''
}

$( document ).ready(function() {
	loadLogoTemplates()

    $('#logoModal .picker.color1').val("#000000")
    $('#logoModal .picker.color2').val("#FFFFFF")

    $('#logoModal .picker').change(function() {
        generateLogoPreview()
    });

    var $airlineColorPicker = $('#officeCanvas .airlineColor .picker')
    $airlineColorPicker.change(function() {
        setAirlineColor($(this).val())
    });

    document.querySelectorAll('#officeCanvas .sheetOptions .cell[data-type]').forEach(tab => {
        tab.addEventListener('click', () => selectSheet(tab))
    })

    document.querySelector('#officeCanvas select.period')?.addEventListener('change', function() {
        changeOfficePeriod(this.value)
    })

})

function showOfficeCanvas() {
	setActiveDiv($("#officeCanvas"))

	updateAirlineDetails()
	writeMilestones(activeAirline.reputationBreakdowns.breakdowns)
	loadSheets();
	updateResetAirlineInfo()
	updateAirlineAssets()
	updateChampionedCountriesDetails()
	updateChampionedAirportsDetails()
	updateServiceFundingDetails()
	updateMinimumRenewalBalanceDetails()
	updateAirplaneRenewalDetails()
	updateAirlineBases()
	updateAirlineColorPicker()
	updateHeadquartersMap($('#officeCanvas .headquartersMap'), activeAirline.id)
	updateLiveryInfo()
	updateManagerStatus()
	loadSlogan(function(slogan) { $('#officeCanvas .slogan').val(slogan)})
}

function updateAirlineAssets() {
    $.ajax({
        type: 'GET',
        url: "/airlines/" + activeAirline.id + "/airport-assets",
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
    	var url = "/airlines/" + airlineId + "/office-capacity"
        $.ajax({
    		type: 'GET',
    		url: url,
    	    contentType: 'application/json; charset=utf-8',
    	    dataType: 'json',
    	    success: function(officeCapacity) {
    	    	 $(activeAirline.baseAirports).each(function(index, base) {
                    var row = $(`<div class='table-row clickable' data-link='airport' onclick='page("/airport/${base.airportCode}")'></div>`)
                    if (base.headquarter) {
                        row.append($("<div class='cell'><img src='/assets/images/icons/building-hedge.png' class='pr-1'><span style='font-size: 130%;vertical-align: top;'>" + base.scale + "</span></div><div class='cell'>" + getCountryFlagImg(base.countryCode) + base.city + " " + base.airportCode + "</div>"))

                    } else {
                        row.append($("<div class='cell'><img src='/assets/images/icons/building-low.png' class='pr-1'><span style='font-size: 130%;vertical-align: top;'>" + base.scale + "</span></div><div class='cell'>" + getCountryFlagImg(base.countryCode) + base.city + " " + base.airportCode + "</div>"))
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
    	    },
            error: function(jqXHR, textStatus, errorThrown) {
    	            console.log(JSON.stringify(jqXHR));
    	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
    	    }
    	});
}

function setProgressWidth(elemId, current, past, next, isLog = false){
	let percent
    next = next > 10e12 ? past + 1 : next //make infinite ceiling still "success"
	if (!isLog) {
		percent = Math.max((current - past) / (next - past) * 100, 0)
	} else {
		// Logarithmic scaling: map values into log space.
		// We want percent = (log(current - past) - log(min)) / (log(max) - log(min)) * 100
		// where min is a small positive number to avoid log(0).
		const raw = current - past
		const range = next - past
		// Guard against non-positive range
		if (range <= 0) {
			percent = 0
		} else {
			const min = Math.max(minPositiveLog, Math.min(raw, range > 0 ? Math.min(1, range) : minPositiveLog))
			// Use lower bound as tiny positive and upper bound as range
			const lower = minPositiveLog
			const upper = Math.max(range, minPositiveLog)
			const clamped = Math.max(Math.min(raw, range), minPositiveLog)
			const logLower = Math.log10(lower)
			const logUpper = Math.log10(upper)
			const logValue = Math.log10(clamped)
			percent = (logValue - logLower) / (logUpper - logLower) * 100
			percent = Math.max(percent, 0)
		}
	}

	const red = Math.max((percent > 15 ? 250 - (percent - 15) * 4 : 250).toFixed(0), 0)
	const green = Math.min(percent * 3, 230)
	$(elemId).css({'width': percent + "%", 'background-color': "rgba("+red+","+green+",0,0.65)"})
}

function addProgressGrades(track, grades, prettyLabel, isLog = false, isCurrency = false) {
	const progressBar = document.querySelector(`#${track}Progress .threshold-bar`);
	if (!progressBar) return

	// remove any existing dots to avoid duplicates
	const existing = progressBar.querySelectorAll('.dot')
	existing.forEach(d => d.remove())

	// If no grades, nothing to do
	if (!grades || grades.length === 0) return

	const maxGrade = Math.max(...grades)

	// For log scaling, compute log-space bounds
	let logMin = null
	let logMax = null
	if (isLog) {
		const safeGrades = grades.map(g => Math.max(g, minPositiveLog))
		logMin = Math.log10(minPositiveLog)
		logMax = Math.log10(Math.max(...safeGrades))
		if (logMax === logMin) {
			// fallback to linear if all equal
			isLog = false
		}
	}

	grades.forEach((grade, index) => {
	  const dot = document.createElement('div');
	  dot.classList.add('dot','tooltip-attr');
	  dot.dataset.tooltip = `Level ${index + 1} at ${isCurrency ? `$${grade.toFixed(2)}` : grade} ${prettyLabel}`;

	  let positionPercentage
	  if (!isLog) {
		  positionPercentage = (grade / maxGrade) * 100
	  } else {
		  const safe = Math.max(grade, 1e-6)
		  const logV = Math.log10(safe)
		  positionPercentage = (logV - logMin) / (logMax - logMin) * 100
	  }

	  // clamp and apply
	  positionPercentage = Math.min(100, Math.max(0, positionPercentage))
	  dot.style.left = `${positionPercentage}%`;

	  progressBar.appendChild(dot);
	});
}

function updateProgress(stats){
  const stockPrice = activeAirline.stock ? activeAirline.stock.stockPrice : 0;
    const shortNumber = (number) => {
        if(number >= 10000){
            return (number / 1000).toFixed(0).toLocaleString() + "k"
        } else {
            return number.toFixed(0).toLocaleString()
        }
    }

    var $starBar = $(getGradeStarsImgs(activeAirline.gradeLevel - 3, 20))
    $('.reputationText').text(activeAirline.gradeDescription)
    $('.reputationLevel').text("Level " + activeAirline.gradeLevel)
    $('.reputationTrend').text((activeAirline.reputationBreakdowns.total).toFixed(0))
    $('.reputationValueNext').text(activeAirline.gradeCeiling > 10e12 ? "∞" : activeAirline.gradeCeiling)
    $('.reputationValuePrev').text(activeAirline.gradeFloor)
    setProgressWidth("#reputationBar", activeAirline.reputation, activeAirline.gradeFloor, activeAirline.gradeCeiling)

    $('.reputationValueCurrent').text(activeAirline.reputation)
    $("#officeCanvas .reputationStars").html($starBar)

    if (activeAirline.stock !== undefined) {
        $('.stockValueCurrent').text("$" + stockPrice.toFixed(2))
        $('.stockText').text(activeAirline.stock.stockDescription)
        $('.stockLevel').text("Level " + activeAirline.stock.stockLevel)
        $('.stockValueNext').text("$"+activeAirline.stock.stockCeiling)
        setProgressWidth("#stockBar", stockPrice, 0, activeAirline.stock.grades.at(-1), true)
        addProgressGrades("stock", activeAirline.stock.grades, "stock price", true, true)
    }

    if (activeAirline.touristsTravelers !== undefined) {
        const touristsTravelersCeiling = activeAirline.touristsTravelers.touristsTravelersCeiling > 10e12 ? "∞" : commaSeparateNumber(activeAirline.touristsTravelers.touristsTravelersCeiling)
        $('.touristsTravelersValueCurrent').text(shortNumber(activeAirline.touristsTravelers.touristsTravelers))
        $('.touristsTravelersText').text(activeAirline.touristsTravelers.touristsTravelersDescription)
        $('.touristsTravelersLevel').text("Level " + activeAirline.touristsTravelers.touristsTravelersLevel)
        $('.touristsTravelersValueNext').text(touristsTravelersCeiling)
        setProgressWidth("#touristsTravelersBar", activeAirline.touristsTravelers.touristsTravelers, 0, activeAirline.touristsTravelers.grades.at(-1))
        addProgressGrades("touristsTravelers", activeAirline.touristsTravelers.grades, "tourist & travelers")
    }

    if (activeAirline.elites !== undefined) {
        const eliteCeiling = activeAirline.elites.elitesCeiling > 10e12 ? "∞" : commaSeparateNumber(activeAirline.elites.elitesCeiling)
        $('.elitesValueCurrent').text(shortNumber(activeAirline.elites.elites))
        $('.elitesText').text(activeAirline.elites.elitesDescription)
        $('.elitesLevel').text("Level " + activeAirline.elites.elitesLevel)
        $('.elitesValueNext').text(eliteCeiling)
        setProgressWidth("#elitesBar", activeAirline.elites.elites, 0, activeAirline.elites.grades.at(-1))
        addProgressGrades("elites", activeAirline.elites.grades, "elites")
    }
}

function writeMilestones(breakdowns) {
    const $container = $('#milestones');
    $container.empty();

    const milestones = gameConstants.milestones[activeAirline.type] || gameConstants.milestones.Legacy

    milestones.forEach(milestone => {
        const currentValue = breakdowns[milestone.name] ? breakdowns[milestone.name].value : 0;
        const currentQuantityValue = breakdowns[milestone.name] ? breakdowns[milestone.name].quantityValue : 0;

        const $col = $("<div></div>")
        $col.append(
            `<h5 class="mb-1">${milestone.description}</h5>`,
            `<p class="pb-1 opacity-70 italic">@ ${commaSeparateNumber(currentQuantityValue)}</p>`,
        );

        milestone.conditions.forEach(cond => {
            const metCondtion = currentValue >= cond.reward ? "tick" : "cross";
            $col.append(
                `<div style="width: 160px;justify-content: space-between;" class="flex-row py-1">
					<div class="font-mono text-sm flex-align-center">
						${commaSeparateNumber(cond.threshold)}
						<img height="16" class="pl-1 svg" src="/assets/images/icons/${metCondtion}.svg">
					</div>
					<div style="color:gold;display: flex;flex-direction: row;">
						${cond.reward}
						<img height="16" src="/assets/images/icons/reputation.svg">
					</div>
				</div>
			`);
        });
        $container.append($col);
    });
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

  const milestoneValue = airline.reputationBreakdowns.milestoneTotal
  breakdownList.append("<li>Milestones: <span class='rep-value'>" + milestoneValue.toFixed(2) + "</span></li>")
  $('#officeCanvas .reputationDetails').html(breakdownList)

  updateAllTextNodes('.airlineName', airline.name);
  document.querySelector('.airlineType').textContent = airline.type;
  const typeDescriptionEl = document.querySelector('.airlineTypeDescription');
  typeDescriptionEl.innerHTML = '';
  const rulesList = document.createElement('ul');
  rulesList.classList.add('list-disc');
  airline.typeRules.forEach(ruleText => {
      const listItem = document.createElement('li');
      listItem.textContent = ruleText;
      listItem.classList.add('font-normal');
      rulesList.appendChild(listItem);
  });
  typeDescriptionEl.appendChild(rulesList);

  if (airline.hasOwnProperty('stock')) {
    airline.tempStockPrice ??= airline.stock.stockPrice;
    const brokerFee = gameConstants.stockConsts.brokerFeeBase + airline.stock.stockPrice * gameConstants.stockConsts.brokerFee * 1000000;
    document.querySelector('.airlineStockPrice').textContent = "$" + commaSeparateNumber(airline.tempStockPrice);
    document.querySelector('.airlineSharesOutstanding').textContent = commaSeparateNumber(airline.stock.sharesOutstanding);
    document.querySelector('.airlineMarketCap').textContent = "$" + commaSeparateNumber(airline.stock.stockPrice * airline.stock.sharesOutstanding, "m");
    document.querySelector('.stockBuyBackCost').textContent = "$" + commaSeparateNumber(1000000 * airline.tempStockPrice + brokerFee, "m");
    document.querySelector('.stockSellRevenue').textContent = "$" + commaSeparateNumber(1000000 * airline.tempStockPrice - brokerFee, "m");
  }

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

  $('.linkCount').text(airline.extendedStats.linkCount)
  $('.destinations').text(airline.extendedStats.destinations)
  $('.countriesServed').text(airline.extendedStats.countriesServed)
  $('.fleetSize').text(airline.extendedStats.fleetSize)
  $('.fleetAge').text(airline.extendedStats.fleetAge)
  $('.fleetCondition').text(airline.extendedStats.fleetCondition)
  $('.fleetUtilization').text(airline.extendedStats.fleetUtilization)
  $('#assets').text('$' + commaSeparateNumber(airline.assets))
  $('#minimumRenewalBalance').text('$' + commaSeparateNumber(airline.minimumRenewalBalance))
}


async function loadSheets() {
	var airlineId = activeAirline.id
	const periodKeys = ['WEEKLY', 'QUARTER', 'YEAR']

	sheetPages.financial = 0
	sheetPages.stats = 0
	officePeriod = 'WEEKLY'
	document.querySelector('#officeCanvas select.period').value = officePeriod

	const headers = {}
	if (_financesEtag) headers['If-None-Match'] = _financesEtag

	try {
		const res = await fetch('/airlines/' + airlineId + '/finances', { method: 'GET', headers })

		if (res.status === 304) {
			// Re-render from existing loaded data without re-fetching
			const totalIncomePages = loadedIncomes[officePeriod].length
			if (totalIncomePages > 0) {
				sheetPages.financial = totalIncomePages - 1
				updateIncomeSheet(loadedIncomes[officePeriod][sheetPages.financial])
			}
			if (loadedLedger.length > 0) {
				sheetPages.ledger = loadedLedger.length - 1
				updateLedgerSheet(loadedLedger[sheetPages.ledger])
			}
			updateIncomeChart(loadedIncomes[officePeriod])

			sheetPages.stats = loadedAirlineStats[officePeriod].length > 0 ? loadedAirlineStats[officePeriod].length - 1 : 0
			const statData304 = loadedAirlineStats[officePeriod][sheetPages.stats] ?? null
			if (statData304) {
				updateAirlineOpSheet(statData304)
				updateAirlineStatSheet(statData304)
				updateAirlineReputationSheet(statData304)
				updateProgress(statData304)
			}
			updateAirlineStatChart(loadedAirlineStats[officePeriod])
			updateOpsChart(loadedAirlineStats[officePeriod])
			updateAirlineReputationChart(loadedAirlineStats[officePeriod])
			renderSheetNav(getActiveSheetType())
			return
		}

		// Reset loaded data for fresh response
		loadedIncomes = Object.fromEntries(periodKeys.map(key => [key, []]))
		loadedLedger = []
		loadedAirlineOps = Object.fromEntries(periodKeys.map(key => [key, []]))
		loadedAirlineStats = Object.fromEntries(periodKeys.map(key => [key, []]))
		loadedAirlineReputation = Object.fromEntries(periodKeys.map(key => [key, []]))

		if (!res.ok) {
			console.log('AJAX error: ' + res.status)
			return
		}
		const etag = res.headers.get('ETag')
		if (etag) _financesEtag = etag
		const data = await res.json()

		var airlineIncomes = data.balances
		$.each(airlineIncomes, function(index, airlineIncome) {
			loadedIncomes[airlineIncome.period].push(airlineIncome)
		})
		var totalPages = loadedIncomes[officePeriod].length
		if (totalPages > 0) {
			sheetPages.financial = totalPages - 1
			updateIncomeSheet(loadedIncomes[officePeriod][sheetPages.financial])
		}
		updateIncomeChart(loadedIncomes[officePeriod])

		const byCycle = {}
		;(data.ledger || []).forEach(e => {
			if (!byCycle[e.cycle]) byCycle[e.cycle] = { cycle: e.cycle, period: 'WEEKLY', entries: [] }
			byCycle[e.cycle].entries.push(e)
		})
		loadedLedger = Object.keys(byCycle).sort((a, b) => parseInt(a) - parseInt(b)).map(c => byCycle[c])
		if (loadedLedger.length > 0) {
			sheetPages.ledger = loadedLedger.length - 1
			updateLedgerSheet(loadedLedger[sheetPages.ledger])
		}

		data.airlineStats.forEach(stat => {
			loadedAirlineStats[stat.period].push(stat)
		})
		const airlineStat = data.airlineStats[data.airlineStats.length - 1] ?? null
		sheetPages.stats = loadedAirlineStats[officePeriod].length > 0 ? loadedAirlineStats[officePeriod].length - 1 : 0

		updateAirlineStatChart(loadedAirlineStats[officePeriod])
		updateOpsChart(loadedAirlineStats[officePeriod])
		updateAirlineReputationChart(loadedAirlineStats[officePeriod])

		const statData = loadedAirlineStats[officePeriod][sheetPages.stats] ?? airlineStat
		updateAirlineOpSheet(statData)
		updateAirlineStatSheet(statData)
		updateAirlineReputationSheet(statData)
		updateProgress(statData)
		renderSheetNav(getActiveSheetType())
	} catch (e) {
		console.log('AJAX error: ' + e)
	}
}

function updateIncomeChart(airlineIncomes) {
	var incomes = airlineIncomes || loadedIncomes[officePeriod];
	ensureChart('totalProfitChart', function() {
		plotIncomeChart(incomes, officePeriod, 'totalProfitChart')
	})
}

function updateAirlineStatChart(stats) {
	var data = stats || loadedAirlineStats[officePeriod];
	ensureChart('airlineStatsChart', function() {
		plotAirlineStats(data, officePeriod, 'airlineStatsChart')
	})
}

function updateOpsChart(stats) {
	var data = stats || loadedAirlineStats[officePeriod];
	ensureChart('opsChart', function() {
		plotOpsChart(data, officePeriod, 'opsChart')
	})
}

function updateAirlineReputationChart(stats) {
	var data = stats || loadedAirlineStats[officePeriod];
	ensureChart('airlineReputationChart', function() {
		plotAirlineReputationChart(data, officePeriod, 'airlineReputationChart')
	})
}

// Helper: ensure chart for containerId is created if visible, otherwise destroyed
function ensureChart(containerId, createCb) {
	var $c = $('#' + containerId)
	if ($c.length === 0) return
	if ($c.is(':visible')) {
		// create chart (plot-* functions will call ChartUtils.createChart)
		try { createCb() } catch (e) { /* ignore create errors */ }
	} else if (window.ChartUtils) {
		window.ChartUtils.destroyChart($c.get(0))
	}
}

function officeHistoryStep(step) {
	const type = getActiveSheetType()
	if (!type) return
	const config = SHEET_CONFIG[type]
	const data = config.getData()
	const newPage = Math.max(0, Math.min(data.length - 1, getSheetPage(type) + step))
	setSheetPage(type, newPage)
	config.updateSheet(data[newPage])
	renderSheetNav(type)
}

function changeOfficePeriod(period) {
	officePeriod = period
	const type = getActiveSheetType()
	if (!type || type === 'ledger') return  // ledger tab doesn't use periods
	const config = SHEET_CONFIG[type]
	const data = config.getData()
	setSheetPage(type, Math.max(data.length - 1, 0))
	config.updateSheet(data[getSheetPage(type)])
	config.updateChart()
	// Rebuild nav so period label and disabled states refresh
	document.getElementById(config.sheetId)?.querySelector('.sheet-nav')?.remove()
	renderSheetNav(type)
}

function updateIncomeSheet(b) {
	if (!b) return
	const fmt = v => '$' + commaSeparateNumber(v)
	$('#balTicketRevenue').text(fmt(b.ticketRevenue))
	$('#balLoungeRevenue').text(fmt(b.loungeRevenue))
	const totalRevenue = b.ticketRevenue + b.loungeRevenue
	$('#balRevenueTot').text(fmt(totalRevenue))

	$('#balStaff').text(fmt(b.staff))
	$('#balStaffOvertime').text(fmt(b.staffOvertime))
	$('#balFlightCrew').text(fmt(b.flightCrew))
	$('#balFuelNormalized').text(fmt(b.fuelNormalized))
	$('#balDeprication').text(fmt(b.deprecation))
	$('#balAirportRentals').text(fmt(b.airportRentals))
	$('#balInflightService').text(fmt(b.inflightService))
	$('#balDelay').text(fmt(b.delay))
	$('#balMaintenance').text(fmt(b.maintenance))
	$('#balLounge').text(fmt(b.lounge))
	$('#balAdvertising').text(fmt(b.advertising))
	const totalExpense = b.staff + b.staffOvertime + b.flightCrew + b.fuelNormalized + b.deprecation + b.airportRentals + b.inflightService + b.delay + b.maintenance + b.lounge + b.advertising
	$('#balExpenseTot').text(fmt(totalExpense))
	$('#balOperatingIncome').text(fmt(b.normalizedOperatingIncome))
	$('#balFuelTax').text(fmt(b.fuelTax))
	$('#balDenormalizedFuel').text(fmt(b.fuel - b.fuelNormalized))
	$('#balLoanInterest').text(fmt(b.loanInterest))
	const totalNonOperating = b.fuelTax + (b.fuel + b.fuelNormalized) + b.loanInterest
	$('#balNonOperatingTot').text(fmt(totalNonOperating))

	$('#balNetIncome').text(fmt(b.income))
}

function updateAssetChart() {
	var data = loadedIncomes['WEEKLY'] ?? []
	ensureChart('assetChart', function() {
		plotAssetChart(data, 'assetChart')
	})
}


function formatLedgerType(type) {
	return type.split('_').map(w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase()).join(' ')
}

function updateLedgerSheet(weekData) {
	const $entries = $('#ledgerEntries')
	$entries.empty()
	if (!weekData) return
	weekData.entries.forEach(entry => {
		const $row = $('<div class="table-row"></div>')
		$row.append('<div class="cell info text-xs opacity-60" style="width: 15%;">#' + entry.id + '</div>')
		$row.append('<div class="cell" style="width: 25%;">' + formatLedgerType(entry.entryType) + '</div>')
		$row.append('<div class="cell" style="width: 40%;">' + (entry.description || '') + '</div>')
		$row.append('<div class="cell text-right" style="width: 20%;">' + '$' + commaSeparateNumber(entry.amount) + '</div>')
		$entries.append($row)
	})
	const total = weekData.entries.reduce((sum, e) => sum + e.amount, 0)
	const sign = total < 0 ? '-' : ''
	const $row = $('<div class="table-row"></div>')
	$row.append('<div class="cell" style="width: 15%;"></div>')
	$row.append('<div class="cell h4" style="width: 25%;">Total:</div>')
	$row.append('<div class="cell" style="width: 40%;"></div>')
	$row.append('<div class="cell totalLedger" style="width: 20%; text-align: right;">' + sign + '$' + commaSeparateNumber(Math.abs(total)) + '</div>')
	$entries.append($row)
}

function updateAirlineStatSheet(airlineStats) {
    if (!airlineStats) return
    // updateAllTextNodes(".officeCycleText", getGameDate(airlineStats.cycle, airlineStats.period, true));
    Object.keys(airlineStats).forEach(key => {
        const element = document.querySelector(`#airlineStatsSheet .${key}`)
        if (element) element.textContent = commaSeparateNumber(airlineStats[key])
    })
}

function updateAirlineOpSheet(opsData) {
    if (!opsData) return
    // updateAllTextNodes(".officeCycleText", getGameDate(opsData.cycle, opsData.period, true));
    Object.keys(opsData).forEach(key => {
        const element = document.querySelector(`#opsSheet .${key}`)
        if (!element) return
        element.textContent = opsData[key] < 1
            ? commaSeparateNumber(opsData[key] * 100)
            : commaSeparateNumber(opsData[key])
        const metric = gameConstants?.stockMetrics?.[key.toLowerCase()]
        if (metric) {
            const normalizedValue = (opsData[key] - metric.floor) / (metric.target - metric.floor)
            element.classList.remove('text-success', 'text-middling', 'text-danger', 'text-warning')
            if (normalizedValue >= 0.8) element.classList.add('text-success')
            else if (normalizedValue <= 0.2) element.classList.add('text-danger')
            else if (normalizedValue <= 0.5) element.classList.add('text-warning')
            else element.classList.add('text-middling')
        }
    })
}

function updateAirlineReputationSheet(airlineReputation) {
    if (!airlineReputation) return
    Object.keys(airlineReputation).forEach(key => {
        const el = document.querySelector(`#airlineReputationSheet .${key}`)
        if (el) el.textContent = commaSeparateNumber(airlineReputation[key])
    })
}

function setTargetServiceQuality(targetServiceQuality) {
	var airlineId = activeAirline.id
	var url = "/airlines/" + airlineId + "/target-service-quality"
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
	var url = "/airlines/" + airlineId + "/minimum-renewal-balance"
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
	var url = "/airlines/" + airlineId + "/airplane-renewal"
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
	    	if (result.threshold) {
	    		$('#airplaneRenewal').text('Below ' + result.threshold + "%")
	    		$('#airplaneRenewalInput').val(result.threshold)
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

function doStockOp(operation = 'buyback') {
  const buttons = document.querySelectorAll('.stockOpBtn');
  buttons.forEach(button => {
      button.disabled = true;
  });
	var url = `/airlines/${activeAirline.id}/stock-op/${operation}`
	var data = {"sharesOutstanding": parseInt(activeAirline.stock.sharesOutstanding)}

	$.ajax({
		type: 'PUT',
		url: url,
	    data: JSON.stringify(data),
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(result) {
				activeAirline.stock.sharesOutstanding = result.sharesOutstanding
				activeAirline.tempStockPrice = result.stockPrice
				activeAirline.balance = result.balance
        updateAirlineDetails()
        refreshTopBar(activeAirline)
        buttons.forEach(button => {
            button.disabled = false;
        });
	    },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	})
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
	var url = "/airlines/" + airlineId + "/airline-code"
    var data = { "airlineCode" : airlineCode }
	$.ajax({
		type: 'PUT',
		url: url,
	    data: JSON.stringify(data),
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(result) {
	    	activeAirline.airlineCode = result.airlineCode
	    	$('#airlineCode').text(result.airlineCode)
	    	$('#airlineCodeInput').val(result.airlineCode)
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
    var url = "/airlines/" + airlineId + "/airline-name"
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
        var url = "/airlines/" + airlineId + "/airline-name"
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
		url: "/logos/templates",
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(templates) {
	    	$.each(templates, function(index, templateIndex) {
	    		$('#logoTemplates').append('<div style="padding: 2px;" class="clickable" onclick="selectLogoTemplate(' + templateIndex + ')"><img style="width:48px;height:24px;border: 1px solid var(--border-color);" src="/logos/templates/' + templateIndex + '"></div>')
	    	})


	    },
	    error: function(jqXHR, textStatus, errorThrown) {
            console.log(JSON.stringify(jqXHR));
            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

function editAirlineLogo() {
	logoModalConfirm = setAirlineLogo
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

	var url = "/logos/templates/" + logoTemplate + "?color1=" + encodeURIComponent(color1) + "&color2=" + encodeURIComponent(color2) + "&dummy=" + Math.random()
	if (logoTemplate >= 0) {
	    url = "/logos/preview?templateIndex=" + logoTemplate + "&color1=" + encodeURIComponent(color1) + "&color2=" + encodeURIComponent(color2) + "&dummy=" + Math.random()
	}
	$('#logoPreview').empty();
	$('#logoPreview').append('<img style="width:128px;height:auto;border: 1px solid var(--border-color);" src="' + url + '">')
}

function setAirlineLogo() {
	var logoTemplate = $('#logoTemplateIndex').val()
	var color1 = $('#logoModal .picker.color1').val()
    var color2 = $('#logoModal .picker.color2').val()

	var url = "/airlines/" + activeAirline.id + "/set-logo?templateIndex=" + logoTemplate + "&color1=" + encodeURIComponent(color1) + "&color2=" + encodeURIComponent(color2)
    $.ajax({
		type: 'POST',
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
	var url = "/airlines/" + activeAirline.id + "/set-color?color=" + encodeURIComponent(color)
    $.ajax({
		type: 'POST',
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
    initLogoUpload($("#uploadLogoModal .uploadPanel"), "/airlines/" + activeAirline.id + "/logo", "logoFile", function(data) {
        closeModal($('#uploadLogoModal'))
        updateAirlineLogo()
    });
}

function updateLiveryInfo() {
    $('#officeCanvas img.livery').attr('src', '/airlines/' + activeAirline.id + "/livery?dummy=" + Math.random())
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
    initLogoUpload($("#uploadLiveryModal .uploadPanel"), "/airlines/" + activeAirline.id + "/livery", "liveryFile", function(data) {
        closeModal($('#uploadLiveryModal'))
        updateLiveryInfo()
    });
}

function deleteLivery() {
    var url = "/airlines/" + activeAirline.id + "/livery"
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
    var url = "/airlines/" + activeAirline.id + "/slogan"
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
    var url = "/airlines/" + activeAirline.id + "/slogan"
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
    $('#currentServiceQuality').empty()
    $('#currentServiceQuality').append($(getGradeStarsImgs(Math.round(activeAirline.serviceQuality/10))))

	$('#targetServiceQuality').text(activeAirline.targetServiceQuality)
	$('#targetServiceQualityInput').val(activeAirline.targetServiceQuality)

	$('#serviceFundingDisplaySpan').show()
	$('#serviceFundingInputSpan').hide()

	$('#fundingProjection').text('...')
	$.ajax({
		type: 'GET',
		url: "/airlines/" + activeAirline.id + "/service-funding-projection",
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
		url: "/airlines/" + activeAirline.id + "/airplane-renewal",
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
    		url: "/airlines/" + activeAirline.id + "/championed-countries",
    	    contentType: 'application/json; charset=utf-8',
    	    dataType: 'json',
    	    success: function(championedCountries) {
    	    	$(championedCountries).each(function(index, country) {
    	    		var row = $("<div class='table-row clickable' data-link='country' onclick=\"navigateTo('/country/" + country.countryCode + "');\"></div>")
    	    		row.append("<div class='cell'>" + getRankingImg(country.ranking) + "</div>")
    	    		row.append("<div class='cell'>" + getCountryFlagImg(country.countryCode) + country.name + "</div>")
    	    		$('#championedCountriesList').append(row)
    	    	})

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
		url: "/airlines/" + activeAirline.id + "/championed-airports",
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


function selectSheet(tabEl) {
	const type = tabEl.dataset.type
	const config = SHEET_CONFIG[type]
	if (!config) return

	tabEl.closest('.sheetOptions').querySelectorAll('.cell').forEach(el => el.classList.remove('selected'))
	tabEl.classList.add('selected')

	// Disable period dropdown for the ledger tab
	const periodSelect = document.querySelector('#officeCanvas select.period')
	if (periodSelect) periodSelect.disabled = (type === 'ledger')

	const targetSheet = document.getElementById(config.sheetId)
	targetSheet.parentElement.querySelectorAll('.sheet').forEach(sheet => {
		if (sheet === targetSheet) {
			sheet.style.display = ''
		} else {
			allSheetChartIds.forEach(id => {
				const el = sheet.querySelector(`#${id}`)
				if (el && window.ChartUtils) window.ChartUtils.destroyChart(el)
			})
			sheet.style.display = 'none'
		}
	})

	const data = config.getData()
	config.updateSheet(data[getSheetPage(type)])
	config.updateChart()
	renderSheetNav(type)
}

function updateResetAirlineInfo() {
	var airlineId = activeAirline.id
	var url = "/airlines/" + airlineId + "/reset-consideration"
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
		type: 'POST',
		url: "/airlines/" + activeAirline.id + "/reset?keepAssets=" + keepAssets,
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function() {
	    	updateAirlineInfo(activeAirline.id)
	    	selectedLink = undefined
	    	showWorldMap()
	    },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});

}

function updateManagerStatus() {
    $.ajax({
        type: 'GET',
        url: '/managers/airline/' + activeAirline.id,
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(delegateInfo) {
            refreshAirlineDelegateStatus($('#managerStatus .managerGroups'), delegateInfo)
        },
        error: function(jqXHR, textStatus, errorThrown) {
            console.log('AJAX error: ' + textStatus + ' : ' + errorThrown)
        }
    })
}
