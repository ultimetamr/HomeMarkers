package com.spatialapps.homemarkers.domain

object MarkerSearch {
    fun filter(markers: List<HomeMarker>, query: String): List<HomeMarker> {
        val key = query.trim().lowercase(); if (key.isEmpty()) return markers
        return markers.filter { marker -> listOf(marker.name, marker.location, marker.note, marker.color.label).any { value -> value.lowercase().contains(key) || isSubsequence(key, value.lowercase()) } }
    }
    private fun isSubsequence(needle: String, haystack: String): Boolean { var pos = 0; haystack.forEach { if (pos < needle.length && it == needle[pos]) pos++ }; return pos == needle.length }
}
