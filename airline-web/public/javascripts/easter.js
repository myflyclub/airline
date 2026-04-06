//need to set flag in christmas.js & run chritmas JVM app
var easterBunnyFound = false

function initEasterBunny() {
    $('#easterBunnyModal').hide()
    $("#easterBunnyButton").hide()
    if (activeAirline) {
        updateEasterBunnyModal()
    }
}

function closeEasterBunnyModal() {
    closeModal($('#easterBunnyModal'))
    removeEasterEggs($('#easterBunnyModal'))
    removeConfetti($('#easterBunnyModal'))
}

function updateEasterBunnyModal() {
    var table = $("#easterBunnyGuessTable")
    table.children(".table-row").remove()

    var url = "/easter-bunny/attempt-status/" + activeAirportId + "/" + activeAirline.id
    $.ajax({
        type: 'GET',
        url: url,
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(result) {
            if ($.isEmptyObject(result)) {
                return
            }
            $("#easterBunnyAttemptsLeft").text(result.attemptsLeft)
            if (typeof result.difficulty === 'undefined') {
                $("#easterBunnyModal .difficultyOptions").show();
                $("#easterBunnyModal .difficultyPick").hide();
            } else {
                $("#easterBunnyModal .difficultyOptions").hide();
                $("#easterBunnyModal .difficultyPick").show();
                $("#easterBunnyModal .difficultyLabel").text(result.difficulty)
            }

            $.each(result.guesses, function(index, guess) {
                var row = $("<div class='table-row'></div>")
                row.append("<div class='cell label'>" + getAirportText(guess.city, guess.airportCode) + "</div>")
                row.append("<div class='cell label'>" + guess.distanceText + "</div>")
                table.append(row)
            });
            if (result.guesses.length == 0) {
                var row = $("<div class='table-row'></div>")
                row.append("<div class='cell label'>-</div>")
                row.append("<div class='cell label'>-</div>")
                table.append(row)
            }

            $("#easterBunnyAttemptsLeft").text(result.attemptsLeft)

            var awardOptionsDiv = $("#easterBunnyRewardOptions")
            var pickedRewardDiv = $("#easterBunnyPickedReward")
            var exhaustedDiv = $("#easterBunnyExhausted")
            var guessButton = $("#easterBunnyModal .guessButton")
            awardOptionsDiv.hide()
            pickedRewardDiv.hide()
            exhaustedDiv.hide()
            guessButton.hide()

            var flipped = easterBunnyFound != result.found
            easterBunnyFound = result.found
            if ($("#easterBunnyModal").is(":visible") && flipped) {
                refreshEasterBackgroundAnimation()
            }

            if (result.found) {
                if (result.pickedAwardDescription) {
                    $("#easterBunnyPickedRewardText").text(result.pickedAwardDescription)
                    pickedRewardDiv.show()
                } else {
                    getEasterAwardOptionsTable()
                    awardOptionsDiv.show()
                }
            } else {
                if (result.attemptsLeft <= 0) {
                    $("#easterBunnyExhausted").show()
                } else {
                    $("#easterBunnyModal .guessButton").show()
                }
            }
            $("#easterBunnyButton").show()
        },
        error: function(jqXHR, textStatus, errorThrown) {
            console.log(JSON.stringify(jqXHR));
            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    });
}

function showEasterBunnyAttemptStatus() {
    refreshEasterBackgroundAnimation()
    $('#easterBunnyModal').fadeIn(200)
}

function refreshEasterBackgroundAnimation() {
    removeEasterEggs($('#easterBunnyModal'))
    removeConfetti($('#easterBunnyModal'))
    if (easterBunnyFound) {
        showConfetti($("#easterBunnyModal"))
    } else {
        putEasterEggs($("#easterBunnyModal"), 20)
    }
}

function getEasterAwardOptionsTable() {
    var url = "/easter-bunny/award-options/" + activeAirline.id
    var table = $("#easterBunnyRewardOptionsTable")
    table.children(".table-row").remove()

    $.ajax({
        type: 'GET',
        url: url,
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(result) {
            $.each(result, function(index, option) {
                var row = $("<div class='table-row'></div>")
                row.append("<div class='cell'><a href='#' class='round-button tick' onclick=pickEasterBunnyAward(" + option.id + ")></a></div>")
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

function pickEasterBunnyAward(optionId) {
    var url = "/easter-bunny/pick-award/" + activeAirline.id + "/" + optionId
    $.ajax({
        type: 'POST',
        url: url,
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(result) {
            updateEasterBunnyModal()
            updateAirlineInfo(activeAirline.id)
        },
        error: function(jqXHR, textStatus, errorThrown) {
            console.log(JSON.stringify(jqXHR));
            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    });
}

function guessEasterBunny() {
    var url = "/easter-bunny/guess/" + activeAirportId + "/" + activeAirline.id + "/" + $("#easterBunnyModal .easterDifficulty:checked").val()

    $.ajax({
        type: 'POST',
        url: url,
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(result) {
            updateEasterBunnyModal()
        },
        error: function(jqXHR, textStatus, errorThrown) {
            console.log(JSON.stringify(jqXHR));
            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    });
}


function putEasterEggs(container, eggCount) {
    removeEasterEggs(container)
    var eggs = ["🥚", "🐣", "🐰", "🌸", "🌷"]
    for (var i = 0; i < eggCount; i++) {
        var egg = $("<div class='snowflake'>" + eggs[Math.floor(Math.random() * eggs.length)] + "</div>").appendTo(container)

        var depth = Math.floor(Math.random() * 5) + 1
        egg.css("animation-name", "snowflakes-fall,snowflakes-shake-" + depth + ", snowflakes-shimmer")
        egg.css("animation-duration", depth * 10 + "s," + depth * 3 + "s," + depth * 10 + "s")
        egg.css("left", Math.floor(Math.random() * 101) + "%")
        egg.css("animation-delay", Math.random() * 10 + "s," + Math.random() * -10 + "s")
        egg.css("font-size", (7 - depth) / 2 + "em")
    }
}

function removeEasterEggs(container) {
    container.children(".snowflake").remove()
}
