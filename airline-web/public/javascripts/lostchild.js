var lostChildFound = false

function initLostChild() {
    $('#lostChildModal').hide()
    $("#lostChildButton").hide()
    if (activeAirline) {
        updateLostChildModal()
    }
}

function closeLostChildModal() {
    closeModal($('#lostChildModal'))
    removeFootprints($('#lostChildModal'))
    removeConfetti($('#lostChildModal'))
}

function updateLostChildModal() {
    var table = $("#lostChildGuessTable")
    table.children(".table-row").remove()

    var url = "/lost-child/attempt-status/" + activeAirportId + "/" + activeAirline.id
    $.ajax({
        type: 'GET',
        url: url,
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(result) {
            if ($.isEmptyObject(result)) {
                return
            }
            $("#lostChildAttemptsLeft").text(result.attemptsLeft)
            $("#lostChildInterrogationsLeft").text(result.interrogationsLeft)

            $.each(result.guesses, function(index, guess) {
                var row = $("<div class='table-row'></div>")
                var actionCell
                if (guess.isInterrogation) {
                    actionCell = "<div class='cell label' style='width:35%'>🔍 Interrogated</div>"
                } else {
                    actionCell = "<div class='cell label' style='width:35%'>" + getAirportText(guess.city, guess.airportCode) + "</div>"
                }
                row.append(actionCell)
                row.append("<div class='cell label' style='width:65%'>" + guess.clueText + "</div>")
                table.append(row)
            });
            if (result.guesses.length == 0) {
                var row = $("<div class='table-row'></div>")
                row.append("<div class='cell label' style='width:35%'>-</div>")
                row.append("<div class='cell label' style='width:65%'>No actions yet. Interrogate the child or fly them to an airport!</div>")
                table.append(row)
            }

            var awardOptionsDiv = $("#lostChildRewardOptions")
            var pickedRewardDiv = $("#lostChildPickedReward")
            var exhaustedDiv = $("#lostChildExhausted")
            var interrogateButton = $("#lostChildModal .interrogateButton")
            var flyButton = $("#lostChildModal .flyButton")
            awardOptionsDiv.hide()
            pickedRewardDiv.hide()
            exhaustedDiv.hide()
            interrogateButton.hide()
            flyButton.hide()

            var flipped = lostChildFound != result.found
            lostChildFound = result.found
            if ($("#lostChildModal").is(":visible") && flipped) {
                refreshLostChildAnimation()
            }

            if (result.found) {
                if (result.pickedAwardDescription) {
                    $("#lostChildPickedRewardText").text(result.pickedAwardDescription)
                    pickedRewardDiv.show()
                } else {
                    getLostChildAwardOptionsTable()
                    awardOptionsDiv.show()
                }
            } else {
                if (result.attemptsLeft <= 0) {
                    $("#lostChildExhaustedCity").text(result.targetCity)
                    exhaustedDiv.show()
                } else {
                    if (result.interrogationsLeft > 0) {
                        interrogateButton.show()
                    }
                    flyButton.show()
                    var disableFly = result.isHq && result.guesses.length === 0
                    flyButton.toggleClass('disabled', disableFly).css('opacity', disableFly ? '0.4' : '')
                }
            }
            if (result.isHq || result.guesses.length > 0) {
                $("#lostChildButton").show()
            }
        },
        error: function(jqXHR, textStatus, errorThrown) {
            console.log(JSON.stringify(jqXHR));
            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    });
}

function showLostChildAttemptStatus() {
    refreshLostChildAnimation()
    $('#lostChildModal').fadeIn(200)
}

function refreshLostChildAnimation() {
    removeFootprints($('#lostChildModal'))
    removeConfetti($('#lostChildModal'))
    if (lostChildFound) {
        showConfetti($("#lostChildModal"))
    } else {
        putFootprints($("#lostChildModal"), 15)
    }
}

function getLostChildAwardOptionsTable() {
    var url = "/lost-child/award-options/" + activeAirline.id
    var table = $("#lostChildRewardOptionsTable")
    table.children(".table-row").remove()

    $.ajax({
        type: 'GET',
        url: url,
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(result) {
            $.each(result, function(index, option) {
                var row = $("<div class='table-row'></div>")
                row.append("<div class='cell'><a href='#' class='round-button tick' onclick=pickLostChildAward(" + option.id + ")></a></div>")
                row.append("<div class='cell label'>" + option.description + "</div>")
                table.append(row)
            });
        },
        error: function(jqXHR, textStatus, errorThrown) {
            console.log(JSON.stringify(jqXHR));
            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    });
}

function pickLostChildAward(optionId) {
    var url = "/lost-child/pick-award/" + activeAirline.id + "/" + optionId
    $.ajax({
        type: 'POST',
        url: url,
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(result) {
            updateLostChildModal()
            updateAirlineInfo(activeAirline.id)
        },
        error: function(jqXHR, textStatus, errorThrown) {
            console.log(JSON.stringify(jqXHR));
            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    });
}

function interrogateLostChild() {
    var url = "/lost-child/interrogate/" + activeAirline.id
    $.ajax({
        type: 'POST',
        url: url,
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(result) {
            updateLostChildModal()
        },
        error: function(jqXHR, textStatus, errorThrown) {
            console.log(JSON.stringify(jqXHR));
            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    });
}

function flyLostChild() {
    if ($("#lostChildModal .flyButton").hasClass('disabled')) return
    var url = "/lost-child/guess/" + activeAirportId + "/" + activeAirline.id
    $.ajax({
        type: 'POST',
        url: url,
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(result) {
            updateLostChildModal()
        },
        error: function(jqXHR, textStatus, errorThrown) {
            console.log(JSON.stringify(jqXHR));
            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    });
}

function putFootprints(container, count) {
    removeFootprints(container)
    var icons = ["🧒", "👣", "🔍", "🎒", "❓"]
    for (var i = 0; i < count; i++) {
        var fp = $("<div class='snowflake'>" + icons[Math.floor(Math.random() * icons.length)] + "</div>").appendTo(container)
        var depth = Math.floor(Math.random() * 5) + 1
        fp.css("animation-name", "snowflakes-fall,snowflakes-shake-" + depth + ", snowflakes-shimmer")
        fp.css("animation-duration", depth * 10 + "s," + depth * 3 + "s," + depth * 10 + "s")
        fp.css("left", Math.floor(Math.random() * 101) + "%")
        fp.css("animation-delay", Math.random() * 10 + "s," + Math.random() * -10 + "s")
        fp.css("font-size", (7 - depth) / 2 + "em")
    }
}

function removeFootprints(container) {
    container.children(".snowflake").remove()
}
