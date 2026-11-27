package com.wrapvision.app

/**
 * Canonical class order exported with the trained WrapVision RF-DETR model.
 * The 24th labels output channel is intentionally not mapped here: the
 * exported tensor has 24 channels while class_names.json contains 23 names.
 * Its meaning must be confirmed from the export/post-processing code.
 */
object WrapVisionClassMap {
    val classNames: List<String> = listOf(
        "back_bumper",
        "back_door",
        "back_glass",
        "back_left_door",
        "back_left_light",
        "back_light",
        "back_right_door",
        "back_right_light",
        "front_bumper",
        "front_door",
        "front_glass",
        "front_left_door",
        "front_left_light",
        "front_light",
        "front_right_door",
        "front_right_light",
        "hood",
        "left_mirror",
        "object",
        "right_mirror",
        "tailgate",
        "trunk",
        "wheel"
    )

    /** Panels suitable for applying a body-wrap color; excludes glass, lamps, wheels and generic object. */
    val bodyPanelClasses: Set<String> = setOf(
        "back_bumper",
        "back_door",
        "back_left_door",
        "back_right_door",
        "front_bumper",
        "front_door",
        "front_left_door",
        "front_right_door",
        "hood",
        "left_mirror",
        "right_mirror",
        "tailgate",
        "trunk"
    )

    fun classNameOrNull(channel: Int): String? =
        classNames.getOrNull(channel)

    fun shouldApplyWrap(channel: Int, wholeBodyMode: Boolean): Boolean {
        val name = classNameOrNull(channel) ?: return false
        return if (wholeBodyMode) name in bodyPanelClasses else name != "object"
    }
}
