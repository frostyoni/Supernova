package com.supernova.launcher.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

// A page transition a person writes, not one of the fixed effects: a little
// timeline of chips on two lanes, incoming and outgoing, over the swipe's
// 0..1 progress. A chip moves one channel from one value to another between
// two times, with an easing; chips may overlap and stack.
//
// The values are portable: move is a share of the page's width/height,
// scale 1 is normal, alpha 1 is opaque, roll/pitch/yaw are degrees. The
// incoming lane is the page arriving from the right (a forward swipe); the
// outgoing lane is the page leaving to the left. A backward swipe uses the
// two lanes by sign, so a fully symmetric transition should mirror them.
@Serializable
data class CustomTransition(
    val version: Int = 1,
    val name: String = "Custom",
    val incoming: List<Chip> = emptyList(),
    val outgoing: List<Chip> = emptyList()
) {

    @Serializable
    enum class Channel { MOVE_X, MOVE_Y, SCALE, ALPHA, ROLL, PITCH, YAW }

    @Serializable
    enum class Ease { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, OVERSHOOT }

    @Serializable
    data class Chip(
        val at: Float = 0f,
        val to: Float = 1f,
        val channel: Channel = Channel.ALPHA,
        val from: Float = 1f,
        val value: Float = 1f,
        val ease: Ease = Ease.LINEAR,
        // Add to the channel instead of setting it, for stacking.
        val add: Boolean = false
    )

    // What a lane says a page should look like at one point in the swipe.
    class Frame {
        var moveX = 0f
        var moveY = 0f
        var scale = 1f
        var alpha = 1f
        var roll = 0f
        var pitch = 0f
        var yaw = 0f
    }

    fun sample(incomingLane: Boolean, progress: Float): Frame {
        val frame = Frame()
        val time = progress.coerceIn(0f, 1f)
        (if (incomingLane) incoming else outgoing).forEach { chip ->
            // A chip does nothing before it starts; once it has ended its
            // last value stands, so a fade out stays out.
            if (time < chip.at) return@forEach
            val local = if (time >= chip.to) {
                1f
            } else {
                ((time - chip.at) / (chip.to - chip.at)).coerceIn(0f, 1f)
            }
            val eased = ease(chip.ease, local)
            val value = chip.from + (chip.value - chip.from) * eased
            frame.combine(chip.channel, value, chip.add)
        }
        return frame
    }

    // A spec from a share sheet, a paste or a backup: null when it is not one
    // this build can read or when a chip is out of range.
    fun validated(): CustomTransition? {
        if (version != 1) return null
        if (incoming.size > MAX_CHIPS || outgoing.size > MAX_CHIPS) return null
        val chips = incoming + outgoing
        if (chips.any { chip ->
                !chip.at.isFinite() || !chip.to.isFinite() ||
                    !chip.from.isFinite() || !chip.value.isFinite() ||
                    chip.at < 0f || chip.to > 1f || chip.to <= chip.at
            }
        ) {
            return null
        }
        return this
    }

    companion object {
        const val MAX_CHIPS = 64
        const val MAX_PRESETS = 24

        // Pretty, so a shared spec is readable in a chat or an issue.
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }

        fun parse(text: String): CustomTransition? =
            runCatching { json.decodeFromString<CustomTransition>(text).validated() }.getOrNull()

        fun encode(transition: CustomTransition): String = json.encodeToString(transition)

        // The user's saved presets, as one JSON array. A bad entry is dropped
        // rather than failing the whole list.
        private val listSerializer = ListSerializer(CustomTransition.serializer())
        fun encodeList(transitions: List<CustomTransition>): String =
            json.encodeToString(listSerializer, transitions)
        fun parseList(text: String): List<CustomTransition> =
            runCatching { json.decodeFromString(listSerializer, text) }
                .getOrDefault(emptyList())
                .mapNotNull { it.validated() }
                .take(MAX_PRESETS)

        // Slide, roll, a small jump and a fade: the example a new spec starts
        // from, and the one the Labs dialog offers when nothing is saved.
        val example = CustomTransition(
            name = "Nod",
            incoming = listOf(
                // No Move X here: the pager already slides the page in, and an
                // offset that was 1w at the rest end snapped the page when it
                // became the current one. The lane is an extra on top of that
                // slide and must be identity at rest.
                // Out and back, so the page is upright and at rest when it
                // settles; a roll that only went out left a late correction.
                Chip(0.2f, 0.45f, Channel.ROLL, 0f, 10f, Ease.EASE_OUT),
                Chip(0.45f, 0.8f, Channel.ROLL, 10f, 0f, Ease.EASE_IN_OUT),
                Chip(0.35f, 0.55f, Channel.SCALE, 1f, 1.04f, Ease.EASE_OUT),
                Chip(0.55f, 0.8f, Channel.SCALE, 1.04f, 1f, Ease.EASE_IN_OUT)
            ),
            outgoing = listOf(
                Chip(0.35f, 1f, Channel.ALPHA, 1f, 0f, Ease.EASE_IN),
                Chip(0f, 0.55f, Channel.MOVE_X, 0f, -0.15f, Ease.EASE_OUT)
            )
        )

    }
}

private fun ease(ease: CustomTransition.Ease, t: Float): Float = when (ease) {
    CustomTransition.Ease.LINEAR -> t
    CustomTransition.Ease.EASE_IN -> t * t
    CustomTransition.Ease.EASE_OUT -> 1f - (1f - t) * (1f - t)
    CustomTransition.Ease.EASE_IN_OUT ->
        if (t < 0.5f) 2f * t * t else 1f - 2f * (1f - t) * (1f - t)
    CustomTransition.Ease.OVERSHOOT -> {
        val u = t - 1f
        1f + 2.70158f * u * u * u + 1.70158f * u * u
    }
}

private fun CustomTransition.Frame.combine(
    channel: CustomTransition.Channel,
    value: Float,
    add: Boolean
) {
    fun put(current: Float): Float = if (add) current + value else value
    when (channel) {
        CustomTransition.Channel.MOVE_X -> moveX = put(moveX)
        CustomTransition.Channel.MOVE_Y -> moveY = put(moveY)
        CustomTransition.Channel.SCALE -> scale = put(scale)
        CustomTransition.Channel.ALPHA -> alpha = put(alpha)
        CustomTransition.Channel.ROLL -> roll = put(roll)
        CustomTransition.Channel.PITCH -> pitch = put(pitch)
        CustomTransition.Channel.YAW -> yaw = put(yaw)
    }
}
