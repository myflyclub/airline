var loadedAlliances = []
var loadedAlliancesById = {}
var selectedAlliance

$( document ).ready(function() {
	loadAllAlliances()
})

function showAllianceCanvas(selectedAllianceId) {
    //not the most ideal point to recheck (since current pending actions could include other canvas irrelevant to this). but this is the easiest for now
    checkPendingActions()

    setActiveDiv($("#allianceCanvas"))
	if (!selectedAllianceId) {
        if (activeAirline) {
            selectedAllianceId = activeAirline.allianceId
        }
    }

	loadAllAlliances(selectedAllianceId)
	if (!activeAirline) {
		$('#currentAirlineMemberDetails').hide()
	} else {
		loadCurrentAirlineMemberDetails()
		$('#currentAirlineMemberDetails').show()
	}
}

function loadCurrentAirlineAlliance(callback) {
	var getUrl = "/airlines/" + activeAirline.id + "/alliance-details"
	$.ajax({
		type: 'GET',
		url: getUrl,
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(allianceDetails) {
	    	callback(allianceDetails)
	    },
	    error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

function loadCurrentAirlineMemberDetails() {
	$('#currentAirlineMemberDetails .allianceName').show()
	$('#toggleFormAllianceButton').hide()
	$('#formAllianceSpan').hide()
	
	$('#currentAirlineAllianceHistory').empty()
	loadCurrentAirlineAlliance(function(allianceDetails) {
		if (allianceDetails.allianceId) {
    		var alliance = loadedAlliancesById[allianceDetails.allianceId]
    		$('#currentAirlineMemberDetails .allianceName').text(alliance.name)
    		$('#currentAirlineMemberDetails .allianceRole').text(allianceDetails.allianceRole)
    		if (alliance.ranking) {
	    		var rankingImg = getRankingImg(alliance.ranking)
	    		$('#currentAirlineMemberDetails .allianceRanking').html(rankingImg)
    		} else {
    			$('#currentAirlineMemberDetails .allianceRanking').text('-')
    		}
    		
    		if (alliance.status == 'Forming') {
				$("#currentAirlineMemberDetails .allianceStatus").text(alliance.status + " - need 2 approved members")
			} else {
				$("#currentAirlineMemberDetails .allianceStatus").text(alliance.status)
			}
    		$('#toggleFormAllianceButton').hide()
    	} else {
    		$('#currentAirlineMemberDetails .allianceName').text('-')
    		$('#currentAirlineMemberDetails .allianceRanking').text('-')
    		$('#currentAirlineMemberDetails .allianceStatus').text('-')
    		if (activeAirline.headquarterAirport) {
    			$('#toggleFormAllianceButton').show()
    		} else {
    			$('#toggleFormAllianceButton').hide()
    		}
    	}
    	
    	$('#currentAirlineAllianceHistory').children("div.table-row").remove()
    	if (allianceDetails.history) {
    		$.each(allianceDetails.history, function(index, entry) {
    			var row = $("<div class='table-row'><div class='cell value' style='width: 30%;'>Week " + entry.cycle + "</div><div class='cell value' style='width: 70%;'>" + entry.description + "</div></div>")
    			$('#currentAirlineAllianceHistory').append(row)
    		})
    	} else {
    		var row = $("<div class='table-row'><div class='cell value' style='width: 30%;'>-</div><div class='cell value' style='width: 70%;'>-</div></div>")
    		$('#currentAirlineAllianceHistory').append(row)
    	}
	})
}

function loadAllAlliances(selectedAllianceId) {	
	loadedAlliances = []
	loadedAlliancesById = {}
	$.ajax({
		type: 'GET',
		url: `/alliances`,
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(alliances) {
	    	loadedAlliances = alliances
	    	$.each(alliances, function(index, alliance) {
	    		loadedAlliancesById[alliance.id] = alliance
	    		alliance.memberCount = 0
	    		$.each(alliance.members, function(index, member) {
	    		    if (member.allianceRole != 'Applicant') {
	    		        alliance.memberCount ++
	    		    }
	    		})
			if (alliance.leader) {
	    			alliance.leaderAirlineName = alliance.leader.name
			} else {
				alliance.leaderAirlineName = '-'
			}
	    		if (alliance.championPoints) {
	    			alliance.championPointsValue = alliance.championPoints
	    		} else {
	    			alliance.championPointsValue = 0
	    		}
	    	})
	    	
	    	var selectedSortHeader = $('#allianceTableSortHeader .table-header .cell.selected')
	    	updateAllianceTable(selectedSortHeader.data('sort-property'), selectedSortHeader.data('sort-order'))
	    	
	    	if (selectedAlliance) {
	    		if (!loadedAlliancesById[selectedAlliance.id]) { //alliance was just deleted
	    			selectedAlliance = undefined
	    			$('#allianceDetails').hide()
	    		}
			} else {
				$('#allianceDetails').hide()
			}

			if (selectedAllianceId) {
                selectAlliance(selectedAllianceId, true)
			}
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

function updateAllianceTable(sortProperty, sortOrder) {
	var allianceTable = $("#allianceCanvas #allianceTable")
	
	allianceTable.children("div.table-row").remove()
	
	//sort the list
	loadedAlliances.sort(sortByProperty(sortProperty, sortOrder == "ascending"))
	
	$.each(loadedAlliances, function(index, alliance) {
		var row = $("<div class='table-row clickable' data-alliance-id='" + alliance.id + "' onclick=\"selectAlliance('" + alliance.id + "')\"></div>")
//		var countryFlagImg = ""
//		if (airline.countryCode) {
//			countryFlagImg = getCountryFlagImg(airline.countryCode)
//		}
		row.append("<div class='cell'>" + alliance.name + "</div>")
		if (alliance.leader) {
			row.append("<div class='cell'>" + getAirlineSpan(alliance.leader.id, alliance.leader.name) + "</div>")
		} else {
			row.append("<div class='cell'>-</div>")
		}
		row.append("<div class='cell' align='right'>" + alliance.memberCount + "</div>")
		if (alliance.championPoints) {
			row.append("<div class='cell' align='right'>" + alliance.championPoints + "</div>")
		} else {
			row.append("<div class='cell' align='right'>-</div>")
		}
		
		if (selectedAlliance && selectedAlliance.id == alliance.id) {
			row.addClass("selected")
		}
		
		allianceTable.append(row)
	});
}

function toggleAllianceTableSortOrder(sortHeader) {
	if (sortHeader.data("sort-order") == "ascending") {
		sortHeader.data("sort-order", "descending")
	} else {
		sortHeader.data("sort-order", "ascending")
	}
	
	sortHeader.siblings().removeClass("selected")
	sortHeader.addClass("selected")
	
	updateAllianceTable(sortHeader.data("sort-property"), sortHeader.data("sort-order"))
}

function selectAlliance(allianceId, isScrollToRow) {
	//update table
	var $row = $("#allianceCanvas #allianceTable .table-row[data-alliance-id='" + allianceId + "']")
	$row.siblings().removeClass("selected")
	$row.addClass("selected")
	loadAllianceDetails(allianceId)

    if (isScrollToRow) {
        scrollToRow($row, $("#allianceCanvas #allianceTableContainer"))
    }
}

function loadAllianceDetails(allianceId) {
	updateAllianceBasicsDetails(allianceId)
	updateAllianceBonus(allianceId)
	updateAllianceChampions(allianceId)
	updateAllianceHistory(allianceId)
	updateAllianceTagColor(allianceId)
	$('#allianceDetails').fadeIn(200)
}


function updateAllianceBasicsDetails(allianceId) {
	var alliance = loadedAlliancesById[allianceId]
	selectedAlliance = alliance

	$("#allianceDetails .allianceName").text(alliance.name)
	if (alliance.status == 'Forming') {
		$("#allianceDetails .allianceStatus").text(alliance.status + " - need 3 approved members")
	} else {
		$("#allianceDetails .allianceStatus").text(alliance.status)
	}


	if (alliance.ranking) {
		var rankingImg = getRankingImg(alliance.ranking)
		$('#allianceDetails .allianceRanking').html(rankingImg)
	} else {
		$('#allianceDetails .allianceRanking').text('-')
	}
	$("#allianceMemberList").children("div.table-row").remove()

	var isAdmin = false
	$.each(alliance.members, function(index, member) {
        if (activeAirline && member.airlineId == activeAirline.id) {
            isAdmin = member.isAdmin
        }
	})

    document.getElementById('allianceStatsTable').innerHTML = '';
    if (alliance.stats) {
        Object.entries(alliance.stats).forEach(([name, value]) => {
            const output =
                `<div class="table-row">
                <div class="cell label">${prettyLabel(name)}:</div>
                <div class="cell value">${commaSeparateNumber(value)}</div>
            </div>`;
            document.getElementById('allianceStatsTable').insertAdjacentHTML('beforeend', output);
        });
    }

    $.ajax({
        type: 'GET',
        url: "/alliances/" + allianceId + "/member-login-status",
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(loginStatusByAirlineId) {
            $.each(alliance.members, function(index, member) {
                var row = $("<div class='table-row clickable' style='height: 20px;' onclick='showAllianceMemberDetails($(this).data(\"member\"))'></div>")
                row.data("member", member)
                row.attr("data-airline-id", member.airlineId)
                let loginStatus = loginStatusByAirlineId[member.airlineId]
                row.append("<div class='cell' style='vertical-align:middle; width: 10px;'><img src='" + getStatusLogo(loginStatus) + "' title='" + getStatusTitle(loginStatus) + "' style='vertical-align:middle;'/>")
                row.append("<div class='cell' style='vertical-align: middle;'>" + getAirlineSpan(member.airlineId, member.airlineName) + "</div>")
                row.append("<div class='cell' style='vertical-align: middle;'>" + member.airlineType + "</div>")
                if (member.allianceRole == "Applicant") {
                    row.append("<div class='cell warning' style='vertical-align: middle;'>" + member.allianceRole + "</div>")
                } else {
                    row.append("<div class='cell' style='vertical-align: middle;'>" + member.allianceRole + "</div>")
                }
                if (activeAirline) {
                    var $actionCell = $("<div class='cell action' style='vertical-align: middle;'></div>")
                    row.append($actionCell)
                }
                $("#allianceMemberList").append(row)
            });
            if (activeAirline && selectedAlliance) {
                $.ajax({
                    type: 'GET',
                    url: "/airlines/" + activeAirline.id + "/evaluate-alliance/" + selectedAlliance.id,
                    contentType: 'application/json; charset=utf-8',
                    dataType: 'json',
                    success: function(result) {
                        if (!result.isMember && !result.rejection) {
                            $('#applyForAllianceButton').show()
                            $('#applyForAllianceRejectionSpan').hide();
                        } else {
                            $('#applyForAllianceButton').hide();
                            if (result.rejection) {
                                $('#applyForAllianceRejection').text(result.rejection)
                                $('#applyForAllianceRejectionSpan').show()
                            } else if (result.isMember){
                                $('#applyForAllianceButton').hide();
                                $('#applyForAllianceRejectionSpan').hide();
                            }
                        }

                        if (result.memberActions) {
                            $.each(result.memberActions, function(index, entry) {
                                var $cell = $("#allianceMemberList .table-row[data-airline-id='" + entry.airlineId + "'] .action")


                                if (entry.acceptRejection) {
                                    $cell.append("<img src='/assets/images/icons/exclamation-circle.png' class='img-button disabled' title='Cannot accept member : " + entry.rejection + "'>")
                                } else if (entry.acceptPrompt) {
                                    var $icon = $("<img src='/assets/images/icons/tick.png' class='img-button' title='Accept Member'>")
                                    $icon.click(function(event) {
                                        event.stopPropagation()
                                        promptConfirm(entry.acceptPrompt, acceptAllianceMember, entry.airlineId)
                                    })
                                    $cell.append($icon)
                                }

                                if (!entry.promoteRejection && entry.promotePrompt) {
                                    var $icon = $("<img src='/assets/images/icons/user-promote.png' class='img-button' title='Promote Member'>")
                                    $icon.click(function(event) {
                                        event.stopPropagation()
                                        promptConfirm(entry.promotePrompt, promoteAllianceMember, entry.airlineId)
                                    })
                                    $cell.append($icon)
                                }
                                if (!entry.demoteRejection && entry.demotePrompt) {
                                    var $icon = $("<img src='/assets/images/icons/user-demote.png' class='img-button' title='Demote Member'>")
                                    $icon.click(function(event) {
                                        event.stopPropagation()
                                        promptConfirm(entry.demotePrompt, demoteAllianceMember, entry.airlineId)
                                    })
                                    $cell.append($icon)
                                }
                                if (!entry.removeRejection && entry.removePrompt) {
                                    var $icon = $("<img src='/assets/images/icons/cross.png' class='img-button' title='Remove Member'>")
                                    $icon.click(function(event) {
                                        event.stopPropagation()
                                        promptConfirm(entry.removePrompt, removeAllianceMember, entry.airlineId)
                                    })
                                    $cell.append($icon)
                                }
                            })
                        }
                    },
                    error: function(jqXHR, textStatus, errorThrown) {
                            console.log(JSON.stringify(jqXHR));
                            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
                    }
                });
            } else {
                $('#applyForAllianceButton').hide()
                $('#applyForAllianceRejectionSpan').hide();
            }
        },
        error: function(jqXHR, textStatus, errorThrown) {
                console.log(JSON.stringify(jqXHR));
                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    });
}

function updateAllianceBonus(allianceId) {
	var alliance = loadedAlliancesById[allianceId]
	
	if (alliance.status == "Forming") {
		$('#allianceCodeShareBonus').hide();
		$('#allianceMaxFrequencyBonus').hide();
		$('#allianceReputationBonus').hide();
		$('#allianceNoneBonus').show();
		
	} else {
		$('#allianceCodeShareBonus').show();
		$('#allianceNoneBonus').hide();

		
		if (alliance.reputationBonus) {
			$('#allianceReputationBonusValue').text(alliance.reputationBonus)
			$('#allianceReputationBonus').show();
		} else {
			$('#allianceReputationBonus').hide();
		}
	}
}

function updateAllianceChampions(allianceId) {
    updateAllianceAirportChampions(allianceId)
    updateAllianceCountryChampions(allianceId)
}

function updateAllianceAirportChampions(allianceId) {
	$('#allianceChampionAirportList').children('div.table-row').remove()
	
	$.ajax({
		type: 'GET',
		url: "/alliances/" + allianceId + "/championed-airports",
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(result) {
	    	var approvedMembersChampions = result.members
	    	var applicantChampions = result.applicants
	    	$(approvedMembersChampions).each(function(index, championDetails) {

	    		var row = $("<div class='table-row clickable' data-link='airport' onclick=\"showAirportDetails('" + championDetails.airportId + "');\"></div>")
	    		row.append("<div class='cell'>" + getRankingImg(championDetails.ranking) + "</div>")
	    		row.append("<div class='cell'>" + getCountryFlagImg(championDetails.countryCode) + championDetails.airportText + "</div>")
	    		row.append("<div class='cell'>" + getAirlineLogoImg(championDetails.airlineId) + championDetails.airlineName + "</div>")
	    		row.append("<div class='cell' align='right'>" + commaSeparateNumber(championDetails.loyalistCount) + "</div>")
	    		row.append("<div class='cell' align='right'>" + championDetails.reputationBoost + "</div>") 
	    		$('#allianceChampionAirportList').append(row)
	    	})
	    	
	    	$(applicantChampions).each(function(index, championDetails) {
	    		var row = $("<div class='table-row clickable' data-link='airport' onclick=\"showAirportDetails('" + championDetails.airportId + "');\"></div>")
	    		row.append("<div class='cell'>" + getRankingImg(championDetails.ranking) + "</div>")
                row.append("<div class='cell'>" + getCountryFlagImg(championDetails.countryCode) + championDetails.airportText + "</div>")
                row.append("<div class='cell'>" + getAirlineLogoImg(championDetails.airlineId) + championDetails.airlineName + "</div>")
                row.append("<div class='cell' align='right'>" + commaSeparateNumber(championDetails.loyalistCount) + "</div>")
                row.append("<div class='cell warning info svg' align='right'><img src='/assets/images/icons/information.svg' title='Points not counted as this airline is not an approved member yet'>" + championDetails.reputationBoost + "</div>")
	    		$('#allianceChampionAirportList').append(row)
	    	})
	    	
	    	if ($(approvedMembersChampions).length == 0 && $(applicantChampions).length == 0) {
	    		var row = $("<div class='table-row'></div>")
	    		row.append("<div class='cell'>-</div>")
	    		row.append("<div class='cell'>-</div>")
	    		row.append("<div class='cell'>-</div>")
	    		row.append("<div class='cell' align='right'>-</div>")
	    		row.append("<div class='cell' align='right'>-</div>")
	    		$('#allianceChampionAirportList').append(row)
	    	}
	    	$('#allianceCanvas .totalReputation').text(result.totalReputation)
	    	$('#allianceCanvas .reputationTruncatedEntries').text(result.truncatedEntries)
	    },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

function updateAllianceCountryChampions(allianceId) {
	$('#allianceChampionCountryList').children('div.table-row').remove()

	$.ajax({
		type: 'GET',
		url: "/alliances/" + allianceId + "/championed-countries",
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(championedCountries) {
	    	$(championedCountries).each(function(index, championDetails) {
                var country = championDetails.country
                var row = $("<div class='table-row clickable' onClick='navigateTo(/country/" + country.countryCode + "'></div>")
                row.append("<div class='cell'>" + getRankingImg(championDetails.ranking) + "</div>")
                row.append("<div class='cell'>" + getCountryFlagImg(country.countryCode) + country.name + "</div>")
                row.append("<div class='cell'>" + getAirlineLogoImg(championDetails.airlineId) + championDetails.airlineName + "</div>")
                $('#allianceChampionCountryList').append(row)
            })

            if ($(championedCountries).length == 0) {
	    		var row = $("<div class='table-row'></div>")
	    		row.append("<div class='cell'>-</div>")
	    		row.append("<div class='cell'>-</div>")
	    		row.append("<div class='cell'>-</div>")
	    		$('#allianceChampionCountryList').append(row)
	    	}
	    },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

function updateAllianceHistory(allianceId) {
	var alliance = loadedAlliancesById[allianceId]
	$('#allianceHistory').children("div.table-row").remove()
	$.each(alliance.history, function(index, entry) {
		var row = $("<div class='table-row'><div class='cell value' style='width: 30%;'>Week " + entry.cycle + "</div><div class='cell value' style='width: 70%;'>" + entry.description + "</div></div>")
		$('#allianceHistory').append(row)
	})
}

function updateAllianceTagColor(allianceId) {
    if (activeAirline) {
        $('#allianceDetails .tagColor.picker').off("change.setColor")

        $('#allianceDetails .tagColor.picker').on("change.setColor", function() {
            var newColor = $(this).val()

           checkAllianceLabelColorAction(allianceId, function(airlineOverride) {
            setAllianceLabelColor(allianceId, newColor, function() {
                var selectedSortHeader = $('#allianceTableSortHeader .table-header .cell.selected')
                updateAllianceTable(selectedSortHeader.data('sort-property'), selectedSortHeader.data('sort-order'))
                updateAllianceBasicsDetails(allianceId)
            }, airlineOverride)
           })

        });

        $.ajax({
            type: 'GET',
            url: "/airlines/" + activeAirline.id + "/alliance-label-color?allianceId=" + allianceId,
            contentType: 'application/json; charset=utf-8',
            dataType: 'json',
            success: function(result) {
                if (result.color) {
                    $('#allianceDetails .tagColor.picker').val('#' + result.color)
                } else {
                    $('#allianceDetails .tagColor.picker').val('')
                }
                $('#allianceDetails .tagColor').show()
            },
            error: function(jqXHR, textStatus, errorThrown) {
                    console.log(JSON.stringify(jqXHR));
                    console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
            }
        });


    } else {
        $('#allianceDetails .tagColor').hide()
    }
}


function toggleFormAlliance() {
	$('#currentAirlineMemberDetails .allianceName').hide()
	$('#toggleFormAllianceButton').hide()
	$('#formAllianceWarning').hide()
	$('#formAllianceSpan').show()
}

function formAlliance(allianceName) {
	var url = "/airlines/" + activeAirline.id + "/form-alliance"
	$.ajax({
		type: 'POST',
		url: url,
		data: { 'allianceName' : allianceName } ,
	    dataType: 'json',
	    success: function(newAlliance) {
	    	if (!newAlliance.rejection) {
	    		showAllianceCanvas()
	    	} else {
	    		$('#formAllianceWarning').text(newAlliance.rejection)
	    		$('#formAllianceWarning').show()
	    		activeAirline.allianceId = newAlliance.id
	    		activeAirline.allianceName = newAlliance.name
	    		updateChatTabs()
	    	}
	    	
	    },
        error: function(jqXHR, textStatus, errorThrown) {
            console.log(JSON.stringify(jqXHR));
            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

function removeAllianceMember(removeAirlineId) {
	var url = "/airlines/" + activeAirline.id + "/remove-alliance-member/" + removeAirlineId
	$.ajax({
		type: 'DELETE',
		url: url,
		contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(result) {
	    	showAllianceCanvas()
	    	if (activeAirline.id == removeAirlineId) { //leaving alliance
	    	    activeAirline.allianceId = undefined
	    	    activeAirline.allianceName = undefined
	    	    updateChatTabs()
	    	}
	    },
        error: function(jqXHR, textStatus, errorThrown) {
            console.log(JSON.stringify(jqXHR));
            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

function acceptAllianceMember(acceptAirlineId) {
	var url = "/airlines/" + activeAirline.id + "/accept-alliance-member/" + acceptAirlineId
	$.ajax({
		type: 'POST',
		url: url,
		contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(result) {
	    	showAllianceCanvas()
	    },
        error: function(jqXHR, textStatus, errorThrown) {
            console.log(JSON.stringify(jqXHR));
            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

function promoteAllianceMember(promoteAirlineId) {
	var url = "/airlines/" + activeAirline.id + "/promote-alliance-member/" + promoteAirlineId
	$.ajax({
		type: 'POST',
		url: url,
		contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(result) {
	    	showAllianceCanvas()
	    },
        error: function(jqXHR, textStatus, errorThrown) {
            console.log(JSON.stringify(jqXHR));
            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

function demoteAllianceMember(promoteAirlineId) {
	var url = "/airlines/" + activeAirline.id + "/demote-alliance-member/" + promoteAirlineId
	$.ajax({
		type: 'POST',
		url: url,
		contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(result) {
	    	showAllianceCanvas()
	    },
        error: function(jqXHR, textStatus, errorThrown) {
            console.log(JSON.stringify(jqXHR));
            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

function applyForAlliance() {
	$.ajax({
		type: 'POST',
		url: "/airlines/" + activeAirline.id + "/apply-for-alliance/" + selectedAlliance.id,
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(result) {
	        activeAirline.allianceId = result.allianceId
	    	showAllianceCanvas()
	    	//activeAirline.allianceName = result.allianceName //not yet a member
	    },
	    error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

function checkResetAllianceLabelColor(targetAllianceId) {
    checkAllianceLabelColorAction(targetAllianceId, function(airlineOverride) {
        resetAllianceLabelColor(targetAllianceId, function() {
            var selectedSortHeader = $('#allianceTableSortHeader .table-header .cell.selected')
            updateAllianceTable(selectedSortHeader.data('sort-property'), selectedSortHeader.data('sort-order'))
            updateAllianceBasicsDetails(targetAllianceId)
            $('#allianceDetails .tagColor.picker').val('')
        },airlineOverride)
    })
}

function checkAllianceLabelColorAction(targetAllianceId, colorAction) {
    if (activeAirline.isAllianceAdmin) {
        promptSelection("Do you want to apply this to all your alliance members or just your airline?", ["Alliance", "Airline"], function(changeType) {
            var airlineOverride = (changeType === "Airline")
            colorAction(airlineOverride)
        })
    } else {
        colorAction(true)
    }
}

function updateAllianceLogo() {
	$('.allianceLogo').attr('src', '/alliances/' + activeAirline.allianceId + "/logo");
}

function showUploadLogoAlliance() {
	if (activeAirline.isAllianceAdmin && activeAirline.reputation >= 60) {
		updateLogoUploadAlliance()
		$('#uploadLogoModalAlliance .uploadForbidden').hide()
		$('#uploadLogoModalAlliance .uploadPanel').show()
	} else {
		$('#uploadLogoModalAlliance .uploadForbidden .warning').text('You may only upload alliance logo at Reputation 40 or above')
		$('#uploadLogoModalAlliance .uploadForbidden').show()
		$('#uploadLogoModalAlliance .uploadPanel').hide()
	}

	$('#uploadLogoModalAlliance').fadeIn(200)
}

function updateLogoUploadAlliance() {
	const $panel = $('#uploadLogoModalAlliance .uploadPanel');
	const uploadUrl = "/airlines/" + activeAirline.id + "/set-alliance-logo/" + activeAirline.allianceId;

	initLogoUpload($panel, uploadUrl, "logoFile", function(data) {
		closeModal($('#uploadLogoModalAlliance'));
		updateAllianceLogo();
	});
}

function editAllianceLogo() {
	logoModalConfirm = setAllianceLogoFromTemplate
	$('#logoTemplateIndex').val(0)
	generateLogoPreview()
	$('#logoModal').fadeIn(200)
}

function setAllianceLogoFromTemplate() {
	var logoTemplate = $('#logoTemplateIndex').val()
	var color1 = $('#logoModal .picker.color1').val()
	var color2 = $('#logoModal .picker.color2').val()

	var url = "/airlines/" + activeAirline.id + "/set-alliance-logo-template/" + activeAirline.allianceId
		+ "?templateIndex=" + logoTemplate + "&color1=" + encodeURIComponent(color1)
		+ "&color2=" + encodeURIComponent(color2)
	$.ajax({
		type: 'POST',
		url: url,
		contentType: 'application/json; charset=utf-8',
		dataType: 'json',
		success: function() {
			updateAllianceLogo()
		},
		error: function(jqXHR, textStatus, errorThrown) {
			console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
		}
	});
}
