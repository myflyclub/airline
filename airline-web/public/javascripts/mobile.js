function showExtendedTopBarDetails() {
    let isHidden = $(".extendedTopBarDetails").is(":hidden");
    if (isHidden) {
        $("#moreTopBarTab").css('transform', 'translateY(40px)');
        $(".extendedTopBarDetails").slideDown("slow", function(){
            isHidden = false;
            setTimeout(function() {
                $("#moreTopBarTab").css('transform', '');
                $(".extendedTopBarDetails").slideUp("slow", function() {});
                isHidden = true;
            }, 12000);
        });
    } else {
        $(".extendedTopBarDetails").slideUp("slow", function() {});
        $("#moreTopBarTab").css('transform', '');
        isHidden = true;
    }
}