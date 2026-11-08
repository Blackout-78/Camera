package com.blackout.camera.settings

/** Icon drawings, in 108×108 adaptive-icon units. Generated together with the app drawer icons so both match. */
object IconShapes {
    class Shape(val stroke: Float, val onBackground: Boolean, val path: String)
    val STYLES: Map<String, Pair<Boolean, List<Shape>>> = mapOf(
        "A" to Pair(true, listOf(
            Shape(5.0f, false, "M38,40h6l3,-5h14l3,5h6a4,4 0 0 1 4,4v24a4,4 0 0 1 -4,4h-32a4,4 0 0 1 -4,-4v-24a4,4 0 0 1 4,-4z"),
            Shape(4.0f, false, "M45,56a9,9 0 1 0 18,0a9,9 0 1 0 -18,0z"),
            Shape(0f, false, "M51,56a3,3 0 1 0 6,0a3,3 0 1 0 -6,0z"),
        )),
        "B" to Pair(false, listOf(
            Shape(2.4f, false, "M22.5,54a31.5,31.5 0 1 0 63.0,0a31.5,31.5 0 1 0 -63.0,0z"),
            Shape(3.5f, false, "M38,40h6l3,-5h14l3,5h6a4,4 0 0 1 4,4v24a4,4 0 0 1 -4,4h-32a4,4 0 0 1 -4,-4v-24a4,4 0 0 1 4,-4z"),
            Shape(3.0f, false, "M46,56a8,8 0 1 0 16,0a8,8 0 1 0 -16,0z"),
            Shape(0f, false, "M51.5,56a2.5,2.5 0 1 0 5.0,0a2.5,2.5 0 1 0 -5.0,0z"),
        )),
        "C" to Pair(true, listOf(
            Shape(5.0f, false, "M32,54a22,22 0 1 0 44,0a22,22 0 1 0 -44,0z"),
            Shape(4.0f, false, "M41,54a13,13 0 1 0 26,0a13,13 0 1 0 -26,0z"),
            Shape(0f, false, "M49,54a5,5 0 1 0 10,0a5,5 0 1 0 -10,0z"),
            Shape(0f, false, "M74,31a3,3 0 1 0 6,0a3,3 0 1 0 -6,0z"),
        )),
        "D" to Pair(true, listOf(
            Shape(0f, false, "M29,54a25,25 0 1 0 50,0a25,25 0 1 0 -50,0z"),
            Shape(0f, true, "M64.0,54.0L59.0,62.7L49.0,62.7L44.0,54.0L49.0,45.3L59.0,45.3z"),
            Shape(2.5f, true, "M64.0,54.0L66.7,74.4M59.0,62.7L42.7,75.2M49.0,62.7L30.0,54.8M44.0,54.0L41.3,33.6M49.0,45.3L65.3,32.8M59.0,45.3L78.0,53.2"),
        )),
    )
    val NAMES = mapOf("A" to "Classic", "B" to "Ring", "C" to "Big lens", "D" to "Shutter")
}
