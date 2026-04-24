function showRankingCanvas() {
    setActiveDiv($("#rankingCanvas"))
    loadRanking()
}

function loadRanking() {
    $('#rankingCanvas .table').hide() //hide all tables until they are loaded
    $.ajax({
        type: 'GET',
        url: activeAirline ? "/rankings/" + activeAirline.id : "/rankings",
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function (allRankings) {
            $.each(allRankings, function (rankingType, rankings) {
                updateRankingTable(rankingType, rankings)
            })
            $('#rankingCanvas .table').fadeIn(200)
        },
        error: function (jqXHR, textStatus, errorThrown) {
            console.log(JSON.stringify(jqXHR));
            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        },
        beforeSend: function () {
            $('body .loadingSpinner').show()
        },
        complete: function () {
            $('body .loadingSpinner').hide()
        }
    });
}

var CURRENCY_TYPES = new Set(["STOCK_PRICE", "LINK_PROFIT", "LINK_PROFIT_ALL", "LINK_LOSS", "AIRLINE_VALUE"])
var PERCENTAGE_TYPES = new Set(["PASSENGER_SATISFACTION", "PASSENGER_DISSATISFACTION", "ON_TIME", "MOST_CONGESTED_AIRPORT"])

function updateRankingTable(rankingType, rankings) {
    //locate which table
    var rankingTable;
    if (rankingType == "PASSENGER") {
        rankingTable = $('#passengerRank')
    } else if (rankingType == "PASSENGER_MILE") {
        rankingTable = $('#passengerMileRank')
    } else if (rankingType == "TOURIST_COUNT") {
        rankingTable = $('#touristCountRank')
    } else if (rankingType == "BUSINESS_COUNT") {
        rankingTable = $('#businessCountRank')
    } else if (rankingType == "ELITE_COUNT") {
        rankingTable = $('#eliteCountRank')
    } else if (rankingType == "CODESHARE_COUNT") {
        rankingTable = $('#codeshareCountRank')
    } else if (rankingType == "STOCK_PRICE") {
        rankingTable = $('#stockRank')
    } else if (rankingType == "PASSENGER_QUALITY") {
        rankingTable = $('#passengerMileQualityRank')
    } else if (rankingType == "LINK_DISTANCE") {
        rankingTable = $('#linkDistanceRank')
    } else if (rankingType == "LINK_SHORTEST") {
        rankingTable = $('#linkShortestRank')
    } else if (rankingType == "PASSENGER_SATISFACTION") {
        rankingTable = $('#satisfactionRank')
    } else if (rankingType == "PASSENGER_SPEED") {
        rankingTable = $('#speedRank')
    } else if (rankingType == "REPUTATION") {
        rankingTable = $('#reputationRank')
    } else if (rankingType == "AIRLINE_PROFIT_MARGIN") {
        rankingTable = $('#airlineProfitMarginRank')
    } else if (rankingType == "ON_TIME") {
        rankingTable = $('#onTimeRank')
    } else if (rankingType == "PASSENGER_DISSATISFACTION") {
        rankingTable = $('#dissatisfactionRank')
    } else if (rankingType == "SERVICE_QUALITY") {
        rankingTable = $('#serviceQualityRank')
    } else if (rankingType == "LINK_COUNT") {
        rankingTable = $('#linkCountRank')
    } else if (rankingType == "LINK_FREQUENCY") {
        rankingTable = $('#linkFrequency')
    } else if (rankingType == "LINK_PROFIT") {
        rankingTable = $('#linkProfitRank')
    } else if (rankingType == "LINK_PROFIT_ALL") {
        rankingTable = $('#linkProfitTotalRank')
    } else if (rankingType == "LINK_LOSS") {
        rankingTable = $('#linkLossRank')
    } else if (rankingType == "LINKS_COUNT_LOSS") {
        rankingTable = $('#linksLossCountRank')
    } else if (rankingType == "UNIQUE_IATA") {
        rankingTable = $('#uniqueIataRank')
    } else if (rankingType == "UNIQUE_COUNTRIES") {
        rankingTable = $('#uniqueCountriesRank')
    } else if (rankingType == "LINK_COUNT_SMALL_TOWN") {
        rankingTable = $('#linksSmallTown')
    } else if (rankingType == "LINK_COUNT_LOW_INCOME") {
        rankingTable = $('#linksLowIncome')
    } else if (rankingType == "LOUNGE") {
        rankingTable = $('#loungeRank')
    } else if (rankingType == "AIRPORT_TRAFFIC") {
        rankingTable = $('#airportTrafficRank')
    } else if (rankingType == "AIRLINE_VALUE") {
        rankingTable = $('#airlineValueRank')
    } else if (rankingType == "AIRLINE_PRESTIGE") {
        rankingTable = $('#airlinePrestigeRank')
    } else if (rankingType == "MOST_CONGESTED_AIRPORT") {
        rankingTable = $('#mostCongestedAirportRank')
    } else if (rankingType == "LARGEST_FLEET") {
        rankingTable = $('#largestFleetRank')
    } else if (rankingType == "AIRPORT") {
        rankingTable = $('#airportRank')
    } else if (rankingType == "INTERNATIONAL_PAX") {
        rankingTable = $('#internationPaxRank')
    } else if (rankingType == "DOMESTIC_PAX") {
        rankingTable = $('#domesticPaxRank')
    } else if (rankingType == "ALLIANCE_TRAVELERS") {
        rankingTable = $('#allianceTravelersRank')
    } else if (rankingType == "ALLIANCE_CODESHARES") {
        rankingTable = $('#allianceCodesharesRank')
    } else if (rankingType == "ALLIANCE_ELITE") {
        rankingTable = $('#allianceElitesRank')
    } else if (rankingType == "ALLIANCE_AIRPORT_REP") {
        rankingTable = $('#allianceAirportRank')
    } else if (rankingType == "ALLIANCE_STOCKS") {
        rankingTable = $('#allianceStockRank')
    } else if (rankingType == "ALLIANCE_LOUNGE") {
        rankingTable = $('#allianceLoungeRank')
    } else {
        console.log("Unknown ranking type " + rankingType)
    }

    if (rankingTable) {
        rankingTable.children('.table-row').remove()
        var maxEntry = 20
        var currentAirlineRanking;
        var hasPrize = rankings.length > 0 && rankings[0].reputationPrize;
        $.each(rankings, function (index, ranking) {
            if (index < maxEntry) {
                rankingTable.append(getRankingRow(ranking, rankingType))
            }
            if (rankingType != "LINK_PROFIT_ALL" && activeAirline && !currentAirlineRanking) {
                if (ranking.airlineId == activeAirline.id) {
                    currentAirlineRanking = ranking
                } else if ((rankingType == "AIRPORT" || rankingType == "MOST_CONGESTED_AIRPORT") && ranking.airportId &&
                           activeAirline.headquarterAirport &&
                           ranking.airportId == activeAirline.headquarterAirport.airportId) {
                    currentAirlineRanking = ranking
                }
            }
        })

        if (currentAirlineRanking) {
            rankingTable.append(getDividerRow())
            if (hasPrize) {
                currentAirlineRanking.reputationPrize = currentAirlineRanking.reputationPrize || 0
            }
            rankingTable.append(getRankingRow(currentAirlineRanking, rankingType)) //lastly append a row of current airline
        }

    }
}

function getRankingRow(ranking, rankingType) {
    var row = $("<div class='table-row'></div>")
    row.append("<div class='cell'>" + ranking.rank + "</div>")
    row.append("<div class='cell'>" + getMovementLabel(ranking.movement) + "</div>")
    if (ranking.airlineId) {
        var entry = getAirlineSpan(ranking.airlineId, ranking.airlineName, buildAirlineTooltipContent(ranking.airlineId, ranking.airlineSlogan))
        if (ranking.rankInfo) {
            if (ranking.rankInfo.from && ranking.rankInfo.to) {
                entry += ' : ' + "<span style='vertical-align:bottom'>" + getAirportSpan(ranking.rankInfo.from) + "<img style='vertical-align:bottom; margin:0 3px;' src='/assets/images/icons/12px/arrow-double.png'/>" + getAirportSpan(ranking.rankInfo.to) + "</span>"
            } else {
                entry += ' : ' + ranking.rankInfo
            }
        }
        row.append("<div class='cell'>" + entry + "</div>")
    } else if (ranking.airportId) {
        var entry = getCountryFlagImg(ranking.countryCode) + ranking.iata + " : " + ranking.airportName
        row.append("<div class='cell'>" + entry + "</div>")
    } else if (ranking.allianceId) {
        row.append("<div class='cell'>" + getAllianceLogoImg(ranking.allianceId) + ranking.allianceName + "</div>")
    } else if (ranking.airport1 && ranking.airport2) {
        var entry = getAirportSpan(ranking.airport1) + " ↔ " + getAirportSpan(ranking.airport2)
        row.append("<div class='cell'>" + entry + "</div>")
    }
    var valueDisplay = commaSeparateNumber(ranking.rankedValue)
    if (CURRENCY_TYPES.has(rankingType)) valueDisplay = '$' + valueDisplay
    else if (PERCENTAGE_TYPES.has(rankingType)) valueDisplay = valueDisplay + '%'
    row.append("<div class='cell' style='text-align: right;'>" + valueDisplay + "</div>")

    if (ranking.reputationPrize || ranking.reputationPrize === 0) {
        row.append("<div class='cell' style='text-align: right;'>" + getRankingImg(ranking.rank, true) + commaSeparateNumber(ranking.reputationPrize) + "</div>")
    }

    return row
}

function getDividerRow() {
    var row = $("<div class='table-row'></div>")
    row.append("<div class='cell' style='border-top: 1px solid #6093e7; padding: 0;'></div>")
    row.append("<div class='cell' style='border-top: 1px solid #6093e7; padding: 0;'></div>")
    row.append("<div class='cell' style='border-top: 1px solid #6093e7; padding: 0;'></div>")
    row.append("<div class='cell' style='border-top: 1px solid #6093e7; padding: 0;'></div>")
    row.append("<div class='cell' style='border-top: 1px solid #6093e7; padding: 0;'></div>")

    return row
}


function getMovementLabel(movement) {
    if (movement == 0) {
        return ''
    } else if (movement < 0) { //going up in ranking
        return "↑" + movement * -1
    } else {
        return "↓" + movement
    }
}