package com.example.domain

enum class DiscomRegion(val label: String) {
    DELHI("Delhi"),
    MAHARASHTRA("Maharashtra"),
    NORTH("UP, Bihar, Rajasthan & Haryana"),
    SOUTH_EAST("South & East India")
}

data class IndianDiscom(
    val id: String,
    val shortName: String,
    val fullName: String,
    val region: DiscomRegion,
    val consumerLabel: String,
    val portalUrl: String
)

object DiscomCatalog {
    val allDiscoms: List<IndianDiscom> = listOf(
        // Delhi
        IndianDiscom(
            id = "brpl",
            shortName = "BSES Rajdhani (BRPL)",
            fullName = "BSES Rajdhani Power Limited - Delhi",
            region = DiscomRegion.DELHI,
            consumerLabel = "CA Number",
            portalUrl = "https://www.bsesdelhi.com/web/brpl/quick-pay"
        ),
        IndianDiscom(
            id = "bypl",
            shortName = "BSES Yamuna (BYPL)",
            fullName = "BSES Yamuna Power Limited - Delhi",
            region = DiscomRegion.DELHI,
            consumerLabel = "CA Number",
            portalUrl = "https://www.bsesdelhi.com/web/bypl/quick-pay"
        ),
        IndianDiscom(
            id = "tpddl",
            shortName = "Tata Power-DDL",
            fullName = "Tata Power Delhi Distribution Limited",
            region = DiscomRegion.DELHI,
            consumerLabel = "CA Number",
            portalUrl = "https://www.tatapower-ddl.com/customer/paybillonline"
        ),
        // Maharashtra
        IndianDiscom(
            id = "msedcl",
            shortName = "MSEDCL (Mahavitaran)",
            fullName = "Maharashtra State Electricity Distribution Co.",
            region = DiscomRegion.MAHARASHTRA,
            consumerLabel = "Consumer Number",
            portalUrl = "https://wss.mahadiscom.in/wss/wss?uiActionName=getViewPayBill"
        ),
        IndianDiscom(
            id = "adani_mh",
            shortName = "Adani Electricity Mumbai",
            fullName = "Adani Electricity Mumbai Limited",
            region = DiscomRegion.MAHARASHTRA,
            consumerLabel = "Account Number",
            portalUrl = "https://www.adanielectricity.com/quick-bill-pay"
        ),
        IndianDiscom(
            id = "tata_mh",
            shortName = "Tata Power Mumbai",
            fullName = "The Tata Power Company - Mumbai",
            region = DiscomRegion.MAHARASHTRA,
            consumerLabel = "Consumer Number",
            portalUrl = "https://customerportal.tatapower.com/"
        ),
        // North India: UP, Bihar, Rajasthan, Haryana
        IndianDiscom(
            id = "uppcl",
            shortName = "UPPCL (Uttar Pradesh)",
            fullName = "Uttar Pradesh Power Corporation Limited",
            region = DiscomRegion.NORTH,
            consumerLabel = "Account ID",
            portalUrl = "https://www.uppcl.org"
        ),
        IndianDiscom(
            id = "nbpdcl",
            shortName = "NBPDCL (North Bihar)",
            fullName = "North Bihar Power Distribution Company",
            region = DiscomRegion.NORTH,
            consumerLabel = "CA Number",
            portalUrl = "https://nbpdcl.co.in/"
        ),
        IndianDiscom(
            id = "sbpdcl",
            shortName = "SBPDCL (South Bihar)",
            fullName = "South Bihar Power Distribution Company",
            region = DiscomRegion.NORTH,
            consumerLabel = "CA Number",
            portalUrl = "https://sbpdcl.co.in/"
        ),
        IndianDiscom(
            id = "jvvnl",
            shortName = "JVVNL (Jaipur)",
            fullName = "Jaipur Vidyut Vitran Nigam Limited",
            region = DiscomRegion.NORTH,
            consumerLabel = "K Number",
            portalUrl = "https://energy.rajasthan.gov.in/jvvnl"
        ),
        IndianDiscom(
            id = "avvnl",
            shortName = "AVVNL (Ajmer)",
            fullName = "Ajmer Vidyut Vitran Nigam Limited",
            region = DiscomRegion.NORTH,
            consumerLabel = "K Number",
            portalUrl = "https://energy.rajasthan.gov.in/avvnl"
        ),
        IndianDiscom(
            id = "jdvvnl",
            shortName = "JdVVNL (Jodhpur)",
            fullName = "Jodhpur Vidyut Vitran Nigam Limited",
            region = DiscomRegion.NORTH,
            consumerLabel = "K Number",
            portalUrl = "https://energy.rajasthan.gov.in/jdvvnl"
        ),
        IndianDiscom(
            id = "dhbvn",
            shortName = "DHBVN (South Haryana)",
            fullName = "Dakshin Haryana Bijli Vitran Nigam",
            region = DiscomRegion.NORTH,
            consumerLabel = "Account Number",
            portalUrl = "https://epayment.dhbvn.org.in/"
        ),
        IndianDiscom(
            id = "uhbvn",
            shortName = "UHBVN (North Haryana)",
            fullName = "Uttar Haryana Bijli Vitran Nigam",
            region = DiscomRegion.NORTH,
            consumerLabel = "Account Number",
            portalUrl = "https://epayment.uhbvn.org.in/"
        ),
        // South & East India
        IndianDiscom(
            id = "bescom",
            shortName = "BESCOM (Bengaluru)",
            fullName = "Bangalore Electricity Supply Company",
            region = DiscomRegion.SOUTH_EAST,
            consumerLabel = "Account ID",
            portalUrl = "https://www.bescom.co.in/bescom/quick-payment"
        ),
        IndianDiscom(
            id = "tangedco",
            shortName = "TANGEDCO / TNPDCL",
            fullName = "Tamil Nadu Power Distribution Corporation",
            region = DiscomRegion.SOUTH_EAST,
            consumerLabel = "Consumer Number",
            portalUrl = "https://www.tnpdcl.org/"
        ),
        IndianDiscom(
            id = "wbsedcl",
            shortName = "WBSEDCL (West Bengal)",
            fullName = "West Bengal State Electricity Distribution Co.",
            region = DiscomRegion.SOUTH_EAST,
            consumerLabel = "Consumer ID",
            portalUrl = "https://www.wbsedcl.in/irj/go/km/docs/internet/new_website/ViewBill.html"
        ),
        IndianDiscom(
            id = "cesc",
            shortName = "CESC (Kolkata)",
            fullName = "Calcutta Electric Supply Corporation",
            region = DiscomRegion.SOUTH_EAST,
            consumerLabel = "Customer ID",
            portalUrl = "https://www.cesc.co.in/"
        )
    )

    fun findById(id: String?): IndianDiscom? {
        if (id.isNullOrBlank()) return null
        return allDiscoms.firstOrNull { it.id == id }
    }
}
