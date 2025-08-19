function showExtendedTopBarDetails() {
    if ($('.extendedTopBarDetails').is(":hidden")) {
        $(".extendedTopBarDetails").slideDown("slow", function(){
            setTimeout(function() {
                $(".extendedTopBarDetails").slideUp("slow")
            }, 8000)
        })
    } else {
        $(".extendedTopBarDetails").slideUp("slow")
    }
}