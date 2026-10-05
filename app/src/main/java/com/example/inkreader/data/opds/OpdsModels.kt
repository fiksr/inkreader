package com.example.inkreader.data.opds

data class OpdsCatalog(
    val id: String,
    val name: String,
    val url: String,
    val description: String = "",
    val username: String = "",
    val password: String = "",
    val isDefault: Boolean = false
)

data class OpdsAcquisitionLink(
    val href: String,
    val type: String,
    val rel: String = "http://opds-spec.org/acquisition",
    val title: String = ""
)

data class OpdsLink(
    val rel: String,
    val href: String,
    val type: String = "",
    val title: String = ""
)

data class OpdsEntry(
    val id: String,
    val title: String,
    val author: String = "Unknown",
    val summary: String = "",
    val published: String = "",
    val coverUrl: String? = null,
    val thumbnailUrl: String? = null,
    val acquisitionLinks: List<OpdsAcquisitionLink> = emptyList(),
    val navigationHref: String? = null,
    val isNavigationOnly: Boolean = false
) {
    val epubLink: OpdsAcquisitionLink?
        get() = acquisitionLinks.find {
            it.type.contains("epub", ignoreCase = true) || it.href.endsWith(".epub", ignoreCase = true)
        } ?: acquisitionLinks.firstOrNull()
}

data class OpdsFeed(
    val title: String,
    val subtitle: String = "",
    val entries: List<OpdsEntry> = emptyList(),
    val navigationLinks: List<OpdsLink> = emptyList(),
    val searchHref: String? = null,
    val nextPageHref: String? = null
)
