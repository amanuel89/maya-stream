package com.nuvio.tv.core.player

enum class StreamSelectionPolicy {
    FAST_START,
    BALANCED,
    QUALITY_FIRST,
    TORRENT_SAFE,
    MOBILE_SAVER;

    val weights: PolicyWeights get() = POLICY_WEIGHTS.getValue(this)

    val idealResolution: Int
        get() = when (this) {
            FAST_START -> 720
            BALANCED, QUALITY_FIRST, TORRENT_SAFE -> 1080
            MOBILE_SAVER -> 720
        }

    val minDominantScore: Double
        get() = when (this) {
            FAST_START, MOBILE_SAVER -> 85.0
            BALANCED -> 82.0
            QUALITY_FIRST -> 80.0
            TORRENT_SAFE -> 78.0
        }

    val dominantConfidenceGap: Double
        get() = 12.0

    val highDeliveryTierThreshold: Int
        get() = 90

    val skipViabilityProbeByDefault: Boolean
        get() = this == MOBILE_SAVER

    companion object {
        private val POLICY_WEIGHTS: Map<StreamSelectionPolicy, PolicyWeights> = mapOf(
            FAST_START to PolicyWeights(
                delivery = 0.45,
                quality = 0.20,
                size = 0.20,
                health = 0.10,
                penalty = 0.05
            ),
            BALANCED to PolicyWeights(
                delivery = 0.30,
                quality = 0.30,
                size = 0.20,
                health = 0.10,
                penalty = 0.10
            ),
            QUALITY_FIRST to PolicyWeights(
                delivery = 0.20,
                quality = 0.45,
                size = 0.15,
                health = 0.10,
                penalty = 0.10
            ),
            TORRENT_SAFE to PolicyWeights(
                delivery = 0.25,
                quality = 0.20,
                size = 0.15,
                health = 0.35,
                penalty = 0.05
            ),
            MOBILE_SAVER to PolicyWeights(
                delivery = 0.40,
                quality = 0.15,
                size = 0.35,
                health = 0.05,
                penalty = 0.05
            )
        )

        fun fromStoredName(name: String?): StreamSelectionPolicy {
            if (name.isNullOrBlank()) return FAST_START
            return entries.firstOrNull { it.name == name } ?: FAST_START
        }
    }
}

data class PolicyWeights(
    val delivery: Double,
    val quality: Double,
    val size: Double,
    val health: Double,
    val penalty: Double
) {
    init {
        val sum = delivery + quality + size + health + penalty
        require(kotlin.math.abs(sum - 1.0) < 0.001) { "Policy weights must sum to 1.0, got $sum" }
    }
}
